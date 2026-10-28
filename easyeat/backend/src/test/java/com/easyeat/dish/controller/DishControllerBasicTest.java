package com.easyeat.dish.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.DishSummaryResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.DuplicateDishNameException;
import com.easyeat.dish.service.DishService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishController.class)
class DishControllerBasicTest extends DishControllerTestBase {

    @MockBean
    private DishService dishService;

    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    private DishDetailResponse createSampleDetailResponse(UUID id, String name, DishCategory category, DishStatus status) {
        return new DishDetailResponse(
                id,
                defaultTenantId,
                name,
                category,
                "Descripción detallada del plato",
                null,
                status,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                List.of("Se debe confirmar la sección de alérgenos"),
                defaultChefId,
                Instant.now(),
                Instant.now()
        );
    }

    @Nested
    @DisplayName("POST /api/v1/dishes - Create Dish Draft (FR-001, FR-002, CHF-2.2.1)")
    class CreateDishDraftContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 201 Created and DishDetailResponse when request is valid")
        void createDishDraft_shouldReturn201_whenValid() throws Exception {
            CreateDishRequest request = new CreateDishRequest(
                    "Risotto de Hongos Portobello",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Arroz arborio cremoso con hongos"
            );

            DishDetailResponse detail = createSampleDetailResponse(
                    dishId, request.name(), request.category(), DishStatus.DRAFT
            );

            when(dishService.createDraft(any(CreateDishRequest.class), any())).thenReturn(detail);

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(dishId.toString()))
                    .andExpect(jsonPath("$.name").value("Risotto de Hongos Portobello"))
                    .andExpect(jsonPath("$.category").value("Platos principales"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.allergensConfirmed").value(false))
                    .andExpect(jsonPath("$.isCompleteForReview").value(false));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when user is not a CHEF")
        void createDishDraft_shouldReturn403_whenNotChef() throws Exception {
            CreateDishRequest request = new CreateDishRequest(
                    "Risotto de Hongos",
                    DishCategory.PLATOS_PRINCIPALES,
                    null
            );

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when unauthenticated")
        void createDishDraft_shouldReturn401_whenUnauthenticated() throws Exception {
            CreateDishRequest request = new CreateDishRequest(
                    "Risotto de Hongos",
                    DishCategory.PLATOS_PRINCIPALES,
                    null
            );

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 422 Unprocessable Entity when name is empty or missing")
        void createDishDraft_shouldReturn422_whenNameIsBlank() throws Exception {
            String invalidJson = """
                    {
                      "name": "   ",
                      "category": "Platos principales"
                    }
                    """;

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 400 Bad Request when category is invalid or missing")
        void createDishDraft_shouldReturn400_whenCategoryIsInvalid() throws Exception {
            String invalidJson = """
                    {
                      "name": "Plato con categoría inválida",
                      "category": "CategoriaInexistente"
                    }
                    """;

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict with ProblemDetail when dish name already exists in tenant")
        void createDishDraft_shouldReturn409_whenDuplicateName() throws Exception {
            CreateDishRequest request = new CreateDishRequest(
                    "Risotto Duplicado",
                    DishCategory.PLATOS_PRINCIPALES,
                    null
            );

            when(dishService.createDraft(any(CreateDishRequest.class), any()))
                    .thenThrow(new DuplicateDishNameException("Risotto Duplicado", defaultTenantId));

            mockMvc.perform(post("/api/v1/dishes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-name-duplicate"))
                    .andExpect(jsonPath("$.title").value("Duplicate Dish Name"))
                    .andExpect(jsonPath("$.status").value(409));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/dishes - List Dishes (FR-021, CHF-2.2.1, CHF-2.2.4)")
    class ListDishesContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK with paginated DishSummaryResponse list")
        void listDishes_shouldReturn200_withPagination() throws Exception {
            DishSummaryResponse summary = new DishSummaryResponse(
                    dishId,
                    defaultTenantId,
                    "Risotto de Hongos",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Descripción",
                    null,
                    DishStatus.DRAFT,
                    false,
                    3,
                    false,
                    Instant.now(),
                    Instant.now()
            );

            DishPageResponse pageResponse = new DishPageResponse(
                    List.of(summary),
                    0,
                    20,
                    1L,
                    1,
                    true
            );

            when(dishService.listDishes(eq("DRAFT"), any(Pageable.class))).thenReturn(pageResponse);

            mockMvc.perform(get("/api/v1/dishes")
                            .param("status", "DRAFT")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(dishId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("Risotto de Hongos"))
                    .andExpect(jsonPath("$.pageNumber").value(0))
                    .andExpect(jsonPath("$.pageSize").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/dishes/{id} - Get Dish Details (FR-001, CHF-2.2.1)")
    class GetDishByIdContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK with DishDetailResponse when found")
        void getDishById_shouldReturn200_whenFound() throws Exception {
            DishDetailResponse detail = createSampleDetailResponse(
                    dishId, "Risotto de Hongos", DishCategory.PLATOS_PRINCIPALES, DishStatus.DRAFT
            );

            when(dishService.getDishById(dishId)).thenReturn(detail);

            mockMvc.perform(get("/api/v1/dishes/{id}", dishId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(dishId.toString()))
                    .andExpect(jsonPath("$.name").value("Risotto de Hongos"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish does not exist")
        void getDishById_shouldReturn404_whenNotFound() throws Exception {
            when(dishService.getDishById(dishId)).thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(get("/api/v1/dishes/{id}", dishId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-not-found"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/dishes/{id} - Update Dish Identity (FR-005, FR-006, CHF-2.2.1)")
    class UpdateDishContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK and updated DishDetailResponse when valid")
        void updateDish_shouldReturn200_whenValid() throws Exception {
            UpdateDishRequest request = new UpdateDishRequest(
                    "Risotto Actualizado",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Nueva descripción"
            );

            DishDetailResponse detail = createSampleDetailResponse(
                    dishId, request.name(), request.category(), DishStatus.DRAFT
            );

            when(dishService.updateBasicInfo(eq(dishId), any(UpdateDishRequest.class))).thenReturn(detail);

            mockMvc.perform(put("/api/v1/dishes/{id}", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Risotto Actualizado"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is locked in PENDING_PRICE status")
        void updateDish_shouldReturn409_whenLockedInPendingPrice() throws Exception {
            UpdateDishRequest request = new UpdateDishRequest(
                    "Risotto Bloqueado",
                    DishCategory.PLATOS_PRINCIPALES,
                    null
            );

            when(dishService.updateBasicInfo(eq(dishId), any(UpdateDishRequest.class)))
                    .thenThrow(new DishLockedException(dishId, "PENDING_PRICE"));

            mockMvc.perform(put("/api/v1/dishes/{id}", dishId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-locked"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/dishes/{id} - Delete Dish Draft (FR-007, CHF-2.2.1)")
    class DeleteDishContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 204 No Content when draft is deleted successfully")
        void deleteDish_shouldReturn204_whenSuccessful() throws Exception {
            doNothing().when(dishService).deleteDraft(dishId);

            mockMvc.perform(delete("/api/v1/dishes/{id}", dishId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when attempting to delete dish in PENDING_PRICE status")
        void deleteDish_shouldReturn409_whenLocked() throws Exception {
            doThrow(new DishLockedException(dishId, "PENDING_PRICE"))
                    .when(dishService).deleteDraft(dishId);

            mockMvc.perform(delete("/api/v1/dishes/{id}", dishId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-locked"));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 404 Not Found when dish to delete does not exist")
        void deleteDish_shouldReturn404_whenNotFound() throws Exception {
            doThrow(new DishNotFoundException(dishId)).when(dishService).deleteDraft(dishId);

            mockMvc.perform(delete("/api/v1/dishes/{id}", dishId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-not-found"));
        }
    }
}
