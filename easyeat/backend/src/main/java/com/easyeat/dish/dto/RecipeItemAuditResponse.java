package com.easyeat.dish.dto;

import com.easyeat.inventory.dto.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeItemAuditResponse(
        UUID ingredientId,
        String ingredientName,
        BigDecimal recipeQuantity,
        UnitOfMeasure unitOfMeasure,
        BigDecimal unitPurchaseCost,
        BigDecimal lineSubtotal,
        boolean unpricedWarning
) {}

