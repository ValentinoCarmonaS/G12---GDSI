package com.easyeat.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CounterReadyOrderResponse(
        UUID ticketId,
        UUID orderId,
        Integer ticketNumber,
        TimeSlotDto timeSlot,
        Instant readyAt,
        CustomerSummaryDto customer,
        String securityCode,
        List<CounterOrderItemDto> items
) {
    public record TimeSlotDto(
            @JsonFormat(pattern = "HH:mm")
            LocalTime start,
            @JsonFormat(pattern = "HH:mm")
            LocalTime end
    ) {}

    public record CustomerSummaryDto(
            UUID userId,
            String fullName,
            String photoUrl
    ) {}

    public record CounterOrderItemDto(
            Integer quantity,
            String dishName
    ) {}
}
