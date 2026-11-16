package com.easyeat.order.dto;

import com.easyeat.order.domain.KdsTicketStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para la transición de comanda al iniciar preparación activa.
 */
public record KdsStartPreparationResponse(
        UUID ticketId,
        Integer ticketNumber,
        UUID orderId,
        KdsTicketStatus status,
        Instant startedAt
) {}
