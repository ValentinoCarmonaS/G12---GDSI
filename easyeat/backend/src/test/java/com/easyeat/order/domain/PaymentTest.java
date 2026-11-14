package com.easyeat.order.domain;

import com.easyeat.operations.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    @DisplayName("Should create payment in IN_PROCESS status with required fields")
    void shouldCreatePaymentInProcess() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-1", Instant.now().plusSeconds(900));
        BigDecimal amount = new BigDecimal("15500.00");
        String externalRef = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, externalRef, amount, idempotencyKey);

        assertThat(payment.getOrder()).isEqualTo(order);
        assertThat(payment.getReservation()).isEqualTo(reservation);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        assertThat(payment.getExternalReference()).isEqualTo(externalRef);
        assertThat(payment.getTransactionAmount()).isEqualByComparingTo(amount);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROCESS);
        assertThat(payment.getStatusDetail()).isNull();
        assertThat(payment.getGatewayPreferenceId()).isNull();
        assertThat(payment.getGatewayPaymentId()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getRejectedAt()).isNull();
    }

    @Test
    @DisplayName("Should update gateway preference and redirect URLs")
    void shouldUpdateGatewayPreference() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-2", Instant.now().plusSeconds(900));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("1000.00"), "idemp-1");

        payment.assignGatewayPreference("pref-123", "https://mp.com/init", "https://sandbox.mp.com/init");

        assertThat(payment.getGatewayPreferenceId()).isEqualTo("pref-123");
        assertThat(payment.getInitPoint()).isEqualTo("https://mp.com/init");
        assertThat(payment.getSandboxInitPoint()).isEqualTo("https://sandbox.mp.com/init");
    }

    @Test
    @DisplayName("Should approve payment recording gateway payment id, status detail and approvedAt")
    void shouldApprovePayment() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-3", Instant.now().plusSeconds(900));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("2500.00"), "idemp-2");

        payment.approve("pay-987", "accredited");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getGatewayPaymentId()).isEqualTo("pay-987");
        assertThat(payment.getStatusDetail()).isEqualTo("accredited");
        assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject payment recording gateway payment id, detail and rejectedAt")
    void shouldRejectPayment() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-4", Instant.now().plusSeconds(900));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("3500.00"), "idemp-3");

        payment.reject("pay-654", "cc_rejected_insufficient_amount");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getGatewayPaymentId()).isEqualTo("pay-654");
        assertThat(payment.getStatusDetail()).isEqualTo("cc_rejected_insufficient_amount");
        assertThat(payment.getRejectedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should cancel payment when user aborts or cancels")
    void shouldCancelPayment() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-5", Instant.now().plusSeconds(900));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("4500.00"), "idemp-4");

        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should fail payment upon network error or gateway failure")
    void shouldFailPayment() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-6", Instant.now().plusSeconds(900));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("5500.00"), "idemp-5");

        payment.fail("gateway_timeout");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getStatusDetail()).isEqualTo("gateway_timeout");
    }

    @Test
    @DisplayName("Should expire payment upon TTL timeout")
    void shouldExpirePayment() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-res-7", Instant.now().minusSeconds(10));
        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, "ext-ref", new BigDecimal("6500.00"), "idemp-6");

        payment.expire();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }
}

