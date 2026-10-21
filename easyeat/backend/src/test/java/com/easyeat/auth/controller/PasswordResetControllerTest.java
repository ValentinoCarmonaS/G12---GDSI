package com.easyeat.auth.controller;

import com.easyeat.auth.dto.PasswordResetConfirmRequest;
import com.easyeat.auth.dto.PasswordResetRequest;
import com.easyeat.auth.exception.InvalidTokenException;
import com.easyeat.auth.exception.TokenAlreadyConsumedException;
import com.easyeat.auth.exception.TokenExpiredException;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.service.AuthService;
import com.easyeat.auth.service.PasswordResetService;
import com.easyeat.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/request - 200 OK generic confirmation for valid email")
    void shouldReturn200OnPasswordResetRequest() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com");
        doNothing().when(passwordResetService).requestPasswordReset("user@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If that email address is registered, a recovery link has been sent."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/request - 422 Unprocessable Entity on invalid email")
    void shouldReturn422OnInvalidEmailInResetRequest() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("not-an-email");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/confirm - 200 OK on valid token and password")
    void shouldReturn200OnValidPasswordResetConfirm() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("valid-token-123", "NewSecurePass1");
        doNothing().when(passwordResetService).confirmPasswordReset("valid-token-123", "NewSecurePass1");

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully. All previous sessions have been invalidated."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/confirm - 400 Bad Request when token is expired")
    void shouldReturn400WhenTokenExpired() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("expired-token", "NewSecurePass1");
        doThrow(new TokenExpiredException()).when(passwordResetService).confirmPasswordReset(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Reset Token Expired"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/confirm - 400 Bad Request when token is already consumed")
    void shouldReturn400WhenTokenAlreadyConsumed() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("consumed-token", "NewSecurePass1");
        doThrow(new TokenAlreadyConsumedException()).when(passwordResetService).confirmPasswordReset(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Reset Token Already Used"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/confirm - 400 Bad Request when token is invalid")
    void shouldReturn400WhenTokenInvalid() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("invalid-token", "NewSecurePass1");
        doThrow(new InvalidTokenException()).when(passwordResetService).confirmPasswordReset(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Reset Token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password-reset/confirm - 422 Unprocessable Entity when new password is too simple")
    void shouldReturn422WhenNewPasswordInvalid() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("token-123", "weak");

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }
}
