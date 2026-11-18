package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.KdsTicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 022, T011 (US1, contract 03): {@code GET /api/v1/orders/{orderId}/live-tracking} through the real
 * controller, service and PostgreSQL schema.
 * <ul>
 *   <li>§3.1: in {@code READY_FOR_PICKUP} the response carries the stored PIN (RF-003, AC-1).</li>
 *   <li>Before that state the PIN is not exposed (spec 021 contract, AC-1 clarified on 2026-11-17).</li>
 * </ul>
 */
@DisplayName("OrderLiveTrackingPinContractTest (T011, spec 022, US1, contract 03)")
class OrderLiveTrackingPinContractTest extends BaseIntegrationTest {

    private static final String READY_MESSAGE =
            "¡Tu pedido está listo para retirar! Por favor, acercate al mostrador para retirar tu comida.";

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Test
    @DisplayName("READY_FOR_PICKUP exposes the stored PIN with the contract 03 §3.1 payload")
    void readyForPickupExposesStoredPin() throws Exception {
        TrackedOrder tracked = trackedOrder(OrderStatus.READY_FOR_PICKUP, KdsTicketStatus.READY_FOR_PICKUP);

        liveTracking(tracked)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.orderId").value(tracked.order().getId().toString()))
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.isReadyForPickup").value(true))
                .andExpect(jsonPath("$.isCountdownActive").value(false))
                .andExpect(jsonPath("$.progressPercentage").value(100))
                .andExpect(jsonPath("$.message").value(READY_MESSAGE))
                .andExpect(jsonPath("$.securityCode").value(tracked.order().getSecurityCode()))
                .andExpect(jsonPath("$.securityCode").value(org.hamcrest.Matchers.matchesPattern("^[1-9][0-9]{3}$")));
    }

    @Test
    @DisplayName("QUEUED does not expose the PIN yet")
    void queuedHidesPin() throws Exception {
        liveTracking(trackedOrder(OrderStatus.IN_PROGRESS, KdsTicketStatus.QUEUED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isReadyForPickup").value(false))
                .andExpect(jsonPath("$.securityCode").doesNotExist());
    }

    @Test
    @DisplayName("IN_PREPARATION does not expose the PIN yet")
    void inPreparationHidesPin() throws Exception {
        liveTracking(trackedOrder(OrderStatus.IN_PROGRESS, KdsTicketStatus.IN_PREPARATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isReadyForPickup").value(false))
                .andExpect(jsonPath("$.securityCode").doesNotExist());
    }

    private ResultActions liveTracking(TrackedOrder tracked) throws Exception {
        UsernamePasswordAuthenticationToken dinerAuth = new UsernamePasswordAuthenticationToken(
                tracked.diner(), null, List.of(new SimpleGrantedAuthority("ROLE_COMENSAL")));
        return mockMvc.perform(get("/api/v1/orders/{orderId}/live-tracking", tracked.order().getId())
                .with(authentication(dinerAuth))
                .accept(MediaType.APPLICATION_JSON));
    }

    private TrackedOrder trackedOrder(OrderStatus orderStatus, KdsTicketStatus ticketStatus) {
        Establishment establishment = createTestEstablishment("T011 Tracking Bistro " + UUID.randomUUID());
        User diner = createTestDiner("t011-" + UUID.randomUUID() + "@easyeat.test");
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        Order order = new Order(null, establishment.getId(), diner.getId(), FulfillmentMode.IMMEDIATE, today,
                LocalTime.of(12, 0), LocalTime.of(12, 15), orderStatus, new BigDecimal("4500.00"), null);
        if (orderStatus == OrderStatus.READY_FOR_PICKUP) {
            order.setReadyAt(now);
        }
        order = orderRepository.saveAndFlush(order);

        KdsTicket ticket = new KdsTicket(null, order.getId(), establishment, 42, ticketStatus,
                FulfillmentMode.IMMEDIATE, today, LocalTime.of(12, 0), LocalTime.of(12, 15), now.minusSeconds(600));
        if (ticketStatus != KdsTicketStatus.QUEUED) {
            ticket.setStartedAt(now.minusSeconds(300));
        }
        if (ticketStatus == KdsTicketStatus.READY_FOR_PICKUP) {
            ticket.setReadyAt(now);
        }
        kdsTicketRepository.saveAndFlush(ticket);
        return new TrackedOrder(order, diner);
    }

    private record TrackedOrder(Order order, User diner) {
    }
}
