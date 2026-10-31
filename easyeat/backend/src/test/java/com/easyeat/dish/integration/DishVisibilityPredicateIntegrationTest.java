package com.easyeat.dish.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T096 (Phase 7, FR-033, SC-001): D4's diner-visibility predicate — only {@code PUBLISHED} AND
 * {@code is_active} dishes reach {@code GET /api/v1/establishments/{tenantId}/dishes} — and D2's
 * wire contract, that the diner payload's {@code price} field carries {@code retail_price}, not
 * the legacy {@code price} column.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DishVisibilityPredicateIntegrationTest (T096 - Diner Visibility Predicate PostgreSQL Integration Test)")
class DishVisibilityPredicateIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_visibility_predicate_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private UserRepository userRepository;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
        dishRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setEmail("admin.visibility@easyeat.com");
        admin.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(tenantId);
        admin.setTokenVersion(0);
        adminId = userRepository.save(admin).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Dish dish(String name, DishStatus status, boolean active) {
        Dish dish = new Dish(tenantId, name, DishCategory.PLATOS_PRINCIPALES, "Desc", null, active, adminId);
        dish.setStatus(status);
        return dish;
    }

    @Test
    @DisplayName("Only PUBLISHED and active dishes are visible to the diner")
    void shouldReturnOnlyPublishedAndActiveDishes() throws Exception {
        Dish published = dish("Lomo Publicado", DishStatus.PUBLISHED, true);
        published.setRetailPrice(new BigDecimal("5000.00"));
        dishRepository.save(published);

        Dish pendingPrice = dish("Milanesa Pendiente", DishStatus.PENDING_PRICE, true);
        dishRepository.save(pendingPrice);

        Dish publishedInactive = dish("Postre Pausado", DishStatus.PUBLISHED, false);
        publishedInactive.setRetailPrice(new BigDecimal("1200.00"));
        dishRepository.save(publishedInactive);

        Dish draft = dish("Borrador", DishStatus.DRAFT, true);
        dishRepository.save(draft);

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(published.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Lomo Publicado"));
    }

    @Test
    @DisplayName("The diner payload's price field carries retail_price, not the legacy price column")
    void shouldReturnRetailPriceNotLegacyPrice() throws Exception {
        Dish published = new Dish(tenantId, "Ravioles", DishCategory.PLATOS_PRINCIPALES, "Desc",
                new BigDecimal("999.99"), true, adminId);
        published.setStatus(DishStatus.PUBLISHED);
        published.setRetailPrice(new BigDecimal("6300.00"));
        published = dishRepository.save(published);

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].price").value(6300.00));
    }

    @Test
    @DisplayName("An empty catalog with no PUBLISHED dishes returns an empty list, not an error")
    void shouldReturnEmptyList_whenNoPublishedDishes() throws Exception {
        dishRepository.save(dish("Borrador Solo", DishStatus.DRAFT, true));

        mockMvc.perform(get("/api/v1/establishments/{tenantId}/dishes", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
