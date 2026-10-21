package com.easyeat.auth.service;

import com.easyeat.auth.domain.PasswordResetToken;
import com.easyeat.auth.domain.TokenStatus;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.InvalidTokenException;
import com.easyeat.auth.exception.TokenAlreadyConsumedException;
import com.easyeat.auth.exception.TokenExpiredException;
import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String baseUrl;
    private final long tokenValidityMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    private final Cache<String, Instant> emailCooldownCache;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.security.password-reset.base-url:http://localhost:3000/auth/reset-password}") String baseUrl,
            @Value("${app.security.password-reset.token-validity-minutes:15}") long tokenValidityMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.tokenValidityMinutes = tokenValidityMinutes;
        this.emailCooldownCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this(userRepository, tokenRepository, emailService, passwordEncoder, clock, "http://localhost:3000/auth/reset-password", 15);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        // 60-second cooldown per email
        if (emailCooldownCache.getIfPresent(normalizedEmail) != null) {
            log.info("Password reset request for {} ignored due to 60s cooldown", normalizedEmail);
            return;
        }
        emailCooldownCache.put(normalizedEmail, clock.instant());

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email (anti-enumeration): {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Password reset requested for non-active user: status={}, email={}", user.getStatus(), normalizedEmail);
            return;
        }

        // Revoke any prior pending tokens for this user
        tokenRepository.revokeAllPendingForUser(user.getId(), TokenStatus.PENDING, TokenStatus.REVOKED);

        // Generate 32 bytes cryptographically secure random token (64 hex characters)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = clock.instant().plus(tokenValidityMinutes, ChronoUnit.MINUTES);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, expiresAt);
        tokenRepository.save(token);

        String resetLink = baseUrl + "?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        log.info("AUDIT_SECURITY: event=PASSWORD_RESET_REQUESTED, userId={}, email={}", user.getId(), user.getEmail());
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("The password reset token is invalid.");
        }

        String tokenHash = hashToken(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("The password reset token is invalid."));

        if (token.getStatus() == TokenStatus.CONSUMED) {
            throw new TokenAlreadyConsumedException("This password reset link has already been used.");
        }

        Instant now = clock.instant();
        if (token.getStatus() == TokenStatus.REVOKED || token.getExpiresAt().isBefore(now)) {
            throw new TokenExpiredException("This password reset link has expired. Please request a new one.");
        }

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountSuspendedException("Account is suspended. Please contact support.");
        }

        // Atomically consume token in database
        int updated = tokenRepository.consumeToken(tokenHash, now);
        if (updated == 0) {
            throw new TokenAlreadyConsumedException("This password reset link has already been used.");
        }

        // Update password and increment token_version to invalidate prior active JWT sessions
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.incrementTokenVersion();
        userRepository.save(user);

        log.info("AUDIT_SECURITY: event=PASSWORD_RESET_COMPLETED, userId={}, email={}", user.getId(), user.getEmail());
    }

    public void clearCooldowns() {
        emailCooldownCache.invalidateAll();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
