package com.easyeat.dish.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RecipeIngredientItem(
        @NotNull(message = "El ID del ingrediente es obligatorio")
        UUID ingredientId,

        String ingredientName,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Double quantity,

        String unitOfMeasure,

        Boolean isIngredientActive
) {
    public RecipeIngredientItem(UUID ingredientId, Double quantity) {
        this(ingredientId, null, quantity, null, null);
    }
}
