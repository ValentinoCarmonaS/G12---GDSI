package com.easyeat.dish.controller;

import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.dto.RecipeItemAuditResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.service.FoodCostAuditService;
import com.easyeat.inventory.dto.UnitOfMeasure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDishCostAuditController.class)
@DisplayName("AdminDishCostAuditController MockMvc Slices - RBAC and State Checks")
class AdminDishCostAuditControllerTest extends DishControllerTestBase {

    @MockBean
    private FoodCostAuditService foodCostAuditService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Returns 200 OK with food cost breakdown when dish is in PENDING_PRICE")
    void auditDishCost_shouldReturn200_whenAuthorizedAndInPendingPrice() throws Exception {
        UUID dishId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();

        RecipeItemAuditResponse item = new RecipeItemAuditResponse(
                ingredientId,
                "Carne Picada",
                new BigDecimal("0.5000"),
                UnitOfMeasure.KG,
                new BigDecimal("5000.00"),
                new BigDecimal("2500.00"),
                false
        );

        FoodCostAuditResponse response = new FoodCostAuditResponse(
                dishId,
                "Pastel de Papa",
                new BigDecimal("2500.00"),
                false,
                Collections.emptyList(),
                List.of("CELIAC"),
                List.of(item)
        );

        when(foodCostAuditService.auditDishCost(dishId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.dishName").value("Pastel de Papa"))
                .andExpect(jsonPath("$.totalFoodCost").value(2500.00))
                .andExpect(jsonPath("$.hasUnpricedIngredients").value(false))
                .andExpect(jsonPath("$.requiredDietaryRestrictions[0]").value("CELIAC"))
                .andExpect(jsonPath("$.items[0].ingredientName").value("Carne Picada"))
                .andExpect(jsonPath("$.items[0].lineSubtotal").value(2500.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Returns 409 Conflict when dish is still in DRAFT status")
    void auditDishCost_shouldReturn409_whenDishInDraft() throws Exception {
        UUID dishId = UUID.randomUUID();
        when(foodCostAuditService.auditDishCost(dishId))
                .thenThrow(new DishLockedException("Requiere estado 'PENDING_PRICE'"));

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Returns 400 Bad Request when dish has an empty recipe")
    void auditDishCost_shouldReturn400_whenDishHasEmptyRecipe() throws Exception {
        UUID dishId = UUID.randomUUID();
        when(foodCostAuditService.auditDishCost(dishId))
                .thenThrow(new EmptyRecipeException(dishId));

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Returns 404 Not Found when dish does not exist")
    void auditDishCost_shouldReturn404_whenDishNotFound() throws Exception {
        UUID dishId = UUID.randomUUID();
        when(foodCostAuditService.auditDishCost(dishId))
                .thenThrow(new DishNotFoundException(dishId));

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("Returns 403 Forbidden when accessed by CHEF role")
    void auditDishCost_shouldReturn403_whenRoleIsChef() throws Exception {
        UUID dishId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns 401 Unauthorized when unauthenticated")
    void auditDishCost_shouldReturn401_whenUnauthenticated() throws Exception {
        UUID dishId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/dishes/{id}/cost-audit", dishId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
