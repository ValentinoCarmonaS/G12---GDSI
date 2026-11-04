package com.easyeat.inventory.security;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.controller.AdminIngredientController;
import com.easyeat.inventory.dto.StringToIngredientCategoryConverter;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.inventory.service.StockAdjustmentService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminIngredientController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
@DisplayName("T010: Inventory Security Test (INV-16, FR-017, SC-009, EC-018)")
class InventorySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngredientService ingredientService;

    @MockBean
    private StockAdjustmentService stockAdjustmentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("INV-16, FR-017, SC-009, EC-018: testChefCannotSetThreshold - CHEF gets HTTP 403 on create ingredient with threshold")
    void testChefCannotSetThreshold_onCreate() throws Exception {
        String jsonPayload = """
                {
                    "name": "Secret Spice",
                    "category": "Spices",
                    "unitOfMeasure": "g",
                    "unitPurchaseCost": 15.50,
                    "criticalStockThreshold": 50
                }
                """;

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("INV-16, FR-017, SC-009, EC-018: testChefCannotSetThreshold - CHEF gets HTTP 403 on update ingredient threshold")
    void testChefCannotSetThreshold_onUpdate() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        String jsonPayload = """
                {
                    "name": "Updated Spice",
                    "category": "Spices",
                    "unitOfMeasure": "g",
                    "unitPurchaseCost": 15.50,
                    "criticalStockThreshold": 50
                }
                """;

        mockMvc.perform(put("/api/v1/admin/ingredients/{id}", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }
}
