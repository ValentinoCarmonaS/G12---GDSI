package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import com.easyeat.catalog.service.EstablishmentDiscoveryService;
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

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EstablishmentDiscoveryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("EstablishmentDiscoveryController Web Contract Tests (T013, US1, COM-1.5.1, FR-001, FR-003, FR-026)")
class EstablishmentDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EstablishmentDiscoveryService discoveryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Nested
    @DisplayName("Security & Role Boundaries")
    class SecurityBoundaryTests {

        @Test
        @DisplayName("Should reject unauthenticated requests with HTTP 401 Unauthorized")
        void shouldRejectUnauthenticatedRequestWith401() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "palermo")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(discoveryService);
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should reject non-COMENSAL roles (e.g. CHEF) with HTTP 403 Forbidden")
        void shouldRejectChefRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "palermo")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(discoveryService);
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR")
        @DisplayName("Should reject non-COMENSAL roles (e.g. ADMINISTRADOR) with HTTP 403 Forbidden")
        void shouldRejectAdminRoleWith403() throws Exception {
            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "palermo")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(discoveryService);
        }
    }

    @Nested
    @DisplayName("Query Validation & Search Contract (ROLE_COMENSAL)")
    class SearchContractTests {

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 400 Bad Request ProblemDetail when query has fewer than 3 characters")
        void shouldReturn400WhenQueryTooShort() throws Exception {
            when(discoveryService.searchEstablishments("ca"))
                    .thenThrow(new InvalidSearchQueryException("Search query must contain at least 3 non-whitespace characters."));

            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "ca")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-search-query"))
                    .andExpect(jsonPath("$.title").value("Invalid Search Query"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value(containsString("at least 3")));

            verify(discoveryService, times(1)).searchEstablishments("ca");
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 200 OK and establishments array when query matches active venues")
        void shouldReturn200WithEstablishments() throws Exception {
            UUID id1 = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
            EstablishmentSummaryResponse item1 = new EstablishmentSummaryResponse(
                    id1,
                    "Café Central Palermo",
                    EstablishmentStatus.ACTIVE,
                    LocalTime.of(8, 0, 0),
                    LocalTime.of(23, 30, 0),
                    "America/Argentina/Buenos_Aires",
                    true
            );

            when(discoveryService.searchEstablishments("cafe")).thenReturn(List.of(item1));

            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "cafe")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(id1.toString()))
                    .andExpect(jsonPath("$[0].name").value("Café Central Palermo"))
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[0].openingTime").value("08:00:00"))
                    .andExpect(jsonPath("$[0].closingTime").value("23:30:00"))
                    .andExpect(jsonPath("$[0].timezone").value("America/Argentina/Buenos_Aires"))
                    .andExpect(jsonPath("$[0].open").value(true));

            verify(discoveryService, times(1)).searchEstablishments("cafe");
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 200 OK and empty array when query yields zero active establishments")
        void shouldReturn200WithEmptyList() throws Exception {
            when(discoveryService.searchEstablishments("desconocido")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/establishments/search")
                            .param("q", "desconocido")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(discoveryService, times(1)).searchEstablishments("desconocido");
        }
    }

    @Nested
    @DisplayName("Complete Establishment Listing Contract (Fix C-01, FR-030)")
    class ListingAllEstablishmentsTests {

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return HTTP 200 OK with the complete active establishments list when no query is provided")
        void shouldReturn200WithAllActiveEstablishments() throws Exception {
            UUID id1 = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
            EstablishmentSummaryResponse item1 = new EstablishmentSummaryResponse(
                    id1,
                    "Café Central Palermo",
                    EstablishmentStatus.ACTIVE,
                    LocalTime.of(8, 0, 0),
                    LocalTime.of(23, 30, 0),
                    "America/Argentina/Buenos_Aires",
                    true
            );

            when(discoveryService.getAllActiveEstablishments()).thenReturn(List.of(item1));

            mockMvc.perform(get("/api/v1/establishments")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(id1.toString()))
                    .andExpect(jsonPath("$[0].name").value("Café Central Palermo"))
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[0].open").value(true));

            verify(discoveryService, times(1)).getAllActiveEstablishments();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests to the listing endpoint with HTTP 401 Unauthorized")
        void shouldReturn401ForUnauthenticatedListingRequest() throws Exception {
            mockMvc.perform(get("/api/v1/establishments")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(discoveryService);
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should reject non-COMENSAL roles (e.g. CHEF) on the listing endpoint with HTTP 403 Forbidden")
        void shouldReturn403ForChefOnListingEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/establishments")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(discoveryService);
        }
    }
}

