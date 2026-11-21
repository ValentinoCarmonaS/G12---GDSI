package com.easyeat.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderPaidAtTest (T004, spec 023): paidAt attribute mapping on Order")
class OrderPaidAtTest {

    @Test
    @DisplayName("paidAt defaults to null and can be assigned and retrieved")
    void paidAtGetterAndSetter() {
        Order order = new Order();
        assertThat(order.getPaidAt()).isNull();

        Instant now = Instant.parse("2026-11-20T20:00:00Z");
        order.setPaidAt(now);

        assertThat(order.getPaidAt()).isEqualTo(now);
    }
}
