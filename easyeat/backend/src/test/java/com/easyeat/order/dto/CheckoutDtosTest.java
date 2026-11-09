package com.easyeat.order.dto;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutDtosTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should instantiate and validate PaymentInitiationRequest")
    void testPaymentInitiationRequest() {
        PaymentInitiationRequest.OrderItemRequest item = new PaymentInitiationRequest.OrderItemRequest(UUID.randomUUID(), 2);
        PaymentInitiationRequest request = new PaymentInitiationRequest(
                UUID.randomUUID(),
                FulfillmentMode.SCHEDULED,
                PaymentMethod.MERCADO_PAGO,
                LocalDate.now().plusDays(1),
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                List.of(item)
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.establishmentId()).isNotNull();
        assertThat(request.fulfillmentMode()).isEqualTo(FulfillmentMode.SCHEDULED);
        assertThat(request.paymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        assertThat(request.items()).hasSize(1);
    }

    @Test
    @DisplayName("Should instantiate PaymentInitiationResponse")
    void testPaymentInitiationResponse() {
        UUID orderId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        UUID payId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(900);

        PaymentInitiationResponse response = new PaymentInitiationResponse(
                orderId,
                resId,
                payId,
                "pref-123",
                "https://mp.com/init",
                true,
                expiresAt,
                new BigDecimal("15500.00"),
                "ARS"
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.reservationId()).isEqualTo(resId);
        assertThat(response.paymentId()).isEqualTo(payId);
        assertThat(response.gatewayPreferenceId()).isEqualTo("pref-123");
        assertThat(response.redirectUrl()).isEqualTo("https://mp.com/init");
        assertThat(response.sandbox()).isTrue();
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("15500.00"));
        assertThat(response.currency()).isEqualTo("ARS");
    }

    @Test
    @DisplayName("Should instantiate CancelPaymentRequest and CancelPaymentResponse")
    void testCancelPaymentDtos() {
        UUID orderId = UUID.randomUUID();
        CancelPaymentRequest request = new CancelPaymentRequest(orderId);
        CancelPaymentResponse response = new CancelPaymentResponse(orderId, ReservationStatus.RELEASED, PaymentStatus.CANCELLED, "Ok");

        assertThat(request.orderId()).isEqualTo(orderId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.message()).isEqualTo("Ok");
    }

    @Test
    @DisplayName("Should instantiate OrderPaymentStatusResponse")
    void testOrderPaymentStatusResponse() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentStatusResponse.ReservationSummary res = new OrderPaymentStatusResponse.ReservationSummary(
                UUID.randomUUID(), ReservationStatus.ACTIVE, Instant.now().plusSeconds(900), 642L, null
        );
        OrderPaymentStatusResponse.PaymentSummary pay = new OrderPaymentStatusResponse.PaymentSummary(
                UUID.randomUUID(), PaymentStatus.IN_PROCESS, null, "https://mp.com/init", true
        );
        OrderPaymentStatusResponse.FulfillmentSummary ful = new OrderPaymentStatusResponse.FulfillmentSummary(
                FulfillmentMode.SCHEDULED, LocalDate.now(), LocalTime.of(13, 0), LocalTime.of(13, 15)
        );

        OrderPaymentStatusResponse response = new OrderPaymentStatusResponse(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                res,
                pay,
                ful,
                new BigDecimal("15500.00"),
                "ARS",
                Instant.now()
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.reservation().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(response.payment().status()).isEqualTo(PaymentStatus.IN_PROCESS);
        assertThat(response.fulfillment().mode()).isEqualTo(FulfillmentMode.SCHEDULED);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("15500.00"));
        assertThat(response.currency()).isEqualTo("ARS");
    }
}

