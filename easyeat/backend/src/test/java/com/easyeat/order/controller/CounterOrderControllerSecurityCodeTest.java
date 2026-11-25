package com.easyeat.order.controller;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CounterReadyOrderResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 022, T016b (US1, edge case 10, contract 02 invariant 3, research A-07): {@code GET /api/v1/counter/orders}
 * returns the PIN stored in the order or {@code null}, never a code derived from the order id, because the counter
 * must show exactly what the diner received (invariant 4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CounterOrderControllerSecurityCodeTest (T016b, spec 022, edge case 10)")
class CounterOrderControllerSecurityCodeTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private OrderRepository orderRepository;

    private CounterOrderController controller;
    private UUID establishmentId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(13, 30).atZone(BUENOS_AIRES).toInstant(), BUENOS_AIRES);
        controller = new CounterOrderController(kdsTicketRepository, orderRepository, null, null, clock);
        establishmentId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);
        KdsTicket ticket = new KdsTicket(UUID.randomUUID(), orderId, establishment, 104, KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE, TODAY, LocalTime.of(13, 0), LocalTime.of(13, 15), Instant.parse("2026-11-15T16:00:00Z"));
        ticket.setReadyAt(Instant.parse("2026-11-15T16:20:00Z"));
        when(kdsTicketRepository.findCounterReadyTickets(establishmentId, TODAY)).thenReturn(List.of(ticket));
    }

    @Test
    @DisplayName("returns the stored PIN unchanged")
    void returnsStoredPin() {
        Order order = readyOrder();
        order.assignSecurityCodeIfAbsent("4821");
        when(orderRepository.findAllByIdWithItems(List.of(orderId))).thenReturn(List.of(order));

        assertThat(counterSecurityCode()).isEqualTo("4821");
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("returns null, not a code derived from the order id, when the order has no PIN")
    void returnsNullWhenPinAbsent() {
        when(orderRepository.findAllByIdWithItems(List.of(orderId))).thenReturn(List.of(readyOrder()));

        assertThat(counterSecurityCode()).isNull();
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("returns null when the stored value is malformed (contract 02 invariant 3)")
    void returnsNullWhenPinMalformed() {
        Order order = readyOrder();
        ReflectionTestUtils.setField(order, "securityCode", "12a4");
        when(orderRepository.findAllByIdWithItems(List.of(orderId))).thenReturn(List.of(order));

        assertThat(counterSecurityCode()).isNull();
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("returns null when the ticket's order cannot be found")
    void returnsNullWhenOrderMissing() {
        when(orderRepository.findAllByIdWithItems(List.of(orderId))).thenReturn(List.of());

        assertThat(counterSecurityCode()).isNull();
        verify(orderRepository, never()).findById(any());
    }

    private Order readyOrder() {
        Order order = new Order();
        order.setId(orderId);
        order.setEstablishmentId(establishmentId);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        return order;
    }

    private String counterSecurityCode() {
        List<CounterReadyOrderResponse> body =
                controller.getCounterReadyOrders("READY_FOR_PICKUP", establishmentId, null).getBody();
        assertThat(body).hasSize(1);
        return body.get(0).securityCode();
    }
}
