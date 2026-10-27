package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.profile.dto.EmailChangeInitiatedResponse;
import com.easyeat.profile.dto.EmailChangeRequestDto;
import com.easyeat.profile.dto.EmailConfirmationRequest;
import com.easyeat.profile.dto.EmailConfirmedResponse;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.EmailVerificationException;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailChangeService Unit Tests (T011, COM-1.4.1)")
class EmailChangeServiceTest {

    @Mock
    private ProfileUserRepository profileUserRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailChangeService emailChangeService;

    private User sampleDiner;

    @BeforeEach
    void setUp() {
        sampleDiner = new User("current@example.com", "hashed_pwd", UserRole.COMENSAL);
        sampleDiner.setId(UUID.randomUUID());
        sampleDiner.setFirstName("Valentino");
        sampleDiner.setLastName("Carmona");
        sampleDiner.setFullName("Valentino Carmona");
        sampleDiner.setStatus(UserStatus.ACTIVE);
        sampleDiner.setTokenVersion(0);

        ReflectionTestUtils.setField(emailChangeService, "baseUrl", "http://localhost:3000/verificar-email");
        ReflectionTestUtils.setField(emailChangeService, "cooldownSeconds", 60);
        ReflectionTestUtils.setField(emailChangeService, "tokenValidityHours", 24);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("requestEmailChange tests")
    class RequestEmailChangeTests {

        @Test
        @DisplayName("Initiates email change, hashes token with SHA-256 and dispatches verification email")
        void requestEmailChange_success() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.existsByEmailIgnoreCase("new.email@example.com")).thenReturn(false);
            when(profileUserRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            EmailChangeRequestDto request = new EmailChangeRequestDto("New.Email@example.com");
            EmailChangeInitiatedResponse response = emailChangeService.requestEmailChange(sampleDiner, request);

            assertThat(response).isNotNull();
            assertThat(response.pendingEmail()).isEqualTo("new.email@example.com");
            assertThat(response.expiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(profileUserRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();

            assertThat(saved.getPendingEmail()).isEqualTo("new.email@example.com");
            assertThat(saved.getEmail()).isEqualTo("current@example.com");
            assertThat(saved.getEmailVerificationTokenHash()).isNotNull().hasSize(64);
            assertThat(saved.getEmailTokenExpiresAt()).isNotNull();
            assertThat(saved.getEmailVerificationLastSentAt()).isNotNull();

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailSender).sendVerificationEmail(eq("new.email@example.com"), urlCaptor.capture());
            assertThat(urlCaptor.getValue()).startsWith("http://localhost:3000/verificar-email?token=");
        }

        @Test
        @DisplayName("Rejects change if proposed email is already registered to another user")
        void requestEmailChange_emailAlreadyRegistered() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

            EmailChangeRequestDto request = new EmailChangeRequestDto("taken@example.com");
            assertThatThrownBy(() -> emailChangeService.requestEmailChange(sampleDiner, request))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);
        }

        @Test
        @DisplayName("Rejects change if proposed email is identical to current active email")
        void requestEmailChange_sameAsCurrentEmail() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            EmailChangeRequestDto request = new EmailChangeRequestDto("current@example.com");
            assertThatThrownBy(() -> emailChangeService.requestEmailChange(sampleDiner, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Revokes previous verification token on consecutive email change request")
        void requestEmailChange_revokesPreviousToken() {
            sampleDiner.setPendingEmail("old.pending@example.com");
            sampleDiner.setEmailVerificationTokenHash("old_hash_12345");
            sampleDiner.setEmailTokenExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS));

            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.existsByEmailIgnoreCase("brand.new@example.com")).thenReturn(false);
            when(profileUserRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            EmailChangeRequestDto request = new EmailChangeRequestDto("brand.new@example.com");
            emailChangeService.requestEmailChange(sampleDiner, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(profileUserRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();

            assertThat(saved.getPendingEmail()).isEqualTo("brand.new@example.com");
            assertThat(saved.getEmailVerificationTokenHash()).isNotEqualTo("old_hash_12345");
        }
    }

    @Nested
    @DisplayName("resendVerificationEmail tests")
    class ResendVerificationEmailTests {

        @Test
        @DisplayName("Resends verification email when 60s cooldown has expired")
        void resendVerificationEmail_success() {
            sampleDiner.setPendingEmail("new.email@example.com");
            sampleDiner.setEmailVerificationLastSentAt(Instant.now().minus(65, ChronoUnit.SECONDS));

            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            EmailChangeInitiatedResponse response = emailChangeService.resendVerificationEmail(sampleDiner);

            assertThat(response).isNotNull();
            assertThat(response.pendingEmail()).isEqualTo("new.email@example.com");
            verify(emailSender).sendVerificationEmail(eq("new.email@example.com"), any(String.class));
        }

        @Test
        @DisplayName("Throws CooldownRateLimitException (429) when requested within 60s cooldown")
        void resendVerificationEmail_cooldownActive_throws429() {
            sampleDiner.setPendingEmail("new.email@example.com");
            sampleDiner.setEmailVerificationLastSentAt(Instant.now().minus(20, ChronoUnit.SECONDS));

            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            assertThatThrownBy(() -> emailChangeService.resendVerificationEmail(sampleDiner))
                    .isInstanceOf(CooldownRateLimitException.class)
                    .satisfies(ex -> {
                        CooldownRateLimitException crl = (CooldownRateLimitException) ex;
                        assertThat(crl.getRetryAfterSeconds()).isBetween(35L, 41L);
                    });
        }

        @Test
        @DisplayName("Throws NoSuchElementException when there is no pending email change")
        void resendVerificationEmail_noPendingEmail_throwsNotFound() {
            sampleDiner.setPendingEmail(null);
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            assertThatThrownBy(() -> emailChangeService.resendVerificationEmail(sampleDiner))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("cancelPendingEmailChange tests")
    class CancelPendingEmailChangeTests {

        @Test
        @DisplayName("Clears all pending email verification fields on cancellation")
        void cancelPendingEmailChange_success() {
            sampleDiner.setPendingEmail("pending@example.com");
            sampleDiner.setEmailVerificationTokenHash("some_hash");
            sampleDiner.setEmailTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
            sampleDiner.setEmailVerificationLastSentAt(Instant.now());

            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            emailChangeService.cancelPendingEmailChange(sampleDiner);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(profileUserRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getPendingEmail()).isNull();
            assertThat(saved.getEmailVerificationTokenHash()).isNull();
            assertThat(saved.getEmailTokenExpiresAt()).isNull();
            assertThat(saved.getEmailVerificationLastSentAt()).isNull();
        }

        @Test
        @DisplayName("Throws NoSuchElementException if cancellation requested without pending email")
        void cancelPendingEmailChange_noPendingEmail_throwsNotFound() {
            sampleDiner.setPendingEmail(null);
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            assertThatThrownBy(() -> emailChangeService.cancelPendingEmailChange(sampleDiner))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("confirmEmailChange tests")
    class ConfirmEmailChangeTests {

        @Test
        @DisplayName("Promotes pending email, revokes token, and increments token_version")
        void confirmEmailChange_success() {
            String rawToken = "raw_secret_token_1234567890";
            String tokenHash = sha256(rawToken);

            sampleDiner.setPendingEmail("verified.new@example.com");
            sampleDiner.setEmailVerificationTokenHash(tokenHash);
            sampleDiner.setEmailTokenExpiresAt(Instant.now().plus(10, ChronoUnit.HOURS));
            sampleDiner.setTokenVersion(1);

            when(profileUserRepository.findByEmailVerificationTokenHash(tokenHash)).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            EmailConfirmationRequest request = new EmailConfirmationRequest(rawToken);
            EmailConfirmedResponse response = emailChangeService.confirmEmailChange(request);

            assertThat(response).isNotNull();
            assertThat(response.activeEmail()).isEqualTo("verified.new@example.com");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(profileUserRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("verified.new@example.com");
            assertThat(saved.getPendingEmail()).isNull();
            assertThat(saved.getEmailVerificationTokenHash()).isNull();
            assertThat(saved.getEmailTokenExpiresAt()).isNull();
            assertThat(saved.getTokenVersion()).isEqualTo(2); // session revoked via token_version increment
        }

        @Test
        @DisplayName("Rejects confirmation when token is not found or already consumed")
        void confirmEmailChange_invalidToken_throwsBadRequest() {
            String rawToken = "non_existent_token";
            String tokenHash = sha256(rawToken);

            when(profileUserRepository.findByEmailVerificationTokenHash(tokenHash)).thenReturn(Optional.empty());

            EmailConfirmationRequest request = new EmailConfirmationRequest(rawToken);
            assertThatThrownBy(() -> emailChangeService.confirmEmailChange(request))
                    .isInstanceOf(EmailVerificationException.class)
                    .hasMessageContaining("inválido");
        }

        @Test
        @DisplayName("Rejects confirmation when token has expired (>24h)")
        void confirmEmailChange_expiredToken_throwsBadRequest() {
            String rawToken = "expired_token";
            String tokenHash = sha256(rawToken);

            sampleDiner.setPendingEmail("verified.new@example.com");
            sampleDiner.setEmailVerificationTokenHash(tokenHash);
            sampleDiner.setEmailTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

            when(profileUserRepository.findByEmailVerificationTokenHash(tokenHash)).thenReturn(Optional.of(sampleDiner));

            EmailConfirmationRequest request = new EmailConfirmationRequest(rawToken);
            assertThatThrownBy(() -> emailChangeService.confirmEmailChange(request))
                    .isInstanceOf(EmailVerificationException.class);
        }
    }
}
