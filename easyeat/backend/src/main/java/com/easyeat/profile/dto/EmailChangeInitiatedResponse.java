package com.easyeat.profile.dto;

import java.time.Instant;

public record EmailChangeInitiatedResponse(
        String message,
        String pendingEmail,
        Instant expiresAt
) {
}
