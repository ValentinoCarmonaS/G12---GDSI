package com.easyeat.order.service;

import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.Order;
import com.easyeat.order.dto.KdsDelayIncrementResponse;
import com.easyeat.order.dto.KdsStartPreparationResponse;
import java.util.UUID;

public interface KdsQueueService {

    /**
     * Encola de forma atómica e idempotente la orden confirmada en la cola del KDS.
     *
     * @param order Orden confirmada en estado IN_PROGRESS.
     * @return Comanda encolada generada o existente.
     */
    KdsTicket dispatchOrderToKitchen(Order order);

    /**
     * Transiciona de forma atómica e idempotente el ticket de comanda al estado IN_PREPARATION,
     * fijando startedAt con el timestamp actual si aún estaba en QUEUED, y asegurando que la Order
     * asociada se encuentre en estado IN_PROGRESS.
     *
     * @param ticketId Identificador único del ticket KDS.
     * @param establishmentId Identificador del establecimiento del chef autenticado.
     * @return DTO con datos de la comanda en preparación.
     */
    KdsStartPreparationResponse startTicketPreparation(UUID ticketId, UUID establishmentId);

    /**
     * Incrementa atómica y linealmente en 5 minutos la demora específica de una comanda activa
     * bajo bloqueo pesimista PESSIMISTIC_WRITE.
     *
     * @param ticketId Identificador único del ticket KDS.
     * @param establishmentId Identificador del establecimiento del chef autenticado.
     * @return DTO con la demora acumulada consolidada y el tiempo estimado actualizado.
     */
    KdsDelayIncrementResponse addTicketDelay(UUID ticketId, UUID establishmentId);
}
