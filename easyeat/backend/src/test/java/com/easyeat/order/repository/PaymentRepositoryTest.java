package com.easyeat.order.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class PaymentRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TemporaryReservationRepository reservationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    private Order createPersistedOrder() {
        Establishment establishment = new Establishment();
        establishment.setName("Restaurante Pago " + UUID.randomUUID());
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(22, 0));
        establishment.setLeadTimeMinutes(30);
        establishment.setMaxCapacityPerSlot(10);
        establishment = establishmentRepository.saveAndFlush(establishment);

        User user = new User();
        user.setEmail("user-pay-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash123");
        user.setRole(UserRole.COMENSAL);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(user.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.now());
        order.setTimeSlotEnd(LocalTime.now().plusMinutes(15));
        order.setTotalAmount(new BigDecimal("2500.00"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        return orderRepository.saveAndFlush(order);
    }

    private TemporaryReservation createPersistedReservation(Order order) {
        TemporaryReservation reservation = new TemporaryReservation(order, UUID.randomUUID().toString(), Instant.now().plus(15, ChronoUnit.MINUTES));
        return reservationRepository.saveAndFlush(reservation);
    }

    @Test
    @DisplayName("Should find payment by gatewayPaymentId")
    void shouldFindByGatewayPaymentId() {
        Order order = createPersistedOrder();
        TemporaryReservation reservation = createPersistedReservation(order);
        String gatewayPaymentId = "mp-pay-" + UUID.randomUUID();

        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, order.getId().toString(), order.getTotalAmount(), UUID.randomUUID().toString());
        payment.approve(gatewayPaymentId, "accredited");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> found = paymentRepository.findByGatewayPaymentId(gatewayPaymentId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("Should find payment by gatewayPreferenceId")
    void shouldFindByGatewayPreferenceId() {
        Order order = createPersistedOrder();
        TemporaryReservation reservation = createPersistedReservation(order);
        String preferenceId = "mp-pref-" + UUID.randomUUID();

        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, order.getId().toString(), order.getTotalAmount(), UUID.randomUUID().toString());
        payment.assignGatewayPreference(preferenceId, "https://mp.com/init", "https://sandbox.mp.com/init");
        paymentRepository.saveAndFlush(payment);

        Optional<Payment> found = paymentRepository.findByGatewayPreferenceId(preferenceId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
    }

    @Test
    @DisplayName("Should find latest payment by order id")
    void shouldFindTopByOrderIdOrderByCreatedAtDesc() {
        Order order = createPersistedOrder();
        TemporaryReservation reservation = createPersistedReservation(order);

        Payment payment1 = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, order.getId().toString(), order.getTotalAmount(), UUID.randomUUID().toString());
        paymentRepository.saveAndFlush(payment1);

        Payment payment2 = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, order.getId().toString(), order.getTotalAmount(), UUID.randomUUID().toString());
        paymentRepository.saveAndFlush(payment2);

        Optional<Payment> latest = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId());

        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(payment2.getId());
    }
}

