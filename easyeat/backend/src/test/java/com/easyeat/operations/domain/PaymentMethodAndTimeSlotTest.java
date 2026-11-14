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
    @DisplayName("fromWebhookSlug resolves supported provider slugs uniformly ignoring case and '-'/'_' separators")
    void fromWebhookSlugResolvesKnownSlugsUniformly() {
        assertThat(PaymentMethod.fromWebhookSlug("mercadopago")).contains(PaymentMethod.MERCADO_PAGO);
        assertThat(PaymentMethod.fromWebhookSlug("mercado-pago")).contains(PaymentMethod.MERCADO_PAGO);
        assertThat(PaymentMethod.fromWebhookSlug("mercado_pago")).contains(PaymentMethod.MERCADO_PAGO);
        assertThat(PaymentMethod.fromWebhookSlug("MERCADOPAGO")).contains(PaymentMethod.MERCADO_PAGO);
        assertThat(PaymentMethod.fromWebhookSlug("Mercado-Pago")).contains(PaymentMethod.MERCADO_PAGO);
        assertThat(PaymentMethod.fromWebhookSlug("stripe")).contains(PaymentMethod.STRIPE);
        assertThat(PaymentMethod.fromWebhookSlug("STRIPE")).contains(PaymentMethod.STRIPE);
    }

    @Test
    @DisplayName("fromWebhookSlug returns empty for null, blank, whitespace or unknown provider slugs")
    void fromWebhookSlugReturnsEmptyForInvalidOrUnknownSlugs() {
        assertThat(PaymentMethod.fromWebhookSlug(null)).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("   ")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("mercado pago")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug(" mercadopago")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("mercadopago ")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("paypal")).isEmpty();
        assertThat(PaymentMethod.fromWebhookSlug("custom-provider")).isEmpty();
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
