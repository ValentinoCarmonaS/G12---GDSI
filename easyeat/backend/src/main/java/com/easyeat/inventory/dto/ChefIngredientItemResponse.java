package com.easyeat.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ChefIngredientItemResponse(
        UUID id,
        String name,
        IngredientCategory category,
        UnitOfMeasure unitOfMeasure,
        StockAvailabilityStatus stockStatus,
        BigDecimal currentStock
) {
}
