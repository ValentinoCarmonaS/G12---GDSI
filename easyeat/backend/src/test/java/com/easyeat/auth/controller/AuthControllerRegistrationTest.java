package com.easyeat.auth.controller;

import com.easyeat.auth.dto.RegisterRequest;
import com.easyeat.auth.dto.UserSummaryResponse;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.service.AuthService;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
class AuthControllerRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.easyeat.auth.service.PasswordResetService passwordResetService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/auth/register - 201 Created on valid comensal registration")
    void shouldRegisterComensalSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest("comensal@example.com", "SecurePass1");
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserSummaryResponse("Account created successfully."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 409 Conflict when email already registered")
    void shouldReturn409WhenEmailAlreadyRegistered() throws Exception {
        RegisterRequest request = new RegisterRequest("existing@example.com", "SecurePass1");
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("An account with this email address already exists."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email Already Registered"))
                .andExpect(jsonPath("$.detail").value("An account with this email address already exists."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 422 Unprocessable Entity when email is invalid format")
    void shouldReturn422WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("invalid-email-format", "SecurePass1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 422 Unprocessable Entity when password has no uppercase")
    void shouldReturn422WhenPasswordLacksUppercase() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "securepass1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 422 Unprocessable Entity when password has no digit")
    void shouldReturn422WhenPasswordLacksDigit() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "SecurePassword");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 422 Unprocessable Entity when password is under 8 chars")
    void shouldReturn422WhenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "Sec1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 422 Unprocessable Entity when fields are blank")
    void shouldReturn422WhenFieldsAreBlank() throws Exception {
        RegisterRequest request = new RegisterRequest("", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}
