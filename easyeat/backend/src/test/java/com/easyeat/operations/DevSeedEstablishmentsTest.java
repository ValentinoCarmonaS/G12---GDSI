package com.easyeat.operations;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev seed gave staff a tenant_id with no row in establishments, so saving the operating
 * configuration as that administrator violated the foreign key. After the dev seed runs, every
 * user that has a tenant must point to an existing establishment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Dev seed: every staff tenant has an establishment")
class DevSeedEstablishmentsTest {

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
    @DisplayName("no user is left pointing to a tenant without an establishment")
    void noStaffWithoutEstablishment() {
        List<String> orphans = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE tenant_id IS NOT NULL "
                        + "AND tenant_id NOT IN (SELECT id FROM establishments) ORDER BY email",
                String.class);

        assertThat(orphans).isEmpty();
    }

    @Test
    @DisplayName("the seeded restaurants are the three missing ones, active")
    void missingRestaurantsAreActive() {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM establishments WHERE status = 'ACTIVE' ORDER BY name", String.class);

        assertThat(names).contains("Café Parque", "La Esquina", "Comedor Central");
    }
}
