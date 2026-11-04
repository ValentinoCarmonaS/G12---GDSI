package com.easyeat.operations;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A failure inside an authenticated request must surface as a 500, never as the 401 that makes the
 * frontend log the user out. Runs the real security chain with a real JWT.
 */
@AutoConfigureMockMvc
@Import(ServerErrorIsNotSessionExpiryTest.BoomController.class)
@DisplayName("A server error is not a session expiry")
class ServerErrorIsNotSessionExpiryTest extends OperationsPostgresTestBase {

    @TestConfiguration
    @RestController
    static class BoomController {
        @GetMapping("/api/v1/admin/test-boom-checked")
        String boomChecked() throws Exception {
            throw new Exception("checked failure");
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private String token;
    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setEmail("boom.admin." + UUID.randomUUID() + "@easyeat.com");
        admin.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(CAFE_ID);
        admin.setTokenVersion(0);
        admin = userRepository.save(admin);
        token = jwtService.generateToken(admin);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(admin.getId());
    }

    @Test
    @DisplayName("An unexpected exception answers 500 ProblemDetail")
    void unexpectedExceptionAnswers500() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test-boom-checked").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }

    @Test
    @DisplayName("The token is still valid after the failure")
    void tokenStillValidAfterFailure() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test-boom-checked").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/v1/admin/payment-methods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
