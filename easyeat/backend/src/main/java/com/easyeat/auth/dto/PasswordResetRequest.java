package com.easyeat.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 255, message = "Email cannot exceed 255 characters.")
        @JsonProperty("email")
        String email
) {
}
