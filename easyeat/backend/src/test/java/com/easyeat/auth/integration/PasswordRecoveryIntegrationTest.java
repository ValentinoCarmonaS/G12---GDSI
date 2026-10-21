package com.easyeat.auth.integration;

import com.easyeat.auth.domain.PasswordResetToken;
import com.easyeat.auth.domain.TokenStatus;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.dto.PasswordResetConfirmRequest;
import com.easyeat.auth.dto.PasswordResetRequest;
import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.service.EmailService;
import com.easyeat.auth.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PasswordRecoveryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @SpyBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        passwordResetService.clearCooldowns();
        reset(emailService);
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

    private String extractTokenFromUrl(String url) {
        int idx = url.indexOf("token=");
        assertThat(idx).isGreaterThan(-1);
        return url.substring(idx + "token=".length());
    }

    @Test
    @DisplayName("Full reset lifecycle: request, email dispatch, confirm update, and session invalidation")
    void testFullPasswordResetLifecycleAndSessionRevocation() throws Exception {
        String email = "comensal@easyeat.com";
        String oldPassword = "OldPassword123";
        String newPassword = "NewSecurePassword456";

        User user = new User(email, passwordEncoder.encode(oldPassword), UserRole.COMENSAL);
        user = userRepository.save(user);

        // 1. User logs in with old credentials to get active JWT (token_version = 0)
        LoginRequest initialLogin = new LoginRequest(email, oldPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse initialTokenResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );
        String oldJwt = initialTokenResponse.accessToken();

        // Verify old JWT works on protected endpoints
        mockMvc.perform(get("/api/v1/any-protected")
                        .header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isNotFound()); // 404 means authenticated, resource not found

        // 2. Request password recovery
        PasswordResetRequest resetRequest = new PasswordResetRequest(email);
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If that email address is registered, a recovery link has been sent."));

        // Capture dispatched email
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000)).sendPasswordResetEmail(eq(email), linkCaptor.capture());
        String rawToken = extractTokenFromUrl(linkCaptor.getValue());

        // 3. Confirm password reset
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(rawToken, newPassword);
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully. All previous sessions have been invalidated."));

        // Verify token in DB is marked CONSUMED
        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getStatus()).isEqualTo(TokenStatus.CONSUMED);
        assertThat(tokens.get(0).getConsumedAt()).isNotNull();

        // Verify old password no longer works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Credentials"));

        // Verify new password works
        LoginRequest newLogin = new LoginRequest(email, newPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());

        // Verify OLD JWT session is revoked (403 Forbidden due to token_version mismatch)
        mockMvc.perform(get("/api/v1/any-protected")
                        .header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reject double consumption of reset token with 400 Bad Request")
    void testDoubleConsumptionRejection() throws Exception {
        String email = "double@easyeat.com";
        String password = "Password123";

        User user = new User(email, passwordEncoder.encode(password), UserRole.COMENSAL);
        userRepository.save(user);

        // Request reset
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000)).sendPasswordResetEmail(eq(email), linkCaptor.capture());
        String rawToken = extractTokenFromUrl(linkCaptor.getValue());

        // 1st consumption -> 200 OK
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, "NewPass1234"))))
                .andExpect(status().isOk());

        // 2nd consumption -> 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, "AnotherPass1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Reset Token Already Used"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Enforce 60-second cooldown per email silently without sending multiple emails")
    void testCooldownEnforcement() throws Exception {
        String email = "cooldown@easyeat.com";
        User user = new User(email, passwordEncoder.encode("Password123"), UserRole.COMENSAL);
        userRepository.save(user);

        // 1st request -> sends email
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());

        verify(emailService, timeout(2000).times(1)).sendPasswordResetEmail(eq(email), anyString());

        // 2nd immediate request -> 200 OK, but no extra email sent
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());

        verify(emailService, times(1)).sendPasswordResetEmail(eq(email), anyString());
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Anti-enumeration: non-existent email returns 200 OK without dispatching email")
    void testAntiEnumerationForNonExistentEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("ghost@easyeat.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If that email address is registered, a recovery link has been sent."));

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Reject expired token with 400 Bad Request")
    void testExpiredTokenRejection() throws Exception {
        String email = "expired@easyeat.com";
        User user = new User(email, passwordEncoder.encode("Password123"), UserRole.COMENSAL);
        user = userRepository.save(user);

        String rawToken = "raw-expired-token-1234567890123456";
        String tokenHash = sha256(rawToken);

        PasswordResetToken expiredToken = new PasswordResetToken(user, tokenHash, Instant.now().minus(5, ChronoUnit.MINUTES));
        tokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetConfirmRequest(rawToken, "NewPass1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Reset Token Expired"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
