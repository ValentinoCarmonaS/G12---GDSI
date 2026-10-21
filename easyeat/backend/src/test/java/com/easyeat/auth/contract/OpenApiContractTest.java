package com.easyeat.auth.contract;

import com.easyeat.auth.dto.AuthTokenResponse;
import com.easyeat.auth.dto.UserSummaryResponse;
import com.easyeat.auth.service.AuthService;
import com.easyeat.auth.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("Assert OpenAPI contract specification file exists and defines all 4 required auth endpoints")
    void shouldVerifyOpenApiContractFileExistsAndDefinesEndpoints() throws Exception {
        Path contractPath = Paths.get("../../specs/001-central-auth-recovery/contracts/openapi.yaml").toAbsolutePath().normalize();
        if (!Files.exists(contractPath)) {
            // Alternative search path if run from repo root
            contractPath = Paths.get("specs/001-central-auth-recovery/contracts/openapi.yaml").toAbsolutePath().normalize();
        }

        assertThat(Files.exists(contractPath))
                .withFailMessage("OpenAPI contract file not found at " + contractPath)
                .isTrue();

        String contractYaml = Files.readString(contractPath);
        assertThat(contractYaml).contains("openapi: \"3.0.3\"");
        assertThat(contractYaml).contains("/api/v1/auth/register:");
        assertThat(contractYaml).contains("/api/v1/auth/login:");
        assertThat(contractYaml).contains("/api/v1/auth/password-reset/request:");
        assertThat(contractYaml).contains("/api/v1/auth/password-reset/confirm:");
    }

    @Test
    @DisplayName("Verify /api/v1/auth/register response schema conforms to OpenAPI 201 contract")
    void shouldConformToRegisterSuccessContract() throws Exception {
        when(authService.register(any())).thenReturn(new UserSummaryResponse("Account created successfully."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"valid.user@example.com\",\"password\":\"StrongP@ssw0rd1\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Account created successfully."));
    }

    @Test
    @DisplayName("Verify /api/v1/auth/register validation error conforms to OpenAPI 422 ProblemDetail contract")
    void shouldConformToRegisterValidation422Contract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Verify /api/v1/auth/login response schema conforms to OpenAPI 200 Bearer token contract")
    void shouldConformToLoginSuccessContract() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(new AuthTokenResponse("sample.jwt.token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"valid@example.com\",\"password\":\"ValidP@ssw0rd\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").value("sample.jwt.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800));
    }

    @Test
    @DisplayName("Verify /api/v1/auth/password-reset/request response schema conforms to OpenAPI 200 contract")
    void shouldConformToPasswordResetRequestContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"registered@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("If that email address is registered, a recovery link has been sent."));
    }

    @Test
    @DisplayName("Verify /api/v1/auth/password-reset/confirm response schema conforms to OpenAPI 200 contract")
    void shouldConformToPasswordResetConfirmContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-hex-token-1234567890\",\"new_password\":\"NewStrongP@ss1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Password updated successfully. All previous sessions have been invalidated."));
    }
}
