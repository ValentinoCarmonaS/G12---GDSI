package com.easyeat.profile.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.easyeat.profile.dto.*;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.EmailVerificationException;
import com.easyeat.profile.service.EmailChangeService;
import com.easyeat.profile.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("ProfileController Web Contract Tests (T012, COM-1.4.1)")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private EmailChangeService emailChangeService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private User sampleDiner;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        sampleDiner = new User("diner@example.com", "hash", UserRole.COMENSAL);
        sampleDiner.setId(UUID.randomUUID());
        sampleDiner.setFirstName("Valentino");
        sampleDiner.setLastName("Carmona");
        sampleDiner.setFullName("Valentino Carmona");
        sampleDiner.setPhone("+54 9 11 2345-6789");
        sampleDiner.setStatus(UserStatus.ACTIVE);

        authPrincipal = new UsernamePasswordAuthenticationToken(
                sampleDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Nested
    @DisplayName("GET /api/v1/profile/me")
    class GetProfileTests {

        @Test
        @DisplayName("200 OK with ProfileResponse when authenticated")
        void getProfile_authenticated_returns200() throws Exception {
            ProfileResponse response = new ProfileResponse(
                    sampleDiner.getId(),
                    "Valentino",
                    "Carmona",
                    "Valentino Carmona",
                    "+54 9 11 2345-6789",
                    "diner@example.com",
                    null,
                    false,
                    List.of()
            );

            when(profileService.getProfile(any(User.class))).thenReturn(response);

            mockMvc.perform(get("/api/v1/profile/me")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(sampleDiner.getId().toString()))
                    .andExpect(jsonPath("$.firstName").value("Valentino"))
                    .andExpect(jsonPath("$.lastName").value("Carmona"))
                    .andExpect(jsonPath("$.email").value("diner@example.com"))
                    .andExpect(jsonPath("$.emailVerificationPending").value(false));
        }

        @Test
        @DisplayName("401 Unauthorized when unauthenticated")
        void getProfile_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/profile/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile/personal-data")
    class UpdatePersonalDataTests {

        @Test
        @DisplayName("200 OK with updated profile on valid names and phone")
        void updatePersonalData_valid_returns200() throws Exception {
            PersonalDataRequest request = new PersonalDataRequest("Agustin", "Fraga", "+5491112345678");
            ProfileResponse response = new ProfileResponse(
                    sampleDiner.getId(),
                    "Agustin",
                    "Fraga",
                    "Agustin Fraga",
                    "+5491112345678",
                    "diner@example.com",
                    null,
                    false,
                    List.of()
            );

            when(profileService.updatePersonalData(any(User.class), any(PersonalDataRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/v1/profile/personal-data")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Agustin"))
                    .andExpect(jsonPath("$.lastName").value("Fraga"))
                    .andExpect(jsonPath("$.fullName").value("Agustin Fraga"))
                    .andExpect(jsonPath("$.phone").value("+5491112345678"));
        }

        @Test
        @DisplayName("422 Unprocessable Entity when names contain digits or symbols")
        void updatePersonalData_invalidName_returns422() throws Exception {
            PersonalDataRequest request = new PersonalDataRequest("Valen123", "Carmona", null);

            mockMvc.perform(put("/api/v1/profile/personal-data")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        @DisplayName("422 Unprocessable Entity when phone format is invalid")
        void updatePersonalData_invalidPhone_returns422() throws Exception {
            PersonalDataRequest request = new PersonalDataRequest("Valen", "Carmona", "123");

            mockMvc.perform(put("/api/v1/profile/personal-data")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors").isArray());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/profile/email-change/request")
    class RequestEmailChangeTests {

        @Test
        @DisplayName("202 Accepted on valid email change request")
        void requestEmailChange_valid_returns202() throws Exception {
            EmailChangeRequestDto request = new EmailChangeRequestDto("new.email@example.com");
            EmailChangeInitiatedResponse response = new EmailChangeInitiatedResponse(
                    "Verification email sent",
                    "new.email@example.com",
                    Instant.now().plusSeconds(86400)
            );

            when(emailChangeService.requestEmailChange(any(User.class), any(EmailChangeRequestDto.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/profile/email-change/request")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.pendingEmail").value("new.email@example.com"));
        }

        @Test
        @DisplayName("409 Conflict when email is already registered")
        void requestEmailChange_alreadyTaken_returns409() throws Exception {
            EmailChangeRequestDto request = new EmailChangeRequestDto("taken@example.com");

            when(emailChangeService.requestEmailChange(any(User.class), any(EmailChangeRequestDto.class)))
                    .thenThrow(new EmailAlreadyRegisteredException("El correo electrónico ya está registrado"));

            mockMvc.perform(post("/api/v1/profile/email-change/request")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("422 Unprocessable Entity when email syntax is invalid")
        void requestEmailChange_invalidEmail_returns422() throws Exception {
            EmailChangeRequestDto request = new EmailChangeRequestDto("not-an-email");

            mockMvc.perform(post("/api/v1/profile/email-change/request")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/profile/email-change/resend")
    class ResendEmailVerificationTests {

        @Test
        @DisplayName("202 Accepted when cooldown has passed")
        void resendEmailVerification_success_returns202() throws Exception {
            EmailChangeInitiatedResponse response = new EmailChangeInitiatedResponse(
                    "Verification link redispatched",
                    "new.email@example.com",
                    Instant.now().plusSeconds(86400)
            );

            when(emailChangeService.resendVerificationEmail(any(User.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/profile/email-change/resend")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.pendingEmail").value("new.email@example.com"));
        }

        @Test
        @DisplayName("429 Too Many Requests with Retry-After header during active cooldown")
        void resendEmailVerification_cooldownActive_returns429() throws Exception {
            when(emailChangeService.resendVerificationEmail(any(User.class)))
                    .thenThrow(new CooldownRateLimitException(45));

            mockMvc.perform(post("/api/v1/profile/email-change/resend")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "45"))
                    .andExpect(jsonPath("$.retryAfterSeconds").value(45));
        }

        @Test
        @DisplayName("404 Not Found when no pending change exists")
        void resendEmailVerification_noPending_returns404() throws Exception {
            when(emailChangeService.resendVerificationEmail(any(User.class)))
                    .thenThrow(new NoSuchElementException("No pending email verification"));

            mockMvc.perform(post("/api/v1/profile/email-change/resend")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/profile/email-change/pending")
    class CancelEmailChangeTests {

        @Test
        @DisplayName("204 No Content on successful cancellation")
        void cancelEmailChange_success_returns204() throws Exception {
            doNothing().when(emailChangeService).cancelPendingEmailChange(any(User.class));

            mockMvc.perform(delete("/api/v1/profile/email-change/pending")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isNoContent());

            verify(emailChangeService).cancelPendingEmailChange(any(User.class));
        }

        @Test
        @DisplayName("404 Not Found when trying to cancel non-existent pending change")
        void cancelEmailChange_noPending_returns404() throws Exception {
            doThrow(new NoSuchElementException("No pending email")).when(emailChangeService).cancelPendingEmailChange(any(User.class));

            mockMvc.perform(delete("/api/v1/profile/email-change/pending")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/profile/email-change/confirm")
    class ConfirmEmailChangeTests {

        @Test
        @DisplayName("200 OK without authentication on valid token confirmation")
        void confirmEmailChange_validToken_returns200() throws Exception {
            EmailConfirmationRequest request = new EmailConfirmationRequest("valid_raw_token");
            EmailConfirmedResponse response = new EmailConfirmedResponse(
                    "Email successfully verified",
                    "new.email@example.com"
            );

            when(emailChangeService.confirmEmailChange(any(EmailConfirmationRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/profile/email-change/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeEmail").value("new.email@example.com"));
        }

        @Test
        @DisplayName("400 Bad Request when token is invalid or expired")
        void confirmEmailChange_invalidToken_returns400() throws Exception {
            EmailConfirmationRequest request = new EmailConfirmationRequest("expired_token");

            when(emailChangeService.confirmEmailChange(any(EmailConfirmationRequest.class)))
                    .thenThrow(new EmailVerificationException("Token expirado o inválido"));

            mockMvc.perform(post("/api/v1/profile/email-change/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
