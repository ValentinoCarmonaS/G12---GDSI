package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.CancelPaymentRequest;
import com.easyeat.order.dto.CancelPaymentResponse;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutPaymentService;
import com.easyeat.order.service.PaymentWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T017: PaymentRejectionAndCancellationIntegrationTest (AS-3, AS-4, FR-005, FR-007, INV-03)")
class PaymentRejectionAndCancellationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("AS-3 & FR-005: Rechazo por fondos insuficientes o fraude libera inmediatamente la reserva con motivo REJECTED y cancela orden")
    void shouldReleaseReservationImmediatelyOnPaymentRejection() {
        Establishment establishment = createTestEstablishment("Restaurante Rechazo");
        Dish dish = createTestDish(establishment.getId(), "Plato Rechazo", new BigDecimal("3000.00"));
        User diner = createTestDiner("diner_rejected@test.com");

        Order order = new Order();
        order.setEstablishment(establishment);
        order.setUser(diner);
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(14, 0));
        order.setTimeSlotEnd(LocalTime.of(14, 15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("3000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        Payment payment = new Payment(
                order,
                reservation,
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                "pref-reject-123",
                order.getId().toString(),
                order.getTotalAmount(),
                "https://mercadopago.com/init",
                "https://sandbox.mercadopago.com/init",
                UUID.randomUUID().toString()
        );
        paymentRepository.save(payment);

        paymentWebhookService.processPaymentUpdate("mercadopago", "mp-pay-rejected-123", order.getId().toString(), "rejected", "cc_rejected_insufficient_amount");

        TemporaryReservation updatedReservation = temporaryReservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(updatedReservation.getReleaseReason()).isEqualTo(ReleaseReason.REJECTED);
        assertThat(updatedReservation.getReleasedAt()).isNotNull();

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(updatedPayment.getStatusDetail()).isEqualTo("cc_rejected_insufficient_amount");
        assertThat(updatedPayment.getRejectedAt()).isNotNull();

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("AS-4 & FR-007: Cancelación voluntaria por comensal libera reserva con CANCELLED_BY_USER y re-habilita el checkout")
    void shouldReleaseReservationImmediatelyOnVoluntaryCancellation() {
        Establishment establishment = createTestEstablishment("Restaurante Cancelacion");
        Dish dish = createTestDish(establishment.getId(), "Plato Cancelacion", new BigDecimal("4000.00"));
        User diner = createTestDiner("diner_voluntary_cancel@test.com");

        Order order = new Order();
        order.setEstablishment(establishment);
        order.setUser(diner);
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(15, 0));
        order.setTimeSlotEnd(LocalTime.of(15, 15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("4000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        Payment payment = new Payment(
                order,
                reservation,
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                "pref-cancel-123",
                order.getId().toString(),
                order.getTotalAmount(),
                "https://mercadopago.com/init",
                "https://sandbox.mercadopago.com/init",
                UUID.randomUUID().toString()
        );
        paymentRepository.save(payment);

        CancelPaymentResponse cancelResponse = checkoutPaymentService.cancelPaymentAttempt(diner.getId(), new CancelPaymentRequest(order.getId()));

        assertThat(cancelResponse.success()).isTrue();
        assertThat(cancelResponse.orderId()).isEqualTo(order.getId());
        assertThat(cancelResponse.reservationStatus()).isEqualTo(ReservationStatus.RELEASED.name());

        TemporaryReservation updatedReservation = temporaryReservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(updatedReservation.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED_BY_USER);
        assertThat(updatedReservation.getReleasedAt()).isNotNull();

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
