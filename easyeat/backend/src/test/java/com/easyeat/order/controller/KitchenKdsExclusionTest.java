package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.KdsTicketRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 022, T037 (research D-13, boundary with spec 019): once the counter completes a delivery, the kitchen KDS
 * never shows that ticket again. Its two data sources ({@code useKdsQueuePolling} reads
 * {@code GET /api/v1/kitchen/orders?activeQueue=true}; the header metrics read
 * {@code GET /api/v1/kitchen/dashboard/summary}) are exercised against PostgreSQL (Testcontainers) after a real
 * {@code complete-delivery}, and the other tickets of the kitchen stay untouched.
 */
@DisplayName("KitchenKdsExclusionTest (T037, spec 022, D-13, boundary with spec 019)")
class KitchenKdsExclusionTest extends BaseIntegrationTest {

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Establishment establishment;
    private KdsTicket queuedTicket;
    private KdsTicket inPreparationTicket;
    private KdsTicket readyTicket;

    @BeforeEach
    void setUpKitchen() {
        establishment = createTestEstablishment("T037 Kitchen Bistro " + UUID.randomUUID());
        queuedTicket = ticketFor(OrderStatus.IN_PROGRESS, KdsTicketStatus.QUEUED, 101);
        inPreparationTicket = ticketFor(OrderStatus.IN_PROGRESS, KdsTicketStatus.IN_PREPARATION, 102);
        readyTicket = ticketFor(OrderStatus.READY_FOR_PICKUP, KdsTicketStatus.READY_FOR_PICKUP, 103);
    }

    @Test
    @DisplayName("the active kitchen queue never lists the ticket of a delivered order")
    void activeQueueExcludesCompletedTicket() throws Exception {
        deliver(readyTicket);

        kitchen(get("/api/v1/kitchen/orders").param("activeQueue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].ticketId").value(containsInAnyOrder(
                        queuedTicket.getId().toString(), inPreparationTicket.getId().toString())))
                .andExpect(jsonPath("$.content[*].ticketId").value(not(hasItem(readyTicket.getId().toString()))))
                .andExpect(jsonPath("$.content[*].status").value(not(hasItem("COMPLETED"))));
    }

    @Test
    @DisplayName("the kitchen metrics move the delivered ticket from ready to completed today, other columns unchanged")
    void dashboardCountsDeliveredTicketAsCompleted() throws Exception {
        kitchen(get("/api/v1/kitchen/dashboard/summary"))
                .andExpect(jsonPath("$.readyForPickupOrdersCount").value(1))
                .andExpect(jsonPath("$.completedTodayCount").value(0));

        deliver(readyTicket);

        kitchen(get("/api/v1/kitchen/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedOrdersCount").value(1))
                .andExpect(jsonPath("$.inPreparationOrdersCount").value(1))
                .andExpect(jsonPath("$.readyForPickupOrdersCount").value(0))
                .andExpect(jsonPath("$.completedTodayCount").value(1));
    }

    @Test
    @DisplayName("completing a delivery leaves the kitchen's other tickets exactly as they were")
    void deliveryDoesNotTouchOtherKitchenTickets() throws Exception {
        Map<String, Object> queuedBefore = ticketRow(queuedTicket.getId());
        Map<String, Object> inPreparationBefore = ticketRow(inPreparationTicket.getId());

        deliver(readyTicket);

        assertThat(ticketRow(queuedTicket.getId())).isEqualTo(queuedBefore);
        assertThat(ticketRow(inPreparationTicket.getId())).isEqualTo(inPreparationBefore);
        assertThat(ticketRow(readyTicket.getId()).get("status")).isEqualTo("COMPLETED");
    }

    private void deliver(KdsTicket ticket) throws Exception {
        User encargado = staff(UserRole.ENCARGADO);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                encargado, null, List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO")));
        mockMvc.perform(post("/api/v1/counter/orders/{orderId}/complete-delivery", ticket.getOrderId())
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private ResultActions kitchen(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        User chef = staff(UserRole.CHEF);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                chef, null, List.of(new SimpleGrantedAuthority("ROLE_CHEF")));
        return mockMvc.perform(request.with(authentication(auth)));
    }

    private KdsTicket ticketFor(OrderStatus orderStatus, KdsTicketStatus ticketStatus, int ticketNumber) {
        User diner = createTestDiner("t037-" + UUID.randomUUID() + "@easyeat.test");
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        Order order = new Order(null, establishment.getId(), diner.getId(), FulfillmentMode.IMMEDIATE, today,
                LocalTime.of(12, 0), LocalTime.of(12, 15), orderStatus, new BigDecimal("4500.00"), null);
        if (orderStatus == OrderStatus.READY_FOR_PICKUP) {
            order.setReadyAt(now);
        }
        order = orderRepository.saveAndFlush(order);

        KdsTicket ticket = new KdsTicket(null, order.getId(), establishment, ticketNumber, ticketStatus,
                FulfillmentMode.IMMEDIATE, today, LocalTime.of(12, 0), LocalTime.of(12, 15),
                now.minusSeconds(900 - ticketNumber));
        if (ticketStatus != KdsTicketStatus.QUEUED) {
            ticket.setStartedAt(now.minusSeconds(600));
        }
        if (ticketStatus == KdsTicketStatus.READY_FOR_PICKUP) {
            ticket.setReadyAt(now);
        }
        return kdsTicketRepository.saveAndFlush(ticket);
    }

    private Map<String, Object> ticketRow(UUID ticketId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, queued_at, started_at, ready_at, completed_at FROM kds_tickets WHERE id = ?", ticketId);
    }

    private User staff(UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@easyeat.test");
        user.setRole(role);
        user.setTenantId(establishment.getId());
        return user;
    }
}
