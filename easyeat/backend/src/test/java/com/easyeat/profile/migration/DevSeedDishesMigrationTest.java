package com.easyeat.profile.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DevSeedDishesMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dev_seed_test")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("R__seed_dev_dishes_and_dietary should seed sample dishes and restrictions for admin tenant")
    void devSeedDishesAndDietary() {
        UUID tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

        // Verify dishes were inserted for tenant
        Integer dishCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dishes WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(dishCount).isGreaterThanOrEqualTo(5);

        // Verify restrictions mappings exist
        List<String> restrictions = jdbcTemplate.queryForList(
                "SELECT DISTINCT restriction_code FROM dish_dietary_restrictions ddr " +
                        "JOIN dishes d ON ddr.dish_id = d.id WHERE d.tenant_id = ?",
                String.class,
                tenantId
        );
        assertThat(restrictions).contains("CELIAC", "VEGETARIAN", "PEANUTS", "SOY", "WHEAT");
    }

    /**
     * Added by spec 007 (T016). Decision D5, which would have published these five dishes, is
     * withdrawn — see {@code specs/007-admin-dish-approval-pricing/research.md §3.6}. What the
     * Administrative Back-Office depends on is the opposite state: five dishes that are pending,
     * unpriced and never approved, which is what its review inbox exists to work on and what its
     * quickstart approves one of.
     *
     * <p>{@code status} is asserted here as well as in spec 005's own
     * {@code V7MigrationAndSeedDevTest} on purpose. Duplicating it records the state this feature
     * depends on inside this feature's own suite, instead of trusting another spec to preserve it.
     */
    @Test
    @DisplayName("the dev dishes are pending, unpriced and never approved (spec 007, D5 withdrawn)")
    void devSeedDishesArePendingAndUnpriced() {
        UUID tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

        List<java.util.Map<String, Object>> dishes = jdbcTemplate.queryForList(
                "SELECT name, status, retail_price, published_at FROM dishes "
                        + "WHERE tenant_id = ? ORDER BY name",
                tenantId
        );

        assertThat(dishes).hasSizeGreaterThanOrEqualTo(5);

        assertThat(dishes)
                .allSatisfy(dish -> {
                    assertThat(dish.get("status"))
                            .as("the review inbox of spec 007 decides dishes that are awaiting a "
                                    + "decision, so the dev fixture must be pending")
                            .isEqualTo("PENDING_PRICE");
                    assertThat(dish.get("retail_price"))
                            .as("stated explicitly as NULL in the seed: V8's backfill would "
                                    + "otherwise price these rows on a pre-existing database and "
                                    + "leave them unpriced on a fresh one")
                            .isNull();
                    assertThat(dish.get("published_at"))
                            .as("a dish never approved has no publication instant")
                            .isNull();
                });
    }

    @Test
    @DisplayName("the dev dishes carry no administrative review history (spec 007, D5 withdrawn)")
    void devSeedDishesHaveNoReviewHistory() {
        UUID tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

        Integer reviewRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dish_review_history WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );

        assertThat(reviewRows)
                .as("D5 would have inserted a synthetic APPROVED row per dish. It is withdrawn, "
                        + "and a pending dish has no decision to record — a synthetic audit trail "
                        + "would be a fabricated history of a decision nobody took")
                .isZero();
    }
}
