package com.easyeat.auth.service;

import com.easyeat.auth.domain.PasswordResetToken;
import com.easyeat.auth.domain.TokenStatus;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.InvalidTokenException;
import com.easyeat.auth.exception.TokenAlreadyConsumedException;
import com.easyeat.auth.exception.TokenExpiredException;
import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock clock;
    private PasswordResetService resetService;

    private final Instant now = Instant.parse("2026-10-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        resetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                emailService,
                passwordEncoder,
                clock
        );
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Request reset: generates 15-minute token, saves SHA-256 hash, and dispatches email for active user")
    void shouldGenerate15MinuteTokenAndSendEmailForActiveUser() {
        String email = "comensal@example.com";
        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        resetService.requestPasswordReset(email);

        verify(tokenRepository).revokeAllPendingForUser(eq(user.getId()), eq(TokenStatus.PENDING), eq(TokenStatus.REVOKED));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getStatus()).isEqualTo(TokenStatus.PENDING);
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getExpiresAt()).isEqualTo(now.plus(15, ChronoUnit.MINUTES));

        verify(emailService).sendPasswordResetEmail(eq(email), contains("token="));
    }

    @Test
    @DisplayName("Request reset: silently succeeds without token or email for non-existent email (anti-enumeration)")
    void shouldSilentlyIgnoreNonExistentUser() {
        String email = "unknown@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        resetService.requestPasswordReset(email);

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Request reset: silently ignores suspended account (no token, no email)")
    void shouldSilentlyIgnoreSuspendedUser() {
        String email = "suspended@example.com";
        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        resetService.requestPasswordReset(email);

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Request reset: enforces 60-second cooldown per email (anti-spam)")
    void shouldEnforce60SecondCooldownPerEmail() {
        String email = "user@example.com";
        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // 1st request -> dispatches email
        resetService.requestPasswordReset(email);
        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), anyString());

        // 2nd request within 60s -> cooldown blocks new email
        resetService.requestPasswordReset(email);
        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), anyString());
        verify(tokenRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Confirm reset: updates password, marks token CONSUMED, and increments token_version to revoke active sessions")
    void shouldSuccessfullyConfirmPasswordResetAndIncrementTokenVersion() {
        String rawToken = "my-secret-raw-reset-token-123456";
        String tokenHash = sha256(rawToken);
        String newPassword = "NewSecurePassword123";

        User user = new User("user@example.com", "old_hash", UserRole.COMENSAL);
        int initialTokenVersion = user.getTokenVersion();

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(10, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(newPassword)).thenReturn("new_hash_encoded");
        when(tokenRepository.consumeToken(eq(tokenHash), any(Instant.class))).thenReturn(1);

        resetService.confirmPasswordReset(rawToken, newPassword);

        assertThat(user.getPasswordHash()).isEqualTo("new_hash_encoded");
        assertThat(user.getTokenVersion()).isEqualTo(initialTokenVersion + 1);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Confirm reset: rejects expired token with TokenExpiredException")
    void shouldRejectExpiredToken() {
        String rawToken = "expired-token-123";
        String tokenHash = sha256(rawToken);

        User user = new User("user@example.com", "hash", UserRole.COMENSAL);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.minus(1, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.confirmPasswordReset(rawToken, "NewPass123"))
                .isInstanceOf(TokenExpiredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirm reset: rejects already consumed token with TokenAlreadyConsumedException")
    void shouldRejectAlreadyConsumedToken() {
        String rawToken = "consumed-token-123";
        String tokenHash = sha256(rawToken);

        User user = new User("user@example.com", "hash", UserRole.COMENSAL);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(10, ChronoUnit.MINUTES));
        token.setStatus(TokenStatus.CONSUMED);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.confirmPasswordReset(rawToken, "NewPass123"))
                .isInstanceOf(TokenAlreadyConsumedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirm reset: rejects invalid/unknown token with InvalidTokenException")
    void shouldRejectInvalidToken() {
        String rawToken = "unknown-token-123";
        String tokenHash = sha256(rawToken);

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetService.confirmPasswordReset(rawToken, "NewPass123"))
                .isInstanceOf(InvalidTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirm reset: rejects reset if account is suspended with AccountSuspendedException")
    void shouldRejectConfirmIfUserSuspended() {
        String rawToken = "valid-token-suspended-user";
        String tokenHash = sha256(rawToken);

        User user = new User("user@example.com", "hash", UserRole.COMENSAL);
        user.setStatus(UserStatus.SUSPENDED);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, now.plus(10, ChronoUnit.MINUTES));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> resetService.confirmPasswordReset(rawToken, "NewPass123"))
                .isInstanceOf(AccountSuspendedException.class);
        verify(userRepository, never()).save(any());
    }
}
