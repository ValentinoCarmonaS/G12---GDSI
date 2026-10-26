package com.easyeat.inventory.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.exception.DuplicateIngredientNameException;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.UnitOfMeasureModificationException;
import com.easyeat.inventory.service.IngredientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminIngredientController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
class AdminIngredientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngredientService ingredientService;

    @MockBean
    private com.easyeat.inventory.service.StockAdjustmentService stockAdjustmentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients - 201 Created on valid request")
    void createIngredient_success() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreateIngredientRequest request = new CreateIngredientRequest(
                "Fresh Basil",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.G,
                new BigDecimal("45.5000")
        );

        AdminIngredientDetailResponse response = new AdminIngredientDetailResponse(
                id,
                tenantId,
                "Fresh Basil",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.G,
                new BigDecimal("45.5000"),
                IngredientStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.createIngredient(any(CreateIngredientRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Fresh Basil"))
                .andExpect(jsonPath("$.category").value("Produce"))
                .andExpect(jsonPath("$.unitOfMeasure").value("g"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStock").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients - 422 Unprocessable Entity on invalid fields")
    void createIngredient_validationError() throws Exception {
        CreateIngredientRequest invalidRequest = new CreateIngredientRequest(
                "",
                null,
                null,
                new BigDecimal("-5.0000")
        );

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients - 409 Conflict when duplicate name in tenant")
    void createIngredient_duplicateConflict() throws Exception {
        CreateIngredientRequest request = new CreateIngredientRequest(
                "Duplicate Salt",
                IngredientCategory.SPICES,
                UnitOfMeasure.G,
                new BigDecimal("10.0000")
        );

        when(ingredientService.createIngredient(any(CreateIngredientRequest.class)))
                .thenThrow(new DuplicateIngredientNameException("Duplicate Salt"));

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("POST /api/v1/admin/ingredients - 403 Forbidden for CHEF role")
    void createIngredient_forbiddenForChef() throws Exception {
        CreateIngredientRequest request = new CreateIngredientRequest(
                "Truffle Oil",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.ML,
                new BigDecimal("250.0000")
        );

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/ingredients - 401 Unauthorized when unauthenticated")
    void createIngredient_unauthorized() throws Exception {
        CreateIngredientRequest request = new CreateIngredientRequest(
                "Pepper",
                IngredientCategory.SPICES,
                UnitOfMeasure.G,
                new BigDecimal("20.0000")
        );

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /api/v1/admin/ingredients - 200 OK list with query parameters")
    void listIngredients_success() throws Exception {
        AdminIngredientDetailResponse item = new AdminIngredientDetailResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Cumin",
                IngredientCategory.SPICES,
                UnitOfMeasure.G,
                new BigDecimal("15.0000"),
                IngredientStatus.ACTIVE,
                new BigDecimal("100.0000"),
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.listIngredients(eq(IngredientCategory.SPICES), eq(IngredientStatus.ACTIVE)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/admin/ingredients")
                        .param("category", "Spices")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Cumin"))
                .andExpect(jsonPath("$[0].category").value("Spices"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/ingredients/{id} - 200 OK when found")
    void getIngredient_success() throws Exception {
        UUID id = UUID.randomUUID();
        AdminIngredientDetailResponse response = new AdminIngredientDetailResponse(
                id,
                UUID.randomUUID(),
                "Cumin",
                IngredientCategory.SPICES,
                UnitOfMeasure.G,
                new BigDecimal("15.0000"),
                IngredientStatus.ACTIVE,
                new BigDecimal("100.0000"),
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.getIngredient(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/ingredients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Cumin"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/ingredients/{id} - 404 Not Found when absent")
    void getIngredient_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(ingredientService.getIngredient(id)).thenThrow(new NoSuchElementException("Not found"));

        mockMvc.perform(get("/api/v1/admin/ingredients/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/ingredients/{id} - 200 OK on valid update")
    void updateIngredient_success() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Organic Whole Milk",
                IngredientCategory.DAIRY,
                UnitOfMeasure.L,
                new BigDecimal("140.0000")
        );

        AdminIngredientDetailResponse response = new AdminIngredientDetailResponse(
                id,
                UUID.randomUUID(),
                "Organic Whole Milk",
                IngredientCategory.DAIRY,
                UnitOfMeasure.L,
                new BigDecimal("140.0000"),
                IngredientStatus.ACTIVE,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.updateIngredient(eq(id), any(UpdateIngredientRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/ingredients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Organic Whole Milk"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/ingredients/{id} - 409 Conflict when unit change is locked")
    void updateIngredient_lockedUnitConflict() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Flour",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.G,
                new BigDecimal("300.0000")
        );

        when(ingredientService.updateIngredient(eq(id), any(UpdateIngredientRequest.class)))
                .thenThrow(new UnitOfMeasureModificationException("Cannot change UoM"));

        mockMvc.perform(put("/api/v1/admin/ingredients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/v1/admin/ingredients/{id}/status - 200 OK on valid transition")
    void updateIngredientStatus_success() throws Exception {
        UUID id = UUID.randomUUID();
        IngredientStatusUpdateRequest request = new IngredientStatusUpdateRequest(IngredientStatus.DISCONTINUED);

        AdminIngredientDetailResponse response = new AdminIngredientDetailResponse(
                id,
                UUID.randomUUID(),
                "Seasonal Berries",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                new BigDecimal("800.0000"),
                IngredientStatus.DISCONTINUED,
                new BigDecimal("2.5000"),
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.updateIngredientStatus(eq(id), any(IngredientStatusUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/ingredients/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/v1/admin/ingredients/{id}/status - 409 Conflict on invalid lifecycle transition")
    void updateIngredientStatus_invalidTransitionConflict() throws Exception {
        UUID id = UUID.randomUUID();
        IngredientStatusUpdateRequest request = new IngredientStatusUpdateRequest(IngredientStatus.DISCONTINUED);

        when(ingredientService.updateIngredientStatus(eq(id), any(IngredientStatusUpdateRequest.class)))
                .thenThrow(new InvalidLifecycleTransitionException("Invalid lifecycle transition"));

        mockMvc.perform(patch("/api/v1/admin/ingredients/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/admin/ingredients/{id} - 204 No Content when stock is zero")
    void deleteIngredient_success() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(ingredientService).deleteIngredient(id);

        mockMvc.perform(delete("/api/v1/admin/ingredients/{id}", id))
                .andExpect(status().isNoContent());

        verify(ingredientService).deleteIngredient(id);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/admin/ingredients/{id} - 409 Conflict when stock > 0")
    void deleteIngredient_conflictWhenStockPositive() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new InvalidLifecycleTransitionException("Cannot archive ingredient with physical stock"))
                .when(ingredientService).deleteIngredient(id);

        mockMvc.perform(delete("/api/v1/admin/ingredients/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
