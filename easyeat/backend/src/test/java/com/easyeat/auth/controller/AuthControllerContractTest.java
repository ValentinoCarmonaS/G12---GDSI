package com.easyeat.auth.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.LoginRequest;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
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

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Contract: POST /api/v1/auth/login returns JWT asserting all required claims (user_id, role, tenant_id, token_version)")
    void shouldReturnJwtWithAllRequiredClaimsForStaff() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String rawPassword = "StaffPassword123";
        String email = "carlos.chef@comedorcentral.com";

        User staff = new User(
                email,
                passwordEncoder.encode(rawPassword),
                UserRole.CHEF,
                tenantId
        );
        staff.setTokenVersion(1);
        staff = userRepository.save(staff);

        LoginRequest request = new LoginRequest(email, rawPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        AuthTokenResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );

        Claims claims = jwtService.extractAllClaims(response.accessToken());

        // Standard sub claim matches user UUID
        assertThat(claims.getSubject()).isEqualTo(staff.getId().toString());

        // Explicit user_id claim
        assertThat(claims.get("user_id", String.class)).isEqualTo(staff.getId().toString());

        // Role claim
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.CHEF.name());

        // Tenant ID claim
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());

        // Token version claim
        assertThat(claims.get("token_version", Integer.class)).isNotNull();

        // Email claim
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    @Test
    @DisplayName("Contract: POST /api/v1/auth/login returns JWT with null tenant_id for COMENSAL")
    void shouldReturnJwtWithNullTenantIdForComensal() throws Exception {
        String rawPassword = "DinerPassword123";
        String email = "juan.comensal@gmail.com";

        User diner = new User(
                email,
                passwordEncoder.encode(rawPassword),
                UserRole.COMENSAL
        );
        diner = userRepository.save(diner);

        LoginRequest request = new LoginRequest(email, rawPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );

        Claims claims = jwtService.extractAllClaims(response.accessToken());
        assertThat(claims.getSubject()).isEqualTo(diner.getId().toString());
        assertThat(claims.get("user_id", String.class)).isEqualTo(diner.getId().toString());
        assertThat(claims.get("role", String.class)).isEqualTo(UserRole.COMENSAL.name());
        assertThat(claims.get("tenant_id")).isNull();
        assertThat(claims.get("token_version", Integer.class)).isNotNull();
    }
}
