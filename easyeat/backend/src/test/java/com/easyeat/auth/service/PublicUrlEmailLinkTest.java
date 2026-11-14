package com.easyeat.auth.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.SecurityAuditLogger;
import com.easyeat.profile.dto.EmailChangeRequestDto;
import com.easyeat.profile.repository.ProfileUserRepository;
import com.easyeat.profile.service.EmailChangeService;
import com.easyeat.profile.service.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PublicUrlEmailLinkTest (T026)")
class PublicUrlEmailLinkTest {

    @Test
    @DisplayName("PasswordResetService debe construir enlaces con APP_PASSWORD_RESET_BASE_URL configurada sin localhost")
    void passwordResetService_shouldBuildResetLinkWithConfiguredPublicUrl() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        EmailService emailService = mock(EmailService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("UTC"));
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        String publicResetBaseUrl = "https://easyeat-frontend.onrender.com/reset-password";
        long tokenValidityMinutes = 15;

        PasswordResetService resetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                emailService,
                passwordEncoder,
                clock,
                auditLogger,
                publicResetBaseUrl,
                tokenValidityMinutes
        );

        User user = new User("diner@example.com", "encoded-pass", UserRole.COMENSAL);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

        when(userRepository.findByEmail("diner@example.com")).thenReturn(Optional.of(user));

        // When
        resetService.requestPasswordReset("diner@example.com");

        // Then
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("diner@example.com"), linkCaptor.capture());

        String generatedLink = linkCaptor.getValue();
        assertThat(generatedLink).isNotNull();
        assertThat(generatedLink).startsWith(publicResetBaseUrl + "?token=");
        assertThat(generatedLink).doesNotContain("localhost");
        assertThat(generatedLink).doesNotContain("127.0.0.1");
    }

    @Test
    @DisplayName("EmailChangeService debe construir enlaces de verificación con APP_EMAIL_VERIFICATION_BASE_URL configurada sin localhost")
    void emailChangeService_shouldBuildVerificationLinkWithConfiguredPublicUrl() {
        ProfileUserRepository profileUserRepository = mock(ProfileUserRepository.class);
        EmailSender emailSender = mock(EmailSender.class);

        EmailChangeService emailChangeService = new EmailChangeService(profileUserRepository, emailSender);

        String publicVerificationBaseUrl = "https://easyeat-frontend.onrender.com/verificar-email";
        ReflectionTestUtils.setField(emailChangeService, "baseUrl", publicVerificationBaseUrl);

        User user = new User("diner@example.com", "encoded-pass", UserRole.COMENSAL);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        when(profileUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileUserRepository.existsByEmailIgnoreCase("new-diner@example.com")).thenReturn(false);

        // When
        emailChangeService.requestEmailChange(user, new EmailChangeRequestDto("new-diner@example.com"));

        // Then
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(eq("new-diner@example.com"), linkCaptor.capture());

        String generatedLink = linkCaptor.getValue();
        assertThat(generatedLink).isNotNull();
        assertThat(generatedLink).startsWith(publicVerificationBaseUrl + "?token=");
        assertThat(generatedLink).doesNotContain("localhost");
        assertThat(generatedLink).doesNotContain("127.0.0.1");
    }
}
