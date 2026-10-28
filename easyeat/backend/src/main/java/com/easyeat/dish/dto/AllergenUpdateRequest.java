package com.easyeat.dish.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Payload for updating dish dietary restrictions and allergen confirmation state (CHF-2.2.3).
 *
 * @param restrictionCodes standardized taxonomy restriction codes (can be empty)
 * @param confirmed explicit chef confirmation state for the allergen section
 */
public record AllergenUpdateRequest(
        @NotNull(message = "La lista de alérgenos y restricciones no puede ser nula")
        List<String> restrictionCodes,

        @NotNull(message = "El estado de confirmación de alérgenos es obligatorio")
        Boolean confirmed
) {
}
