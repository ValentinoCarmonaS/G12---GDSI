package com.easyeat.dish.dto;

import com.easyeat.dish.domain.DishCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDishRequest(
        @NotBlank(message = "El nombre del plato es obligatorio")
        @Size(max = 100, message = "El nombre del plato no puede exceder los 100 caracteres")
        String name,

        @NotNull(message = "La categoría del plato es obligatoria")
        DishCategory category,

        @Size(max = 500, message = "La descripción no puede exceder los 500 caracteres")
        String description
) {
}
