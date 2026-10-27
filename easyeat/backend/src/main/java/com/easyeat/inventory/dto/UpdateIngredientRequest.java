package com.easyeat.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateIngredientRequest(
        @NotBlank(message = "Ingredient name is required")
        @Size(min = 1, max = 255, message = "Ingredient name must be between 1 and 255 characters")
        String name,

        @NotNull(message = "Category is required")
        IngredientCategory category,

        @NotNull(message = "Unit of measure is required")
        UnitOfMeasure unitOfMeasure,

        @DecimalMin(value = "0.0000", message = "Unit purchase cost must be non-negative")
        BigDecimal unitPurchaseCost
) {
    public UpdateIngredientRequest(String name, IngredientCategory category, UnitOfMeasure unitOfMeasure) {
        this(name, category, unitOfMeasure, null);
    }
}
