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
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigDishAndNotificationTest.TestDishAndNotificationEndpointsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, SecurityConfigDishAndNotificationTest.TestDishAndNotificationEndpointsController.class})
@DisplayName("SecurityConfig Dish and Admin Notification Role Boundaries Test (T016)")
class SecurityConfigDishAndNotificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @TestConfiguration
    @RestController
    static class TestDishAndNotificationEndpointsController {
        @GetMapping("/api/v1/dishes")
        public ResponseEntity<String> getDishes() {
            return ResponseEntity.ok("chef dishes");
        }

        @GetMapping("/api/v1/dishes/test-id")
        public ResponseEntity<String> getDishById() {
            return ResponseEntity.ok("chef dish detail");
        }

        @GetMapping("/api/v1/admin/notifications")
        public ResponseEntity<String> getAdminNotifications() {
            return ResponseEntity.ok("admin notifications");
        }
    }

    @Test
    @DisplayName("GET /api/v1/dishes should return 401 Unauthorized when unauthenticated")
    void getDishes_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dishes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("GET /api/v1/dishes should return 403 Forbidden for non-CHEF roles")
    void getDishes_forbiddenForComensal() throws Exception {
        mockMvc.perform(get("/api/v1/dishes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/dishes should return 200 OK for CHEF role")
    void getDishes_allowedForChef() throws Exception {
        mockMvc.perform(get("/api/v1/dishes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/dishes/{id} should return 200 OK for CHEF role")
    void getDishDetail_allowedForChef() throws Exception {
        mockMvc.perform(get("/api/v1/dishes/test-id"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/admin/notifications should return 401 Unauthorized when unauthenticated")
    void getAdminNotifications_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/admin/notifications should return 403 Forbidden for CHEF role")
    void getAdminNotifications_forbiddenForChef() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /api/v1/admin/notifications should return 200 OK for ADMINISTRADOR role")
    void getAdminNotifications_allowedForAdministrador() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isOk());
    }
}
