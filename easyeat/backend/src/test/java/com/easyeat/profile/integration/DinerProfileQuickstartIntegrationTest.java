package com.easyeat.profile.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.profile.dto.EmailChangeRequestDto;
import com.easyeat.profile.dto.EmailConfirmationRequest;
import com.easyeat.profile.dto.PersonalDataRequest;
import com.easyeat.profile.repository.ProfileUserRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import com.easyeat.profile.service.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test validating Scenarios 1 through 5 of
 * {@code specs/003-diner-profile-dietary/quickstart.md} across database and web layers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DinerProfileQuickstartIntegrationTest (T063 - Quickstart Scenarios 1 to 5)")
class DinerProfileQuickstartIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_quickstart_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileUserRepository profileUserRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private EmailSender emailSender;

    private User diner;
    private String dinerToken;

    @BeforeEach
    void setUp() {
        dishRepository.deleteAll();
        userDietaryRestrictionRepository.deleteAll();
        profileUserRepository.deleteAll();

        diner = new User("diner.qs." + UUID.randomUUID() + "@easyeat.com", "$2a$12$e8Y6bF0X0Lz2F0O4F5o4uOa.E.vV2o4G2c9k6e/z.j5iQ3Z.0c7K6", UserRole.COMENSAL);
        diner.setFirstName("Valen");
        diner.setLastName("Carmona");
        diner.setFullName("Valen Carmona");
        diner.setStatus(UserStatus.ACTIVE);
        diner.setTokenVersion(0);
        diner = profileUserRepository.save(diner);

        dinerToken = jwtService.generateToken(diner);
    }

    @Test
    @DisplayName("Scenario 1 — Personal Data Update & Format Validation (1a, 1b)")
    void scenario1_personalDataUpdateAndValidation() throws Exception {
        // 1a: Update name and valid phone -> 200 OK
        PersonalDataRequest validRequest = new PersonalDataRequest("Valentino", "Carmona", "+54 9 11 2345-6789");

        mockMvc.perform(put("/api/v1/profile/personal-data")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Valentino"))
                .andExpect(jsonPath("$.lastName").value("Carmona"))
                .andExpect(jsonPath("$.phone").value("+54 9 11 2345-6789"))
                .andExpect(jsonPath("$.fullName").value("Valentino Carmona"))
                .andExpect(jsonPath("$.email").value(diner.getEmail()));

        // 1b: Rejection of digits/symbols in name -> 422 Unprocessable Entity (RFC 9457 ProblemDetail)
        PersonalDataRequest invalidRequest = new PersonalDataRequest("Valentino123", "Carmona!", null);

        mockMvc.perform(put("/api/v1/profile/personal-data")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/api/v1/profile/personal-data"));
    }

    @Test
    @DisplayName("Scenario 2 — Asynchronous Email Verification Workflow & Cooldown (2a, 2b, 2c)")
    void scenario2_emailVerificationAndCooldownWorkflow() throws Exception {
        String proposedEmail = "nuevo.email." + UUID.randomUUID() + "@example.com";

        // 2a: Request email change -> 202 Accepted
        EmailChangeRequestDto changeRequest = new EmailChangeRequestDto(proposedEmail);

        mockMvc.perform(post("/api/v1/profile/email-change/request")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendingEmail").value(proposedEmail))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        // 2b: Immediate resend rejected by rate-limiting cooldown (<60s) -> 429 Too Many Requests
        mockMvc.perform(post("/api/v1/profile/email-change/resend")
                        .header("Authorization", "Bearer " + dinerToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Email Verification Cooldown Active"));

        // Capture verification token dispatched via EmailSender
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationEmail(eq(proposedEmail), urlCaptor.capture());
        String verificationUrl = urlCaptor.getValue();
        assertThat(verificationUrl).contains("token=");
        String rawToken = verificationUrl.substring(verificationUrl.indexOf("token=") + 6);

        // 2c: Confirm email change with token -> 200 OK
        EmailConfirmationRequest confirmRequest = new EmailConfirmationRequest(rawToken);

        mockMvc.perform(post("/api/v1/profile/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.activeEmail").value(proposedEmail));

        // Verification of Session Revocation: Old JWT token MUST be rejected with 401 Unauthorized
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + dinerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Scenario 3 — Standardized Taxonomy & Dietary Preferences Persistence (3a, 3b)")
    void scenario3_standardizedTaxonomyAndPreferences() throws Exception {
        // 3a: Retrieve taxonomy (Public endpoint) -> 200 OK (11 items)
        mockMvc.perform(get("/api/v1/dietary-restrictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[?(@.code == 'CELIAC')].category").value("DIETARY_LIFESTYLE"))
                .andExpect(jsonPath("$[?(@.code == 'PEANUTS')].category").value("COMMON_ALLERGEN"));

        // 3b: Save diner dietary preferences -> 200 OK
        Map<String, Object> preferencesPayload = Map.of(
                "restrictionCodes", List.of("CELIAC", "PEANUTS")
        );

        mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferencesPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("CELIAC", "PEANUTS")));

        // Verify retrieval via /profile/me
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + dinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dietaryRestrictions", hasSize(2)))
                .andExpect(jsonPath("$.dietaryRestrictions[*].code", containsInAnyOrder("CELIAC", "PEANUTS")));
    }

    @Test
    @DisplayName("Scenario 4 — Automated Catalog Filtering & Conjunction Rules (4a, 4b)")
    void scenario4_automatedCatalogFilteringAndConjunction() throws Exception {
        UUID tenantId = UUID.randomUUID();

        // Dish 1: Compliant (CELIAC + VEGETARIAN, no allergens)
        Dish salad = new Dish(tenantId, "Ensalada Mediterránea", "Fresca", new BigDecimal("4500.00"), true);
        salad.setStatus(DishStatus.PUBLISHED);
        salad.setRetailPrice(new BigDecimal("4500.00"));
        salad.setRestrictionCodes(Set.of("CELIAC", "VEGETARIAN"));
        salad = dishRepository.save(salad);

        // Dish 2: Conflicting (PEANUTS + SOY allergens, not celiac)
        Dish padThai = new Dish(tenantId, "Pad Thai con Maní", "Salteado con maní", new BigDecimal("6200.00"), true);
        padThai.setStatus(DishStatus.PUBLISHED);
        padThai.setRetailPrice(new BigDecimal("6200.00"));
        padThai.setRestrictionCodes(Set.of("PEANUTS", "SOY"));
        padThai = dishRepository.save(padThai);

        // 4a: Retrieve catalog as guest (Public endpoint without Bearer token) -> 200 OK
        mockMvc.perform(get("/api/v1/establishments/" + tenantId + "/dishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name == 'Ensalada Mediterránea')].isCompliant").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Pad Thai con Maní')].isCompliant").value(true));

        // Configure diner preferences: CELIAC + PEANUTS
        Map<String, Object> preferencesPayload = Map.of(
                "restrictionCodes", List.of("CELIAC", "PEANUTS")
        );
        mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferencesPayload)))
                .andExpect(status().isOk());

        // 4b: Retrieve catalog as authenticated diner -> 200 OK with upfront compliance enrichment
        mockMvc.perform(get("/api/v1/establishments/" + tenantId + "/dishes")
                        .header("Authorization", "Bearer " + dinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name == 'Ensalada Mediterránea')].isCompliant").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Ensalada Mediterránea')].conflictingRestrictions[0]").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == 'Pad Thai con Maní')].isCompliant").value(false))
                .andExpect(jsonPath("$[?(@.name == 'Pad Thai con Maní')].conflictingRestrictions", hasItem(containsInAnyOrder("PEANUTS", "CELIAC"))));
    }

    @Test
    @DisplayName("Scenario 5 — Live Cart Dietary Re-evaluation & Preventive Risk Assumption (5a)")
    void scenario5_liveCartDietaryReEvaluation() throws Exception {
        UUID tenantId = UUID.randomUUID();

        Dish salad = new Dish(tenantId, "Ensalada Mediterránea", "Fresca", new BigDecimal("4500.00"), true);
        salad.setRestrictionCodes(Set.of("CELIAC", "VEGETARIAN"));
        salad = dishRepository.save(salad);

        Dish padThai = new Dish(tenantId, "Pad Thai con Maní", "Salteado con maní", new BigDecimal("6200.00"), true);
        padThai.setRestrictionCodes(Set.of("PEANUTS", "SOY"));
        padThai = dishRepository.save(padThai);

        // Configure diner with CELIAC and PEANUTS
        Map<String, Object> preferencesPayload = Map.of(
                "restrictionCodes", List.of("CELIAC", "PEANUTS")
        );
        mockMvc.perform(put("/api/v1/profile/dietary-restrictions")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(preferencesPayload)))
                .andExpect(status().isOk());

        // 5a: Live evaluate cart dishes -> 200 OK
        CartComplianceRequest cartRequest = new CartComplianceRequest(
                List.of(salad.getId(), padThai.getId())
        );

        mockMvc.perform(post("/api/v1/cart/evaluate-compliance")
                        .header("Authorization", "Bearer " + dinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cartRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.dishId == '" + padThai.getId() + "')].isCompliant").value(false))
                .andExpect(jsonPath("$.items[?(@.dishId == '" + padThai.getId() + "')].conflictingRestrictions", hasItem(containsInAnyOrder("PEANUTS", "CELIAC"))))
                .andExpect(jsonPath("$.items[?(@.dishId == '" + salad.getId() + "')].isCompliant").value(true))
                .andExpect(jsonPath("$.items[?(@.dishId == '" + salad.getId() + "')].conflictingRestrictions[0]").doesNotExist());
    }
}
