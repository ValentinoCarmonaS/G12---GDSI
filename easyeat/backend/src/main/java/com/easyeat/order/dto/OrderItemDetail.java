package com.easyeat.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDetail(
        UUID id,
        UUID dishId,
        String dishName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
}
