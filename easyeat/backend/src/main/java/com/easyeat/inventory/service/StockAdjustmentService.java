package com.easyeat.inventory.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.inventory.exception.ZeroDeltaAdjustmentException;
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
    private final StockAuditEntryRepository stockAuditEntryRepository;
    private final UserRepository userRepository;

    public StockAdjustmentService(IngredientRepository ingredientRepository,
                                  StockAuditEntryRepository stockAuditEntryRepository,
                                  UserRepository userRepository) {
        this.ingredientRepository = ingredientRepository;
        this.stockAuditEntryRepository = stockAuditEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StockAdjustmentResponse adjustStock(UUID ingredientId, StockAdjustmentRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        Ingredient ingredient = ingredientRepository.findByIdAndTenantIdForUpdate(ingredientId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found: " + ingredientId));

        if (ingredient.getStatus() == IngredientStatus.DISCONTINUED && request.resolveAction() == StockAdjustmentAction.ADD) {
            throw new InvalidLifecycleTransitionException("Stock additions are not permitted on DISCONTINUED ingredients.");
        }

        if (ingredient.getUnitOfMeasure() == UnitOfMeasure.U) {
            if (request.value().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException("Discrete unit 'u' accepts only whole integer quantities.");
            }
        }

        BigDecimal previousStock = ingredient.getCurrentStock() != null
                ? ingredient.getCurrentStock().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal newStock;
        BigDecimal netDelta;

        if (request.mode() == StockAdjustmentMode.ABSOLUTE) {
            newStock = request.value().setScale(4, RoundingMode.HALF_UP);
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new NegativeStockException(previousStock, newStock.subtract(previousStock), newStock);
            }
            netDelta = newStock.subtract(previousStock);
            if (netDelta.compareTo(BigDecimal.ZERO) == 0) {
                throw new ZeroDeltaAdjustmentException(ingredientId);
            }
        } else if (request.mode() == StockAdjustmentMode.RELATIVE) {
            netDelta = request.value().setScale(4, RoundingMode.HALF_UP);
            if (netDelta.compareTo(BigDecimal.ZERO) == 0) {
                throw new ZeroDeltaAdjustmentException(ingredientId);
            }
            newStock = previousStock.add(netDelta);
            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new NegativeStockException(previousStock, netDelta, newStock);
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
                request.mode(),
                previousStock,
                newStock,
                netDelta,
                request.justification().trim()
        );
        auditEntry = stockAuditEntryRepository.save(auditEntry);

        return new StockAdjustmentResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getUnitOfMeasure(),
                previousStock,
                newStock,
                netDelta,
                auditEntry.getId(),
                auditEntry.getCreatedAt()
        );
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
