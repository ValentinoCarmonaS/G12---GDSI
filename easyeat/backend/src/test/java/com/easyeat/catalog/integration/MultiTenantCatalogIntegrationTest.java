package com.easyeat.catalog.integration;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.catalog.service.DishAvailabilityEngine;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("MultiTenantCatalogIntegrationTest (T039, US1 CA 10, US3, FR-013, FR-014)")
class MultiTenantCatalogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_multitenant_it")
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

    @Autowired
    private DishAvailabilityEngine dishAvailabilityEngine;

    private UUID tenantA;
    private UUID tenantB;
    private UUID dishAId;
    private UUID dishBId;
    private UUID ingAId;
    private UUID ingBId;

    @BeforeEach
    void setUp() {
        // 1. Create Establishments
        Establishment estA = new Establishment(null, "Tenant A Burgers", EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0), LocalTime.of(23, 59), "America/Argentina/Buenos_Aires");
        Establishment estB = new Establishment(null, "Tenant B Burgers", EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0), LocalTime.of(23, 59), "America/Argentina/Buenos_Aires");
        estA = establishmentRepository.save(estA);
        estB = establishmentRepository.save(estB);
        tenantA = estA.getId();
        tenantB = estB.getId();

        // 2. Create Homonymous Ingredients ("Carne")
        Ingredient ingA = new Ingredient(tenantA, "Carne", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(100));
        ingA.setCurrentStock(BigDecimal.ZERO); // 0 stock in Tenant A
        ingA.setStatus(IngredientStatus.ACTIVE);
        ingA = ingredientRepository.save(ingA);
        ingAId = ingA.getId();

        Ingredient ingB = new Ingredient(tenantB, "Carne", IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(100));
        ingB.setCurrentStock(new BigDecimal("50.0000")); // 50 stock in Tenant B
        ingB.setStatus(IngredientStatus.ACTIVE);
        ingB = ingredientRepository.save(ingB);
        ingBId = ingB.getId();

        // 3. Create Homonymous Dishes ("Hamburguesa Clásica")
        Dish dishA = new Dish();
        dishA.setTenantId(tenantA);
        dishA.setName("Hamburguesa Clásica");
        dishA.setStatus(DishStatus.PUBLISHED);
        dishA.setIsActive(true);
        dishA.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dishA.setPrice(BigDecimal.valueOf(8000));
        dishA = dishRepository.save(dishA);
        dishAId = dishA.getId();

        Dish dishB = new Dish();
        dishB.setTenantId(tenantB);
        dishB.setName("Hamburguesa Clásica");
        dishB.setStatus(DishStatus.PUBLISHED);
        dishB.setIsActive(true);
        dishB.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dishB.setPrice(BigDecimal.valueOf(8500));
        dishB = dishRepository.save(dishB);
        dishBId = dishB.getId();

        // 4. Recipes: Both require 0.200 kg Carne
        DishRecipeIngredient lineA = new DishRecipeIngredient(
                new DishRecipeIngredientId(dishAId, ingAId), new BigDecimal("0.2000"));
        dishRecipeIngredientRepository.save(lineA);

        DishRecipeIngredient lineB = new DishRecipeIngredient(
                new DishRecipeIngredientId(dishBId, ingBId), new BigDecimal("0.2000"));
        dishRecipeIngredientRepository.save(lineB);
    }

    @Test
    @DisplayName("Should isolate stock and availability strictly between Tenant A and Tenant B")
    void shouldIsolateStockAndAvailabilityStrictlyBetweenTenants() {
        // Evaluate Tenant A
        Map<UUID, Boolean> mapA = dishAvailabilityEngine.computeAvailabilityMap(tenantA);
        assertThat(mapA).containsKey(dishAId);
        assertThat(mapA).doesNotContainKey(dishBId);
        assertThat(mapA.get(dishAId)).isFalse(); // Out of stock in Tenant A

        // Evaluate Tenant B
        Map<UUID, Boolean> mapB = dishAvailabilityEngine.computeAvailabilityMap(tenantB);
        assertThat(mapB).containsKey(dishBId);
        assertThat(mapB).doesNotContainKey(dishAId);
        assertThat(mapB.get(dishBId)).isTrue(); // Available in Tenant B

        // Replenish Tenant A stock
        Ingredient ingA = ingredientRepository.findById(ingAId).orElseThrow();
        ingA.setCurrentStock(new BigDecimal("10.0000"));
        ingredientRepository.save(ingA);

        Map<UUID, Boolean> updatedMapA = dishAvailabilityEngine.computeAvailabilityMap(tenantA);
        assertThat(updatedMapA.get(dishAId)).isTrue();
    }

    @Test
    @DisplayName("Security: should reject unauthenticated requests with HTTP 401")
    void shouldRejectUnauthenticatedRequestWith401() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("Security: should reject non-comensal role (e.g. CHEF) with HTTP 403")
    void shouldRejectChefRoleWith403() throws Exception {
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", tenantA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("Should return 404 for non-existent establishment ID")
    void shouldReturn404ForNonExistentEstablishment() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/establishments/{tenantId}/catalog/availability", nonExistent)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
