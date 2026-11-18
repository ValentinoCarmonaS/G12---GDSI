package com.easyeat.order.domain;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLifecycleSupportTest {

    @Test
    @DisplayName("Should map readyAt and securityCode JPA columns and accessors on Order (T005)")
    void shouldMapReadyAtAndSecurityCodeFieldsOnOrder() throws Exception {
        Order order = new Order();
        assertThat(order.getReadyAt()).isNull();
        assertThat(order.getSecurityCode()).isNull();

        Instant readyAt = Instant.parse("2026-11-15T19:30:00Z");
        order.setReadyAt(readyAt);
        order.assignSecurityCodeIfAbsent("4821");

        assertThat(order.getReadyAt()).isEqualTo(readyAt);
        assertThat(order.getSecurityCode()).isEqualTo("4821");

        Field readyAtField = Order.class.getDeclaredField("readyAt");
        Column readyAtColumn = readyAtField.getAnnotation(Column.class);
        assertThat(readyAtColumn).isNotNull();
        assertThat(readyAtColumn.name()).isEqualTo("ready_at");

        Field securityCodeField = Order.class.getDeclaredField("securityCode");
        Column securityCodeColumn = securityCodeField.getAnnotation(Column.class);
        assertThat(securityCodeColumn).isNotNull();
        assertThat(securityCodeColumn.name()).isEqualTo("security_code");
        assertThat(securityCodeColumn.length()).isEqualTo(4);
    }

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
