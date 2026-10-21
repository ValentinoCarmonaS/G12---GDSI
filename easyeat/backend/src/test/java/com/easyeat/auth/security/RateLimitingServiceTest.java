package com.easyeat.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService();
    }

    @Test
    @DisplayName("Should not lock user when below 5 attempts")
    void shouldNotBeLockedInitially() {
        assertFalse(rateLimitingService.isLoginLocked("user@example.com", "192.168.1.1"));
    }

    @Test
    @DisplayName("Should lock account after 5 consecutive failed login attempts on email")
    void shouldLockAfter5FailuresOnEmail() {
        String email = "victim@example.com";
        String ip = "10.0.0.1";

        for (int i = 0; i < 4; i++) {
            rateLimitingService.recordLoginFailure(email, ip);
            assertFalse(rateLimitingService.isLoginLocked(email, ip));
        }

        rateLimitingService.recordLoginFailure(email, ip);
        assertTrue(rateLimitingService.isLoginLocked(email, ip));
    }

    @Test
    @DisplayName("Should reset failure count on successful login")
    void shouldResetFailureCountOnSuccess() {
        String email = "user@example.com";
        String ip = "10.0.0.2";

        for (int i = 0; i < 4; i++) {
            rateLimitingService.recordLoginFailure(email, ip);
        }

        rateLimitingService.resetLoginAttempts(email, ip);
        assertFalse(rateLimitingService.isLoginLocked(email, ip));

        // 1 more failure should not lock
        rateLimitingService.recordLoginFailure(email, ip);
        assertFalse(rateLimitingService.isLoginLocked(email, ip));
    }

    @Test
    @DisplayName("Should track failures for non-existent emails for anti-enumeration")
    void shouldTrackFailuresForNonExistentEmails() {
        String ghostEmail = "nonexistent@example.com";
        String ip = "10.0.0.3";

        for (int i = 0; i < 5; i++) {
            rateLimitingService.recordLoginFailure(ghostEmail, ip);
        }

        assertTrue(rateLimitingService.isLoginLocked(ghostEmail, ip));
    }

    @Test
    @DisplayName("Should lock client IP after 50 failures across different accounts")
    void shouldLockAfter50IpFailures() {
        String ip = "192.168.1.100";

        for (int i = 0; i < 50; i++) {
            rateLimitingService.recordLoginFailure("random" + i + "@example.com", ip);
        }

        assertTrue(rateLimitingService.isLoginLocked("anyemail@example.com", ip));
    }
}
