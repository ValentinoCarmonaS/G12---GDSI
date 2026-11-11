package com.easyeat.order.service;

import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.Order;

public interface KdsQueueService {

    /**
     * Encola de forma atómica e idempotente la orden confirmada en la cola del KDS.
     *
     * @param order Orden confirmada en estado IN_PROGRESS.
     * @return Comanda encolada generada o existente.
     */
    KdsTicket dispatchOrderToKitchen(Order order);
}
