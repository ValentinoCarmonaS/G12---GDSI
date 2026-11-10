package com.easyeat.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReservationStatus and ReleaseReason Enums Unit Test")
class ReservationEnumsTest {

    @Test
    @DisplayName("ReservationStatus contains all defined lifecycle states")
    void testReservationStatusValues() {
        assertThat(ReservationStatus.valueOf("ACTIVE")).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(ReservationStatus.valueOf("CONFIRMED")).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(ReservationStatus.valueOf("RELEASED")).isEqualTo(ReservationStatus.RELEASED);
        assertThat(ReservationStatus.valueOf("EXPIRED")).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(ReservationStatus.values()).hasSize(4);
    }

    @Test
    @DisplayName("ReleaseReason contains all specified business reasons")
    void testReleaseReasonValues() {
        assertThat(ReleaseReason.valueOf("REJECTED")).isEqualTo(ReleaseReason.REJECTED);
        assertThat(ReleaseReason.valueOf("CANCELLED_BY_USER")).isEqualTo(ReleaseReason.CANCELLED_BY_USER);
        assertThat(ReleaseReason.valueOf("EXPIRED")).isEqualTo(ReleaseReason.EXPIRED);
        assertThat(ReleaseReason.valueOf("GATEWAY_ERROR")).isEqualTo(ReleaseReason.GATEWAY_ERROR);
        assertThat(ReleaseReason.values()).hasSize(4);
    }
}

