package com.easyeat.order;

import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.OrderExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExpirationScheduler Unit Tests (T018, AS-4b, FR-009, INV-04, SC-004)")
class OrderExpirationSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TemporaryReservationRepository temporaryReservationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("Transitions overdue PENDING_PAYMENT orders to EXPIRED during sweep")
    void sweepsAndTransitionsOverdueOrders() {
        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        order1.setStatus(OrderStatus.PENDING_PAYMENT);

        Order order2 = new Order();
        order2.setId(UUID.randomUUID());
        order2.setStatus(OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of(order1, order2));
        when(temporaryReservationRepository.findExpiredActiveReservations(any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sweepExpiredOrders();

        assertThat(order1.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        verify(orderRepository).saveAll(List.of(order1, order2));
    }

    @Test
    @DisplayName("Does nothing when no pending orders or reservations are expired")
    void doesNothingWhenNoExpiredOrders() {
        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of());
        when(temporaryReservationRepository.findExpiredActiveReservations(any(Instant.class)))
                .thenReturn(List.of());

        scheduler.sweepExpiredOrders();

        verify(orderRepository, never()).saveAll(any());
        verify(temporaryReservationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AS-4b & INV-04: Libera coordinadamente reservas temporales vencidas marcándolas EXPIRED con motivo EXPIRED y cancela pagos asociados")
    void sweepsAndReleasesExpiredReservations() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        TemporaryReservation reservation = new TemporaryReservation(order, "sweep-idemp-1", Instant.now().minusSeconds(10));
        reservation.setStatus(ReservationStatus.ACTIVE);

        Payment payment = new Payment(order, reservation, com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO, order.getId().toString(), new BigDecimal("1000.00"), "pay-idemp-1");

        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of(order));
        when(temporaryReservationRepository.findExpiredActiveReservations(any(Instant.class)))
                .thenReturn(List.of(reservation));
        when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(Optional.of(payment));

        scheduler.sweepExpiredOrders();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getReleaseReason()).isEqualTo(ReleaseReason.EXPIRED);
        assertThat(reservation.getReleasedAt()).isNotNull();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        verify(temporaryReservationRepository).saveAll(List.of(reservation));
        verify(paymentRepository).save(payment);
    }
}
