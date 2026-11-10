package com.easyeat.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta a la confirmación exitosa de un pedido bonificado al 100% con monto cero.
 */
public record ZeroAmountOrderResponse(
        UUID orderId,
        String orderStatus,
        BigDecimal totalAmount,
        Boolean gatewayBypassed,
        Boolean stockDebited,
        KdsTicketSummaryDto kdsTicket,
        String message
) {
}
