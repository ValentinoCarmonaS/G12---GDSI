package com.easyeat.dish.integration;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.service.DinerCatalogService;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import org.junit.jupiter.api.AfterEach;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T037: ChefDishPauseIntegrationTest (US3 AC-3.1, US3 AC-3.2, EC-021)")
class ChefDishPauseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
            .withUsername("easyeat_test")
            .withPassword("easyeat_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private com.easyeat.catalog.repository.EstablishmentRepository establishmentRepository;

    @Autowired
    private DinerCatalogService dinerCatalogService;

    private UUID tenantId;
    private Dish dish1;
    private Dish dish2;

    @BeforeEach
    void setUp() {
        com.easyeat.catalog.domain.Establishment est = new com.easyeat.catalog.domain.Establishment();
        est.setName("Resto Pause Test");
        est.setStatus(com.easyeat.catalog.domain.EstablishmentStatus.ACTIVE);
        est = establishmentRepository.save(est);
        tenantId = est.getId();

        dish1 = new Dish();
        dish1.setTenantId(tenantId);
        dish1.setName("Plato Integracion 1");
        dish1.setDescription("Desc 1");
        dish1.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish1.setPrice(BigDecimal.valueOf(1000.00));
        dish1.setIsActive(true);
        dish1.setStatus(DishStatus.PUBLISHED);
        dish1.setPausedByChef(false);
        dish1.setCreatedAt(Instant.now());
        dish1.setUpdatedAt(Instant.now());
        dish1 = dishRepository.save(dish1);

        dish2 = new Dish();
        dish2.setTenantId(tenantId);
        dish2.setName("Plato Integracion 2");
        dish2.setDescription("Desc 2");
        dish2.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish2.setPrice(BigDecimal.valueOf(1200.00));
        dish2.setIsActive(true);
        dish2.setStatus(DishStatus.PUBLISHED);
        dish2.setPausedByChef(false);
        dish2.setCreatedAt(Instant.now());
        dish2.setUpdatedAt(Instant.now());
        dish2 = dishRepository.save(dish2);
    }

    @AfterEach
    void tearDown() {
        dishRepository.deleteAll();
    }

    @Test
    @DisplayName("testPauseHidesFromDiner: toggle ON -> plato ausente en catalogo comensal (US3 AC-3.1)")
    void testPauseHidesFromDiner() {
        dish1.setPausedByChef(true);
        dishRepository.save(dish1);

        DinerCatalogResponse catalog = dinerCatalogService.getCatalog(tenantId, null);

        boolean dish1Present = catalog.getCategories().stream()
                .flatMap(c -> c.getDishes().stream())
                .anyMatch(d -> d.getId().equals(dish1.getId()));

        assertThat(dish1Present).isFalse();
    }

    @Test
    @DisplayName("testUnpauseRestoresToDiner: toggle OFF -> plato reaparece si no hay otras causas (US3 AC-3.2)")
    void testUnpauseRestoresToDiner() {
        dish1.setPausedByChef(true);
        dishRepository.save(dish1);

        dish1.setPausedByChef(false);
        dishRepository.save(dish1);

        DinerCatalogResponse catalog = dinerCatalogService.getCatalog(tenantId, null);

        boolean dish1Present = catalog.getCategories().stream()
                .flatMap(c -> c.getDishes().stream())
                .anyMatch(d -> d.getId().equals(dish1.getId()));

        assertThat(dish1Present).isTrue();
    }

    @Test
    @DisplayName("testMultipleDishesPaused: multiples platos pausados se ocultan individualmente (EC-021)")
    void testMultipleDishesPaused() {
        dish1.setPausedByChef(true);
        dish2.setPausedByChef(true);
        dishRepository.save(dish1);
        dishRepository.save(dish2);

        DinerCatalogResponse catalog = dinerCatalogService.getCatalog(tenantId, null);

        boolean anyPresent = catalog.getCategories().stream()
                .flatMap(c -> c.getDishes().stream())
                .anyMatch(d -> d.getId().equals(dish1.getId()) || d.getId().equals(dish2.getId()));

        assertThat(anyPresent).isFalse();
    }
}
