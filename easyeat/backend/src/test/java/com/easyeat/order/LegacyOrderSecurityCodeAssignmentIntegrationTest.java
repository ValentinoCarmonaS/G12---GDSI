package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.ZeroAmountOrderRequest;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import com.easyeat.order.service.PaymentWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 022, T005: the confirmation paths assign the pickup PIN through the write-once
 * {@code Order.assignSecurityCodeIfAbsent} (research D-02, RF-001, data-model invariant 2).
 * <p>
 * A legacy order is one whose row holds {@code security_code = NULL}; {@code @PrePersist} always generates
 * a code, so the row is nulled through JDBC to reproduce it, as V26's backfill could have missed.
 */
@DisplayName("LegacyOrderSecurityCodeAssignmentIntegrationTest (T005, spec 022, research D-02)")
class LegacyOrderSecurityCodeAssignmentIntegrationTest extends BaseIntegrationTest {

    private static final String PIN_FORMAT = "^[1-9][0-9]{3}$";

    @Autowired
    private CheckoutConfirmationService checkoutConfirmationService;

    @Autowired
    private PaymentWebhookService paymentWebhookService;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("confirmPayment assigns and persists a PIN to a legacy order without one (RF-001)")
    void confirmPaymentAssignsPinToLegacyOrder() {
        PendingOrder pending = pendingOrder(new BigDecimal("6000.00"));
        nullSecurityCode(pending.order());

        checkoutConfirmationService.confirmPayment(pending.diner().getId(),
                new PaymentConfirmationRequest(pending.order().getId(), "mp_pay_t005_legacy", "approved"));

        assertThat(storedSecurityCode(pending.order())).matches(PIN_FORMAT);
    }

    @Test
    @DisplayName("confirmZeroAmountOrder assigns and persists a PIN to a legacy order without one (RF-001)")
    void confirmZeroAmountOrderAssignsPinToLegacyOrder() {
        PendingOrder pending = pendingOrder(BigDecimal.ZERO);
        nullSecurityCode(pending.order());

        checkoutConfirmationService.confirmZeroAmountOrder(pending.diner().getId(),
                new ZeroAmountOrderRequest(pending.order().getId()));

        assertThat(storedSecurityCode(pending.order())).matches(PIN_FORMAT);
    }

    @Test
    @DisplayName("an approved payment webhook assigns and persists a PIN to a legacy order without one (RF-001)")
    void approvedWebhookAssignsPinToLegacyOrder() {
        PendingOrder pending = pendingOrder(new BigDecimal("5000.00"));
        Payment payment = new Payment(pending.order(), pending.reservation(),
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                pending.order().getId().toString(), pending.order().getTotalAmount(), UUID.randomUUID().toString());
        payment.assignGatewayPreference("pref-t005", "https://mercadopago.com/init", "https://sandbox.mercadopago.com/init");
        paymentRepository.save(payment);
        nullSecurityCode(pending.order());

        paymentWebhookService.processPaymentUpdate("mercadopago", "mp-pay-t005-legacy",
                pending.order().getId().toString(), "approved", "accredited");

        assertThat(storedSecurityCode(pending.order())).matches(PIN_FORMAT);
    }

    @Test
    @DisplayName("confirmPayment keeps the PIN an order already holds (invariant 2)")
    void confirmPaymentKeepsExistingPin() {
        PendingOrder pending = pendingOrder(new BigDecimal("6000.00"));
        String original = storedSecurityCode(pending.order());

        checkoutConfirmationService.confirmPayment(pending.diner().getId(),
                new PaymentConfirmationRequest(pending.order().getId(), "mp_pay_t005_keep", "approved"));

        assertThat(original).matches(PIN_FORMAT);
        assertThat(storedSecurityCode(pending.order())).isEqualTo(original);
    }

    private PendingOrder pendingOrder(BigDecimal total) {
        String suffix = UUID.randomUUID().toString();
        Establishment establishment = createTestEstablishment("T005 Legacy PIN " + suffix);
        Dish dish = createTestDish(establishment.getId(), "T005 Dish " + suffix, new BigDecimal("6000.00"));
        User diner = createTestDiner("t005-" + suffix + "@easyeat.test");

        Order order = new Order(null, establishment.getId(), diner.getId(), FulfillmentMode.SCHEDULED,
                LocalDate.now().plusDays(1), LocalTime.of(13, 0), LocalTime.of(13, 30), OrderStatus.PENDING_PAYMENT,
                total, Instant.now().plusSeconds(900));
        orderRepository.save(order);
        orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        TemporaryReservation reservation = temporaryReservationRepository.save(
                new TemporaryReservation(order, UUID.randomUUID().toString(), Instant.now().plusSeconds(900)));
        return new PendingOrder(order, diner, reservation);
    }

    private void nullSecurityCode(Order order) {
        jdbcTemplate.update("UPDATE orders SET security_code = NULL WHERE id = ?", order.getId());
        assertThat(storedSecurityCode(order)).isNull();
    }

    private String storedSecurityCode(Order order) {
        return jdbcTemplate.queryForObject("SELECT security_code FROM orders WHERE id = ?", String.class, order.getId());
    }

    private record PendingOrder(Order order, User diner, TemporaryReservation reservation) {
    }
}
