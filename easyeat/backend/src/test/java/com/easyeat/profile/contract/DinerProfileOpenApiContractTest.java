package com.easyeat.profile.contract;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.dto.CartItemEvaluation;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.service.CatalogComplianceService;
import com.easyeat.profile.dto.*;
import com.easyeat.profile.service.DietaryRestrictionService;
import com.easyeat.profile.service.EmailChangeService;
import com.easyeat.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DinerProfileOpenApiContractTest (T064, COM-1.4.1, COM-1.4.3, COM-4.2.1)")
class DinerProfileOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private EmailChangeService emailChangeService;

    @MockBean
    private DietaryRestrictionService dietaryRestrictionService;

    @MockBean
    private CatalogComplianceService catalogComplianceService;

    private User diner;
    private UsernamePasswordAuthenticationToken authPrincipal;

    @BeforeEach
    void setUp() {
        diner = new User("diner.contract." + UUID.randomUUID() + "@easyeat.com", "hash123", UserRole.COMENSAL);
        diner.setId(UUID.randomUUID());
        diner.setFirstName("Valen");
        diner.setLastName("Carmona");
        diner.setFullName("Valen Carmona");
        diner.setStatus(UserStatus.ACTIVE);
        diner.setTokenVersion(0);

        authPrincipal = new UsernamePasswordAuthenticationToken(
                diner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("Assert OpenAPI contract specification file exists and defines all 10 diner profile, dietary, and catalog endpoints")
    void shouldVerifyOpenApiContractFileExistsAndDefinesEndpoints() throws Exception {
        Path contractPath = Paths.get("../../specs/003-diner-profile-dietary/contracts/openapi.yaml").toAbsolutePath().normalize();
        if (!Files.exists(contractPath)) {
            contractPath = Paths.get("specs/003-diner-profile-dietary/contracts/openapi.yaml").toAbsolutePath().normalize();
        }

        assertThat(Files.exists(contractPath))
                .withFailMessage("OpenAPI contract file not found at " + contractPath)
                .isTrue();

        String contractYaml = Files.readString(contractPath);
        assertThat(contractYaml).contains("openapi: 3.0.3");

        // Paths defined
        assertThat(contractYaml).contains("/profile/me:");
        assertThat(contractYaml).contains("/profile/personal-data:");
        assertThat(contractYaml).contains("/profile/email-change/request:");
        assertThat(contractYaml).contains("/profile/email-change/resend:");
        assertThat(contractYaml).contains("/profile/email-change/pending:");
        assertThat(contractYaml).contains("/profile/email-change/confirm:");
        assertThat(contractYaml).contains("/dietary-restrictions:");
        assertThat(contractYaml).contains("/profile/dietary-restrictions:");
        assertThat(contractYaml).contains("/establishments/{tenantId}/dishes:");
        assertThat(contractYaml).contains("/cart/evaluate-compliance:");

        // Schemas defined
        assertThat(contractYaml).contains("ProfileResponse:");
        assertThat(contractYaml).contains("PersonalDataRequest:");
        assertThat(contractYaml).contains("EmailChangeRequestDto:");
        assertThat(contractYaml).contains("EmailChangeInitiatedResponse:");
        assertThat(contractYaml).contains("EmailConfirmationRequest:");
        assertThat(contractYaml).contains("EmailConfirmedResponse:");
        assertThat(contractYaml).contains("DietaryRestrictionItem:");
        assertThat(contractYaml).contains("DietaryPreferencesRequest:");
        assertThat(contractYaml).contains("EnrichedDishResponse:");
        assertThat(contractYaml).contains("CartComplianceRequest:");
        assertThat(contractYaml).contains("CartComplianceResponse:");
        assertThat(contractYaml).contains("CartItemEvaluation:");
        assertThat(contractYaml).contains("ProblemDetail:");
    }

    @Test
    @DisplayName("Verify /api/v1/profile/me response schema conforms to OpenAPI 200 ProfileResponse contract")
    void shouldConformToProfileMeContract() throws Exception {
        UUID userId = diner.getId();
        ProfileResponse response = new ProfileResponse(
                userId,
                "Valentino",
                "Carmona",
                "Valentino Carmona",
                "+54 9 11 2345-6789",
                "valentino@example.com",
                null,
                false,
                List.of(new DietaryRestrictionItemDto("CELIAC", "Sin TACC", "DIETARY_LIFESTYLE", "Desc"))
        );

        when(profileService.getProfile(any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/profile/me")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("valentino@example.com"))
                .andExpect(jsonPath("$.firstName").value("Valentino"))
                .andExpect(jsonPath("$.lastName").value("Carmona"))
                .andExpect(jsonPath("$.phone").value("+54 9 11 2345-6789"))
                .andExpect(jsonPath("$.dietaryRestrictions", hasSize(1)))
                .andExpect(jsonPath("$.dietaryRestrictions[0].code").value("CELIAC"));
    }

    @Test
    @DisplayName("Verify /api/v1/profile/personal-data conforms to OpenAPI 200 contract")
    void shouldConformToPersonalDataContract() throws Exception {
        UUID userId = diner.getId();
        ProfileResponse updated = new ProfileResponse(
                userId,
                "Valentino",
                "Carmona",
                "Valentino Carmona",
                "+54 9 11 1111-2222",
                "valentino@example.com",
                null,
                false,
                List.of()
        );

        when(profileService.updatePersonalData(any(User.class), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/profile/personal-data")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Valentino\",\"lastName\":\"Carmona\",\"phone\":\"+54 9 11 1111-2222\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.firstName").value("Valentino"))
                .andExpect(jsonPath("$.phone").value("+54 9 11 1111-2222"));
    }

    @Test
    @DisplayName("Verify email change endpoints conform to OpenAPI 202, 204, and 200 contracts")
    void shouldConformToEmailChangeWorkflowContract() throws Exception {
        EmailChangeInitiatedResponse initiated = new EmailChangeInitiatedResponse(
                "Verification email sent.",
                "new.email@example.com",
                Instant.now().plusSeconds(86400)
        );

        when(emailChangeService.requestEmailChange(any(User.class), any())).thenReturn(initiated);

        // POST /api/v1/profile/email-change/request -> 202 Accepted
        mockMvc.perform(post("/api/v1/profile/email-change/request")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"new.email@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendingEmail").value("new.email@example.com"))
                .andExpect(jsonPath("$.message").value("Verification email sent."))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        // DELETE /api/v1/profile/email-change/pending -> 204 No Content
        doNothing().when(emailChangeService).cancelPendingEmailChange(any(User.class));

        mockMvc.perform(delete("/api/v1/profile/email-change/pending")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal)))
                .andExpect(status().isNoContent());

        // POST /api/v1/profile/email-change/confirm -> 200 OK
        EmailConfirmedResponse confirmed = new EmailConfirmedResponse(
                "Email successfully verified.",
                "new.email@example.com"
        );
        when(emailChangeService.confirmEmailChange(any())).thenReturn(confirmed);

        mockMvc.perform(post("/api/v1/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEmail").value("new.email@example.com"))
                .andExpect(jsonPath("$.message").value("Email successfully verified."));
    }

    @Test
    @DisplayName("Verify dietary taxonomy & preferences endpoints conform to OpenAPI 200 contracts")
    void shouldConformToDietaryEndpointsContract() throws Exception {
        List<DietaryRestrictionItemDto> taxonomy = List.of(
                new DietaryRestrictionItemDto("CELIAC", "Sin TACC", "DIETARY_LIFESTYLE", "Desc"),
                new DietaryRestrictionItemDto("PEANUTS", "Maní", "COMMON_ALLERGEN", "Desc")
        );

        when(dietaryRestrictionService.getDietaryTaxonomy()).thenReturn(taxonomy);
        when(dietaryRestrictionService.updateUserPreferences(any(User.class), any())).thenReturn(taxonomy);

        // GET /api/v1/dietary-restrictions (Public) -> 200 OK
        mockMvc.perform(get("/api/v1/dietary-restrictions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("CELIAC"))
                .andExpect(jsonPath("$[0].category").value("DIETARY_LIFESTYLE"));

        // PUT /api/v1/profile/dietary-restrictions (Authenticated) -> 200 OK
        mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restrictionCodes\":[\"CELIAC\",\"PEANUTS\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].code").value("PEANUTS"));
    }

    @Test
    @DisplayName("Verify catalog and cart compliance endpoints conform to OpenAPI 200 contracts")
    void shouldConformToCatalogAndCartEndpointsContract() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();

        EnrichedDishResponse dish = new EnrichedDishResponse(
                dishId,
                tenantId,
                "Ensalada Mediterránea",
                "Fresca y saludable",
                new BigDecimal("4500.00"),
                true,
                true,
                List.of()
        );

        when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), any())).thenReturn(List.of(dish));

        // GET /api/v1/establishments/{tenantId}/dishes -> 200 OK
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(dishId.toString()))
                .andExpect(jsonPath("$[0].tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$[0].isCompliant").value(true))
                .andExpect(jsonPath("$[0].conflictingRestrictions").isEmpty());

        // POST /api/v1/cart/evaluate-compliance -> 200 OK
        CartItemEvaluation itemEval = new CartItemEvaluation(dishId, true, List.of());
        CartComplianceResponse cartResponse = new CartComplianceResponse(List.of(itemEval));

        when(catalogComplianceService.evaluateCartCompliance(any(), any())).thenReturn(cartResponse);

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishIds\":[\"" + dishId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].dishId").value(dishId.toString()))
                .andExpect(jsonPath("$.items[0].isCompliant").value(true));
    }
}
