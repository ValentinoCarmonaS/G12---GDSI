package com.easyeat.contract;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.controller.AdminIngredientController;
import com.easyeat.inventory.controller.ChefCatalogController;
import com.easyeat.inventory.controller.StringToIngredientCategoryConverter;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.inventory.service.StockAdjustmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminIngredientController.class, ChefCatalogController.class})
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
class OpenApiContractValidationTest {

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
    @DisplayName("Verify OpenAPI contract exists and defines all required paths for iteration 004")
    void shouldVerifyOpenApiContractFileExistsAndDefinesEndpoints() throws Exception {
        String contractYaml = null;
        try (var is = getClass().getResourceAsStream("/contracts/openapi.yaml")) {
            if (is != null) {
                contractYaml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        if (contractYaml == null) {
            Path contractPath = Paths.get("../../specs/004-staff-mgmt-rbac-inventory/contracts/openapi.yaml").toAbsolutePath().normalize();
            if (!Files.exists(contractPath)) {
                contractPath = Paths.get("specs/004-staff-mgmt-rbac-inventory/contracts/openapi.yaml").toAbsolutePath().normalize();
            }
            if (Files.exists(contractPath)) {
                contractYaml = Files.readString(contractPath);
            }
        }

        assertThat(contractYaml)
                .withFailMessage("OpenAPI contract could not be loaded from classpath or filesystem")
                .isNotNull();

        assertThat(contractYaml).contains("openapi: 3.0.3");
        assertThat(contractYaml).contains("/admin/staff:");
        assertThat(contractYaml).contains("/admin/staff/{id}:");
        assertThat(contractYaml).contains("/admin/ingredients:");
        assertThat(contractYaml).contains("/admin/ingredients/{id}:");
        assertThat(contractYaml).contains("/admin/ingredients/{id}/status:");
        assertThat(contractYaml).contains("/admin/ingredients/{id}/stock-adjustments:");
        assertThat(contractYaml).contains("/chef/ingredients:");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Verify stock adjustments endpoint conforms to OpenAPI 200 contract")
    void shouldConformToStockAdjustmentContract() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        Instant now = Instant.now();

        StockAdjustmentResponse response = new StockAdjustmentResponse(
                ingredientId,
                "Olive Oil",
                UnitOfMeasure.L,
                new BigDecimal("10.0000"),
                new BigDecimal("15.0000"),
                new BigDecimal("5.0000"),
                auditId,
                now
        );

        when(stockAdjustmentService.adjustStock(eq(ingredientId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"ABSOLUTE\",\"value\":15.0,\"justification\":\"Weekly recount\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ingredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$.ingredientName").value("Olive Oil"))
                .andExpect(jsonPath("$.unitOfMeasure").value("l"))
                .andExpect(jsonPath("$.previousStock").value(10.0))
                .andExpect(jsonPath("$.newStock").value(15.0))
                .andExpect(jsonPath("$.netDelta").value(5.0))
                .andExpect(jsonPath("$.auditEntryId").value(auditId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Verify stock audit history endpoint conforms to OpenAPI 200 contract")
    void shouldConformToStockAuditContract() throws Exception {
        UUID ingredientId = UUID.randomUUID();
        StockAuditEntryResponse entry = new StockAuditEntryResponse(
                UUID.randomUUID(),
                ingredientId,
                UUID.randomUUID(),
                "Admin User",
                StockAdjustmentMode.ABSOLUTE,
                new BigDecimal("10.0000"),
                new BigDecimal("15.0000"),
                new BigDecimal("5.0000"),
                "Recount notes",
                Instant.now()
        );

        when(stockAdjustmentService.getAuditHistory(eq(ingredientId))).thenReturn(List.of(entry));

        // Test OpenAPI documented path GET /admin/ingredients/{id}/stock-adjustments
        mockMvc.perform(get("/api/v1/admin/ingredients/{id}/stock-adjustments", ingredientId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ingredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$[0].adminName").value("Admin User"))
                .andExpect(jsonPath("$[0].adjustmentMode").value("ABSOLUTE"))
                .andExpect(jsonPath("$[0].previousStock").value(10.0))
                .andExpect(jsonPath("$[0].newStock").value(15.0))
                .andExpect(jsonPath("$[0].netDelta").value(5.0))
                .andExpect(jsonPath("$[0].justification").value("Recount notes"));

        // Also test convenience alias GET /admin/ingredients/{id}/stock-audit
        mockMvc.perform(get("/api/v1/admin/ingredients/{id}/stock-audit", ingredientId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].ingredientId").value(ingredientId.toString()))
                .andExpect(jsonPath("$[0].justification").value("Recount notes"));
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("Verify chef kitchen catalog endpoint conforms to OpenAPI 200 contract")
    void shouldConformToChefCatalogContract() throws Exception {
        ChefIngredientItemResponse item = new ChefIngredientItemResponse(
                UUID.randomUUID(),
                "Tomato",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                StockAvailabilityStatus.IN_STOCK,
                new BigDecimal("12.0000")
        );

        ChefIngredientPageResponse pageResponse = new ChefIngredientPageResponse(
                List.of(item),
                0,
                20,
                1L,
                1,
                false
        );

        when(ingredientService.getChefCatalog(any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/chef/ingredients"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Tomato"))
                .andExpect(jsonPath("$.items[0].stockStatus").value("In Stock"))
                .andExpect(jsonPath("$.items[0].unitPurchaseCost").doesNotExist());
    }
}
