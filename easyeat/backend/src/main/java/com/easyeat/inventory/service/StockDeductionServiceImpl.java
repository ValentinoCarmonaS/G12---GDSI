package com.easyeat.inventory.service;

import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientDeductionDetail;
import com.easyeat.inventory.dto.StockAdjustmentMode;
import com.easyeat.inventory.dto.StockDeductionResult;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import com.easyeat.order.dto.OrderItemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class StockDeductionServiceImpl implements StockDeductionService {

    private static final Logger log = LoggerFactory.getLogger(StockDeductionServiceImpl.class);

    private final IngredientRepository ingredientRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final StockAuditEntryRepository stockAuditEntryRepository;

    @Autowired
    public StockDeductionServiceImpl(
            IngredientRepository ingredientRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            StockAuditEntryRepository stockAuditEntryRepository
    ) {
        this.ingredientRepository = ingredientRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.stockAuditEntryRepository = stockAuditEntryRepository;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDeductionResult deductRecipeStockForOrder(
            UUID orderId,
            UUID tenantId,
            List<OrderItemSnapshot> orderItems
    ) {
        Objects.requireNonNull(orderId, "OrderId cannot be null");
        Objects.requireNonNull(tenantId, "TenantId cannot be null");

        if (orderItems == null || orderItems.isEmpty()) {
            return new StockDeductionResult(
                    orderId,
                    tenantId,
                    0,
                    Collections.emptyList(),
                    Instant.now(),
                    false
            );
        }

        // 1. Consolidación de demanda agregada por receta técnica (C3, CB-06, FR-009)
        Map<UUID, BigDecimal> totalDemandByIngredient = new HashMap<>();

        for (OrderItemSnapshot item : orderItems) {
            if (item.dishId() == null || item.quantity() == null || item.quantity() <= 0) {
                continue;
            }

            List<DishRecipeIngredient> recipeLines = dishRecipeIngredientRepository.findByIdDishId(item.dishId());
            if (recipeLines == null || recipeLines.isEmpty()) {
                continue;
            }

            for (DishRecipeIngredient recipeLine : recipeLines) {
                if (recipeLine.getId() == null || recipeLine.getId().getIngredientId() == null || recipeLine.getQuantity() == null) {
                    continue;
                }

                UUID ingredientId = recipeLine.getId().getIngredientId();
                BigDecimal lineDemand = recipeLine.getQuantity()
                        .multiply(BigDecimal.valueOf(item.quantity()))
                        .setScale(4, RoundingMode.HALF_UP);

                totalDemandByIngredient.merge(ingredientId, lineDemand, BigDecimal::add);
            }
        }

        if (totalDemandByIngredient.isEmpty()) {
            return new StockDeductionResult(
                    orderId,
                    tenantId,
                    0,
                    Collections.emptyList(),
                    Instant.now(),
                    false
            );
        }

        // 2. Ordenamiento anti-deadlock estricto: ingredient_id ASC
        List<UUID> sortedIngredientIds = new ArrayList<>(totalDemandByIngredient.keySet());
        Collections.sort(sortedIngredientIds);

        // 3. Bloqueo pesimista en lote ordenado (SELECT ... FOR UPDATE)
        List<Ingredient> lockedIngredients = ingredientRepository.findAllByIdInOrderByIdAscForUpdate(sortedIngredientIds, tenantId);

        Map<UUID, Ingredient> ingredientMap = new HashMap<>();
        for (Ingredient ing : lockedIngredients) {
            ingredientMap.put(ing.getId(), ing);
        }

        // 4. Verificación previa "todo o nada" (INV-01, FR-008, CB-07, CB-08)
        for (UUID ingredientId : sortedIngredientIds) {
            Ingredient ingredient = ingredientMap.get(ingredientId);
            BigDecimal demanded = totalDemandByIngredient.get(ingredientId).setScale(4, RoundingMode.HALF_UP);

            if (ingredient == null) {
                throw new InsufficientIngredientStockException(
                        ingredientId,
                        "Insumo no encontrado: " + ingredientId,
                        demanded,
                        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                        "unidades"
                );
            }

            BigDecimal currentStock = ingredient.getCurrentStock() != null
                    ? ingredient.getCurrentStock().setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            if (currentStock.compareTo(demanded) < 0) {
                log.warn("Stock insuficiente para insumo {} ({}) en orden {}: disponible={}, requerido={}",
                        ingredient.getId(), ingredient.getName(), orderId, currentStock, demanded);
                throw new InsufficientIngredientStockException(
                        ingredient.getId(),
                        ingredient.getName(),
                        demanded,
                        currentStock,
                        ingredient.getUnitOfMeasure() != null ? ingredient.getUnitOfMeasure().getValue() : "unidades"
                );
            }
        }

        // 5. Aplicar descuentos atómicos exactos y generar auditorías inmutables
        List<IngredientDeductionDetail> deductionDetails = new ArrayList<>();
        String justification = "Deducción automática por confirmación de Orden " + orderId;

        for (UUID ingredientId : sortedIngredientIds) {
            Ingredient ingredient = ingredientMap.get(ingredientId);
            BigDecimal demanded = totalDemandByIngredient.get(ingredientId).setScale(4, RoundingMode.HALF_UP);
            BigDecimal previousStock = ingredient.getCurrentStock().setScale(4, RoundingMode.HALF_UP);
            BigDecimal newStock = previousStock.subtract(demanded).setScale(4, RoundingMode.HALF_UP);

            ingredient.setCurrentStock(newStock);
            ingredientRepository.save(ingredient);

            BigDecimal threshold = ingredient.getCriticalStockThreshold() != null
                    ? BigDecimal.valueOf(ingredient.getCriticalStockThreshold()).setScale(4, RoundingMode.HALF_UP)
                    : null;
            boolean isNowCritical = threshold != null && newStock.compareTo(threshold) < 0;

            StockAuditEntry auditEntry = new StockAuditEntry(
                    tenantId,
                    ingredient.getId(),
                    null,
                    StockAdjustmentMode.SUBTRACT,
                    previousStock,
                    newStock,
                    demanded,
                    justification
            );
            stockAuditEntryRepository.save(auditEntry);

            deductionDetails.add(new IngredientDeductionDetail(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredient.getUnitOfMeasure() != null ? ingredient.getUnitOfMeasure().getValue() : "unidades",
                    demanded,
                    previousStock,
                    newStock,
                    threshold,
                    isNowCritical
            ));
        }

        return new StockDeductionResult(
                orderId,
                tenantId,
                deductionDetails.size(),
                deductionDetails,
                Instant.now(),
                false
        );
    }
}
