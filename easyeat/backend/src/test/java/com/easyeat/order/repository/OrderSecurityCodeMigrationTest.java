package com.easyeat.order.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 022, T006: the schema Flyway actually applies on PostgreSQL 16 (Testcontainers).
 * <ul>
 *   <li>V28 adds {@code orders.completed_at} as a nullable {@code timestamptz} (data-model §2.1, RF-011).</li>
 *   <li>Regression: {@code chk_orders_security_code} from spec 020's V26 still enforces
 *       {@code ^[1-9][0-9]{3}$} (data-model invariant 1).</li>
 * </ul>
 */
@DisplayName("OrderSecurityCodeMigrationTest (T006, spec 022): applied V28 schema and security code CHECK")
class OrderSecurityCodeMigrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Establishment establishment;
    private User diner;
    private UUID orderId;

    /**
     * The row is inserted through JDBC, not JPA, so this schema test does not depend on how {@code Order}
     * maps the table: without V28, only the {@code completed_at} assertions fail.
     */
    @BeforeEach
    void insertOrderRow() {
        establishment = createTestEstablishment("T006 Migration Bistro " + UUID.randomUUID());
        diner = createTestDiner("t006-" + UUID.randomUUID() + "@easyeat.test");
        orderId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO orders (id, establishment_id, user_id, fulfillment_mode, pickup_date,
                                    time_slot_start, time_slot_end, status, total_amount)
                VALUES (?, ?, ?, 'IMMEDIATE', CURRENT_DATE, '12:00', '12:15', 'PENDING_PAYMENT', 1500.00)
                """, orderId, establishment.getId(), diner.getId());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        userRepository.deleteById(diner.getId());
        establishmentRepository.deleteById(establishment.getId());
    }

    @Test
    @DisplayName("orders.completed_at exists as a nullable timestamptz (V28)")
    void completedAtIsNullableTimestamptz() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT data_type, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'orders' AND column_name = 'completed_at'
                """);

        assertThat(column.get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(column.get("is_nullable")).isEqualTo("YES");
    }

    @Test
    @DisplayName("a newly inserted order has no completion instant until it is delivered (no column default)")
    void newOrderHasNullCompletedAt() {
        OffsetDateTime completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM orders WHERE id = ?", OffsetDateTime.class, orderId);

        assertThat(completedAt).isNull();
    }

    @ParameterizedTest(name = "accepts security_code \"{0}\"")
    @ValueSource(strings = {"1000", "9999"})
    @DisplayName("chk_orders_security_code accepts both range boundaries (regression, V26)")
    void checkAcceptsBoundaries(String code) {
        int updated = jdbcTemplate.update("UPDATE orders SET security_code = ? WHERE id = ?", code, orderId);

        assertThat(updated).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT security_code FROM orders WHERE id = ?", String.class, orderId)).isEqualTo(code);
    }

    @ParameterizedTest(name = "rejects security_code \"{0}\"")
    @ValueSource(strings = {"0999", "10000", "abcd"})
    @DisplayName("chk_orders_security_code rejects values outside ^[1-9][0-9]{3}$ (regression, V26)")
    void checkRejectsMalformedCodes(String code) {
        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE orders SET security_code = ? WHERE id = ?", code, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
