package com.easyeat.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthTokenResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn
) {
    public AuthTokenResponse(String accessToken) {
        this(accessToken, "Bearer", 28800L);
    }
}
