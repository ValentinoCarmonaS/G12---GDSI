package com.easyeat.inventory.contract;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.controller.AdminIngredientController;
import com.easyeat.inventory.dto.AdminIngredientDetailResponse;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.StringToIngredientCategoryConverter;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.inventory.service.StockAdjustmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminIngredientController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
@DisplayName("T066: Ingredient OpenAPI Contract Test (specs/013-critical-stock-chef-pause/contracts/openapi.yaml, D-003)")
class IngredientOpenApiContractTest {

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

    private UUID tenantId;
    private UUID ingredientId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("OpenAPI contract file exists and defines all inventory ingredient paths")
    void shouldVerifyOpenApiContractFileDefinesAllInventoryOperations() throws Exception {
        Path contractPath = Paths.get("../../specs/013-critical-stock-chef-pause/contracts/openapi.yaml")
                .toAbsolutePath().normalize();
        if (!Files.exists(contractPath)) {
            contractPath = Paths.get("specs/013-critical-stock-chef-pause/contracts/openapi.yaml")
                    .toAbsolutePath().normalize();
        }

        assertThat(Files.exists(contractPath))
                .withFailMessage("OpenAPI contract file not found at " + contractPath)
                .isTrue();

        String contractYaml = Files.readString(contractPath);
        assertThat(contractYaml).contains("openapi: 3.0.3");
        assertThat(contractYaml).contains("/api/v1/admin/ingredients:");
        assertThat(contractYaml).contains("/api/v1/admin/ingredients/{id}:");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/ingredients matches OpenAPI 200 response shape")
    void shouldConformToListIngredientsContract() throws Exception {
        AdminIngredientDetailResponse sample = new AdminIngredientDetailResponse(
                ingredientId,
                tenantId,
                "Tomate Perita",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                new BigDecimal("1200.00"),
                null,
                1,
                IngredientStatus.ACTIVE,
                new BigDecimal("5.0000"),
                10,
                true,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.listIngredients(any(), any(), eq(true))).thenReturn(List.of(sample));

        mockMvc.perform(get("/api/v1/admin/ingredients")
                        .param("criticalOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(ingredientId.toString()))
                .andExpect(jsonPath("$[0].name").value("Tomate Perita"))
                .andExpect(jsonPath("$[0].category").value("Produce"))
                .andExpect(jsonPath("$[0].unitOfMeasure").value("kg"))
                .andExpect(jsonPath("$[0].criticalStockThreshold").value(10))
                .andExpect(jsonPath("$[0].isCritical").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/admin/ingredients matches OpenAPI 201 response shape")
    void shouldConformToCreateIngredientContract() throws Exception {
        AdminIngredientDetailResponse sample = new AdminIngredientDetailResponse(
                ingredientId,
                tenantId,
                "Harina 000",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG,
                new BigDecimal("800.00"),
                null,
                1,
                IngredientStatus.ACTIVE,
                BigDecimal.ZERO,
                15,
                true,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.createIngredient(any())).thenReturn(sample);

        String payload = """
                {
                    "name": "Harina 000",
                    "category": "Dry Goods",
                    "unitOfMeasure": "kg",
                    "unitPurchaseCost": 800.00,
                    "criticalStockThreshold": 15
                }
                """;

        mockMvc.perform(post("/api/v1/admin/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                .andExpect(jsonPath("$.name").value("Harina 000"))
                .andExpect(jsonPath("$.criticalStockThreshold").value(15))
                .andExpect(jsonPath("$.isCritical").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/admin/ingredients/{id} matches OpenAPI 200 response shape")
    void shouldConformToGetIngredientDetailContract() throws Exception {
        AdminIngredientDetailResponse sample = new AdminIngredientDetailResponse(
                ingredientId,
                tenantId,
                "Queso Muzzarella",
                IngredientCategory.DAIRY,
                UnitOfMeasure.KG,
                new BigDecimal("4500.00"),
                null,
                1,
                IngredientStatus.ACTIVE,
                new BigDecimal("20.0000"),
                8,
                false,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.getIngredient(ingredientId)).thenReturn(sample);

        mockMvc.perform(get("/api/v1/admin/ingredients/{id}", ingredientId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                .andExpect(jsonPath("$.criticalStockThreshold").value(8))
                .andExpect(jsonPath("$.isCritical").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/v1/admin/ingredients/{id} matches OpenAPI 200 response shape")
    void shouldConformToUpdateIngredientContract() throws Exception {
        AdminIngredientDetailResponse sample = new AdminIngredientDetailResponse(
                ingredientId,
                tenantId,
                "Queso Muzzarella",
                IngredientCategory.DAIRY,
                UnitOfMeasure.KG,
                new BigDecimal("4800.00"),
                null,
                1,
                IngredientStatus.ACTIVE,
                new BigDecimal("20.0000"),
                12,
                false,
                Instant.now(),
                Instant.now()
        );

        when(ingredientService.updateIngredient(eq(ingredientId), any())).thenReturn(sample);

        String payload = """
                {
                    "name": "Queso Muzzarella",
                    "category": "Dairy",
                    "unitOfMeasure": "kg",
                    "unitPurchaseCost": 4800.00,
                    "criticalStockThreshold": 12
                }
                """;

        mockMvc.perform(put("/api/v1/admin/ingredients/{id}", ingredientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                .andExpect(jsonPath("$.criticalStockThreshold").value(12))
                .andExpect(jsonPath("$.isCritical").value(false));
    }
}
