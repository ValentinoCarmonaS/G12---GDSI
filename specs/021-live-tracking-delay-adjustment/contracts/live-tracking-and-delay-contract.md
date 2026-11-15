# Contratos de API: seguimiento en vivo y demora granular (+5)

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Fecha**: 15/11/2026  
**Rama**: `021-live-tracking-delay-adjustment`  

---

## 1. Resumen de contratos

Este documento define los contratos de interfaz HTTP REST para la funcionalidad de seguimiento en vivo del comensal y el ajuste granular de demoras de cocina:

1. **Contrato 01**: Incremento atómico y acumulativo de demora en cocina (`POST /api/v1/kitchen/tickets/{ticketId}/add-delay`).
2. **Contrato 02**: Consulta adaptativa de seguimiento en vivo con métricas dinámicas (`GET /api/v1/orders/{orderId}/live-tracking`).

---

## 2. Contrato 01: Incremento granular de demora en cocina

### 2.1 Definición

- **Método y ruta**: `POST /api/v1/kitchen/tickets/{ticketId}/add-delay`
- **Componente responsable**: `KitchenKdsController` / `KdsQueueService`
- **Consumidores**: Botón táctil "+5" en la Columna 2 del KDS de cocina (`KitchenWorkspacePage.tsx` / `KitchenKanbanBoard.tsx`)
- **Seguridad**: Requiere autenticación con rol de cocina o administrador (`@PreAuthorize("hasAnyRole('CHEF', 'ADMINISTRADOR')")`).

### 2.2 Parámetros de petición

- **Path parameter**:
  - `ticketId` (`UUID`, obligatorio): Identificador unívoco del ticket KDS.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`
- **Request Body**: Vacío.

### 2.3 Respuestas

#### Caso 200 OK — Incremento exitoso (+5 min acumulados)

Retornado cuando la comanda activa incrementa exitosamente su demora específica en 5 minutos bajo bloqueo pesimista `PESSIMISTIC_WRITE`.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Date: Sun, 15 Nov 2026 17:00:00 GMT

{
  "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 104,
  "specificDelayMinutes": 10,
  "estimatedTotalMinutes": 30,
  "updatedAt": "2026-11-15T17:00:00.123456Z"
}
```

*Garantías operativas*:
- **Atomicidad y linealidad**: Cada pulsación suma de manera estricta y lineal 5 minutos a `specific_delay_minutes` sin límite superior artificial. Tres pulsaciones consecutivas consolidan exactamente $+15 \text{ min}$.
- **Aislamiento**: El incremento afecta exclusivamente al ticket intervenido; no muta demoras ni cronómetros de otros pedidos concurrentes.

#### Errores controlados (`ProblemDetail`)

- **404 Not Found — Ticket inexistente**:
  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "No se encontró la comanda con el identificador proporcionado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/add-delay"
  }
  ```
- **403 Forbidden — Ticket ajeno al establecimiento**:
  ```json
  {
    "type": "about:blank",
    "title": "Forbidden",
    "status": 403,
    "detail": "La comanda no pertenece al establecimiento del usuario autenticado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/add-delay"
  }
  ```
- **409 Conflict — Estado no apto para añadir demora**:
  Retornado si el ticket no se encuentra en estado `IN_PREPARATION` (por ejemplo, si aún está en `QUEUED` o ya pasó a `READY_FOR_PICKUP` o `COMPLETED`).
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "Solo es posible añadir demoras a comandas en estado de preparación activa.",
    "code": "INVALID_TICKET_STATE_FOR_DELAY",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/add-delay"
  }
  ```

---

## 3. Contrato 02: Consulta adaptativa de seguimiento en vivo del comensal

### 3.1 Definición

- **Método y ruta**: `GET /api/v1/orders/{orderId}/live-tracking`
- **Componente responsable**: `OrderConfirmationTrackingController` / `OrderLiveTrackingService`
- **Consumidores**: Hook de polling en la pantalla del comensal (`useLiveTrackingPolling` en `/seguimiento/{orderId}` y `/pedidos/{orderId}/seguimiento`)
- **Seguridad**: Requiere rol `COMENSAL` (`@PreAuthorize("hasRole('COMENSAL')")`) y ser titular de la orden.
- **Cadencia de consumo**: 5 a 8 segundos.

### 3.2 Parámetros de petición

- **Path parameter**:
  - `orderId` (`UUID`, obligatorio): Identificador de la orden.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`
  - `Cache-Control: no-cache`
  - `Pragma: no-cache`

### 3.3 Respuestas

#### Caso 200 OK — Estado 1: Orden en cola en cocina (`QUEUED`)

La orden aguarda en la cola de cocina. Exhibe `#ticketNumber`, insignia "En cola en cocina" y posición $N$. El temporizador y la barra de progreso están estrictamente ausentes.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Date: Sun, 15 Nov 2026 17:05:00 GMT

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 104,
  "status": "CONFIRMED",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 0,
  "isReadyForPickup": false,
  "message": "Tu comanda está en cola de espera en la cocina.",
  "serverTime": "2026-11-15T17:05:00.000Z",
  "positionAhead": 3,
  "estimatedTotalMinutes": null,
  "elapsedMinutes": null,
  "remainingMinutes": null,
  "progressPercent": null,
  "specificDelayMinutes": 0,
  "securityCode": null,
  "startedAt": null,
  "readyAt": null,
  "items": [
    {
      "dishName": "Hamburguesa Completa con Papas",
      "quantity": 2
    },
    {
      "dishName": "Limonada con Menta",
      "quantity": 1
    }
  ]
}
```

*Subcaso $N = 0$ (Al tope de la cola)*:
Si no hay pedidos pendientes previos, `positionAhead = 0` y el cliente renderiza literalmente: `"Tenés 0 pedidos por delante"`.

---

#### Caso 200 OK — Estado 2: Orden cocinándose en preparación activa (`IN_PREPARATION`)

El chef pulsó "EMPEZAR". La leyenda de pedidos por delante desaparece, la insignia conmuta a "Cocinando / En preparación" con icono dinámico, y se activan el minutero (con piso 1 min) y la barra (con techo 92%).

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Date: Sun, 15 Nov 2026 17:10:00 GMT

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 104,
  "status": "IN_PROGRESS",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": true,
  "estimatedMinutesRemaining": 17,
  "progressPercentage": 38,
  "isReadyForPickup": false,
  "message": "Preparando tu pedido en los fuegos.",
  "serverTime": "2026-11-15T17:10:00.000Z",
  "positionAhead": null,
  "estimatedTotalMinutes": 25,
  "elapsedMinutes": 8,
  "remainingMinutes": 17,
  "progressPercent": 38,
  "specificDelayMinutes": 5,
  "securityCode": null,
  "startedAt": "2026-11-15T17:02:00.000Z",
  "readyAt": null,
  "items": [
    {
      "dishName": "Hamburguesa Completa con Papas",
      "quantity": 2
    },
    {
      "dishName": "Limonada con Menta",
      "quantity": 1
    }
  ]
}
```

*Cálculo verificado*:
- `startedAt` hace 8 minutos $\to$ `elapsedMinutes = 8`.
- Demora específica $+5$ min $\to$ `estimatedTotalMinutes = 20 + 5 = 25`.
- Minutos restantes: $25 - 8 = 17$ min.
- Si `elapsedMinutes >= estimatedTotalMinutes`, `remainingMinutes` se detiene en `1` (piso) y `progressPercent` en `92` (techo).

---

#### Caso 200 OK — Estado 3: Orden lista para entrega (`READY_FOR_PICKUP`)

El chef pulsó "LISTO". El temporizador, la barra y los minutos de demora se purgan de la vista; la pantalla conmuta al panel verde de retiro con el código numérico de seguridad de 4 dígitos.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Date: Sun, 15 Nov 2026 17:25:00 GMT

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 104,
  "status": "READY_FOR_PICKUP",
  "fulfillmentMode": "IMMEDIATE",
  "isCountdownActive": false,
  "estimatedMinutesRemaining": null,
  "progressPercentage": 100,
  "isReadyForPickup": true,
  "message": "¡Tu pedido está listo para retirar!",
  "serverTime": "2026-11-15T17:25:00.000Z",
  "positionAhead": null,
  "estimatedTotalMinutes": null,
  "elapsedMinutes": null,
  "remainingMinutes": null,
  "progressPercent": null,
  "specificDelayMinutes": null,
  "securityCode": "4821",
  "startedAt": "2026-11-15T17:02:00.000Z",
  "readyAt": "2026-11-15T17:25:00.000Z",
  "items": [
    {
      "dishName": "Hamburguesa Completa con Papas",
      "quantity": 2
    },
    {
      "dishName": "Limonada con Menta",
      "quantity": 1
    }
  ]
}
```

*Invariantes de retiro*:
- Minutero, barra y demora purgados de la visualización (`null`).
- Código numérico de seguridad de 4 dígitos disponible para recitado verbal en mostrador.
- Resumen de platos y `#ticketNumber` conservados.

---

#### Errores controlados (`ProblemDetail`)

- **404 Not Found**: Orden inexistente o ID inválido.
- **403 Forbidden**: Intento de consulta de una orden perteneciente a otro comensal.
