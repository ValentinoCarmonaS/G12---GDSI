package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.service.DinerCatalogService;
import com.easyeat.catalog.service.DishAvailabilityEngine;
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

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DinerCatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("DinerAvailabilityController Web Contract Tests (T038, US3, COM-4.1.2)")
class DinerAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DinerCatalogService dinerCatalogService;

    @MockBean
    private DishAvailabilityEngine dishAvailabilityEngine;

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
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(dishAvailabilityEngine);
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should reject non-COMENSAL roles (e.g. CHEF) with HTTP 403 Forbidden")
        void shouldRejectChefRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(dishAvailabilityEngine);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should reject non-COMENSAL roles (e.g. ADMINISTRADOR) with HTTP 403 Forbidden")
        void shouldRejectAdminRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(dishAvailabilityEngine);
        }
    }

    @Nested
    @DisplayName("Conditional Availability Polling (ETag & 304 Not Modified)")
    class AvailabilityContractTests {

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 200 OK with ETag header and availability map on initial request")
        void shouldReturn200WithETagAndAvailabilityMap() throws Exception {
            UUID dish1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID dish2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
            Map<UUID, Boolean> map = Map.of(dish1, true, dish2, false);
            String etag = "etag-hash-1234567890abcdef";

            when(dishAvailabilityEngine.computeAvailabilityMap(tenantId)).thenReturn(map);
            when(dishAvailabilityEngine.computeETag(map)).thenReturn(etag);

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"" + etag + "\""))
                    .andExpect(jsonPath("$.availability['11111111-1111-1111-1111-111111111111']").value(true))
                    .andExpect(jsonPath("$.availability['22222222-2222-2222-2222-222222222222']").value(false));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 304 Not Modified with empty body when If-None-Match matches ETag")
        void shouldReturn304WhenIfNoneMatchMatchesETag() throws Exception {
            UUID dish1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
            Map<UUID, Boolean> map = Map.of(dish1, true);
            String etag = "etag-hash-1234567890abcdef";

            when(dishAvailabilityEngine.computeAvailabilityMap(tenantId)).thenReturn(map);
            when(dishAvailabilityEngine.computeETag(map)).thenReturn(etag);

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .header("If-None-Match", "\"" + etag + "\"")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", "\"" + etag + "\""))
                    .andExpect(content().string(""));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 200 OK with new ETag when inventory has changed and If-None-Match is stale")
        void shouldReturn200WhenIfNoneMatchIsStale() throws Exception {
            UUID dish1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
            Map<UUID, Boolean> map = Map.of(dish1, false);
            String newEtag = "new-etag-9999999999abcdef";

            when(dishAvailabilityEngine.computeAvailabilityMap(tenantId)).thenReturn(map);
            when(dishAvailabilityEngine.computeETag(map)).thenReturn(newEtag);

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .header("If-None-Match", "\"old-etag-123\"")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"" + newEtag + "\""))
                    .andExpect(jsonPath("$.availability['11111111-1111-1111-1111-111111111111']").value(false));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 404 ProblemDetail when establishment is not found")
        void shouldReturn404WhenEstablishmentNotFound() throws Exception {
            when(dishAvailabilityEngine.computeAvailabilityMap(tenantId))
                    .thenThrow(new EstablishmentNotFoundException(tenantId));

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Establishment Not Found"));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 ProblemDetail when establishment is paused or disabled")
        void shouldReturn403WhenEstablishmentInactive() throws Exception {
            when(dishAvailabilityEngine.computeAvailabilityMap(tenantId))
                    .thenThrow(new EstablishmentInactiveException(tenantId, "PAUSED"));

            mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.title").value("Establishment Inactive"));
        }
    }
}
