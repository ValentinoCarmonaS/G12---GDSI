package com.easyeat.inventory.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.IngredientBatch;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.inventory.exception.ZeroDeltaAdjustmentException;
import com.easyeat.inventory.repository.IngredientBatchRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class StockAdjustmentService {

    private final IngredientRepository ingredientRepository;
    private final IngredientBatchRepository ingredientBatchRepository;
    private final StockAuditEntryRepository stockAuditEntryRepository;
    private final UserRepository userRepository;

    public StockAdjustmentService(IngredientRepository ingredientRepository,
                                  IngredientBatchRepository ingredientBatchRepository,
                                  StockAuditEntryRepository stockAuditEntryRepository,
                                  UserRepository userRepository) {
        this.ingredientRepository = ingredientRepository;
        this.ingredientBatchRepository = ingredientBatchRepository;
        this.stockAuditEntryRepository = stockAuditEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StockAdjustmentResponse adjustStock(UUID ingredientId, StockAdjustmentRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        Ingredient ingredient = ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found: " + ingredientId));

        BigDecimal previousStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        StockAdjustmentAction action = request.resolveAction();
        BigDecimal rawQuantity = request.resolveQuantity();

        if (ingredient.getStatus() == IngredientStatus.DISCONTINUED) {
            if (action == StockAdjustmentAction.ADD) {
                throw new InvalidLifecycleTransitionException("Stock additions are not permitted on DISCONTINUED ingredients.");
            }
            if (request.mode() == StockAdjustmentMode.ABSOLUTE && request.value() != null && request.value().compareTo(previousStock) > 0) {
                throw new InvalidLifecycleTransitionException("Stock additions are not permitted on DISCONTINUED ingredients.");
            }
        }

        if (rawQuantity != null && ingredient.getUnitOfMeasure() == UnitOfMeasure.U) {
            if (rawQuantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Discrete unit 'u' accepts only whole integer quantities.");
            }
        }

        BigDecimal newStock;
        BigDecimal netDelta;
        UUID batchId = null;
        BigDecimal unitCost = null;
        BigDecimal financialLoss = null;
        StockAdjustmentMode auditMode;

        if (request.action() != null) {
            if (rawQuantity == null || rawQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero.");
            }
            BigDecimal quantity = rawQuantity.setScale(4, RoundingMode.HALF_UP);

            if (request.action() == StockAdjustmentAction.ADD) {
                auditMode = StockAdjustmentMode.ADD;
                if (request.unitCost() == null || request.unitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Unit purchase cost is required for stock additions and must be non-negative.");
                }
                unitCost = request.unitCost().setScale(4, RoundingMode.HALF_UP);

                IngredientBatch batch = new IngredientBatch(tenantId, ingredientId, quantity, unitCost);
                IngredientBatch savedBatch = ingredientBatchRepository.save(batch);
                batchId = savedBatch.getId();

                if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
                    ingredient.setUnitPurchaseCost(unitCost);
                }
                netDelta = quantity;
                newStock = previousStock.add(netDelta);
            } else if (request.action() == StockAdjustmentAction.SUBTRACT) {
                auditMode = StockAdjustmentMode.SUBTRACT;
                if (request.batchId() == null) {
                    throw new IllegalArgumentException("Batch ID is required for stock subtractions.");
                }
                final UUID targetBatchId = request.batchId();
                batchId = targetBatchId;

                IngredientBatch batch = ingredientBatchRepository.findByIdAndTenantIdForUpdate(targetBatchId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Batch not found: " + targetBatchId));

                if (!batch.getIngredientId().equals(ingredientId)) {
                    throw new IllegalArgumentException("Target batch does not belong to ingredient: " + ingredientId);
                }

                if (batch.getRemainingQuantity().compareTo(quantity) < 0) {
                    throw new NegativeStockException("Subtracted quantity (" + quantity + ") exceeds available batch balance (" + batch.getRemainingQuantity() + ").");
                }

                netDelta = quantity.negate();
                newStock = previousStock.add(netDelta);
                if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                    throw new NegativeStockException(previousStock, netDelta, newStock);
                }

                batch.setRemainingQuantity(batch.getRemainingQuantity().subtract(quantity));
                ingredientBatchRepository.save(batch);

                unitCost = batch.getUnitCost();
                financialLoss = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            } else {
                throw new IllegalArgumentException("Unsupported action: " + request.action());
            }
        } else if (request.mode() == StockAdjustmentMode.ABSOLUTE) {
            auditMode = StockAdjustmentMode.ABSOLUTE;
            newStock = request.value().setScale(4, RoundingMode.HALF_UP);
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new NegativeStockException(previousStock, newStock.subtract(previousStock), newStock);
            }
            netDelta = newStock.subtract(previousStock);
            if (netDelta.compareTo(BigDecimal.ZERO) == 0) {
                throw new ZeroDeltaAdjustmentException(ingredientId);
            }
            if (netDelta.compareTo(BigDecimal.ZERO) > 0) {
                unitCost = request.unitCost() != null ? request.unitCost().setScale(4, RoundingMode.HALF_UP) : ingredient.getUnitPurchaseCost();
                if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
                    ingredient.setUnitPurchaseCost(unitCost);
                }
                IngredientBatch batch = new IngredientBatch(tenantId, ingredientId, netDelta, unitCost);
                IngredientBatch savedBatch = ingredientBatchRepository.save(batch);
                if (savedBatch != null) {
                    batchId = savedBatch.getId();
                }
            }
        } else if (request.mode() == StockAdjustmentMode.RELATIVE) {
            auditMode = StockAdjustmentMode.RELATIVE;
            netDelta = request.value().setScale(4, RoundingMode.HALF_UP);
            if (netDelta.compareTo(BigDecimal.ZERO) == 0) {
                throw new ZeroDeltaAdjustmentException(ingredientId);
            }
            newStock = previousStock.add(netDelta);
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new NegativeStockException(previousStock, netDelta, newStock);
            }
            if (netDelta.compareTo(BigDecimal.ZERO) > 0) {
                unitCost = request.unitCost() != null ? request.unitCost().setScale(4, RoundingMode.HALF_UP) : ingredient.getUnitPurchaseCost();
                if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
                    ingredient.setUnitPurchaseCost(unitCost);
                }
                IngredientBatch batch = new IngredientBatch(tenantId, ingredientId, netDelta, unitCost);
                IngredientBatch savedBatch = ingredientBatchRepository.save(batch);
                if (savedBatch != null) {
                    batchId = savedBatch.getId();
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported adjustment mode: " + request.mode());
        }

        ingredient.setCurrentStock(newStock);
        ingredient = ingredientRepository.save(ingredient);

        UUID currentUserId = resolveCurrentUserId();

        StockAuditEntry auditEntry = new StockAuditEntry(
                tenantId,
                ingredientId,
                currentUserId,
                auditMode,
                previousStock,
                newStock,
                netDelta,
                request.justification().trim(),
                batchId,
                unitCost,
                financialLoss
        );
        auditEntry = stockAuditEntryRepository.save(auditEntry);

        return new StockAdjustmentResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnitOfMeasure(),
                previousStock,
                newStock,
                netDelta,
                batchId,
                unitCost,
                financialLoss,
                auditEntry.getId(),
                auditEntry.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<IngredientBatchResponse> getActiveBatches(UUID ingredientId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<IngredientBatch> batches = ingredientBatchRepository
                .findByTenantIdAndIngredientIdAndRemainingQuantityGreaterThanOrderByCreatedAtAsc(
                        tenantId, ingredientId, BigDecimal.ZERO
                );

        return batches.stream()
                .map(b -> new IngredientBatchResponse(
                        b.getId(),
                        b.getIngredientId(),
                        b.getInitialQuantity(),
                        b.getRemainingQuantity(),
                        b.getUnitCost(),
                        b.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockAuditEntryResponse> getAuditHistory(UUID ingredientId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        List<StockAuditEntry> entries = stockAuditEntryRepository
                .findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(tenantId, ingredientId);

        Map<UUID, String> userNames = new HashMap<>();

        return entries.stream().map(entry -> {
            String adminName = userNames.computeIfAbsent(entry.getUserId(), id -> {
                if (id == null) return "System";
                return userRepository.findById(id).map(User::getFullName).orElse("Unknown User");
            });

            return new StockAuditEntryResponse(
                    entry.getId(),
                    entry.getIngredientId(),
                    entry.getUserId(),
                    adminName,
                    entry.getAdjustmentMode(),
                    entry.getPreviousStock(),
                    entry.getNewStock(),
                    entry.getNetDelta(),
                    entry.getBatchId(),
                    entry.getUnitCost(),
                    entry.getFinancialLoss(),
                    entry.getJustification(),
                    entry.getCreatedAt()
            );
        }).toList();
    }

    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }

        if (auth.getPrincipal() instanceof User user) {
            return user.getId();
        }

        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        if (auth.getPrincipal() instanceof String principalStr) {
            try {
                return UUID.fromString(principalStr);
            } catch (IllegalArgumentException e) {
                return userRepository.findByEmail(principalStr)
                        .map(User::getId)
                        .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            }
        }

        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
