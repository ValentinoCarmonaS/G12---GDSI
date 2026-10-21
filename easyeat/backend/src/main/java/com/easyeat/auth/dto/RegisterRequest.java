package com.easyeat.auth.dto;

public record RegisterRequest(
        String email,
        String password
) {
}
