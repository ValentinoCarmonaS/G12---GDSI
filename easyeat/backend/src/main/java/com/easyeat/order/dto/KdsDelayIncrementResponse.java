package com.easyeat.order.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para la mutación de incremento de demora granular en cocina (Spec 021).
 */
public record KdsDelayIncrementResponse(
        UUID ticketId,
        UUID orderId,
        Integer ticketNumber,
        int specificDelayMinutes,
        int estimatedTotalMinutes,
        Instant updatedAt
) {}
