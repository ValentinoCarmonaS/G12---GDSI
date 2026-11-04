package com.easyeat.operations.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentMethod enum and TimeSlot value object")
class PaymentMethodAndTimeSlotTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Test
    @DisplayName("PaymentMethod exposes exactly the two supported online methods")
    void paymentMethodHasExactlyTwoValues() {
        assertThat(PaymentMethod.values())
                .containsExactly(PaymentMethod.MERCADO_PAGO, PaymentMethod.STRIPE);
        assertThat(PaymentMethod.valueOf("STRIPE")).isEqualTo(PaymentMethod.STRIPE);
    }

    @Test
    @DisplayName("INV-5: a slot lasts exactly 15 minutes")
    void slotLastsFifteenMinutes() {
        TimeSlot slot = new TimeSlot(DAY, LocalTime.of(12, 0));

        assertThat(slot.endTime()).isEqualTo(LocalTime.of(12, 15));
        assertThat(ChronoUnit.MINUTES.between(slot.startTime(), slot.endTime()))
                .isEqualTo(TimeSlot.DURATION_MINUTES);
    }

    @Test
    @DisplayName("slot interval is semi-open: start inclusive, end exclusive")
    void slotIsSemiOpen() {
        TimeSlot slot = new TimeSlot(DAY, LocalTime.of(12, 0));

        assertThat(slot.contains(LocalTime.of(12, 0))).isTrue();
        assertThat(slot.contains(LocalTime.of(12, 14, 59))).isTrue();
        assertThat(slot.contains(LocalTime.of(12, 15))).isFalse();
        assertThat(slot.contains(LocalTime.of(11, 59, 59))).isFalse();
    }

    @Test
    @DisplayName("EC-015: contiguous slots are independent and do not overlap")
    void contiguousSlotsDoNotOverlap() {
        TimeSlot first = new TimeSlot(DAY, LocalTime.of(12, 0));
        TimeSlot second = new TimeSlot(DAY, first.endTime());

        assertThat(second.startTime()).isEqualTo(first.endTime());
        assertThat(first.contains(second.startTime())).isFalse();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("rejects a missing date or start time")
    void rejectsNulls() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.NOON)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimeSlot(DAY, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects a slot that would wrap past midnight (mirrors CHECK start_time < end_time)")
    void rejectsSlotWrappingPastMidnight() {
        assertThat(new TimeSlot(DAY, LocalTime.of(23, 30)).endTime()).isEqualTo(LocalTime.of(23, 45));
        assertThatThrownBy(() -> new TimeSlot(DAY, LocalTime.of(23, 45)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
