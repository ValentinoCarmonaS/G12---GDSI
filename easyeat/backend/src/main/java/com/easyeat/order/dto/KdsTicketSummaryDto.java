package com.easyeat.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTO para resúmenes de comandas en la cola y tablero del KDS.
 */
public record KdsTicketSummaryDto(
        UUID ticketId,
        Integer ticketNumber,
        String status,
        java.time.Instant queuedAt,
        EstimatedPickup estimatedPickup
) {
    public record EstimatedPickup(
            LocalDate date,
            LocalTime slotStart,
            LocalTime slotEnd
    ) {}
}
