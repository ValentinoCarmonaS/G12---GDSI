package com.easyeat.dish.dto;

import jakarta.validation.constraints.NotNull;

public record ToggleChefPauseRequest(
        @NotNull(message = "El estado de pausa es obligatorio")
        Boolean paused
) {
}
