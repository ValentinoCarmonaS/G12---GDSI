package com.easyeat.order.dto;

import java.util.UUID;

/**
 * Representación de un plato o ítem individual dentro de una comanda activa del KDS.
 */
public record KdsOrderItemResponse(
        UUID dishId,
        String dishName,
        Integer quantity
) {}
