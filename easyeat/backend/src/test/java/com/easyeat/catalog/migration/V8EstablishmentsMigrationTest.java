package com.easyeat.catalog.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("V8 Flyway Migration and Establishments Integration Test (T011)")
class V8EstablishmentsMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_v8_test")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V8 migration should create establishments table with expected columns and constraints")
    void verifyV8SchemaEvolutionAndColumns() {
        // 1. Verify unaccent extension is enabled
        List<String> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname = 'unaccent'",
                String.class
        );
        assertThat(extensions).contains("unaccent");

        // 2. Verify establishments table columns
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'establishments'",
                String.class
        );
        assertThat(columns).contains(
                "id",
                "name",
                "status",
                "opening_time",
                "closing_time",
                "timezone",
                "created_at",
                "updated_at"
        );

        // 3. Verify functional index idx_establishments_search exists
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'establishments'",
                String.class
        );
        assertThat(indexes).contains("idx_establishments_search");
    }

    @Test
    @DisplayName("V8 migration should insert seed development establishments")
    void verifyV8SeedEstablishments() {
        UUID cafeId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        UUID trattoriaId = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");
        UUID bistroId = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");

        List<Map<String, Object>> establishments = jdbcTemplate.queryForList(
                "SELECT id, name, status, timezone FROM establishments WHERE id IN (?, ?, ?) ORDER BY name",
                cafeId, trattoriaId, bistroId
        );

        assertThat(establishments).hasSize(3);

        Map<String, Object> cafe = establishments.stream()
                .filter(e -> cafeId.equals(e.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(cafe.get("name")).isEqualTo("Café Central Palermo");
        assertThat(cafe.get("status")).isEqualTo("ACTIVE");

        Map<String, Object> bistro = establishments.stream()
                .filter(e -> bistroId.equals(e.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(bistro.get("name")).isEqualTo("Bistró San Telmo");
        assertThat(bistro.get("status")).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("CHECK constraint chk_establishment_status should reject invalid status values")
    void verifyStatusCheckConstraintRejectsInvalidStatus() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone) " +
                        "VALUES (?, 'Invalid Venue', 'UNKNOWN_STATUS', '10:00:00', '22:00:00', 'America/Argentina/Buenos_Aires')",
                id
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
