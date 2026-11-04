package com.easyeat.dish.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DishController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("T036: Dish Chef Pause Security Test (INV-16, FR-018, SC-010, EC-019)")
class DishChefPauseSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DishService dishService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("testAdminCannotOperateChefToggle: rol Admin -> HTTP 403 al PATCH chef-pause (INV-16, FR-018, SC-010, EC-019)")
    void testAdminCannotOperateChefToggle() throws Exception {
        UUID dishId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "paused": true
                }
                """;

        mockMvc.perform(patch("/api/v1/dishes/{id}/chef-pause", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("testDinerCannotOperateChefToggle: rol Comensal -> HTTP 403 al PATCH chef-pause")
    void testDinerCannotOperateChefToggle() throws Exception {
        UUID dishId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "paused": true
                }
                """;

        mockMvc.perform(patch("/api/v1/dishes/{id}/chef-pause", dishId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }
}

