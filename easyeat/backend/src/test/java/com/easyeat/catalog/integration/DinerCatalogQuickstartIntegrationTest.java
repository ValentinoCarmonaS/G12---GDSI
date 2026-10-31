package com.easyeat.catalog.integration;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test suite validating all 9 scenarios from quickstart.md
 * for diner real-time catalog & multi-tenant establishment discovery against PostgreSQL Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DinerCatalogQuickstartIntegrationTest (T050, quickstart.md Scenarios 1-9)")
class DinerCatalogQuickstartIntegrationTest {

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
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    private UUID activeTenantId;
    private UUID pausedTenantId;
    private UUID dishClassicBurgerId;
    private UUID panPapaId;
    private UUID carneMolidaId;
    private UUID quesoMozzarellaId;
    private UUID milanesaDishId;
    private UUID pizzaDishId;

    @BeforeEach
    void setUp() {
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        establishmentRepository.deleteAll();

        // 1. Establishments
        Establishment cafeCentral = new Establishment(
                null,
                "Café Central",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0),
                LocalTime.of(23, 59),
                "America/Argentina/Buenos_Aires"
        );
        cafeCentral = establishmentRepository.save(cafeCentral);
        activeTenantId = cafeCentral.getId();

        Establishment trattoriaPaused = new Establishment(
                null,
                "Trattoria Bella Vista",
                EstablishmentStatus.PAUSED,
                LocalTime.of(11, 0),
                LocalTime.of(23, 0),
                "America/Argentina/Buenos_Aires"
        );
        trattoriaPaused = establishmentRepository.save(trattoriaPaused);
        pausedTenantId = trattoriaPaused.getId();

        // 2. Ingredients for activeTenantId
        Ingredient panPapa = new Ingredient(activeTenantId, "Pan de papa", IngredientCategory.DRY_GOODS, UnitOfMeasure.U, BigDecimal.valueOf(500));
        panPapa.setCurrentStock(new BigDecimal("5.0000"));
        panPapa.setStatus(IngredientStatus.ACTIVE);
        panPapa = ingredientRepository.save(panPapa);
        panPapaId = panPapa.getId();

        Ingredient carneMolida = new Ingredient(activeTenantId, "Carne molida", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(4000));
        carneMolida.setCurrentStock(new BigDecimal("2.0000"));
        carneMolida.setStatus(IngredientStatus.ACTIVE);
        carneMolida = ingredientRepository.save(carneMolida);
        carneMolidaId = carneMolida.getId();

        Ingredient quesoMozzarella = new Ingredient(activeTenantId, "Queso Mozzarella", IngredientCategory.DAIRY, UnitOfMeasure.KG, BigDecimal.valueOf(3500));
        quesoMozzarella.setCurrentStock(new BigDecimal("1.0000"));
        quesoMozzarella.setStatus(IngredientStatus.ACTIVE);
        quesoMozzarella = ingredientRepository.save(quesoMozzarella);
        quesoMozzarellaId = quesoMozzarella.getId();

        // 3. Dishes for activeTenantId
        Dish burger = new Dish();
        burger.setTenantId(activeTenantId);
        burger.setName("Hamburguesa Clásica");
        burger.setDescription("Hamburguesa jugosa con pan de papa");
        burger.setCategory(DishCategory.PLATOS_PRINCIPALES);
        burger.setPrice(new BigDecimal("9500.00"));
        burger.setRetailPrice(new BigDecimal("9500.00"));
        burger.setStatus(DishStatus.PUBLISHED);
        burger.setIsActive(true);
        burger = dishRepository.save(burger);
        dishClassicBurgerId = burger.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(new DishRecipeIngredientId(dishClassicBurgerId, panPapaId), new BigDecimal("1.0000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(new DishRecipeIngredientId(dishClassicBurgerId, carneMolidaId), new BigDecimal("1.0000")));

        Dish milanesa = new Dish();
        milanesa.setTenantId(activeTenantId);
        milanesa.setName("Milanesa Napolitana");
        milanesa.setDescription("Milanesa con jamón y queso");
        milanesa.setCategory(DishCategory.PLATOS_PRINCIPALES);
        milanesa.setPrice(new BigDecimal("10500.00"));
        milanesa.setRetailPrice(new BigDecimal("10500.00"));
        milanesa.setStatus(DishStatus.PUBLISHED);
        milanesa.setIsActive(true);
        milanesa = dishRepository.save(milanesa);
        milanesaDishId = milanesa.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(new DishRecipeIngredientId(milanesaDishId, quesoMozzarellaId), new BigDecimal("0.5000")));

        Dish pizza = new Dish();
        pizza.setTenantId(activeTenantId);
        pizza.setName("Pizza Mozzarella");
        pizza.setDescription("Pizza a la piedra con mozzarella");
        pizza.setCategory(DishCategory.PLATOS_PRINCIPALES);
        pizza.setPrice(new BigDecimal("8500.00"));
        pizza.setRetailPrice(new BigDecimal("8500.00"));
        pizza.setStatus(DishStatus.PUBLISHED);
        pizza.setIsActive(true);
        pizza = dishRepository.save(pizza);
        pizzaDishId = pizza.getId();

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(new DishRecipeIngredientId(pizzaDishId, quesoMozzarellaId), new BigDecimal("0.3000")));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 1: Establishment Discovery & Autocomplete (FR-001, FR-002, FR-003, SC-001)")
    void scenario1_establishmentDiscoveryAndAutocomplete() throws Exception {
        // 1. Query with 2 chars: returns HTTP 400 Bad Request guard (minimum 3 chars required)
        mockMvc.perform(get("/api/v1/establishments/search").param("q", "ca"))
                .andExpect(status().isBadRequest());

        // 2. Query with 3 chars without accents ("caf"): matches "Café Central" ignoring case and diacritics
        mockMvc.perform(get("/api/v1/establishments/search").param("q", "caf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Café Central"))
                .andExpect(jsonPath("$[0].id").value(activeTenantId.toString()));

        // 3. Paused establishment ("Trattoria Bella Vista") is strictly excluded
        mockMvc.perform(get("/api/v1/establishments/search").param("q", "bella"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 2: Active Establishment Selection & Catalog Load (FR-004, FR-007, FR-008, SC-001, SC-002)")
    void scenario2_activeEstablishmentSelectionAndCatalogLoad() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishment.id").value(activeTenantId.toString()))
                .andExpect(jsonPath("$.establishment.name").value("Café Central"))
                .andExpect(jsonPath("$.establishment.status").value("ACTIVE"))
                .andExpect(jsonPath("$.establishment.isOpen").value(true))
                .andExpect(jsonPath("$.categories", notNullValue()))
                .andExpect(jsonPath("$.categories[?(@.category == 'Platos principales')].dishes[?(@.name == 'Hamburguesa Clásica')].price").value(hasItem(9500.00)))
                .andExpect(jsonPath("$.categories[?(@.category == 'Platos principales')].dishes[?(@.name == 'Hamburguesa Clásica')].isAvailable").value(hasItem(true)));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 3: Paused / Disabled Establishment Handling (FR-005, SC-008)")
    void scenario3_pausedOrDisabledEstablishmentHandling() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", pausedTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail", containsString("no se encuentra activo")));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 4: Dynamic Stock-Driven Dish Availability (FR-013, FR-014, SC-003)")
    void scenario4_dynamicStockDrivenDishAvailability() throws Exception {
        // Initial state: available
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + dishClassicBurgerId + "']").value(true));

        // Adjust "Carne molida" stock to 0
        Ingredient carne = ingredientRepository.findById(carneMolidaId).orElseThrow();
        carne.setCurrentStock(BigDecimal.ZERO);
        ingredientRepository.save(carne);

        // Within SLA: conditional polling endpoint returns false
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + dishClassicBurgerId + "']").value(false));

        // Replenish stock to 10
        carne.setCurrentStock(new BigDecimal("10.0000"));
        ingredientRepository.save(carne);

        // Re-enabled as available
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + dishClassicBurgerId + "']").value(true));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 5: Simultaneous Invalidation for Shared Critical Ingredients (FR-017, SC-004)")
    void scenario5_simultaneousInvalidationForSharedCriticalIngredients() throws Exception {
        // Milanesa and Pizza both depend on "Queso Mozzarella"
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + milanesaDishId + "']").value(true))
                .andExpect(jsonPath("$.availability['" + pizzaDishId + "']").value(true));

        // Kitchen stock drops to 0
        Ingredient queso = ingredientRepository.findById(quesoMozzarellaId).orElseThrow();
        queso.setCurrentStock(BigDecimal.ZERO);
        ingredientRepository.save(queso);

        // Both dishes transition to unavailable simultaneously
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability['" + milanesaDishId + "']").value(false))
                .andExpect(jsonPath("$.availability['" + pizzaDishId + "']").value(false));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 6: Non-Published Dish and Empty Category Hiding (FR-009, FR-015, SC-005, SC-006)")
    void scenario6_nonPublishedDishAndEmptyCategoryHiding() throws Exception {
        // Create draft drink in BEBIDAS
        Dish draftDrink = new Dish();
        draftDrink.setTenantId(activeTenantId);
        draftDrink.setName("Limonada Casera Draft");
        draftDrink.setCategory(DishCategory.BEBIDAS);
        draftDrink.setPrice(new BigDecimal("2500.00"));
        draftDrink.setStatus(DishStatus.DRAFT);
        draftDrink.setIsActive(true);
        dishRepository.save(draftDrink);

        // Category BEBIDAS should not appear because it has 0 published dishes
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.category == 'Bebidas')]").doesNotExist());

        // Now add a PUBLISHED drink
        Dish publishedDrink = new Dish();
        publishedDrink.setTenantId(activeTenantId);
        publishedDrink.setName("Limonada Menta y Jengibre");
        publishedDrink.setCategory(DishCategory.BEBIDAS);
        publishedDrink.setPrice(new BigDecimal("3000.00"));
        publishedDrink.setRetailPrice(new BigDecimal("3000.00"));
        publishedDrink.setStatus(DishStatus.PUBLISHED);
        publishedDrink.setIsActive(true);
        dishRepository.save(publishedDrink);

        // Now BEBIDAS appears with exactly 1 dish (published one), excluding draft
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.category == 'Bebidas')].dishes.length()").value(1))
                .andExpect(jsonPath("$.categories[?(@.category == 'Bebidas')].dishes[0].name").value("Limonada Menta y Jengibre"));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 7: In-Catalog Real-Time Search & Recipe Ingredients (FR-020, FR-021, FR-022, FR-024, SC-007)")
    void scenario7_inCatalogRealTimeSearchAndRecipeIngredients() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", activeTenantId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].dishes[0].recipeIngredients", hasItems("Pan de papa", "Carne molida")));
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Scenario 8: Operating Hours & Read-Only Mode (FR-012, SC-010)")
    void scenario8_operatingHoursAndReadOnlyMode() throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        LocalTime openTime = now.plusHours(2).toLocalTime();
        LocalTime closeTime = now.plusHours(4).toLocalTime();

        Establishment closedEst = new Establishment(
                null,
                "Bistró Nocturno",
                EstablishmentStatus.ACTIVE,
                openTime,
                closeTime,
                "America/Argentina/Buenos_Aires"
        );
        closedEst = establishmentRepository.save(closedEst);

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", closedEst.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishment.isOpen").value(false))
                .andExpect(jsonPath("$.establishment.nextOpeningSchedule", notNullValue()));
    }

    @Test
    @DisplayName("Scenario 9: Mandatory Authentication & Zero Data Exposure (FR-027, SC-011)")
    void scenario9_mandatoryAuthenticationAndZeroDataExposure() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/search").param("q", "caf"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog", activeTenantId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", activeTenantId))
                .andExpect(status().isUnauthorized());
    }
}
