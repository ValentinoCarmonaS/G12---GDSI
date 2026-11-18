package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 022, T020 (US2, contract 03 §3.2): live tracking of a delivered order is terminal — status
 * {@code COMPLETED}, not ready for pickup, the final message, and the PIN purged at the source
 * (RF-014, RF-015, edge case 12). The order is completed through JDBC so the test depends only on the
 * tracking service.
 */
@DisplayName("OrderLiveTrackingCompletedTest (T020, spec 022, US2, contract 03)")
class OrderLiveTrackingCompletedTest extends BaseIntegrationTest {

    @Autowired
    private OrderLiveTrackingService orderLiveTrackingService;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrderLiveTrackingResponse tracking;

    @BeforeEach
    void trackDeliveredOrder() {
        Establishment establishment = createTestEstablishment("T020 Delivered Bistro " + UUID.randomUUID());
        User diner = createTestDiner("t020-" + UUID.randomUUID() + "@easyeat.test");
        LocalDate today = LocalDate.now();
        Instant readyAt = Instant.now().minusSeconds(300);
        Instant completedAt = Instant.now();

        Order order = new Order(null, establishment.getId(), diner.getId(), FulfillmentMode.IMMEDIATE, today,
                LocalTime.of(12, 0), LocalTime.of(12, 15), OrderStatus.READY_FOR_PICKUP, new BigDecimal("9000.00"), null);
        order.setReadyAt(readyAt);
        UUID dishId = createTestDish(establishment.getId(), "Milanesa con Puré", new BigDecimal("4500.00")).getId();
        order.addItem(new OrderItem(order, dishId, "Milanesa con Puré", new BigDecimal("4500.00"), 2,
                new BigDecimal("9000.00")));
        order = orderRepository.saveAndFlush(order);

        KdsTicket ticket = new KdsTicket(null, order.getId(), establishment, 42, KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE, today, LocalTime.of(12, 0), LocalTime.of(12, 15), readyAt.minusSeconds(900));
        ticket.setStartedAt(readyAt.minusSeconds(600));
        ticket.setReadyAt(readyAt);
        kdsTicketRepository.saveAndFlush(ticket);

        jdbcTemplate.update("UPDATE orders SET status = 'COMPLETED', completed_at = ? WHERE id = ?",
                Timestamp.from(completedAt), order.getId());
        jdbcTemplate.update("UPDATE kds_tickets SET status = 'COMPLETED', completed_at = ? WHERE order_id = ?",
                Timestamp.from(completedAt), order.getId());

        tracking = orderLiveTrackingService.getLiveTracking(order.getId(), diner.getId());
    }

    @Test
    @DisplayName("reports the terminal status COMPLETED")
    void reportsCompleted() {
        assertThat(tracking.status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("is no longer ready for pickup (contract 03 §3.2)")
    void isNotReadyForPickup() {
        assertThat(tracking.isReadyForPickup()).isFalse();
    }

    @Test
    @DisplayName("carries the final delivery message (RF-014)")
    void carriesFinalMessage() {
        assertThat(tracking.message()).isEqualTo("Pedido Entregado — ¡Que disfrutes tu comida!");
    }

    @Test
    @DisplayName("purges the PIN from the payload (RF-015, edge case 12)")
    void purgesPin() {
        assertThat(tracking.securityCode()).isNull();
    }

    @Test
    @DisplayName("keeps the ticket number and the ordered items for the summary")
    void keepsTicketAndItems() {
        assertThat(tracking.ticketNumber()).isEqualTo(42);
        assertThat(tracking.items()).extracting(OrderLiveTrackingResponse.LiveTrackingItemDto::dishName)
                .containsExactly("Milanesa con Puré");
    }
}
