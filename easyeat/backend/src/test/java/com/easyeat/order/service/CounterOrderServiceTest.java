package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CompleteDeliveryResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 022, T017 (US2, ENC-3.3.2): atomic, idempotent delivery completion (research D-03, contract 01,
 * data-model invariants 3, 4 and 5; RF-010 to RF-013).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CounterOrderServiceTest (T017, spec 022, US2)")
class CounterOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T18:30:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    private CounterOrderService service;
    private UUID establishmentId;
    private User encargado;

    @BeforeEach
    void setUp() {
        service = new CounterOrderService(orderRepository, kdsTicketRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        establishmentId = UUID.randomUUID();
        encargado = staff(UserRole.ENCARGADO, establishmentId);
    }

    @Nested
    @DisplayName("first delivery of a READY_FOR_PICKUP order")
    class FirstDelivery {

        @Test
        @DisplayName("moves order and ticket to COMPLETED with the same completedAt instant (invariant 3)")
        void completesOrderAndTicketAtomically() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            KdsTicket ticket = ticket(order, KdsTicketStatus.READY_FOR_PICKUP);
            stubLookup(order, ticket);

            CompleteDeliveryResponse response = service.completeDelivery(order.getId(), encargado);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getCompletedAt()).isEqualTo(NOW);
            assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.COMPLETED);
            assertThat(ticket.getCompletedAt()).isEqualTo(NOW);
            verify(orderRepository).save(order);
            verify(kdsTicketRepository).save(ticket);

            assertThat(response.orderId()).isEqualTo(order.getId());
            assertThat(response.ticketNumber()).isEqualTo(42);
            assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.completedAt()).isEqualTo(NOW);
            assertThat(response.message()).isEqualTo("Entrega completada exitosamente.");
        }

        @Test
        @DisplayName("locks the order row with findByIdForUpdate (contract 01 §4.2)")
        void locksOrderRow() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            stubLookup(order, ticket(order, KdsTicketStatus.READY_FOR_PICKUP));

            service.completeDelivery(order.getId(), encargado);

            verify(orderRepository).findByIdForUpdate(order.getId());
            verify(orderRepository, never()).findById(any());
        }

        @Test
        @DisplayName("completes a ready order that has no KDS ticket, answering ticketNumber null")
        void completesOrderWithoutTicket() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            stubLookup(order, null);

            CompleteDeliveryResponse response = service.completeDelivery(order.getId(), encargado);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.getCompletedAt()).isEqualTo(NOW);
            assertThat(response.ticketNumber()).isNull();
            verify(kdsTicketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("idempotent retry on a COMPLETED order")
    class IdempotentRetry {

        @Test
        @DisplayName("answers with the original completedAt and writes nothing (invariant 4)")
        void returnsOriginalCompletionWithoutWriting() {
            Instant originalCompletion = NOW.minusSeconds(120);
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            order.markDelivered(originalCompletion);
            KdsTicket ticket = ticket(order, KdsTicketStatus.COMPLETED);
            stubLookup(order, ticket);

            CompleteDeliveryResponse response = service.completeDelivery(order.getId(), encargado);

            assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.completedAt()).isEqualTo(originalCompletion);
            assertThat(response.ticketNumber()).isEqualTo(42);
            assertThat(response.message()).isEqualTo("La orden ya se encontraba completada. Respuesta idempotente.");
            assertThat(order.getCompletedAt()).isEqualTo(originalCompletion);
            verify(orderRepository, never()).save(any());
            verify(kdsTicketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("rejections")
    class Rejections {

        @ParameterizedTest(name = "{0} -> 409 INVALID_ORDER_STATE")
        @EnumSource(value = OrderStatus.class, names = {"READY_FOR_PICKUP", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("rejects every state other than READY_FOR_PICKUP and COMPLETED without writing")
        void rejectsNonReadyStates(OrderStatus status) {
            Order order = order(status);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.completeDelivery(order.getId(), encargado))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining(status.name())
                    .hasMessageContaining("READY_FOR_PICKUP")
                    .satisfies(ex -> assertThat(((InvalidOrderStateException) ex).getCode()).isEqualTo("INVALID_ORDER_STATE"));
            assertThat(order.getStatus()).isEqualTo(status);
            assertThat(order.getCompletedAt()).isNull();
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws OrderNotFoundException for an unknown order (404)")
        void rejectsUnknownOrder() {
            UUID unknown = UUID.randomUUID();
            when(orderRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.completeDelivery(unknown, encargado))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("refuses an ENCARGADO from another establishment and writes nothing (403)")
        void rejectsForeignEncargado() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            User foreign = staff(UserRole.ENCARGADO, UUID.randomUUID());

            assertThatThrownBy(() -> service.completeDelivery(order.getId(), foreign))
                    .isInstanceOf(TenantViolationException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("lets an ADMINISTRADOR complete an order of any establishment")
        void allowsAdministratorAcrossTenants() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            stubLookup(order, ticket(order, KdsTicketStatus.READY_FOR_PICKUP));

            service.completeDelivery(order.getId(), staff(UserRole.ADMINISTRADOR, UUID.randomUUID()));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Order.markDelivered domain transition")
    class MarkDelivered {

        @Test
        @DisplayName("refuses to deliver an order that is not READY_FOR_PICKUP (invariant 5)")
        void refusesNonReadyOrder() {
            Order order = order(OrderStatus.IN_PROGRESS);

            assertThatThrownBy(() -> order.markDelivered(NOW)).isInstanceOf(IllegalStateException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
            assertThat(order.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("never overwrites the completion instant once fixed (invariant 4)")
        void neverOverwritesCompletedAt() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);
            order.markDelivered(NOW);

            assertThatThrownBy(() -> order.markDelivered(NOW.plusSeconds(60))).isInstanceOf(IllegalStateException.class);
            assertThat(order.getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("rejects a null completion instant")
        void rejectsNullInstant() {
            Order order = order(OrderStatus.READY_FOR_PICKUP);

            assertThatThrownBy(() -> order.markDelivered(null)).isInstanceOf(IllegalArgumentException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        }
    }

    private void stubLookup(Order order, KdsTicket ticket) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(kdsTicketRepository.findByOrderId(order.getId())).thenReturn(Optional.ofNullable(ticket));
    }

    private Order order(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), establishmentId, UUID.randomUUID(), FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 15), LocalTime.of(13, 0), LocalTime.of(13, 15), status,
                new BigDecimal("4500.00"), null);
        order.assignSecurityCodeIfAbsent("4821");
        return order;
    }

    private KdsTicket ticket(Order order, KdsTicketStatus status) {
        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);
        return new KdsTicket(UUID.randomUUID(), order.getId(), establishment, 42, status, FulfillmentMode.IMMEDIATE,
                order.getPickupDate(), order.getTimeSlotStart(), order.getTimeSlotEnd(), NOW.minusSeconds(900));
    }

    private static User staff(UserRole role, UUID tenantId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setTenantId(tenantId);
        return user;
    }
}
