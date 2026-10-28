package com.easyeat.dish.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FoodCostAuditResponse(
        UUID dishId,
        String dishName,
        BigDecimal totalFoodCost,
        boolean hasUnpricedIngredients,
        List<String> warningNotes,
        List<String> requiredDietaryRestrictions,
        List<RecipeItemAuditResponse> items
) {}
