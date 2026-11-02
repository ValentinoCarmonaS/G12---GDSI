package com.easyeat.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateIngredientRequest(
        @NotBlank(message = "Ingredient name is required")
        @Size(min = 1, max = 255, message = "Ingredient name must be between 1 and 255 characters")
        String name,

        @NotNull(message = "Category is required")
        IngredientCategory category,

        @NotNull(message = "Unit of measure is required")
        UnitOfMeasure unitOfMeasure,

        @DecimalMin(value = "0.0000", message = "Unit purchase cost must be non-negative")
        BigDecimal unitPurchaseCost,

        @DecimalMin(value = "0.0000", message = "Package cost must be non-negative")
        BigDecimal packageCost,

        @jakarta.validation.constraints.Min(value = 1, message = "Units per package must be at least 1")
        Integer unitsPerPackage
) {
    public CreateIngredientRequest(String name, IngredientCategory category, UnitOfMeasure unitOfMeasure) {
        this(name, category, unitOfMeasure, null, null, 1);
    }

    public CreateIngredientRequest(String name, IngredientCategory category, UnitOfMeasure unitOfMeasure,
                                   BigDecimal unitPurchaseCost) {
        this(name, category, unitOfMeasure, unitPurchaseCost, null, 1);
    }
}
