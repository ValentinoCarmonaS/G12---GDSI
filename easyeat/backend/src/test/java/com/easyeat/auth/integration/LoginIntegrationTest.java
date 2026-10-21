package com.easyeat.auth.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.RateLimitingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LoginIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        rateLimitingService.clear();
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    @DisplayName("Unified login succeeds for all 4 roles and issues deterministic JWT claims")
    void testUnifiedLoginForAllRoles(UserRole role) throws Exception {
        UUID tenantId = (role == UserRole.COMENSAL) ? null : UUID.randomUUID();
        String rawPassword = "SecurePass123";
        String email = role.name().toLowerCase() + "@easyeat.com";

        User user = new User(
                email,
                passwordEncoder.encode(rawPassword),
                role,
                tenantId
        );
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest(email, rawPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        AuthTokenResponse tokenResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );

        Claims claims = jwtService.extractAllClaims(tokenResponse.accessToken());
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(role.name());
        assertThat(claims.get("token_version", Integer.class)).isEqualTo(0);

        if (tenantId != null) {
            assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        } else {
            assertThat(claims.get("tenant_id")).isNull();
        }
    }

    @Test
    @DisplayName("Suspended account login is rejected with HTTP 403 Forbidden")
    void testSuspendedAccountLoginBlocked() throws Exception {
        String email = "suspended@easyeat.com";
        String password = "Password123";

        User suspendedUser = new User(
                email,
                passwordEncoder.encode(password),
                UserRole.COMENSAL
        );
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        userRepository.save(suspendedUser);

        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account Suspended"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("The account has been suspended or is not authorized to operate."));
    }

    @Test
    @DisplayName("Rate-limiting locks account after 5 consecutive failures and returns HTTP 429")
    void testRateLimitingLockout() throws Exception {
        String email = "victim@easyeat.com";
        String correctPassword = "CorrectPass123";

        User user = new User(
                email,
                passwordEncoder.encode(correctPassword),
                UserRole.COMENSAL
        );
        userRepository.save(user);

        LoginRequest badRequest = new LoginRequest(email, "WrongPassword");

        // Execute 5 failed login attempts -> 401 Unauthorized
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Invalid Credentials"));
        }

        // 6th attempt with bad password -> 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429));

        // 7th attempt even with correct password -> still 429 locked
        LoginRequest goodRequest = new LoginRequest(email, correctPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    @DisplayName("Non-existent user receives timing-resistant 401 with generic Invalid Credentials message")
    void testNonExistentUserLoginReturns401() throws Exception {
        LoginRequest request = new LoginRequest("ghost@easyeat.com", "SomePassword123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Credentials"))
                .andExpect(jsonPath("$.detail").value("The provided email or password is incorrect."))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint verifies valid JWT and rejects request when token_version is incremented")
    void testProtectedEndpointAccessAndTokenVersionRevocation() throws Exception {
        String email = "tokenversion@easyeat.com";
        String password = "Password123";

        User user = new User(
                email,
                passwordEncoder.encode(password),
                UserRole.COMENSAL
        );
        user = userRepository.save(user);

        // 1. Login to get JWT (token_version = 0)
        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse tokenResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );
        String token = tokenResponse.accessToken();

        // 2. Request to protected endpoint with valid JWT (authenticates successfully, returns 404 because controller route doesn't exist)
        mockMvc.perform(get("/api/v1/nonexistent-protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // 3. Increment token_version in database (simulates logout or password reset session revocation)
        user.incrementTokenVersion();
        userRepository.save(user);

        // 4. Request with old token (token_version mismatch) -> Filter rejects with HTTP 401 Unauthorized ProblemDetail
        mockMvc.perform(get("/api/v1/nonexistent-protected")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }
}
