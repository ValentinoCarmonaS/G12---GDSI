package com.easyeat.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating restaurant visual branding (AS-9, INV-009).
 */
public record RestaurantBrandingUpdateRequest(
        @NotNull(message = "El nombre de fantasía es obligatorio")
        @Size(min = 2, max = 150, message = "El nombre de fantasía debe tener entre 2 y 150 caracteres")
        String fantasyName,

        @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
        String description,

        @NotNull(message = "El color primario es obligatorio")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color primario debe tener formato hexadecimal #RRGGBB")
        String primaryColor,

        @NotNull(message = "El color secundario es obligatorio")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color secundario debe tener formato hexadecimal #RRGGBB")
        String secondaryColor,

        @NotNull(message = "El color de acento es obligatorio")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color de acento debe tener formato hexadecimal #RRGGBB")
        String accentColor
) {}
