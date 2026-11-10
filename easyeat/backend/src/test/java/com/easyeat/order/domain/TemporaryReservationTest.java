package com.easyeat.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporaryReservationTest {

    @Test
    @DisplayName("Should create active reservation with default 15 minute expiration")
    void shouldCreateActiveReservationWithTtl() {
        Order order = new Order();
        String idempotencyKey = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        TemporaryReservation reservation = new TemporaryReservation(order, idempotencyKey, expiresAt);

        assertThat(reservation.getOrder()).isEqualTo(order);
        assertThat(reservation.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reservation.getReleaseReason()).isNull();
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getReleasedAt()).isNull();
    }

    @Test
    @DisplayName("Should confirm reservation transitioning status to CONFIRMED and recording confirmedAt")
    void shouldConfirmReservation() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-123", Instant.now().plus(15, ChronoUnit.MINUTES));

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isNotNull();
        assertThat(reservation.getReleaseReason()).isNull();
    }

    @Test
    @DisplayName("Should release reservation with reason and recording releasedAt")
    void shouldReleaseReservation() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-456", Instant.now().plus(15, ChronoUnit.MINUTES));

        reservation.release(ReleaseReason.CANCELLED_BY_USER);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED_BY_USER);
        assertThat(reservation.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should expire reservation recording releasedAt and EXPIRED reason")
    void shouldExpireReservation() {
        Order order = new Order();
        TemporaryReservation reservation = new TemporaryReservation(order, "key-789", Instant.now().minus(1, ChronoUnit.MINUTES));

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getReleaseReason()).isEqualTo(ReleaseReason.EXPIRED);
        assertThat(reservation.getReleasedAt()).isNotNull();
    }
}

