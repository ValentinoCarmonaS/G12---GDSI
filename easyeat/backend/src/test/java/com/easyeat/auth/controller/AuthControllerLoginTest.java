package com.easyeat.auth.controller;

import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.exception.RateLimitExceededException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
class AuthControllerLoginTest {

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
    @DisplayName("POST /api/v1/auth/login - 200 OK on valid credentials")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "SecurePass1");
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(new AuthTokenResponse("fake.jwt.token", "Bearer", 28800L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("fake.jwt.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 401 Unauthorized on invalid credentials")
    void shouldReturn401WhenInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new InvalidCredentialsException("The provided email or password is incorrect."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Invalid Credentials"))
                .andExpect(jsonPath("$.detail").value("The provided email or password is incorrect."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 403 Forbidden when account is suspended")
    void shouldReturn403WhenAccountIsSuspended() throws Exception {
        LoginRequest request = new LoginRequest("suspended@example.com", "SecurePass1");
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AccountSuspendedException("The account has been suspended or is not authorized to operate."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Account Suspended"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 429 Too Many Requests on rate limit lockout")
    void shouldReturn429WhenRateLimited() throws Exception {
        LoginRequest request = new LoginRequest("locked@example.com", "SecurePass1");
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new RateLimitExceededException("Too many failed login attempts. Please try again in 15 minutes."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 422 Unprocessable Entity when email or password is blank")
    void shouldReturn422WhenFieldsAreBlank() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}
