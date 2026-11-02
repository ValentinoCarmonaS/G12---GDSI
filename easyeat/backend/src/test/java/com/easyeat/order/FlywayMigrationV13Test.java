package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Flyway V13 Migration & Order Fulfillment Foundation Integration Test")
class FlywayMigrationV13Test extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V13 migration adds fulfillment columns to establishments with correct defaults")
    void testEstablishmentsFulfillmentColumns() {
        Establishment establishment = createTestEstablishment("Restaurante V13 Test");
        assertThat(establishment.getId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT lead_time_minutes, max_capacity_per_slot, operating_days FROM establishments WHERE id = ?",
                establishment.getId()
        );

        assertThat(row.get("lead_time_minutes")).isEqualTo(30);
        assertThat(row.get("max_capacity_per_slot")).isEqualTo(10);
        assertThat(row.get("operating_days")).asString().contains("MONDAY");
    }

    @Test
    @DisplayName("V13 migration enforces check constraints on establishments table")
    void testEstablishmentConstraints() {
        UUID estId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone, lead_time_minutes, max_capacity_per_slot, operating_days, created_at, updated_at) " +
                        "VALUES (?, 'Invalid Lead Time', 'ACTIVE', '11:00:00', '23:00:00', 'America/Argentina/Buenos_Aires', 0, 10, 'MONDAY', NOW(), NOW())",
                estId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO establishments (id, name, status, opening_time, closing_time, timezone, lead_time_minutes, max_capacity_per_slot, operating_days, created_at, updated_at) " +
                        "VALUES (?, 'Invalid Capacity', 'ACTIVE', '11:00:00', '23:00:00', 'America/Argentina/Buenos_Aires', 30, 0, 'MONDAY', NOW(), NOW())",
                estId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V13 migration creates orders and order_items and persists complete entity graph")
    void testOrderAndOrderItemsPersistence() {
        Establishment establishment = createTestEstablishment("La Pérgola V13");
        User diner = createTestDiner("diner_v13@easyeat.com");
        Dish dish = createTestDish(establishment.getId(), "Milanesa Napolitana", new BigDecimal("12000.00"));

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(13, 0));
        order.setTimeSlotEnd(LocalTime.of(13, 15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("24000.00"));
        order.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        OrderItem item = new OrderItem();
        item.setDishId(dish.getId());
        item.setDishName(dish.getName());
        item.setUnitPrice(dish.getPrice());
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("24000.00"));
        order.addItem(item);

        Order saved = orderRepository.save(order);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getId()).isNotNull();

        // Verify active bookings query
        long activeCount = orderRepository.countActiveOrdersBySlot(
                establishment.getId(),
                order.getPickupDate(),
                order.getTimeSlotStart(),
                Instant.now()
        );
        assertThat(activeCount).isEqualTo(1);

        // Verify active pending order check
        boolean hasPending = orderRepository.hasActivePendingOrder(diner.getId(), Instant.now());
        assertThat(hasPending).isTrue();
    }

    @Test
    @DisplayName("V13 orders table rejects invalid fulfillment_mode and reversed time slots")
    void testOrderConstraints() {
        Establishment establishment = createTestEstablishment("Constraints Est");
        User diner = createTestDiner("constraints_diner@easyeat.com");

        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (id, establishment_id, user_id, fulfillment_mode, pickup_date, time_slot_start, time_slot_end, status, total_amount, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'INVALID_MODE', CURRENT_DATE, '13:00:00', '13:15:00', 'PENDING_PAYMENT', 1000.00, NOW(), NOW())",
                orderId, establishment.getId(), diner.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (id, establishment_id, user_id, fulfillment_mode, pickup_date, time_slot_start, time_slot_end, status, total_amount, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'SCHEDULED', CURRENT_DATE, '13:15:00', '13:00:00', 'PENDING_PAYMENT', 1000.00, NOW(), NOW())",
                orderId, establishment.getId(), diner.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
