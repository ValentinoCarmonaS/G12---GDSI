package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.service.CatalogComplianceService;
import com.easyeat.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("CatalogController Web Contract Tests (T040, US3, FR-017, FR-026)")
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogComplianceService catalogComplianceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private UUID tenantId;
    private User authenticatedDiner;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        authenticatedDiner = new User();
        authenticatedDiner.setId(UUID.randomUUID());
        authenticatedDiner.setEmail("diner@easyeat.com");
        authenticatedDiner.setRole(UserRole.COMENSAL);
        authenticatedDiner.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Public guest access: GET /api/v1/establishments/{tenantId}/dishes returns 200 without auth (FR-026)")
    void getEstablishmentDishes_guest_returns200() throws Exception {
        UUID dishId = UUID.randomUUID();
        EnrichedDishResponse dish = new EnrichedDishResponse(
                dishId,
                tenantId,
                "Ensalada Mixta",
                "Tomate y lechuga",
                new BigDecimal("4500.00"),
                true,
                true,
                Collections.emptyList()
        );

        when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), isNull()))
                .thenReturn(List.of(dish));

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(dishId.toString()))
                .andExpect(jsonPath("$[0].name").value("Ensalada Mixta"))
                .andExpect(jsonPath("$[0].price").value(4500.00))
                .andExpect(jsonPath("$[0].isCompliant").value(true))
                .andExpect(jsonPath("$[0].conflictingRestrictions").isEmpty());

        verify(catalogComplianceService).getEnrichedCatalog(eq(tenantId), isNull());
    }

    @Test
    @DisplayName("Authenticated diner: GET /api/v1/establishments/{tenantId}/dishes passes user and returns compliance flags (FR-017)")
    void getEstablishmentDishes_authenticatedDiner_returnsEnrichedDishes() throws Exception {
        UUID dish1Id = UUID.randomUUID();
        UUID dish2Id = UUID.randomUUID();

        EnrichedDishResponse compliantDish = new EnrichedDishResponse(
                dish1Id,
                tenantId,
                "Wok de Verduras",
                "Sin TACC / Vegano",
                new BigDecimal("8000.00"),
                true,
                true,
                Collections.emptyList()
        );

        EnrichedDishResponse conflictingDish = new EnrichedDishResponse(
                dish2Id,
                tenantId,
                "Pastel de Papa con Queso",
                "Contiene lácteos",
                new BigDecimal("9500.00"),
                true,
                false,
                List.of("DAIRY")
        );

        when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), any(User.class)))
                .thenReturn(List.of(compliantDish, conflictingDish));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authenticatedDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(dish1Id.toString()))
                .andExpect(jsonPath("$[0].isCompliant").value(true))
                .andExpect(jsonPath("$[0].conflictingRestrictions").isEmpty())
                .andExpect(jsonPath("$[1].id").value(dish2Id.toString()))
                .andExpect(jsonPath("$[1].isCompliant").value(false))
                .andExpect(jsonPath("$[1].conflictingRestrictions[0]").value("DAIRY"));

        verify(catalogComplianceService).getEnrichedCatalog(eq(tenantId), any(User.class));
    }

    @Test
    @DisplayName("Empty menu: returns empty JSON array")
    void getEstablishmentDishes_emptyCatalog_returnsEmptyArray() throws Exception {
        when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), isNull()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Invalid UUID format: returns 400 Bad Request")
    void getEstablishmentDishes_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", "invalid-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
