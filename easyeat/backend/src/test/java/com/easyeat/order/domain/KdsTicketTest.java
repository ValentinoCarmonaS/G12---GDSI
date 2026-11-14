package com.easyeat.order.domain;

import com.easyeat.catalog.domain.Establishment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KdsTicketTest {

    @Test
    @DisplayName("Should initialize KdsTicket with default QUEUED status and current queuedAt")
    void shouldInitializeWithDefaults() {
        UUID orderId = UUID.randomUUID();
        Establishment establishment = new Establishment();
        establishment.setId(UUID.randomUUID());

        KdsTicket ticket = new KdsTicket(
                null,
                orderId,
                establishment,
                101,
                null,
                FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(12, 0),
                LocalTime.of(12, 30),
                null
        );

        ticket.onCreate();

        assertThat(ticket.getOrderId()).isEqualTo(orderId);
        assertThat(ticket.getEstablishment()).isEqualTo(establishment);
        assertThat(ticket.getTicketNumber()).isEqualTo(101);
        assertThat(ticket.getStatus()).isEqualTo(KdsTicketStatus.QUEUED);
        assertThat(ticket.getFulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);
        assertThat(ticket.getQueuedAt()).isNotNull();
        assertThat(ticket.getStartedAt()).isNull();
        assertThat(ticket.getReadyAt()).isNull();
        assertThat(ticket.getCompletedAt()).isNull();
    }
}
