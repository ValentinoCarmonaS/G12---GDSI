package com.easyeat.catalog.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.service.CatalogComplianceService;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestrictionId;
import com.easyeat.profile.repository.ProfileUserRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CatalogComplianceIntegrationTest (T042, US3, COM-4.2.1)")
class CatalogComplianceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_catalog_it")
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
    private DishRepository dishRepository;

    @Autowired
    private ProfileUserRepository profileUserRepository;

    @Autowired
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @Autowired
    private CatalogComplianceService catalogComplianceService;

    private UUID tenantA;
    private UUID tenantB;
    private User testDiner;

    @BeforeEach
    void setUp() {
        dishRepository.deleteAll();
        userDietaryRestrictionRepository.deleteAll();

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        testDiner = new User();
        testDiner.setEmail("diner.it." + UUID.randomUUID() + "@easyeat.com");
        testDiner.setPasswordHash("$2a$12$e8Y6bF0X0Lz2F0O4F5o4uOa.E.vV2o4G2c9k6e/z.j5iQ3Z.0c7K6");
        testDiner.setRole(UserRole.COMENSAL);
        testDiner.setStatus(UserStatus.ACTIVE);
        testDiner = profileUserRepository.save(testDiner);
    }

    @Test
    @DisplayName("Tenant isolation: querying tenant A only returns active dishes for tenant A")
    void getEnrichedCatalog_tenantIsolation_onlyReturnsTenantDishes() {
        Dish dishA1 = new Dish(tenantA, "Pasta Mediterránea", "Pasta fresca con albahaca", new BigDecimal("7500.00"), true);
        dishA1.setRestrictionCodes(Set.of("VEGAN"));
        dishRepository.save(dishA1);

        Dish dishA2Inactive = new Dish(tenantA, "Plato Inactivo", "No disponible", new BigDecimal("5000.00"), false);
        dishRepository.save(dishA2Inactive);

        Dish dishB1 = new Dish(tenantB, "Asado Criollo", "Carne vacuna", new BigDecimal("14000.00"), true);
        dishRepository.save(dishB1);

        List<EnrichedDishResponse> catalogA = catalogComplianceService.getEnrichedCatalog(tenantA, null);

        assertThat(catalogA).hasSize(1);
        assertThat(catalogA.get(0).getName()).isEqualTo("Pasta Mediterránea");
        assertThat(catalogA.get(0).getTenantId()).isEqualTo(tenantA);

        List<EnrichedDishResponse> catalogB = catalogComplianceService.getEnrichedCatalog(tenantB, null);
        assertThat(catalogB).hasSize(1);
        assertThat(catalogB.get(0).getName()).isEqualTo("Asado Criollo");
        assertThat(catalogB.get(0).getTenantId()).isEqualTo(tenantB);
    }

    @Test
    @DisplayName("Strict compliance calculation with real PostgreSQL data: evaluates diner profile against dishes")
    void getEnrichedCatalog_evaluatesDatabaseComplianceAccurately() {
        // Configure diner preferences: VEGAN lifestyle + DAIRY allergen
        userDietaryRestrictionRepository.save(new UserDietaryRestriction(new UserDietaryRestrictionId(testDiner.getId(), "VEGAN")));
        userDietaryRestrictionRepository.save(new UserDietaryRestriction(new UserDietaryRestrictionId(testDiner.getId(), "DAIRY")));

        // Dish 1: Compliant (has VEGAN, no DAIRY)
        Dish dishVegan = new Dish(tenantA, "Ensalada Tofu", "Vegano puro", new BigDecimal("6500.00"), true);
        dishVegan.setRestrictionCodes(Set.of("VEGAN"));
        dishRepository.save(dishVegan);

        // Dish 2: Conflicting with VEGAN and DAIRY
        Dish dishLasagna = new Dish(tenantA, "Lasaña Tradicional", "Carne y queso", new BigDecimal("9500.00"), true);
        dishLasagna.setRestrictionCodes(Set.of("DAIRY"));
        dishRepository.save(dishLasagna);

        List<EnrichedDishResponse> catalog = catalogComplianceService.getEnrichedCatalog(tenantA, testDiner);

        assertThat(catalog).hasSize(2);

        EnrichedDishResponse evalVegan = catalog.stream()
                .filter(d -> d.getName().equals("Ensalada Tofu"))
                .findFirst()
                .orElseThrow();
        assertThat(evalVegan.getIsCompliant()).isTrue();
        assertThat(evalVegan.getConflictingRestrictions()).isEmpty();

        EnrichedDishResponse evalLasagna = catalog.stream()
                .filter(d -> d.getName().equals("Lasaña Tradicional"))
                .findFirst()
                .orElseThrow();
        assertThat(evalLasagna.getIsCompliant()).isFalse();
        assertThat(evalLasagna.getConflictingRestrictions()).containsExactlyInAnyOrder("VEGAN", "DAIRY");
    }

    @Test
    @DisplayName("Cart compliance evaluation against real database entities")
    void evaluateCartCompliance_realDatabaseEntities_evaluatesCorrectly() {
        userDietaryRestrictionRepository.save(new UserDietaryRestriction(new UserDietaryRestrictionId(testDiner.getId(), "CELIAC")));

        Dish dishCeliac = new Dish(tenantA, "Tarta Sin TACC", "Apta celíacos", new BigDecimal("7000.00"), true);
        dishCeliac.setRestrictionCodes(Set.of("CELIAC"));
        dishCeliac = dishRepository.save(dishCeliac);

        Dish dishNormal = new Dish(tenantA, "Pizza con Harina de Trigo", "Contiene gluten", new BigDecimal("8500.00"), true);
        dishNormal.setRestrictionCodes(Set.of("WHEAT"));
        dishNormal = dishRepository.save(dishNormal);

        CartComplianceRequest request = new CartComplianceRequest(List.of(dishCeliac.getId(), dishNormal.getId()));
        CartComplianceResponse response = catalogComplianceService.evaluateCartCompliance(request, testDiner);

        assertThat(response.getItems()).hasSize(2);

        assertThat(response.getItems().get(0).getDishId()).isEqualTo(dishCeliac.getId());
        assertThat(response.getItems().get(0).getIsCompliant()).isTrue();
        assertThat(response.getItems().get(0).getConflictingRestrictions()).isEmpty();

        assertThat(response.getItems().get(1).getDishId()).isEqualTo(dishNormal.getId());
        assertThat(response.getItems().get(1).getIsCompliant()).isFalse();
        assertThat(response.getItems().get(1).getConflictingRestrictions()).containsExactly("CELIAC");
    }
}
