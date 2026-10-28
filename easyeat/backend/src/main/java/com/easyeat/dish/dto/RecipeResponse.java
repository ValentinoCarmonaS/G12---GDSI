package com.easyeat.dish.dto;

import java.util.List;
import java.util.UUID;

public record RecipeResponse(
        UUID dishId,
        List<RecipeIngredientItem> ingredients
) {
}
