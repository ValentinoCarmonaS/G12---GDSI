package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
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

@DisplayName("T016: PaymentWebhookProcessingIntegrationTest (AS-2b, FR-003, INV-02, INV-06, CB-07, SC-008)")
class PaymentWebhookProcessingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("AS-2b: Webhook approved confirma orden fehacientemente, marca reserva CONFIRMED y es idempotente ante eventos repetidos")
    void shouldProcessPaymentApprovedWebhookAndBeStrictlyIdempotent() {
        Establishment establishment = createTestEstablishment("Restaurante Webhook");
        Dish dish = createTestDish(establishment.getId(), "Plato Webhook", new BigDecimal("5000.00"));
        User diner = createTestDiner("webhook_diner@test.com");

        Order order = new Order();
        order.setEstablishment(establishment);
        order.setUser(diner);
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("5000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        String gatewayPaymentId = "mp-pay-999888777";
        String gatewayPreferenceId = "pref-123456";
        Payment payment = new Payment(
                order,
                reservation,
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                gatewayPreferenceId,
                order.getId().toString(),
                order.getTotalAmount(),
                "https://mercadopago.com/init",
                "https://sandbox.mercadopago.com/init",
                UUID.randomUUID().toString()
        );
        paymentRepository.save(payment);

        // Primer procesamiento de webhook de pago aprobado
        paymentWebhookService.processPaymentUpdate("mercadopago", gatewayPaymentId, order.getId().toString(), "approved", "accredited");

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        TemporaryReservation updatedReservation = temporaryReservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(updatedReservation.getConfirmedAt()).isNotNull();

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(updatedPayment.getGatewayPaymentId()).isEqualTo(gatewayPaymentId);
        assertThat(updatedPayment.getApprovedAt()).isNotNull();

        // Segundo procesamiento idéntico (simulación de reintento de webhook de Mercado Pago)
        paymentWebhookService.processPaymentUpdate("mercadopago", gatewayPaymentId, order.getId().toString(), "approved", "accredited");

        // Verificar que el estado se preserva de forma estrictamente idempotente
        Order orderAfterDuplicate = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderAfterDuplicate.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
