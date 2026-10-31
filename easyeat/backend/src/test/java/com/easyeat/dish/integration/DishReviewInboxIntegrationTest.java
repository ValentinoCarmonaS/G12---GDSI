package com.easyeat.dish.integration;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T099 (Phase 7): the review inbox's three cross-cutting guarantees that no single unit test
 * already proves end to end against a real database — FIFO ordering by {@code submittedAt}
 * (FR-003), accurate metadata on a page past the last one (FR-036), and tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DishReviewInboxIntegrationTest (T099 - Review Inbox PostgreSQL Integration Test)")
class DishReviewInboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_review_inbox_it")
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
    private UserRepository userRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private JwtService jwtService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;

    private UUID adminId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
        dishRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setEmail("admin.inbox@easyeat.com");
        admin.setPasswordHash("$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2");
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(tenantId);
        admin.setTokenVersion(0);
        admin = userRepository.save(admin);
        adminId = admin.getId();
        adminToken = jwtService.generateToken(admin);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Dish pendingDish(UUID tenantId, String name, Instant submittedAt) {
        Dish dish = new Dish(tenantId, name, DishCategory.PLATOS_PRINCIPALES, "Desc", null, true, adminId);
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setSubmittedAt(submittedAt);
        return dish;
    }

    @Test
    @DisplayName("The pending tab orders strictly FIFO by submittedAt, oldest first")
    void shouldOrderPendingTabByFifo() throws Exception {
        Instant now = Instant.now();
        Dish third = pendingDish(tenantId, "Tercero en llegar", now);
        Dish first = pendingDish(tenantId, "Primero en llegar", now.minus(10, ChronoUnit.MINUTES));
        Dish second = pendingDish(tenantId, "Segundo en llegar", now.minus(5, ChronoUnit.MINUTES));
        dishRepository.save(third);
        dishRepository.save(first);
        dishRepository.save(second);

        mockMvc.perform(get("/api/v1/admin/dishes/review-inbox")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("tab", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].name").value("Primero en llegar"))
                .andExpect(jsonPath("$.items[1].name").value("Segundo en llegar"))
                .andExpect(jsonPath("$.items[2].name").value("Tercero en llegar"));
    }

    @Test
    @DisplayName("A page past the last one is 200 with an empty items[] and accurate metadata (FR-036)")
    void shouldReturnEmptyPageWithAccurateMetadata_pastLastPage() throws Exception {
        dishRepository.save(pendingDish(tenantId, "Único pendiente", Instant.now()));

        mockMvc.perform(get("/api/v1/admin/dishes/review-inbox")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("tab", "PENDING")
                        .param("page", "5")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    @DisplayName("Tenant isolation: the inbox never shows another tenant's dishes")
    void shouldIsolateInboxByTenant() throws Exception {
        UUID otherTenantId = DishTestFixtures.ANOTHER_TENANT_ID;

        dishRepository.save(pendingDish(tenantId, "Mío", Instant.now()));
        Dish foreign = new Dish(otherTenantId, "Ajeno", DishCategory.PLATOS_PRINCIPALES, "Desc", null, true, adminId);
        foreign.setStatus(DishStatus.PENDING_PRICE);
        foreign.setSubmittedAt(Instant.now());
        dishRepository.save(foreign);

        mockMvc.perform(get("/api/v1/admin/dishes/review-inbox")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("tab", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Mío"))
                .andExpect(jsonPath("$.pendingCount").value(1));
    }
}
