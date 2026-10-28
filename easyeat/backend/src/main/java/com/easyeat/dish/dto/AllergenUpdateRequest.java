package com.easyeat.dish.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AllergenUpdateRequest(
        @NotNull(message = "La lista de alérgenos y restricciones no puede ser nula")
        List<String> restrictionCodes,

        @NotNull(message = "El estado de confirmación de alérgenos es obligatorio")
        Boolean confirmed
) {
}
