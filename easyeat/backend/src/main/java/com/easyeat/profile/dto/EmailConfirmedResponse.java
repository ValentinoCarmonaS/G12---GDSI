package com.easyeat.profile.dto;

public record EmailConfirmedResponse(
        String message,
        String activeEmail
) {
}
