package com.easyeat.order.dto;

import com.easyeat.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record KdsTicketResponse(
        UUID ticketId,
        UUID orderId,
        Integer ticketNumber,
        OrderStatus status,
        Instant readyAt
) {}
