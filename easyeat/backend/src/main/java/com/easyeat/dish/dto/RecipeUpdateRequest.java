package com.easyeat.dish.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RecipeUpdateRequest(
        @NotNull(message = "La lista de ingredientes es obligatoria")
        List<@Valid RecipeIngredientItem> ingredients
) {
}
