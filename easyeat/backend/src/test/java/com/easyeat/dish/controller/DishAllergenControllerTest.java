package com.easyeat.dish.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.dto.AllergenResponse;
import com.easyeat.dish.dto.AllergenUpdateRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.service.DishService;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishController.class)
@DisplayName("DishAllergenControllerTest — Web Contract Tests for Dish Allergen Endpoints (T048, CHF-2.2.3)")
class DishAllergenControllerTest extends DishControllerTestBase {

    @MockBean
    private DishService dishService;

    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    @Nested
    @DisplayName("PUT /api/v1/dishes/{id}/allergens - Update Allergens and Confirmation (CHF-2.2.3)")
    class UpdateAllergensContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK and AllergenResponse when payload is valid")
        void updateAllergens_shouldReturn200_whenValid() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("CELIAC", "DAIRY"), true);
            AllergenResponse response = new AllergenResponse(dishId, List.of("CELIAC", "DAIRY"), true);

            when(dishService.updateAllergens(eq(dishId), any(AllergenUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.restrictionCodes").isArray())
                    .andExpect(jsonPath("$.restrictionCodes.length()").value(2))
                    .andExpect(jsonPath("$.restrictionCodes[0]").value("CELIAC"))
                    .andExpect(jsonPath("$.restrictionCodes[1]").value("DAIRY"))
                    .andExpect(jsonPath("$.allergensConfirmed").value(true));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK when zero labels are supplied with confirmed true")
        void updateAllergens_shouldReturn200_whenZeroLabelsConfirmed() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(Collections.emptyList(), true);
            AllergenResponse response = new AllergenResponse(dishId, Collections.emptyList(), true);

            when(dishService.updateAllergens(eq(dishId), any(AllergenUpdateRequest.class))).thenReturn(response);

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.restrictionCodes").isArray())
                    .andExpect(jsonPath("$.restrictionCodes").isEmpty())
                    .andExpect(jsonPath("$.allergensConfirmed").value(true));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 422 Unprocessable Entity when confirmed field is missing")
        void updateAllergens_shouldReturn422_whenConfirmedMissing() throws Exception {
            String invalidJson = """
                    {
                      "restrictionCodes": ["VEGAN"]
                    }
                    """;

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 400 Bad Request when dietary taxonomy code is unrecognized")
        void updateAllergens_shouldReturn400_whenInvalidTaxonomy() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("INVALID_CODE"), true);

            when(dishService.updateAllergens(eq(dishId), any(AllergenUpdateRequest.class)))
                    .thenThrow(DietaryTaxonomyException.forInvalidCode("INVALID_CODE"));

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Dietary Taxonomy"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish does not exist")
        void updateAllergens_shouldReturn404_whenNotFound() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(Collections.emptyList(), true);

            when(dishService.updateAllergens(eq(dishId), any(AllergenUpdateRequest.class)))
                    .thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Dish Not Found"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is in PENDING_PRICE status")
        void updateAllergens_shouldReturn409_whenLocked() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("CELIAC"), true);

            when(dishService.updateAllergens(eq(dishId), any(AllergenUpdateRequest.class)))
                    .thenThrow(new DishLockedException(dishId, "PENDING_PRICE"));

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Dish Locked"));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request lacks authentication")
        void updateAllergens_shouldReturn401_whenUnauthenticated() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("VEGAN"), true);

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when authenticated user lacks CHEF role")
        void updateAllergens_shouldReturn403_whenForbiddenRole() throws Exception {
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("VEGAN"), true);

            mockMvc.perform(put("/api/v1/dishes/{id}/allergens", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }
}
