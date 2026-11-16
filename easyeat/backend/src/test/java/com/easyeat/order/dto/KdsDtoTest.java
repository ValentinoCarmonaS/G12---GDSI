package com.easyeat.order.dto;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KDS DTO Contract & Serialization Tests (T001)")
class KdsDtoTest {

    @Test
    @DisplayName("KdsOrderItemResponse correctly instantiates and exposes fields")
    void testKdsOrderItemResponse() {
        UUID dishId = UUID.randomUUID();
        KdsOrderItemResponse item = new KdsOrderItemResponse(dishId, "Burger", 2);

        assertThat(item.dishId()).isEqualTo(dishId);
        assertThat(item.dishName()).isEqualTo("Burger");
        assertThat(item.quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("KdsActiveQueueTicketResponse correctly instantiates all fields including startedAt and items")
    void testKdsActiveQueueTicketResponse() {
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(12, 0);
        LocalTime end = LocalTime.of(12, 15);
        Instant queuedAt = Instant.parse("2026-11-16T15:00:00Z");
        Instant startedAt = Instant.parse("2026-11-16T15:05:00Z");
        List<KdsOrderItemResponse> items = List.of(new KdsOrderItemResponse(dishId, "Pizza", 1));

        KdsActiveQueueTicketResponse response = new KdsActiveQueueTicketResponse(
                ticketId,
                101,
                orderId,
                KdsTicketStatus.IN_PREPARATION,
                FulfillmentMode.IMMEDIATE,
                date,
                start,
                end,
                queuedAt,
                startedAt,
                items
        );

        assertThat(response.ticketId()).isEqualTo(ticketId);
        assertThat(response.ticketNumber()).isEqualTo(101);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(KdsTicketStatus.IN_PREPARATION);
        assertThat(response.fulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);
        assertThat(response.pickupDate()).isEqualTo(date);
        assertThat(response.timeSlotStart()).isEqualTo(start);
        assertThat(response.timeSlotEnd()).isEqualTo(end);
        assertThat(response.queuedAt()).isEqualTo(queuedAt);
        assertThat(response.startedAt()).isEqualTo(startedAt);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("KdsStartPreparationResponse correctly instantiates and exposes transition payload")
    void testKdsStartPreparationResponse() {
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-11-16T15:10:00Z");

        KdsStartPreparationResponse response = new KdsStartPreparationResponse(
                ticketId,
                105,
                orderId,
                KdsTicketStatus.IN_PREPARATION,
                startedAt
        );

        assertThat(response.ticketId()).isEqualTo(ticketId);
        assertThat(response.ticketNumber()).isEqualTo(105);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(KdsTicketStatus.IN_PREPARATION);
        assertThat(response.startedAt()).isEqualTo(startedAt);
    }
}
