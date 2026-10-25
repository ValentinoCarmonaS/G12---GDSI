package com.easyeat.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating diner personal contact information.
 * Enforces alphabetic names (rejects digits and symbols) and optional phone format boundaries.
 */
public record PersonalDataRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 1, max = 100, message = "El nombre no debe superar los 100 caracteres")
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$", message = "El nombre solo puede contener letras y espacios")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 1, max = 100, message = "El apellido no debe superar los 100 caracteres")
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$", message = "El apellido solo puede contener letras y espacios")
        String lastName,

        @Pattern(regexp = "^\\+?[0-9 -]{8,20}$", message = "El teléfono debe contener entre 8 y 20 caracteres numéricos válidos")
        String phone
) {
}
