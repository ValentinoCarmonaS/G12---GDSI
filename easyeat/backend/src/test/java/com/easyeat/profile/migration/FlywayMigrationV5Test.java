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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FlywayMigrationV5Test {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway V5 migration should extend users and populate dietary restrictions taxonomy")
    void v5MigrationSchemaAndTaxonomy() {
        // Verify users table columns
        List<String> userColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
                String.class
        );
        assertThat(userColumns).contains(
                "first_name",
                "last_name",
                "phone",
                "pending_email",
                "email_verification_token_hash",
                "email_token_expires_at",
                "email_verification_last_sent_at"
        );

        // Verify dietary_restrictions table exists and has 11 seed entries
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dietary_restrictions",
                Integer.class
        );
        assertThat(count).isEqualTo(11);

        List<Map<String, Object>> lifestyles = jdbcTemplate.queryForList(
                "SELECT code, category FROM dietary_restrictions WHERE category = 'DIETARY_LIFESTYLE'"
        );
        assertThat(lifestyles).extracting(m -> m.get("code"))
                .containsExactlyInAnyOrder("CELIAC", "VEGAN", "VEGETARIAN");

        List<Map<String, Object>> allergens = jdbcTemplate.queryForList(
                "SELECT code, category FROM dietary_restrictions WHERE category = 'COMMON_ALLERGEN'"
        );
        assertThat(allergens).extracting(m -> m.get("code"))
                .containsExactlyInAnyOrder("DAIRY", "PEANUTS", "TREE_NUTS", "EGGS", "FISH", "SHELLFISH", "SOY", "WHEAT");

        // Verify dishes and join tables exist
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );
        assertThat(tables).contains("dishes", "dish_dietary_restrictions", "user_dietary_restrictions");
    }
}
