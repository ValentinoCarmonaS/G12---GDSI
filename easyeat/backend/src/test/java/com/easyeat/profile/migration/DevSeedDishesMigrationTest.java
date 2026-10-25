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
}
