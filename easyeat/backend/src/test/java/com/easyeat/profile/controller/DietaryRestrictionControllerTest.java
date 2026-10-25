package com.easyeat.profile.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.service.DietaryRestrictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DietaryRestrictionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("DietaryRestrictionController Web Contract Tests (T029, COM-1.4.3)")
class DietaryRestrictionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DietaryRestrictionService dietaryRestrictionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private User sampleDiner;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        sampleDiner = new User("diner@example.com", "pwdHash", UserRole.COMENSAL);
        sampleDiner.setId(UUID.randomUUID());
        sampleDiner.setStatus(UserStatus.ACTIVE);

        authPrincipal = new UsernamePasswordAuthenticationToken(
                sampleDiner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Nested
    @DisplayName("GET /api/v1/dietary-restrictions tests")
    class GetDietaryTaxonomyTests {

        @Test
        @DisplayName("Returns 200 OK and taxonomy list without requiring authentication (public access)")
        void getTaxonomy_publicAccess_returns200() throws Exception {
            List<DietaryRestrictionItemDto> items = List.of(
                    new DietaryRestrictionItemDto("CELIAC", "Sin TACC / Celíaco", "DIETARY_LIFESTYLE", "Excluye gluten"),
                    new DietaryRestrictionItemDto("PEANUTS", "Maní", "COMMON_ALLERGEN", "Contiene maní")
            );

            when(dietaryRestrictionService.getDietaryTaxonomy()).thenReturn(items);

            mockMvc.perform(get("/api/v1/dietary-restrictions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].code").value("CELIAC"))
                    .andExpect(jsonPath("$[0].name").value("Sin TACC / Celíaco"))
                    .andExpect(jsonPath("$[0].category").value("DIETARY_LIFESTYLE"))
                    .andExpect(jsonPath("$[1].code").value("PEANUTS"))
                    .andExpect(jsonPath("$[1].category").value("COMMON_ALLERGEN"));

            verify(dietaryRestrictionService).getDietaryTaxonomy();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/profile/dietary-restrictions tests")
    class UpdateDietaryPreferencesTests {

        @Test
        @DisplayName("Returns 401 Unauthorized when unauthenticated request attempts to update preferences")
        void updatePreferences_unauthenticated_returns401() throws Exception {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("CELIAC", "PEANUTS"));

            mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(dietaryRestrictionService, never()).updateUserPreferences(any(), any());
        }

        @Test
        @DisplayName("Returns 200 OK with updated restrictions list for authenticated diner")
        void updatePreferences_authenticatedDiner_returns200() throws Exception {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("CELIAC", "PEANUTS"));
            List<DietaryRestrictionItemDto> updatedItems = List.of(
                    new DietaryRestrictionItemDto("CELIAC", "Sin TACC / Celíaco", "DIETARY_LIFESTYLE", "Excluye gluten"),
                    new DietaryRestrictionItemDto("PEANUTS", "Maní", "COMMON_ALLERGEN", "Contiene maní")
            );

            when(dietaryRestrictionService.updateUserPreferences(eq(sampleDiner), any(DietaryPreferencesRequest.class)))
                    .thenReturn(updatedItems);

            mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].code").value("CELIAC"))
                    .andExpect(jsonPath("$[1].code").value("PEANUTS"));

            verify(dietaryRestrictionService).updateUserPreferences(eq(sampleDiner), any(DietaryPreferencesRequest.class));
        }

        @Test
        @DisplayName("Returns 200 OK with empty array when user deselects all restrictions")
        void updatePreferences_emptyList_returns200() throws Exception {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of());

            when(dietaryRestrictionService.updateUserPreferences(eq(sampleDiner), any(DietaryPreferencesRequest.class)))
                    .thenReturn(List.of());

            mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(dietaryRestrictionService).updateUserPreferences(eq(sampleDiner), any(DietaryPreferencesRequest.class));
        }

        @Test
        @DisplayName("Returns 400 Bad Request ProblemDetail when service throws DietaryTaxonomyException")
        void updatePreferences_invalidCode_returns400ProblemDetail() throws Exception {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("INVALID_CODE"));

            when(dietaryRestrictionService.updateUserPreferences(eq(sampleDiner), any(DietaryPreferencesRequest.class)))
                    .thenThrow(DietaryTaxonomyException.forInvalidCode("INVALID_CODE"));

            mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                            .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/invalid-dietary-taxonomy"))
                    .andExpect(jsonPath("$.title").value("Invalid Dietary Taxonomy"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("Código de restricción dietaria inválido o no reconocido: INVALID_CODE"));
        }
    }
}
