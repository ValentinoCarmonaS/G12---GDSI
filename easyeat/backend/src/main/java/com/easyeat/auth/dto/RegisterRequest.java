package com.easyeat.auth.dto;

import com.easyeat.auth.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 255, message = "Email cannot exceed 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, message = "Password must be at least 8 characters.")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter.")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least one lowercase letter.")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one numeric digit.")
        @MaxBytes(72)
        String password
) {
}
