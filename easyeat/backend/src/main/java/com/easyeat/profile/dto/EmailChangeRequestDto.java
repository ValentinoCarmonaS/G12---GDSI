package com.easyeat.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeRequestDto(
        @NotBlank(message = "El nuevo correo electrónico es obligatorio")
        @Email(message = "El correo electrónico debe ser válido")
        String newEmail
) {
}
