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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigDinerCatalogTest.TestDinerCatalogEndpointsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, SecurityConfigDinerCatalogTest.TestDinerCatalogEndpointsController.class})
@DisplayName("SecurityConfig Diner Catalog and Discovery Role Boundaries Test (T010)")
class SecurityConfigDinerCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    @RestController
    static class TestDinerCatalogEndpointsController {
        @GetMapping("/api/v1/establishments/search")
        public ResponseEntity<String> searchEstablishments() {
            return ResponseEntity.ok("search results");
        }

        @GetMapping("/api/v1/establishments/{tenantId}/catalog")
        public ResponseEntity<String> getCatalog(@PathVariable UUID tenantId) {
            return ResponseEntity.ok("catalog content");
        }

        @GetMapping("/api/v1/establishments/{tenantId}/catalog/availability")
        public ResponseEntity<String> getAvailability(@PathVariable UUID tenantId) {
            return ResponseEntity.ok("availability map");
        }
    }

    @Test
    @DisplayName("GET /api/v1/establishments/search should return 401 Unauthorized when unauthenticated")
    void searchEstablishments_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/search"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/establishments/search should return 403 Forbidden for CHEF role")
    void searchEstablishments_forbiddenForChef() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/search"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/establishments/search should return 403 Forbidden for ADMIN role")
    void searchEstablishments_forbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/search"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("GET /api/v1/establishments/search should return 200 OK for COMENSAL role")
    void searchEstablishments_allowedForComensal() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/search"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/catalog should return 401 Unauthorized when unauthenticated")
    void getCatalog_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/establishments/{id}/catalog should return 403 Forbidden for CHEF role")
    void getCatalog_forbiddenForChef() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("GET /api/v1/establishments/{id}/catalog should return 200 OK for COMENSAL role")
    void getCatalog_allowedForComensal() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/catalog/availability should return 401 Unauthorized when unauthenticated")
    void getAvailability_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog/availability"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/establishments/{id}/catalog/availability should return 403 Forbidden for CHEF role")
    void getAvailability_forbiddenForChef() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog/availability"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("GET /api/v1/establishments/{id}/catalog/availability should return 200 OK for COMENSAL role")
    void getAvailability_allowedForComensal() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/" + UUID.randomUUID() + "/catalog/availability"))
                .andExpect(status().isOk());
    }
}
