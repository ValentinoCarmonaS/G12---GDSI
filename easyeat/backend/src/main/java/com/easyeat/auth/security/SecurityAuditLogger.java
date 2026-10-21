package com.easyeat.auth.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits structured JSON audit log events for all authentication and credential recovery actions.
 * Ensures zero credential leakage (no raw passwords, hashes, or unhashed reset tokens)
 * and masks email identities in accordance with FR-017 and SC-008.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    private final ObjectMapper objectMapper;

    public SecurityAuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void logLoginSuccess(String email, String role, String clientIp, Object userId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (role != null) details.put("role", role);
        if (userId != null) details.put("userId", userId.toString());
        emit("LOGIN_SUCCESS", email, clientIp, details, false);
    }

    public void logLoginFailure(String email, String clientIp, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (reason != null) details.put("reason", reason);
        emit("LOGIN_FAILURE", email, clientIp, details, true);
    }

    public void logLockout(String email, String clientIp, int durationMinutes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("durationMinutes", durationMinutes);
        emit("LOCKOUT", email, clientIp, details, true);
    }

    public void logUserRegistered(String email, String role, String status) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (role != null) details.put("role", role);
        if (status != null) details.put("status", status);
        emit("USER_REGISTERED", email, null, details, false);
    }

    public void logResetRequest(String email, String clientIp, Object userId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (userId != null) details.put("userId", userId.toString());
        emit("RESET_REQUEST", email, clientIp, details, false);
    }

    public void logResetConfirm(String email, String clientIp, Object userId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (userId != null) details.put("userId", userId.toString());
        emit("RESET_CONFIRM", email, clientIp, details, false);
    }

    public void logAccessDenied(String email, String role, String attemptedResource, String clientIp, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (role != null) details.put("role", role);
        if (attemptedResource != null) details.put("attemptedResource", attemptedResource);
        if (reason != null) details.put("reason", reason);
        emit("ACCESS_DENIED", email, clientIp, details, true);
    }

    public String formatEvent(String eventType, String email, String clientIp, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("event", eventType);
        payload.put("email", maskEmail(email));
        if (clientIp != null) {
            payload.put("clientIp", clientIp);
        }
        if (details != null && !details.isEmpty()) {
            payload.put("details", details);
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log event: {}", eventType, e);
            return "{\"event\":\"" + eventType + "\",\"error\":\"serialization_failed\"}";
        }
    }

    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "UNKNOWN";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            if (email.length() <= 2) {
                return email.charAt(0) + "***";
            }
            return email.charAt(0) + "***" + email.charAt(email.length() - 1);
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (localPart.length() == 1) {
            return localPart + "***" + domainPart;
        } else if (localPart.length() == 2) {
            return localPart.charAt(0) + "***" + localPart.charAt(1) + domainPart;
        } else {
            return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domainPart;
        }
    }

    private void emit(String eventType, String email, String clientIp, Map<String, Object> details, boolean isWarning) {
        String json = formatEvent(eventType, email, clientIp, details);
        if (isWarning) {
            log.warn("SECURITY_AUDIT: {}", json);
        } else {
            log.info("SECURITY_AUDIT: {}", json);
        }
    }
}
