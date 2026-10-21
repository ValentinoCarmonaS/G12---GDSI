package com.easyeat.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
