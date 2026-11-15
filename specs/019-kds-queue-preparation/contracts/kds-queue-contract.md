# Contratos de API: KDS — cola de cocina y preparación activa

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Fecha**: 15/11/2026  
**Rama**: `019-kds-queue-preparation`

---

## 1. Resumen de contratos

Este documento especifica los contratos de interfaz HTTP REST para la operación del Kitchen Display System (KDS), cubriendo:
1. **Contrato 01**: Inicio atómico e idempotente de preparación (`POST /api/v1/kitchen/tickets/{ticketId}/start-preparation`).
2. **Contrato 02**: Consulta determinista de la cola activa de comandas (`GET /api/v1/kitchen/orders`).

---

## 2. Contrato 01: Inicio de preparación de comanda

### 2.1 Definición

- **Método y ruta**: `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation`
- **Componente responsable**: `KitchenKdsController` / `KdsQueueService`
- **Consumidores**: Botón "EMPEZAR" de la Columna 1 en `KitchenWorkspacePage.tsx`
- **Seguridad**: Requiere autenticación exclusiva con rol `CHEF` (`@PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF')")`).

### 2.2 Parámetros de petición

- **Path parameter**:
  - `ticketId` (`UUID`, obligatorio): Identificador único del ticket KDS.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`
- **Request Body**: Vacío.

### 2.3 Respuestas

#### Caso 200 OK — Inicio exitoso o reintento idempotente

Retornado cuando el ticket pasa de `QUEUED` a `IN_PREPARATION`, o cuando ya se encontraba en `IN_PREPARATION` (reintento idempotente).

```http
HTTP/1.1 200 OK
Content-Type: application/json
Date: Sun, 15 Nov 2026 15:30:00 GMT

{
  "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "ticketNumber": 104,
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "IN_PREPARATION",
  "startedAt": "2026-11-15T18:30:00Z"
}
```

*Garantía de idempotencia*: Si el ticket ya estaba en `IN_PREPARATION`, el valor de `startedAt` preserva exactamente el timestamp fijado en la primera ejecución. No se genera una nueva tarjeta ni se altera el cronómetro de preparación.

#### Caso 400 / 404 / 409 — Errores controlados (`ProblemDetail`)

- **404 Not Found — Ticket inexistente**:
  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "No se encontró el ticket de cocina con el identificador proporcionado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/start-preparation"
  }
  ```
- **403 Forbidden — Ticket de otro establecimiento**:
  ```json
  {
    "type": "about:blank",
    "title": "Forbidden",
    "status": 403,
    "detail": "El ticket no pertenece al establecimiento del usuario autenticado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/start-preparation"
  }
  ```
- **409 Conflict — Estado inválido**:
  Retornado si el ticket ya se encuentra en `READY_FOR_PICKUP` o `COMPLETED`.
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "La comanda ya ha finalizado su etapa de preparación activa.",
    "code": "INVALID_TICKET_STATE",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/start-preparation"
  }
  ```

---

## 3. Contrato 02: Consulta determinista de la cola activa de comandas

### 3.1 Definición

- **Método y ruta**: `GET /api/v1/kitchen/orders`
- **Componente responsable**: `KitchenKdsController` / `KdsTicketRepository`
- **Consumidores**: Hook de polling `useKdsQueuePolling` en `KitchenWorkspacePage.tsx`
- **Seguridad**: Requiere autenticación exclusiva con rol `CHEF` (`@PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF')")`).

### 3.2 Parámetros de consulta (Query params)

| Parámetro | Tipo | Obligatorio | Default | Descripción |
| --- | --- | --- | --- | --- |
| `activeQueue` | `boolean` | No | `false` | Cuando es `true`, activa la vista Kanban completa: retorna todas las comandas en estado `QUEUED` e `IN_PREPARATION` con `pickup_date <= hoy`, ordenadas determinísticamente sin truncamiento por página. |
| `status` | `KdsTicketStatus` | No | `null` | Si se provee (y `activeQueue` es falso), filtra por un estado particular (`QUEUED`, `IN_PREPARATION`, etc.). |
| `establishmentId` | `UUID` | No | Inferred | Identificador del restaurante; si se omite, se infiere del tenant del usuario autenticado. |
| `page` | `int` | No | `0` | Número de página (para vista administrativa/paginada). |
| `size` | `int` | No | `20` (o `100` si `activeQueue=true`) | Tamaño de página. |

### 3.3 Regla de ordenamiento y filtrado de cola activa (`activeQueue=true`)

1. **Filtro de horizonte temporal**: `pickup_date <= hoy` (en la zona horaria `America/Argentina/Buenos_Aires`). Se excluyen automáticamente comandas con `pickup_date > hoy`.
2. **Criterio de prelación determinista**:
   ```sql
   ORDER BY pickup_date ASC, time_slot_start ASC, queued_at ASC, id ASC
   ```
   - Primer nivel: `pickup_date ASC` (turnos vencidos de días anteriores van primero).
   - Segundo nivel: `time_slot_start ASC` (turnos vencidos de hoy van antes que el turno actual; turnos futuros de hoy van abajo accesibles por scroll).
   - Tercer nivel: `queued_at ASC` (FIFO unificado entre pedidos `IMMEDIATE` y `SCHEDULED` dentro del mismo bloque de 15 minutos).
   - Cuarto nivel: `id ASC` (desempate estricto para garantizar orden total).

### 3.4 Respuesta exitosa (`HTTP 200 OK`)

```http
HTTP/1.1 200 OK
Content-Type: application/json
Date: Sun, 15 Nov 2026 15:30:00 GMT

{
  "content": [
    {
      "ticketId": "a1111111-1111-1111-1111-111111111111",
      "ticketNumber": 101,
      "orderId": "01111111-1111-1111-1111-111111111111",
      "status": "IN_PREPARATION",
      "fulfillmentMode": "IMMEDIATE",
      "pickupDate": "2026-11-15",
      "timeSlotStart": "15:00:00",
      "timeSlotEnd": "15:15:00",
      "queuedAt": "2026-11-15T18:02:10Z",
      "startedAt": "2026-11-15T18:14:00Z",
      "items": [
        {
          "dishId": "d1111111-1111-1111-1111-111111111111",
          "dishName": "Hamburguesa Clásica",
          "quantity": 2
        },
        {
          "dishId": "d2222222-2222-2222-2222-222222222222",
          "dishName": "Papas Rústicas",
          "quantity": 1
        }
      ]
    },
    {
      "ticketId": "b2222222-2222-2222-2222-222222222222",
      "ticketNumber": 102,
      "orderId": "02222222-2222-2222-2222-222222222222",
      "status": "QUEUED",
      "fulfillmentMode": "SCHEDULED",
      "pickupDate": "2026-11-15",
      "timeSlotStart": "15:15:00",
      "timeSlotEnd": "15:30:00",
      "queuedAt": "2026-11-15T14:30:00Z",
      "startedAt": null,
      "items": [
        {
          "dishId": "d3333333-3333-3333-3333-333333333333",
          "dishName": "Ensalada César",
          "quantity": 1
        }
      ]
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 100
  },
  "totalElements": 2,
  "totalPages": 1
}
```

---

## 4. Invariantes de los contratos

1. **Inclusión obligatoria de `startedAt`**: El backend debe devolver el campo `startedAt` (`string | null` en formato ISO-8601 UTC) en todo DTO de comanda.
2. **Ausencia de notas en el contrato**: El array `items` expone únicamente `dishId`, `dishName` y `quantity`. No se transportan campos de notas libres, textos nulos ni marcadores vacíos.
3. **Sincronización de reloj con cabecera `Date`**: Los clientes frontend utilizan la cabecera estándar HTTP `Date` enviada por el servidor para calcular el desfasaje de reloj local y derivar el Count-Up sin skew.
