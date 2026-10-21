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
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.service.EmailService;
import com.easyeat.auth.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
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

import java.util.List;
import java.util.UUID;

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
class AdminPasswordRecoveryIntegrationTest {

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
    private JwtService jwtService;

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

    private String extractTokenFromUrl(String url) {
        int idx = url.indexOf("token=");
        assertThat(idx).isGreaterThan(-1);
        return url.substring(idx + "token=".length());
    }

    @Test
    @DisplayName("Administrator password recovery flows through centralized service and invalidates active admin session tokens")
    void testAdminPasswordRecoveryFlowAndSessionRevocation() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String adminEmail = "admin.restaurante@easyeat.com";
        String oldPassword = "AdminInitialPass1";
        String newPassword = "AdminNewSecurePass2";

        // Create Administrator user with tenant association
        User adminUser = new User(
                adminEmail,
                passwordEncoder.encode(oldPassword),
                UserRole.ADMINISTRADOR,
                tenantId
        );
        userRepository.save(adminUser);

        // 1. Admin logs in with initial credentials
        LoginRequest initialLogin = new LoginRequest(adminEmail, oldPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse tokenResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );
        String oldJwt = tokenResponse.accessToken();

        // Verify initial token claims
        Claims initialClaims = jwtService.extractAllClaims(oldJwt);
        assertThat(initialClaims.get("role", String.class)).isEqualTo("ADMINISTRADOR");
        assertThat(initialClaims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(initialClaims.get("token_version", Integer.class)).isEqualTo(1);

        // Verify old JWT accesses protected endpoints (404 means authenticated)
        mockMvc.perform(get("/api/v1/admin-protected")
                        .header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isNotFound());

        // 2. Admin requests password reset via centralized endpoint
        PasswordResetRequest resetRequest = new PasswordResetRequest(adminEmail);
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If that email address is registered, a recovery link has been sent."));

        // Capture reset email
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000)).sendPasswordResetEmail(eq(adminEmail), linkCaptor.capture());
        String rawToken = extractTokenFromUrl(linkCaptor.getValue());

        // Verify token created for admin user
        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getStatus()).isEqualTo(TokenStatus.PENDING);
        assertThat(tokens.get(0).getUser().getId()).isEqualTo(adminUser.getId());

        // 3. Admin completes password update
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(rawToken, newPassword);
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully. All previous sessions have been invalidated."));

        // 4. Verify previous admin session token is immediately invalidated (401 Unauthorized ProblemDetail)
        mockMvc.perform(get("/api/v1/admin-protected")
                        .header("Authorization", "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());

        // 5. Verify old password is rejected
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialLogin)))
                .andExpect(status().isUnauthorized());

        // 6. Verify subsequent login with new password succeeds and preserves tenant_id claim
        LoginRequest newLogin = new LoginRequest(adminEmail, newPassword);
        MvcResult newLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse newTokenResponse = objectMapper.readValue(
                newLoginResult.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );
        String newJwt = newTokenResponse.accessToken();

        Claims newClaims = jwtService.extractAllClaims(newJwt);
        assertThat(newClaims.get("role", String.class)).isEqualTo("ADMINISTRADOR");
        assertThat(newClaims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(newClaims.get("token_version", Integer.class)).isEqualTo(3);

        // Verify new JWT accesses protected endpoints
        mockMvc.perform(get("/api/v1/admin-protected")
                        .header("Authorization", "Bearer " + newJwt))
                .andExpect(status().isNotFound());
    }
}
