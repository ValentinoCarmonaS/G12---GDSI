package com.easyeat.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus Enum Unit Test")
class PaymentStatusTest {

    @Test
    @DisplayName("PaymentStatus contains all specified transaction states")
    void testPaymentStatusValues() {
        assertThat(PaymentStatus.valueOf("IN_PROCESS")).isEqualTo(PaymentStatus.IN_PROCESS);
        assertThat(PaymentStatus.valueOf("APPROVED")).isEqualTo(PaymentStatus.APPROVED);
        assertThat(PaymentStatus.valueOf("REJECTED")).isEqualTo(PaymentStatus.REJECTED);
        assertThat(PaymentStatus.valueOf("CANCELLED")).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(PaymentStatus.valueOf("FAILED")).isEqualTo(PaymentStatus.FAILED);
        assertThat(PaymentStatus.valueOf("EXPIRED")).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(PaymentStatus.values()).hasSize(6);
    }
}

