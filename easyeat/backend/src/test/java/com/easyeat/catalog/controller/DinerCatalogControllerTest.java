package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.CategoryGroupDto;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.dto.DishCatalogItemDto;
import com.easyeat.catalog.dto.EstablishmentDetailDto;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.service.DinerCatalogService;
import com.easyeat.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DinerCatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("DinerCatalogController Web Contract Tests (T025, US2, COM-4.1.1, FR-007, FR-012, FR-026, FR-027)")
class DinerCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DinerCatalogService dinerCatalogService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private final UUID tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Nested
    @DisplayName("Security & Role Boundaries")
    class SecurityBoundaryTests {

        @Test
        @DisplayName("Should reject unauthenticated requests with HTTP 401 Unauthorized")
        void shouldRejectUnauthenticatedRequestWith401() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(dinerCatalogService);
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should reject non-COMENSAL roles (e.g. CHEF) with HTTP 403 Forbidden")
        void shouldRejectChefRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(dinerCatalogService);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should reject non-COMENSAL roles (e.g. ADMINISTRADOR) with HTTP 403 Forbidden")
        void shouldRejectAdminRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(dinerCatalogService);
        }
    }

    @Nested
    @DisplayName("Catalog Exploration Contract (ROLE_COMENSAL)")
    class CatalogContractTests {

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 200 OK with categorized catalog when establishment is active")
        void shouldReturn200WithCategorizedCatalog() throws Exception {
            EstablishmentDetailDto estDetail = new EstablishmentDetailDto(
                    tenantId,
                    "La Farola de Palermo",
                    EstablishmentStatus.ACTIVE,
                    true,
                    null
            );

            UUID dishId = UUID.fromString("e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55");
            DishCatalogItemDto dishDto = new DishCatalogItemDto(
                    dishId,
                    "Milanesa Napolitana",
                    "Carne con salsa y queso",
                    "Platos principales",
                    BigDecimal.valueOf(5500.00),
                    "/api/v1/dishes/" + dishId + "/photo",
                    true,
                    true,
                    List.of(),
                    List.of("Carne", "Mozzarella", "Tomate")
            );

            CategoryGroupDto categoryGroup = new CategoryGroupDto("Platos principales", List.of(dishDto));
            DinerCatalogResponse catalogResponse = new DinerCatalogResponse(estDetail, List.of(categoryGroup));

            when(dinerCatalogService.getCatalog(eq(tenantId), any())).thenReturn(catalogResponse);

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.establishment.id").value(tenantId.toString()))
                    .andExpect(jsonPath("$.establishment.name").value("La Farola de Palermo"))
                    .andExpect(jsonPath("$.establishment.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.establishment.isOpen").value(true))
                    .andExpect(jsonPath("$.categories", hasSize(1)))
                    .andExpect(jsonPath("$.categories[0].category").value("Platos principales"))
                    .andExpect(jsonPath("$.categories[0].dishes", hasSize(1)))
                    .andExpect(jsonPath("$.categories[0].dishes[0].id").value(dishId.toString()))
                    .andExpect(jsonPath("$.categories[0].dishes[0].name").value("Milanesa Napolitana"))
                    .andExpect(jsonPath("$.categories[0].dishes[0].price").value(5500.00))
                    .andExpect(jsonPath("$.categories[0].dishes[0].photoUrl").value("/api/v1/dishes/" + dishId + "/photo"))
                    .andExpect(jsonPath("$.categories[0].dishes[0].isAvailable").value(true))
                    .andExpect(jsonPath("$.categories[0].dishes[0].recipeIngredients", hasSize(3)));

            verify(dinerCatalogService, times(1)).getCatalog(eq(tenantId), any());
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 404 ProblemDetail when establishment does not exist")
        void shouldReturn404WhenEstablishmentNotFound() throws Exception {
            when(dinerCatalogService.getCatalog(eq(tenantId), any()))
                    .thenThrow(new EstablishmentNotFoundException(tenantId));

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/establishment-not-found"))
                    .andExpect(jsonPath("$.title").value("Establishment Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.establishmentId").value(tenantId.toString()));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 403 ProblemDetail when establishment is PAUSED or DISABLED")
        void shouldReturn403WhenEstablishmentInactive() throws Exception {
            when(dinerCatalogService.getCatalog(eq(tenantId), any()))
                    .thenThrow(new EstablishmentInactiveException(tenantId, "PAUSED"));

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/establishment-inactive"))
                    .andExpect(jsonPath("$.title").value("Establishment Inactive"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.establishmentId").value(tenantId.toString()))
                    .andExpect(jsonPath("$.currentStatus").value("PAUSED"));
        }
    }
}

