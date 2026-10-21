package com.easyeat.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTest {

    private SecurityAuditLogger auditLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogger = new SecurityAuditLogger(objectMapper);
    }

    @Test
    @DisplayName("Should mask emails correctly protecting user identity")
    void shouldMaskEmailsCorrectly() {
        assertThat(auditLogger.maskEmail("comensal@easyeat.com")).isEqualTo("c***l@easyeat.com");
        assertThat(auditLogger.maskEmail("ab@example.com")).isEqualTo("a***b@example.com");
        assertThat(auditLogger.maskEmail("a@example.com")).isEqualTo("a***@example.com");
        assertThat(auditLogger.maskEmail(null)).isEqualTo("UNKNOWN");
        assertThat(auditLogger.maskEmail("invalid-email")).isEqualTo("i***l");
    }

    @Test
    @DisplayName("Should format audit log event as structured JSON without exposing secrets")
    void shouldFormatAuditLogEventAsStructuredJson() throws Exception {
        String jsonOutput = auditLogger.formatEvent(
                "LOGIN_SUCCESS",
                "comensal@easyeat.com",
                "192.168.1.100",
                Map.of("role", "COMENSAL", "userId", "123e4567-e89b-12d3-a456-426614174000")
        );

        JsonNode root = objectMapper.readTree(jsonOutput);

        assertThat(root.has("timestamp")).isTrue();
        assertThat(root.get("event").asText()).isEqualTo("LOGIN_SUCCESS");
        assertThat(root.get("email").asText()).isEqualTo("c***l@easyeat.com");
        assertThat(root.get("clientIp").asText()).isEqualTo("192.168.1.100");
        assertThat(root.get("details").get("role").asText()).isEqualTo("COMENSAL");
        assertThat(root.get("details").get("userId").asText()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");

        // Verify zero leakage of credentials or tokens
        assertThat(jsonOutput).doesNotContain("password");
        assertThat(jsonOutput).doesNotContain("secret");
        assertThat(jsonOutput).doesNotContain("token");
    }

    @Test
    @DisplayName("Should format LOCKOUT event properly")
    void shouldFormatLockoutEventProperly() throws Exception {
        String jsonOutput = auditLogger.formatEvent(
                "LOCKOUT",
                "attacker.victim@test.com",
                "10.0.0.5",
                Map.of("durationMinutes", 15)
        );

        JsonNode root = objectMapper.readTree(jsonOutput);
        assertThat(root.get("event").asText()).isEqualTo("LOCKOUT");
        assertThat(root.get("email").asText()).isEqualTo("a***m@test.com");
        assertThat(root.get("clientIp").asText()).isEqualTo("10.0.0.5");
        assertThat(root.get("details").get("durationMinutes").asInt()).isEqualTo(15);
    }
}
