package com.easyeat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstLoginPasswordChangeRequest(
        String email,

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "New password must be between 8 and 72 characters")
        String newPassword,

        @NotBlank(message = "Confirm new password is required")
        @Size(min = 8, max = 72, message = "Confirm new password must be between 8 and 72 characters")
        String confirmNewPassword
) {
    public FirstLoginPasswordChangeRequest(String currentPassword, String newPassword, String confirmNewPassword) {
        this(null, currentPassword, newPassword, confirmNewPassword);
    }
}
