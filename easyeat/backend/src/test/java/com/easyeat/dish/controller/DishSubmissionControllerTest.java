package com.easyeat.dish.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.IncompleteDishException;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishController.class)
@DisplayName("DishSubmissionControllerTest — Web Contract Tests for Dish Submission Endpoint (T059, CHF-2.2.4)")
class DishSubmissionControllerTest extends DishControllerTestBase {

    @MockBean
    private DishService dishService;

    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    @Nested
    @DisplayName("POST /api/v1/dishes/{id}/submit-review - Review Submission Contract Tests")
    class SubmitReviewContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK and DishDetailResponse when submission succeeds")
        void submitForReview_shouldReturn200_whenValid() throws Exception {
            DishDetailResponse detailResponse = new DishDetailResponse(
                    dishId,
                    DishTestFixtures.DEFAULT_TENANT_ID,
                    "Risotto de Hongos",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Descripción del plato",
                    null,
                    DishStatus.PENDING_PRICE,
                    true,
                    List.of("VEGETARIAN"),
                    Collections.emptyList(),
                    true,
                    Collections.emptyList(),
                    UUID.randomUUID(),
                    Instant.now(),
                    Instant.now()
            );

            when(dishService.submitForReview(dishId)).thenReturn(detailResponse);

            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(dishId.toString()))
                    .andExpect(jsonPath("$.name").value("Risotto de Hongos"))
                    .andExpect(jsonPath("$.status").value("PENDING_PRICE"))
                    .andExpect(jsonPath("$.allergensConfirmed").value(true));
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when request has no authentication token")
        void submitForReview_shouldReturn401_whenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when user does not have CHEF role")
        void submitForReview_shouldReturn403_whenForbiddenRole() throws Exception {
            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish does not exist")
        void submitForReview_shouldReturn404_whenDishNotFound() throws Exception {
            when(dishService.submitForReview(dishId)).thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Dish Not Found"))
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is already in PENDING_PRICE")
        void submitForReview_shouldReturn409_whenDishAlreadyPendingPrice() throws Exception {
            when(dishService.submitForReview(dishId)).thenThrow(new DishLockedException(dishId, "PENDING_PRICE"));

            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Dish Locked"))
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.currentStatus").value("PENDING_PRICE"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 422 Unprocessable Entity when completeness conditions are unmet")
        void submitForReview_shouldReturn422_whenIncompleteDish() throws Exception {
            List<String> missing = List.of(
                    "La receta debe contener al menos un ingrediente con cantidad mayor a cero",
                    "La sección de alérgenos debe ser explícitamente confirmada"
            );
            when(dishService.submitForReview(dishId)).thenThrow(new IncompleteDishException(dishId, missing));

            mockMvc.perform(post("/api/v1/dishes/{id}/submit-review", dishId))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Incomplete Dish Technical File"))
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.missingConditions").isArray())
                    .andExpect(jsonPath("$.missingConditions.length()").value(2));
        }
    }
}
