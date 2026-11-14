package com.easyeat.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLifecycleSupportTest {

    @Test
    @DisplayName("Should correctly evaluate lifecycle helper methods isPendingAccreditation and isInProgress")
    void shouldEvaluateLifecycleStatusHelpers() {
        Order order = new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 30),
                OrderStatus.PENDING_PAYMENT,
                BigDecimal.valueOf(1500.00),
                Instant.now().plusSeconds(900)
        );

        assertThat(order.isPendingAccreditation()).isFalse();
        assertThat(order.isInProgress()).isFalse();

        order.setStatus(OrderStatus.PENDING_ACCREDITATION);
        assertThat(order.isPendingAccreditation()).isTrue();
        assertThat(order.isInProgress()).isFalse();

        order.setStatus(OrderStatus.IN_PROGRESS);
        assertThat(order.isPendingAccreditation()).isFalse();
        assertThat(order.isInProgress()).isTrue();

        order.setStatus(OrderStatus.CONFIRMED);
        assertThat(order.isPendingAccreditation()).isFalse();
        assertThat(order.isInProgress()).isFalse();
    }
}
