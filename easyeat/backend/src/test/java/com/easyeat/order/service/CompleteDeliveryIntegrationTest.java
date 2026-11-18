package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CompleteDeliveryResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 022, T018 (US2, ENC-3.3.2): {@code POST /api/v1/counter/orders/{orderId}/complete-delivery} against
 * PostgreSQL (Testcontainers), through the real controller, security, service and schema (contract 01).
 */
@DisplayName("CompleteDeliveryIntegrationTest (T018, spec 022, US2, contract 01)")
class CompleteDeliveryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private CounterOrderService counterOrderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("200: completes order and ticket atomically with one shared completed_at (contract 01 §3.1)")
    void firstDeliveryCompletesOrderAndTicket() throws Exception {
        Delivery d = readyOrder(true);

        completeDelivery(d.order().getId(), d.encargado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(d.order().getId().toString()))
                .andExpect(jsonPath("$.ticketNumber").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.message").value("Entrega completada exitosamente."));

        Map<String, Object> order = orderRow(d.order().getId());
        Map<String, Object> ticket = ticketRow(d.order().getId());
        assertThat(order.get("status")).isEqualTo("COMPLETED");
        assertThat(ticket.get("status")).isEqualTo("COMPLETED");
        assertThat(order.get("completed_at")).isNotNull().isEqualTo(ticket.get("completed_at"));
    }

    @Test
    @DisplayName("200 idempotent: a retry returns the original completedAt and changes nothing (contract 01 §3.2)")
    void retryIsIdempotent() throws Exception {
        Delivery d = readyOrder(true);
        CompleteDeliveryResponse first = counterOrderService.completeDelivery(d.order().getId(), d.encargado());
        Map<String, Object> rowAfterFirst = orderRow(d.order().getId());

        completeDelivery(d.order().getId(), d.encargado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(first.completedAt().toString()))
                .andExpect(jsonPath("$.message").value("La orden ya se encontraba completada. Respuesta idempotente."));

        assertThat(orderRow(d.order().getId())).isEqualTo(rowAfterFirst);
    }

    @Test
    @DisplayName("409 INVALID_ORDER_STATE for an order still IN_PROGRESS, left untouched (contract 01 §3.3)")
    void rejectsOrderNotReady() throws Exception {
        Delivery d = order(OrderStatus.IN_PROGRESS, KdsTicketStatus.IN_PREPARATION);

        completeDelivery(d.order().getId(), d.encargado())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));

        assertThat(orderRow(d.order().getId()).get("status")).isEqualTo("IN_PROGRESS");
        assertThat(orderRow(d.order().getId()).get("completed_at")).isNull();
    }

    @Test
    @DisplayName("404 ORDER_NOT_FOUND for an unknown order (contract 01 §3.4)")
    void rejectsUnknownOrder() throws Exception {
        Delivery d = readyOrder(true);

        completeDelivery(UUID.randomUUID(), d.encargado())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("403 for a COMENSAL, order left untouched (contract 01 §3.5)")
    void rejectsDinerRole() throws Exception {
        Delivery d = readyOrder(true);

        completeDelivery(d.order().getId(), d.diner(), "ROLE_COMENSAL")
                .andExpect(status().isForbidden());

        assertThat(orderRow(d.order().getId()).get("status")).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    @DisplayName("403 for an ENCARGADO of another establishment, order left untouched (contract 01 §3.5)")
    void rejectsForeignEncargado() throws Exception {
        Delivery d = readyOrder(true);

        completeDelivery(d.order().getId(), staff(UserRole.ENCARGADO, UUID.randomUUID()))
                .andExpect(status().isForbidden());

        assertThat(orderRow(d.order().getId()).get("status")).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    @DisplayName("200 with ticketNumber null for a ready order without KDS ticket")
    void completesOrderWithoutTicket() throws Exception {
        Delivery d = readyOrder(false);

        completeDelivery(d.order().getId(), d.encargado())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ticketNumber").doesNotExist());

        assertThat(orderRow(d.order().getId()).get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("indivisible: if the ticket update fails, the order stays READY_FOR_PICKUP (contract 01 §4.4)")
    void ticketFailureRollsBackOrder() {
        Delivery d = readyOrder(true);
        UUID ticketId = (UUID) ticketRow(d.order().getId()).get("id");
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION t018_fail_ticket_update() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'T018 forced KDS ticket failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("CREATE TRIGGER t018_fail_ticket BEFORE UPDATE ON kds_tickets FOR EACH ROW "
                + "WHEN (OLD.id = '" + ticketId + "') EXECUTE FUNCTION t018_fail_ticket_update()");
        try {
            assertThatThrownBy(() -> counterOrderService.completeDelivery(d.order().getId(), d.encargado()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS t018_fail_ticket ON kds_tickets");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS t018_fail_ticket_update()");
        }

        assertThat(orderRow(d.order().getId()).get("status")).isEqualTo("READY_FOR_PICKUP");
        assertThat(orderRow(d.order().getId()).get("completed_at")).isNull();
        assertThat(ticketRow(d.order().getId()).get("status")).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    @DisplayName("two simultaneous terminals: one completes, the other gets the idempotent answer (contract 01 §4.3)")
    void concurrentDeliveriesSerialize() throws Exception {
        Delivery d = readyOrder(true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CompleteDeliveryResponse> a = pool.submit(() -> {
                start.await();
                return counterOrderService.completeDelivery(d.order().getId(), d.encargado());
            });
            Future<CompleteDeliveryResponse> b = pool.submit(() -> {
                start.await();
                return counterOrderService.completeDelivery(d.order().getId(), d.encargado());
            });
            start.countDown();
            CompleteDeliveryResponse ra = a.get(30, TimeUnit.SECONDS);
            CompleteDeliveryResponse rb = b.get(30, TimeUnit.SECONDS);

            assertThat(ra.completedAt()).isEqualTo(rb.completedAt());
            assertThat(List.of(ra.message(), rb.message())).containsExactlyInAnyOrder(
                    "Entrega completada exitosamente.",
                    "La orden ya se encontraba completada. Respuesta idempotente.");
        } finally {
            pool.shutdownNow();
        }
        assertThat(orderRow(d.order().getId()).get("completed_at"))
                .isEqualTo(ticketRow(d.order().getId()).get("completed_at"));
    }

    private ResultActions completeDelivery(UUID orderId, User actor) throws Exception {
        return completeDelivery(orderId, actor, "ROLE_" + actor.getRole().name());
    }

    private ResultActions completeDelivery(UUID orderId, User actor, String authority) throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority(authority)));
        return mockMvc.perform(post("/api/v1/counter/orders/{orderId}/complete-delivery", orderId)
                .with(authentication(auth)));
    }

    private Delivery readyOrder(boolean withTicket) {
        return withTicket
                ? order(OrderStatus.READY_FOR_PICKUP, KdsTicketStatus.READY_FOR_PICKUP)
                : order(OrderStatus.READY_FOR_PICKUP, null);
    }

    private Delivery order(OrderStatus orderStatus, KdsTicketStatus ticketStatus) {
        Establishment establishment = createTestEstablishment("T018 Counter Bistro " + UUID.randomUUID());
        User diner = createTestDiner("t018-" + UUID.randomUUID() + "@easyeat.test");
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        Order order = new Order(null, establishment.getId(), diner.getId(), FulfillmentMode.IMMEDIATE, today,
                LocalTime.of(12, 0), LocalTime.of(12, 15), orderStatus, new BigDecimal("4500.00"), null);
        if (orderStatus == OrderStatus.READY_FOR_PICKUP) {
            order.setReadyAt(now);
        }
        order = orderRepository.saveAndFlush(order);

        if (ticketStatus != null) {
            KdsTicket ticket = new KdsTicket(null, order.getId(), establishment, 42, ticketStatus,
                    FulfillmentMode.IMMEDIATE, today, LocalTime.of(12, 0), LocalTime.of(12, 15), now.minusSeconds(900));
            ticket.setStartedAt(now.minusSeconds(600));
            if (ticketStatus == KdsTicketStatus.READY_FOR_PICKUP) {
                ticket.setReadyAt(now);
            }
            kdsTicketRepository.saveAndFlush(ticket);
        }
        return new Delivery(order, diner, staff(UserRole.ENCARGADO, establishment.getId()));
    }

    private Map<String, Object> orderRow(UUID orderId) {
        return jdbcTemplate.queryForMap("SELECT status, completed_at FROM orders WHERE id = ?", orderId);
    }

    private Map<String, Object> ticketRow(UUID orderId) {
        return jdbcTemplate.queryForMap("SELECT id, status, completed_at FROM kds_tickets WHERE order_id = ?", orderId);
    }

    private static User staff(UserRole role, UUID tenantId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@easyeat.test");
        user.setRole(role);
        user.setTenantId(tenantId);
        return user;
    }

    private record Delivery(Order order, User diner, User encargado) {
    }
}
