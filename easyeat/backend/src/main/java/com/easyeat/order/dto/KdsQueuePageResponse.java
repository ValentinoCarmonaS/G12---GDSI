package com.easyeat.order.dto;

import java.util.List;
import java.util.Map;

/**
 * Wrapper de respuesta paginada para la cola de cocina del KDS.
 * Preserva compatibilidad exacta con el contrato del cliente frontend.
 */
public record KdsQueuePageResponse(
        List<KdsActiveQueueTicketResponse> content,
        Map<String, Object> pageable,
        long totalElements,
        int totalPages
) {}
