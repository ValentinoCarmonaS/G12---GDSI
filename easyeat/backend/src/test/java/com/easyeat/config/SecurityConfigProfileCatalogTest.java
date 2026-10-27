package com.easyeat.config;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigProfileCatalogTest.TestEndpointsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, SecurityConfigProfileCatalogTest.TestEndpointsController.class})
class SecurityConfigProfileCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    @RestController
    static class TestEndpointsController {
        @GetMapping("/api/v1/dietary-restrictions")
        public ResponseEntity<String> getDietaryRestrictions() {
            return ResponseEntity.ok("public taxonomy");
        }

        @PostMapping("/api/v1/profile/email-change/confirm")
        public ResponseEntity<String> confirmEmailChange() {
            return ResponseEntity.ok("confirm public");
        }

        @GetMapping("/api/v1/establishments/{tenantId}/dishes")
        public ResponseEntity<String> getDishes(@PathVariable UUID tenantId) {
            return ResponseEntity.ok("public catalog");
        }

        @GetMapping("/api/v1/profile/me")
        public ResponseEntity<String> getProfileMe() {
            return ResponseEntity.ok("protected profile");
        }
    }

    @Test
    @DisplayName("GET /api/v1/dietary-restrictions should permitAll without authentication")
    void getDietaryRestrictions_permitted() throws Exception {
        mockMvc.perform(get("/api/v1/dietary-restrictions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/profile/email-change/confirm should permitAll without authentication")
    void confirmEmailChange_permitted() throws Exception {
        mockMvc.perform(post("/api/v1/profile/email-change/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{tenantId}/dishes should permitAll without authentication")
    void getDishes_permitted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/establishments/" + tenantId + "/dishes"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/profile/me should require authentication (401 Unauthorized)")
    void getProfileMe_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me"))
                .andExpect(status().isUnauthorized());
    }
}
