package com.easyeat.inventory.service;

import com.easyeat.inventory.dto.StockDeductionResult;
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
import com.easyeat.order.dto.OrderItemSnapshot;

import java.util.List;
import java.util.UUID;

public interface StockDeductionService {

    /**
     * Deducts recipe stock atomically for a confirmed order.
     *
     * @param orderId    Unique order id
     * @param tenantId   Establishment id
     * @param orderItems List of dishes and requested quantities
     * @return Result of the atomic debit
     * @throws InsufficientIngredientStockException if any ingredient lacks sufficient stock
     */
    StockDeductionResult deductRecipeStockForOrder(
            UUID orderId,
            UUID tenantId,
            List<OrderItemSnapshot> orderItems
    );
}
