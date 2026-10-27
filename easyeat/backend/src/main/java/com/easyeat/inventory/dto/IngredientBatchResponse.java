package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IngredientBatchResponse(
        UUID id,
        UUID ingredientId,
        BigDecimal initialQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitCost,
        Instant createdAt
) {}

