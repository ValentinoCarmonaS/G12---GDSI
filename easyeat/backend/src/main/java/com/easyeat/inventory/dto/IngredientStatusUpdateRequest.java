package com.easyeat.inventory.dto;

import jakarta.validation.constraints.NotNull;

public record IngredientStatusUpdateRequest(
        @NotNull(message = "Target status is required")
        IngredientStatus targetStatus
) {}
