package com.easyeat.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Flyway V18 Migration - Temporary Reservations and Payments DDL Test")
class FlywayMigrationV18Test extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V18 migration creates temporary_reservations table and verifies structure")
    void testTemporaryReservationsTableStructure() {
        // Verify columns existence
        Map<String, Object> columns = jdbcTemplate.queryForMap(
                "SELECT column_name, data_type, is_nullable " +
                        "FROM information_schema.columns " +
                        "WHERE table_name = 'temporary_reservations' AND column_name = 'idempotency_key'"
        );
        assertThat(columns.get("column_name")).isEqualTo("idempotency_key");
        assertThat(columns.get("is_nullable")).isEqualTo("NO");
    }

    @Test
    @DisplayName("V18 migration enforces check constraints and uniqueness on temporary_reservations")
    void testTemporaryReservationsConstraints() {
        UUID orderId = createTestOrder().getId();
        UUID reservationId1 = UUID.randomUUID();
        UUID reservationId2 = UUID.randomUUID();

        // Valid insertion
        jdbcTemplate.update(
                "INSERT INTO temporary_reservations (id, order_id, idempotency_key, status, expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW() + INTERVAL '15 minutes', NOW(), NOW())",
                reservationId1, orderId, "idem-key-1"
        );

        // Cannot insert another ACTIVE reservation for the same order (idx_unique_active_reservation_per_order)
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO temporary_reservations (id, order_id, idempotency_key, status, expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW() + INTERVAL '15 minutes', NOW(), NOW())",
                reservationId2, orderId, "idem-key-2"
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Invalid status fails check constraint
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO temporary_reservations (id, order_id, idempotency_key, status, expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'INVALID_STATUS', NOW() + INTERVAL '15 minutes', NOW(), NOW())",
                UUID.randomUUID(), orderId, "idem-key-3"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V18 migration creates payments table and enforces constraints")
    void testPaymentsTableStructureAndConstraints() {
        UUID orderId = createTestOrder().getId();
        UUID resId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO temporary_reservations (id, order_id, idempotency_key, status, expires_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', NOW() + INTERVAL '15 minutes', NOW(), NOW())",
                resId, orderId, "res-idem-key"
        );

        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, reservation_id, payment_method, gateway_preference_id, gateway_payment_id, " +
                        "external_reference, status, transaction_amount, idempotency_key, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MERCADO_PAGO', 'pref-123', 'pay-123', ?, 'IN_PROCESS', 15000.00, 'pay-idem-1', NOW(), NOW())",
                paymentId, orderId, resId, orderId.toString()
        );

        // Verify gateway_payment_id uniqueness
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, reservation_id, payment_method, gateway_preference_id, gateway_payment_id, " +
                        "external_reference, status, transaction_amount, idempotency_key, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MERCADO_PAGO', 'pref-456', 'pay-123', ?, 'IN_PROCESS', 15000.00, 'pay-idem-2', NOW(), NOW())",
                UUID.randomUUID(), orderId, resId, orderId.toString()
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Verify invalid amount fails check
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, reservation_id, payment_method, gateway_preference_id, gateway_payment_id, " +
                        "external_reference, status, transaction_amount, idempotency_key, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MERCADO_PAGO', 'pref-789', 'pay-789', ?, 'IN_PROCESS', -100.00, 'pay-idem-3', NOW(), NOW())",
                UUID.randomUUID(), orderId, resId, orderId.toString()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private com.easyeat.order.domain.Order createTestOrder() {
        var establishment = createTestEstablishment("Resto V18 " + UUID.randomUUID());
        var diner = createTestDiner("diner_v18_" + UUID.randomUUID() + "@easyeat.com");

        var order = new com.easyeat.order.domain.Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(com.easyeat.order.domain.FulfillmentMode.IMMEDIATE);
        order.setPickupDate(java.time.LocalDate.now());
        order.setTimeSlotStart(java.time.LocalTime.of(12, 0));
        order.setTimeSlotEnd(java.time.LocalTime.of(12, 15));
        order.setStatus(com.easyeat.order.domain.OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("15000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        return orderRepository.save(order);
    }
}

