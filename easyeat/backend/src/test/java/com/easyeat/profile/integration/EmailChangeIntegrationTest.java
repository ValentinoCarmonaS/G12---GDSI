package com.easyeat.profile.integration;

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
import com.easyeat.profile.service.EmailChangeService;
import com.easyeat.profile.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("EmailChangeIntegrationTest (T013, COM-1.4.1)")
class EmailChangeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_profile_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private ProfileUserRepository profileUserRepository;

    @Autowired
    private EmailChangeService emailChangeService;

    @MockBean
    private EmailSender emailSender;

    private User diner;

    @BeforeEach
    void setUp() {
        profileUserRepository.deleteAll();

        diner = new User("original.diner@easyeat.com", "hash123", UserRole.COMENSAL);
        diner.setFirstName("Valentino");
        diner.setLastName("Carmona");
        diner.setFullName("Valentino Carmona");
        diner.setStatus(UserStatus.ACTIVE);
        diner.setTokenVersion(0);
        diner = profileUserRepository.save(diner);
    }

    @Test
    @DisplayName("End-to-end: request change, dispatch hashed token, verify in DB, confirm, and revoke session via token_version")
    void testEndToEndEmailChangeWorkflow() {
        EmailChangeRequestDto request = new EmailChangeRequestDto("promoted.diner@easyeat.com");
        EmailChangeInitiatedResponse initiated = emailChangeService.requestEmailChange(diner, request);

        assertThat(initiated).isNotNull();
        assertThat(initiated.pendingEmail()).isEqualTo("promoted.diner@easyeat.com");

        User inDbAfterRequest = profileUserRepository.findById(diner.getId()).orElseThrow();
        assertThat(inDbAfterRequest.getPendingEmail()).isEqualTo("promoted.diner@easyeat.com");
        assertThat(inDbAfterRequest.getEmail()).isEqualTo("original.diner@easyeat.com");
        assertThat(inDbAfterRequest.getEmailVerificationTokenHash()).isNotNull().hasSize(64);
        assertThat(inDbAfterRequest.getEmailVerificationLastSentAt()).isNotNull();
        assertThat(inDbAfterRequest.getTokenVersion()).isEqualTo(0);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(eq("promoted.diner@easyeat.com"), urlCaptor.capture());
        String verificationUrl = urlCaptor.getValue();
        String rawToken = verificationUrl.substring(verificationUrl.indexOf("token=") + 6);

        EmailConfirmedResponse confirmed = emailChangeService.confirmEmailChange(new EmailConfirmationRequest(rawToken));
        assertThat(confirmed.activeEmail()).isEqualTo("promoted.diner@easyeat.com");

        User inDbAfterConfirm = profileUserRepository.findById(diner.getId()).orElseThrow();
        assertThat(inDbAfterConfirm.getEmail()).isEqualTo("promoted.diner@easyeat.com");
        assertThat(inDbAfterConfirm.getPendingEmail()).isNull();
        assertThat(inDbAfterConfirm.getEmailVerificationTokenHash()).isNull();
        assertThat(inDbAfterConfirm.getEmailTokenExpiresAt()).isNull();
        assertThat(inDbAfterConfirm.getTokenVersion()).isEqualTo(1); // token_version incremented
    }

    @Test
    @DisplayName("Cooldown rate-limiting enforcement and persistence in database")
    void testCooldownPersistence() {
        emailChangeService.requestEmailChange(diner, new EmailChangeRequestDto("cooldown.test@easyeat.com"));

        // Immediate resend must fail with 429 CooldownRateLimitException
        assertThatThrownBy(() -> emailChangeService.resendVerificationEmail(diner))
                .isInstanceOf(CooldownRateLimitException.class);

        // Simulate 65 seconds passed
        diner.setEmailVerificationLastSentAt(Instant.now().minus(65, ChronoUnit.SECONDS));
        profileUserRepository.save(diner);

        // Resend now succeeds
        EmailChangeInitiatedResponse resend = emailChangeService.resendVerificationEmail(diner);
        assertThat(resend.pendingEmail()).isEqualTo("cooldown.test@easyeat.com");
    }

    @Test
    @DisplayName("Already consumed token cannot be reused")
    void testTokenCannotBeReused() {
        emailChangeService.requestEmailChange(diner, new EmailChangeRequestDto("single.use@easyeat.com"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(eq("single.use@easyeat.com"), urlCaptor.capture());
        String rawToken = urlCaptor.getValue().substring(urlCaptor.getValue().indexOf("token=") + 6);

        emailChangeService.confirmEmailChange(new EmailConfirmationRequest(rawToken));

        // Attempting to use the same token again must fail
        assertThatThrownBy(() -> emailChangeService.confirmEmailChange(new EmailConfirmationRequest(rawToken)))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    @DisplayName("Cannot request an email already registered to another active user")
    void testCannotRequestAlreadyRegisteredEmail() {
        User otherUser = new User("already.taken@easyeat.com", "hash", UserRole.COMENSAL);
        otherUser.setStatus(UserStatus.ACTIVE);
        profileUserRepository.save(otherUser);

        assertThatThrownBy(() -> emailChangeService.requestEmailChange(diner, new EmailChangeRequestDto("already.taken@easyeat.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }
}
