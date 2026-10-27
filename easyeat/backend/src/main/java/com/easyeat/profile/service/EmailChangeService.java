package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.profile.dto.EmailChangeInitiatedResponse;
import com.easyeat.profile.dto.EmailChangeRequestDto;
import com.easyeat.profile.dto.EmailConfirmationRequest;
import com.easyeat.profile.dto.EmailConfirmedResponse;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.EmailVerificationException;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EmailChangeService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);

    private final ProfileUserRepository profileUserRepository;
    private final EmailSender emailSender;

    @Value("${app.email-verification.base-url:http://localhost:3000/verificar-email}")
    private String baseUrl = "http://localhost:3000/verificar-email";

    @Value("${app.email-verification.cooldown-seconds:60}")
    private int cooldownSeconds = 60;

    @Value("${app.email-verification.token-validity-hours:24}")
    private int tokenValidityHours = 24;

    public EmailChangeService(ProfileUserRepository profileUserRepository, EmailSender emailSender) {
        this.profileUserRepository = profileUserRepository;
        this.emailSender = emailSender;
    }

    @Transactional
    public EmailChangeInitiatedResponse requestEmailChange(User user, EmailChangeRequestDto request) {
        User target = profileUserRepository.findById(user.getId()).orElse(user);
        String normalizedEmail = request.newEmail().trim().toLowerCase();

        if (normalizedEmail.equalsIgnoreCase(target.getEmail())) {
            throw new IllegalArgumentException("El nuevo correo electrónico no puede ser igual al actual");
        }

        if (profileUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException("El correo electrónico ya se encuentra registrado por otro usuario");
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenValidityHours, ChronoUnit.HOURS);

        target.setPendingEmail(normalizedEmail);
        target.setEmailVerificationTokenHash(tokenHash);
        target.setEmailTokenExpiresAt(expiresAt);
        target.setEmailVerificationLastSentAt(now);

        profileUserRepository.save(target);

        String verificationUrl = baseUrl + "?token=" + rawToken;
        emailSender.sendVerificationEmail(normalizedEmail, verificationUrl);
        log.info("Email change initiated for user ID: {} towards pending email: {}", target.getId(), normalizedEmail);

        return new EmailChangeInitiatedResponse(
                "Se ha enviado un enlace de verificación a su nuevo correo electrónico.",
                normalizedEmail,
                expiresAt
        );
    }

    @Transactional
    public EmailChangeInitiatedResponse resendVerificationEmail(User user) {
        User target = profileUserRepository.findById(user.getId()).orElse(user);

        if (target.getPendingEmail() == null) {
            throw new NoSuchElementException("No hay una solicitud de cambio de correo electrónico pendiente");
        }

        Instant lastSentAt = target.getEmailVerificationLastSentAt();
        if (lastSentAt != null) {
            long elapsedSeconds = Duration.between(lastSentAt, Instant.now()).getSeconds();
            if (elapsedSeconds < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsedSeconds;
                throw new CooldownRateLimitException(remaining);
            }
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenValidityHours, ChronoUnit.HOURS);

        target.setEmailVerificationTokenHash(tokenHash);
        target.setEmailTokenExpiresAt(expiresAt);
        target.setEmailVerificationLastSentAt(now);

        profileUserRepository.save(target);

        String verificationUrl = baseUrl + "?token=" + rawToken;
        emailSender.sendVerificationEmail(target.getPendingEmail(), verificationUrl);
        log.info("Email verification link redispatched for user ID: {} to: {}", target.getId(), target.getPendingEmail());

        return new EmailChangeInitiatedResponse(
                "Enlace de verificación reenviado exitosamente.",
                target.getPendingEmail(),
                expiresAt
        );
    }

    @Transactional
    public void cancelPendingEmailChange(User user) {
        User target = profileUserRepository.findById(user.getId()).orElse(user);

        if (target.getPendingEmail() == null) {
            throw new NoSuchElementException("No hay un cambio de correo electrónico pendiente para cancelar");
        }

        target.setPendingEmail(null);
        target.setEmailVerificationTokenHash(null);
        target.setEmailTokenExpiresAt(null);
        target.setEmailVerificationLastSentAt(null);

        profileUserRepository.save(target);
        log.info("Pending email change canceled for user ID: {}", target.getId());
    }

    @Transactional
    public EmailConfirmedResponse confirmEmailChange(EmailConfirmationRequest request) {
        String rawToken = request.token().trim();
        String tokenHash = hashToken(rawToken);

        User target = profileUserRepository.findByEmailVerificationTokenHash(tokenHash)
                .orElseThrow(() -> new EmailVerificationException("El token de verificación de email es inválido, ha expirado o ya fue utilizado."));

        if (target.getEmailTokenExpiresAt() == null || target.getEmailTokenExpiresAt().isBefore(Instant.now())) {
            throw new EmailVerificationException("El token de verificación de email ha expirado.");
        }

        String newEmail = target.getPendingEmail();
        if (newEmail == null) {
            throw new EmailVerificationException("No hay una dirección de correo pendiente asociada.");
        }

        target.setEmail(newEmail);
        target.setPendingEmail(null);
        target.setEmailVerificationTokenHash(null);
        target.setEmailTokenExpiresAt(null);
        target.setEmailVerificationLastSentAt(null);
        target.incrementTokenVersion(); // Revokes active JWT sessions (FR-009)

        profileUserRepository.save(target);
        log.info("Email change successfully confirmed for user ID: {}. Active email is now: {}. Active sessions revoked.",
                target.getId(), newEmail);

        return new EmailConfirmedResponse(
                "Correo electrónico actualizado correctamente. Todas las sesiones activas han sido cerradas por seguridad.",
                newEmail
        );
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 no disponible", e);
        }
    }
}
