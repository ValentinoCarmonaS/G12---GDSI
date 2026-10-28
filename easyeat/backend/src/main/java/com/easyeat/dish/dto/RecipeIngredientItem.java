package com.easyeat.dish.dto;

import java.util.UUID;

public record RecipeIngredientItem(
        UUID ingredientId,
        String ingredientName,
        Double quantity,
        String unitOfMeasure,
        Boolean isIngredientActive
) {
}
