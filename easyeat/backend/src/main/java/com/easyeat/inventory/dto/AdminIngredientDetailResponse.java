package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminIngredientDetailResponse(
        UUID id,
        UUID tenantId,
        String name,
        IngredientCategory category,
        UnitOfMeasure unitOfMeasure,
        BigDecimal unitPurchaseCost,
        IngredientStatus status,
        BigDecimal currentStock,
        Integer criticalStockThreshold,
        boolean isCritical,
        Instant createdAt,
        Instant updatedAt
) {
    public AdminIngredientDetailResponse(
            UUID id,
            UUID tenantId,
            String name,
            IngredientCategory category,
            UnitOfMeasure unitOfMeasure,
            BigDecimal unitPurchaseCost,
            IngredientStatus status,
            BigDecimal currentStock,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, tenantId, name, category, unitOfMeasure, unitPurchaseCost, status, currentStock, null, false, createdAt, updatedAt);
    }
}
