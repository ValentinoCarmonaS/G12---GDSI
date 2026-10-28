package com.easyeat.dish.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeResponse;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.InvalidIngredientException;
import com.easyeat.dish.exception.InvalidQuantityException;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishController.class)
@DisplayName("DishRecipeControllerTest — Web Contract Tests for Dish Recipe Endpoints")
class DishRecipeControllerTest extends DishControllerTestBase {

    @MockBean
    private DishService dishService;

    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private final UUID ingredientId1 = UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    private final UUID ingredientId2 = UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");

    @Nested
    @DisplayName("PUT /api/v1/dishes/{id}/recipe - Upsert Recipe Ingredients (CHF-2.2.2)")
    class UpsertRecipeContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK and RecipeResponse when request is valid")
        void updateRecipe_shouldReturn200_whenValid() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 150.0),
                    new RecipeIngredientItem(ingredientId2, 2.0)
            ));

            RecipeResponse response = new RecipeResponse(
                    dishId,
                    List.of(
                            new RecipeIngredientItem(ingredientId1, "Arroz Carnaroli", 150.0, "g", true),
                            new RecipeIngredientItem(ingredientId2, "Huevos", 2.0, "u", true)
                    )
            );

            when(dishService.updateRecipe(eq(dishId), any(RecipeUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.ingredients").isArray())
                    .andExpect(jsonPath("$.ingredients.length()").value(2))
                    .andExpect(jsonPath("$.ingredients[0].ingredientId").value(ingredientId1.toString()))
                    .andExpect(jsonPath("$.ingredients[0].ingredientName").value("Arroz Carnaroli"))
                    .andExpect(jsonPath("$.ingredients[0].quantity").value(150.0))
                    .andExpect(jsonPath("$.ingredients[0].unitOfMeasure").value("g"))
                    .andExpect(jsonPath("$.ingredients[0].isIngredientActive").value(true));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request lacks authentication")
        void updateRecipe_shouldReturn401_whenUnauthenticated() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 100.0)
            ));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when user is not a CHEF")
        void updateRecipe_shouldReturn403_whenRoleForbidden() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 100.0)
            ));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish does not exist")
        void updateRecipe_shouldReturn404_whenDishNotFound() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 100.0)
            ));

            when(dishService.updateRecipe(eq(dishId), any(RecipeUpdateRequest.class)))
                    .thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is locked in PENDING_PRICE")
        void updateRecipe_shouldReturn409_whenDishLocked() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 100.0)
            ));

            when(dishService.updateRecipe(eq(dishId), any(RecipeUpdateRequest.class)))
                    .thenThrow(new DishLockedException(dishId, "PENDING_PRICE"));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 422 Unprocessable Entity when ingredient is inactive or discontinued")
        void updateRecipe_shouldReturn422_whenIngredientInactive() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 100.0)
            ));

            when(dishService.updateRecipe(eq(dishId), any(RecipeUpdateRequest.class)))
                    .thenThrow(new InvalidIngredientException(ingredientId1, "El ingrediente está discontinuado"));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 422 Unprocessable Entity when quantity violates unit rules")
        void updateRecipe_shouldReturn422_whenQuantityInvalid() throws Exception {
            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(ingredientId1, 1.5)
            ));

            when(dishService.updateRecipe(eq(dishId), any(RecipeUpdateRequest.class)))
                    .thenThrow(new InvalidQuantityException(BigDecimal.valueOf(1.5), "u", "Las unidades discretas solo aceptan valores enteros"));

            mockMvc.perform(put("/api/v1/dishes/{id}/recipe", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/dishes/{id}/recipe/ingredients/{ingredientId} - Remove Ingredient (CHF-2.2.2)")
    class RemoveRecipeIngredientContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 204 No Content when ingredient line is successfully removed")
        void removeIngredient_shouldReturn204_whenValid() throws Exception {
            doNothing().when(dishService).removeRecipeIngredient(dishId, ingredientId1);

            mockMvc.perform(delete("/api/v1/dishes/{id}/recipe/ingredients/{ingredientId}", dishId, ingredientId1))
                    .andExpect(status().isNoContent());

            verify(dishService).removeRecipeIngredient(dishId, ingredientId1);
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request lacks authentication")
        void removeIngredient_shouldReturn401_whenUnauthenticated() throws Exception {
            mockMvc.perform(delete("/api/v1/dishes/{id}/recipe/ingredients/{ingredientId}", dishId, ingredientId1))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when user is not a CHEF")
        void removeIngredient_shouldReturn403_whenRoleForbidden() throws Exception {
            mockMvc.perform(delete("/api/v1/dishes/{id}/recipe/ingredients/{ingredientId}", dishId, ingredientId1))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish does not exist")
        void removeIngredient_shouldReturn404_whenDishNotFound() throws Exception {
            doThrow(new DishNotFoundException(dishId))
                    .when(dishService).removeRecipeIngredient(dishId, ingredientId1);

            mockMvc.perform(delete("/api/v1/dishes/{id}/recipe/ingredients/{ingredientId}", dishId, ingredientId1))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is locked in PENDING_PRICE")
        void removeIngredient_shouldReturn409_whenDishLocked() throws Exception {
            doThrow(new DishLockedException(dishId, "PENDING_PRICE"))
                    .when(dishService).removeRecipeIngredient(dishId, ingredientId1);

            mockMvc.perform(delete("/api/v1/dishes/{id}/recipe/ingredients/{ingredientId}", dishId, ingredientId1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
