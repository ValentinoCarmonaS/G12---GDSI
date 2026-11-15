# Contrato de API: seguimiento en vivo del comensal (`live-tracking`)

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `GET /api/v1/orders/{orderId}/live-tracking`  
**Seguridad**: `@PreAuthorize("hasAnyRole('ROLE_COMENSAL', 'COMENSAL')")` (Verifica que `order.userId == authenticatedUserId`)  

---

## 1. Descripción operativa

Endpoint consumido mediante polling adaptativo por la pantalla de seguimiento del cliente (`/seguimiento/{orderId}`).

Bajo la especificación 022, este contrato formaliza la bifurcación entre el estado `READY_FOR_PICKUP` (donde se expone el PIN para cotejo oral en ventanilla) y el nuevo estado terminal `COMPLETED` (donde se oculta totalmente el PIN y cesa el sondeo de red).

---

## 2. Petición HTTP

- **Método**: `GET`
- **Ruta**: `/api/v1/orders/{orderId}/live-tracking`
- **Parámetros de ruta**:
  - `orderId` (UUID, obligatorio): Identificador de la orden a consultar.
- **Cabeceras obligatorias**:
  - `Authorization: Bearer <JWT_COMENSAL>`
  - `Cache-Control: no-cache, no-store, must-revalidate`

---

## 3. Respuestas HTTP

### 3.1. Estado `READY_FOR_PICKUP`: Pedido listo para retiro (Con PIN de 4 dígitos)

Retornado cuando la cocina finalizó la bandeja y la comida aguarda en mostrador.

```json
{
  "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "ticketNumber": 42,
  "status": "READY_FOR_PICKUP",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 100,
  "isReadyForPickup": true,
  "securityCode": 4821,
  "kdsQueueSaturation": null,
  "message": "¡Tu pedido está listo! Por favor, acercate al mostrador para retirar tu comida.",
  "serverTime": "2026-11-15T18:15:30Z"
}
```

### 3.2. Estado terminal `COMPLETED`: Pedido entregado (Ocultamiento total de PIN)

Retornado cuando el personal de mostrador confirmó la entrega física mediante `complete-delivery`.

**Invariante de seguridad**: El campo `securityCode` se devuelve como `null` (o se omite del JSON), purgado en origen a nivel del servidor para garantizar que el PIN no pueda ser recuperado ni inspeccionado tras la entrega.

```json
{
  "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "ticketNumber": 42,
  "status": "COMPLETED",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 100,
  "isReadyForPickup": false,
  "securityCode": null,
  "kdsQueueSaturation": null,
  "message": "Pedido Entregado — ¡Que disfrutes tu comida!",
  "serverTime": "2026-11-15T18:30:05Z"
}
```

---

## 4. Comportamiento del cliente frontend

1. **Recepción de `READY_FOR_PICKUP`**:
   - Renderiza el callout destacado de pedido listo.
   - Renderiza el número de pedido `#ticketNumber`.
   - Renderiza el código numérico de seguridad formateado monospace con espaciado amplio: `4 8 2 1`.
   - Continúa el ciclo de polling a cadencia estándar de 5 segundos.
2. **Recepción de `COMPLETED`**:
   - Conmuta la vista al estado terminal permanente con el mensaje `"Pedido Entregado — ¡Que disfrutes tu comida!"`.
   - Purga y oculta de inmediato el código numérico de seguridad.
   - Muestra el resumen íntegro de compra obtenido desde `GET /api/v1/orders/{orderId}/confirmation-tracking` (`items`, cantidades, importes, totales).
   - **Detiene de forma definitiva el polling**: el hook `useOrderLiveTrackingPolling` desactiva el timer y aborta cualquier petición posterior.
