package com.easyeat.operations.repository;

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

/**
 * Verifies the Flyway migration owned by spec 010 (restaurant operation configuration).
 * The migration is located by its Flyway description, never by a hard-coded version.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Restaurant operation config migration (INV-6, INV-7, uniqueness, payment seed)")
class OperationConfigMigrationTest {

    private static final UUID CAFE_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_operation_config_test")
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
    @DisplayName("migration is applied successfully, located by description")
    void migrationIsRegisteredByDescription() {
        Boolean success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE description = 'restaurant operation config'",
                Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("creates the three tables with an optimistic-lock version column")
    void createsThreeTablesWithVersionColumn() {
        for (String table : List.of(
                "establishment_operating_schedules",
                "establishment_slot_capacities",
                "establishment_payment_methods")) {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                    String.class, table);
            assertThat(columns).as(table).contains("id", "establishment_id", "version");
        }
    }

    @Test
    @DisplayName("INV-7: rejects an open day whose opening time is not before closing time")
    void rejectsOpeningNotBeforeClosing() {
        assertThatThrownBy(() -> insertSchedule("MONDAY", "17:00", "15:00", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSchedule("TUESDAY", "15:00", "15:00", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("INV-7: accepts a closed day regardless of the stored times")
    void acceptsClosedDayWithAnyTimes() {
        insertSchedule("SUNDAY", "17:00", "15:00", true);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM establishment_operating_schedules WHERE establishment_id = ? AND day_of_week = 'SUNDAY'",
                Integer.class, CAFE_ID);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a duplicated (establishment, day) schedule and an unknown day")
    void rejectsDuplicateAndUnknownDay() {
        insertSchedule("WEDNESDAY", "11:00", "23:00", false);
        assertThatThrownBy(() -> insertSchedule("WEDNESDAY", "12:00", "22:00", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSchedule("FUNDAY", "11:00", "23:00", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("INV-6: rejects negative capacity and negative approved count")
    void rejectsNegativeCapacity() {
        assertThatThrownBy(() -> insertSlot("2026-11-10", "12:00", -1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSlot("2026-11-10", "12:15", 10, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("accepts zero capacity and rejects a duplicated (establishment, date, start)")
    void acceptsZeroCapacityAndRejectsDuplicateSlot() {
        insertSlot("2026-11-11", "12:00", 0, 0);
        assertThatThrownBy(() -> insertSlot("2026-11-11", "12:00", 5, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a payment method outside the enum and a duplicated (establishment, method)")
    void rejectsUnknownAndDuplicatePaymentMethod() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO establishment_payment_methods (establishment_id, payment_method) VALUES (?, 'PAYPAL')",
                CAFE_ID)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO establishment_payment_methods (establishment_id, payment_method) VALUES (?, 'STRIPE')",
                CAFE_ID)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("seeds MERCADO_PAGO and STRIPE as enabled for every existing establishment")
    void seedsPaymentMethodsForExistingEstablishments() {
        List<Map<String, Object>> missing = jdbcTemplate.queryForList(
                "SELECT e.id FROM establishments e WHERE (SELECT COUNT(*) FROM establishment_payment_methods p "
                        + "WHERE p.establishment_id = e.id AND p.is_enabled = TRUE "
                        + "AND p.payment_method IN ('MERCADO_PAGO', 'STRIPE')) <> 2");
        assertThat(missing).isEmpty();
    }

    private void insertSchedule(String day, String opening, String closing, boolean closed) {
        jdbcTemplate.update(
                "INSERT INTO establishment_operating_schedules "
                        + "(establishment_id, day_of_week, opening_time, closing_time, is_closed) "
                        + "VALUES (?, ?, ?::time, ?::time, ?)",
                CAFE_ID, day, opening, closing, closed);
    }

    private void insertSlot(String date, String start, int maxCapacity, int approved) {
        jdbcTemplate.update(
                "INSERT INTO establishment_slot_capacities "
                        + "(establishment_id, slot_date, start_time, end_time, max_capacity, approved_orders_count) "
                        + "VALUES (?, ?::date, ?::time, ?::time + interval '15 minutes', ?, ?)",
                CAFE_ID, date, start, start, maxCapacity, approved);
    }
}
