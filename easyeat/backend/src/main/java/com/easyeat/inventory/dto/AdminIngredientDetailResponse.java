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
        Instant createdAt,
        Instant updatedAt
) {}
