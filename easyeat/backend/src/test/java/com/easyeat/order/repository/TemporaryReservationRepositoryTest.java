package com.easyeat.order.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TemporaryReservationRepositoryTest extends BaseIntegrationTest {

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
        establishment.setName("Restaurante Prueba " + UUID.randomUUID());
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(22, 0));
        establishment.setLeadTimeMinutes(30);
        establishment.setMaxCapacityPerSlot(10);
        establishment = establishmentRepository.saveAndFlush(establishment);

        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@test.com");
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
        order.setTotalAmount(new BigDecimal("1200.00"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        return orderRepository.saveAndFlush(order);
    }

    @Test
    @DisplayName("Should find active reservation by order id")
    void shouldFindActiveReservationByOrderId() {
        Order order = createPersistedOrder();
        TemporaryReservation reservation = new TemporaryReservation(order, UUID.randomUUID().toString(), Instant.now().plus(15, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(reservation);

        Optional<TemporaryReservation> found = reservationRepository.findActiveByOrderId(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(reservation.getId());
        assertThat(found.get().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should not return reservation if status is not ACTIVE")
    void shouldNotReturnInactiveReservation() {
        Order order = createPersistedOrder();
        TemporaryReservation reservation = new TemporaryReservation(order, UUID.randomUUID().toString(), Instant.now().plus(15, ChronoUnit.MINUTES));
        reservation.release(ReleaseReason.CANCELLED_BY_USER);
        reservationRepository.saveAndFlush(reservation);

        Optional<TemporaryReservation> found = reservationRepository.findActiveByOrderId(order.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find expired active reservations for background scheduler sweep")
    void shouldFindExpiredActiveReservations() {
        Order order = createPersistedOrder();
        Instant pastTime = Instant.now().minus(5, ChronoUnit.MINUTES);
        TemporaryReservation expiredReservation = new TemporaryReservation(order, UUID.randomUUID().toString(), pastTime);
        reservationRepository.saveAndFlush(expiredReservation);

        List<TemporaryReservation> expiredList = reservationRepository.findExpiredActiveReservations(Instant.now());

        assertThat(expiredList).extracting(TemporaryReservation::getId).contains(expiredReservation.getId());
    }

    @Test
    @DisplayName("Should find reservation by idempotency key")
    void shouldFindByIdempotencyKey() {
        Order order = createPersistedOrder();
        String key = "test-idempotency-" + UUID.randomUUID();
        TemporaryReservation reservation = new TemporaryReservation(order, key, Instant.now().plus(15, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(reservation);

        Optional<TemporaryReservation> found = reservationRepository.findByIdempotencyKey(key);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(reservation.getId());
    }
}
