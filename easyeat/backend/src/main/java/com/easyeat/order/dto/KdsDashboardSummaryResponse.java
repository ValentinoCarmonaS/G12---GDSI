package com.easyeat.order.dto;

/**
 * Resumen de métricas en tiempo real para las 4 tarjetas operativas del Kitchen Display System (KDS).
 */
public record KdsDashboardSummaryResponse(
        long queuedOrdersCount,
        long inPreparationOrdersCount,
        long readyForPickupOrdersCount,
        long completedTodayCount
) {
}
