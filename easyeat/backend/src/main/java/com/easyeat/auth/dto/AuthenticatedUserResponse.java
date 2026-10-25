package com.easyeat.auth.dto;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        String email,
        String role,

        @JsonProperty("tenant_id")
        UUID tenantId,

        @JsonProperty("token_version")
        Integer tokenVersion,

        @JsonProperty("must_change_password")
        Boolean mustChangePassword
) {
    public static AuthenticatedUserResponse from(User user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getTenantId(),
                user.getTokenVersion(),
                user.getStatus() == UserStatus.PENDING_FIRST_LOGIN
        );
    }
}
