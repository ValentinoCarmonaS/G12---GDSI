package com.easyeat.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailConfirmationRequest(
        @NotBlank(message = "El token de verificación es obligatorio")
        String token
) {
}
