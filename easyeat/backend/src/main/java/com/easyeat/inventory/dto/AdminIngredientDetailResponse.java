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
        BigDecimal packageCost,
        Integer unitsPerPackage,
        IngredientStatus status,
        BigDecimal currentStock,
        Instant createdAt,
        Instant updatedAt
) {
    /** Backwards-compatible shape for callers that do not read the presentation fields. */
    public AdminIngredientDetailResponse(
            UUID id, UUID tenantId, String name, IngredientCategory category,
            UnitOfMeasure unitOfMeasure, BigDecimal unitPurchaseCost, IngredientStatus status,
            BigDecimal currentStock, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, name, category, unitOfMeasure, unitPurchaseCost, null, 1,
                status, currentStock, createdAt, updatedAt);
    }
}
