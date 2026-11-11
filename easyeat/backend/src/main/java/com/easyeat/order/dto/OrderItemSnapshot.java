package com.easyeat.order.dto;

import java.util.UUID;

public record OrderItemSnapshot(
        UUID dishId,
        String dishName,
        Integer quantity
) {
}
