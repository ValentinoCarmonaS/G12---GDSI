package com.easyeat.inventory.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.inventory.exception.ZeroDeltaAdjustmentException;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.inventory.service.StockAdjustmentService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminIngredientController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
class StockAdjustmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngredientService ingredientService;

    @MockBean
    private StockAdjustmentService stockAdjustmentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients/{id}/stock-adjustments - 200 OK on valid adjustment")
    void adjustStock_success() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("15.5000"),
                "Periodic inventory recount"
        );

        StockAdjustmentResponse response = new StockAdjustmentResponse(
                ingredientId,
                "Flour",
                UnitOfMeasure.KG,
                new BigDecimal("10.0000"),
                new BigDecimal("15.5000"),
                new BigDecimal("5.5000"),
                UUID.randomUUID(),
                Instant.now()
        );

        when(stockAdjustmentService.adjustStock(eq(ingredientId), any(StockAdjustmentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$.ingredientName").value("Flour"))
                .andExpect(jsonPath("$.unitOfMeasure").value("kg"))
                .andExpect(jsonPath("$.previousStock").value(10.0000))
                .andExpect(jsonPath("$.newStock").value(15.5000))
                .andExpect(jsonPath("$.netDelta").value(5.5000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients/{id}/stock-adjustments - 422 on NegativeStockException")
    void adjustStock_negativeStock_throws422() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                new BigDecimal("-20.0000"),
                "Adjustment exceeding stock"
        );

        when(stockAdjustmentService.adjustStock(eq(ingredientId), any(StockAdjustmentRequest.class)))
                .thenThrow(new NegativeStockException(new BigDecimal("10.0000"), new BigDecimal("-20.0000"), new BigDecimal("-10.0000")));

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/negative-stock"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients/{id}/stock-adjustments - 422 on ZeroDeltaAdjustmentException")
    void adjustStock_zeroDelta_throws422() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.RELATIVE,
                BigDecimal.ZERO,
                "Zero adjustment"
        );

        when(stockAdjustmentService.adjustStock(eq(ingredientId), any(StockAdjustmentRequest.class)))
                .thenThrow(new ZeroDeltaAdjustmentException(ingredientId));

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/negative-stock"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients/{id}/stock-adjustments - 422 on validation failure")
    void adjustStock_validationError_returns422() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                null,
                null,
                ""
        );

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/validation-failed"));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("POST /api/v1/admin/ingredients/{id}/stock-adjustments - 403 Forbidden for CHEF")
    void adjustStock_chefRole_forbidden() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("10.0000"),
                "Recount"
        );

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/ingredients/{id}/stock-audit - 200 OK returning audit entries")
    void getAuditHistory_success() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAuditEntryResponse entry = new StockAuditEntryResponse(
                UUID.randomUUID(),
                ingredientId,
                UUID.randomUUID(),
                "Admin Master",
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("10.0000"),
                new BigDecimal("15.0000"),
                new BigDecimal("5.0000"),
                "Recount reconciliation",
                Instant.now()
        );

        when(stockAdjustmentService.getAuditHistory(ingredientId)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/admin/ingredients/{id}/stock-audit", ingredientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ingredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$[0].adminName").value("Admin Master"))
                .andExpect(jsonPath("$[0].justification").value("Recount reconciliation"))
                .andExpect(jsonPath("$[0].adjustmentMode").value("ABSOLUTE"));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/admin/ingredients/{id}/stock-audit - 403 Forbidden for CHEF")
    void getAuditHistory_chefRole_forbidden() throws Exception {
        UUID ingredientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/ingredients/{id}/stock-audit", ingredientId))
                .andExpect(status().isForbidden());
    }
}
