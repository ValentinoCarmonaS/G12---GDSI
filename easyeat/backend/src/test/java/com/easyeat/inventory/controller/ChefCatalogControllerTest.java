package com.easyeat.inventory.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.service.IngredientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChefCatalogController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, StringToIngredientCategoryConverter.class})
class ChefCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngredientService ingredientService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/chef/ingredients - 200 OK with default 20 items/page sorted A-Z and stock availability")
    void getCatalog_asChef_success() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ChefIngredientItemResponse item1 = new ChefIngredientItemResponse(
                id1,
                "Basil",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.G,
                StockAvailabilityStatus.IN_STOCK,
                new BigDecimal("500.0000")
        );

        ChefIngredientItemResponse item2 = new ChefIngredientItemResponse(
                id2,
                "Tomato",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                StockAvailabilityStatus.OUT_OF_STOCK,
                BigDecimal.ZERO.setScale(4)
        );

        ChefIngredientPageResponse pageResponse = new ChefIngredientPageResponse(
                List.of(item1, item2),
                0,
                20,
                2L,
                1,
                false
        );

        when(ingredientService.getChefCatalog(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/chef/ingredients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Basil"))
                .andExpect(jsonPath("$.items[0].category").value("Produce"))
                .andExpect(jsonPath("$.items[0].stockStatus").value("In Stock"))
                .andExpect(jsonPath("$.items[1].name").value("Tomato"))
                .andExpect(jsonPath("$.items[1].stockStatus").value("Out of Stock"))
                // Financial cost suppression verification:
                .andExpect(jsonPath("$.items[0].unitPurchaseCost").doesNotExist())
                .andExpect(jsonPath("$.items[0].purchaseCost").doesNotExist())
                .andExpect(jsonPath("$.items[0].auditHistory").doesNotExist())
                .andExpect(jsonPath("$.items[1].unitPurchaseCost").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("GET /api/v1/chef/ingredients?category=Produce&search=basil - passes filters to service")
    void getCatalog_withFilters() throws Exception {
        ChefIngredientPageResponse pageResponse = new ChefIngredientPageResponse(
                List.of(),
                0,
                20,
                0L,
                0,
                false
        );

        when(ingredientService.getChefCatalog(eq(IngredientCategory.PRODUCE), eq("basil"), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/chef/ingredients")
                        .param("category", "Produce")
                        .param("search", "basil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @WithMockUser(roles = "COUNTER_CLERK")
    @DisplayName("GET /api/v1/chef/ingredients - 403 Forbidden for COUNTER_CLERK")
    void getCatalog_counterClerkRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/chef/ingredients"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DINER")
    @DisplayName("GET /api/v1/chef/ingredients - 403 Forbidden for DINER")
    void getCatalog_dinerRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/chef/ingredients"))
                .andExpect(status().isForbidden());
    }
}
