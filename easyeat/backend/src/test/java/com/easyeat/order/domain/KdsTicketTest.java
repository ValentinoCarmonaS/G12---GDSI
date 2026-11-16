package com.easyeat.order.domain;

import com.easyeat.catalog.domain.Establishment;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KdsTicketTest {

    @Test
    @DisplayName("Should declare composite index idx_kds_tickets_counter_ready on KdsTicket @Table (T005)")
    void shouldDeclareCounterReadyCompositeIndex() {
        Table tableAnnotation = KdsTicket.class.getAnnotation(Table.class);
        assertThat(tableAnnotation).isNotNull();

        assertThat(Arrays.asList(tableAnnotation.indexes()))
                .anySatisfy(index -> {
                    assertThat(index.name()).isEqualTo("idx_kds_tickets_counter_ready");
                    assertThat(index.columnList())
                            .isEqualTo("establishment_id, status, pickup_date, ready_at, ticket_number");
                });
    }

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
