# Contratos de API: despacho a mostrador y sincronización de pedidos listos

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Fecha**: 15/11/2026  
**Rama**: `020-counter-dispatch-ready-orders`  

---

## 1. Resumen de contratos

Este documento especifica los contratos HTTP REST para la interacción entre cocina, mostrador y la gestión de pedidos listos:
1. **Contrato 01**: Finalización atómica e idempotente de comanda en cocina (`POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`).
2. **Contrato 02**: Consulta reactiva de cola de pedidos listos para entrega en mostrador (`GET /api/v1/counter/orders`).
3. **Contrato 03**: Consulta de avatar de comensal para visualización en mostrador (`GET /api/v1/profile/users/{userId}/avatar`).

---

## 2. Contrato 01: Finalización de comanda en cocina

### 2.1 Definición

- **Método y ruta**: `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup`
- **Componente responsable**: `KitchenTicketDispatchController` / `OrderLiveTrackingService`
- **Consumidores**: Botón "LISTO" de la Columna 2 en el KDS de cocina (`KitchenWorkspacePage.tsx` / `KitchenKanbanBoard.tsx`)
- **Seguridad**: Requiere autenticación con rol de cocina o sala (`@PreAuthorize("hasAnyRole('CHEF', 'ENCARGADO', 'ADMINISTRADOR')")`).

### 2.2 Parámetros de petición

- **Path parameter**:
  - `ticketId` (`UUID`, obligatorio): Identificador único del ticket KDS.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`
- **Request Body**: Vacío.

### 2.3 Respuestas

#### Caso 200 OK — Transición exitosa o reintento idempotente

Retornado cuando la comanda pasa a `READY_FOR_PICKUP` (junto con la orden asociada), o cuando ya se encontraba en dicho estado (reintento idempotente).

```http
HTTP/1.1 200 OK
Content-Type: application/json
Date: Sun, 15 Nov 2026 16:30:00 GMT

{
  "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 104,
  "status": "READY_FOR_PICKUP",
  "readyAt": "2026-11-15T19:30:00.123456Z"
}
```

*Garantías transaccionales*:
- **Atomicidad**: `KdsTicket` y `Order` mutan en una misma transacción relacional bajo bloqueo pesimista `PESSIMISTIC_WRITE`.
- **Coincidencia temporal inmutable**: `kds_tickets.ready_at` y `orders.ready_at` registran exactamente el mismo instante `readyAt`.
- **Idempotencia**: Si el ticket ya estaba en `READY_FOR_PICKUP`, se devuelve el valor `readyAt` original sin modificar timestamps ni duplicar registros.

#### Caso 404 / 403 / 409 — Errores controlados (`ProblemDetail`)

- **404 Not Found — Ticket inexistente**:
  ```json
  {
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "No se encontró la comanda con el identificador proporcionado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/ready-for-pickup"
  }
  ```
- **403 Forbidden — Ticket de otro establecimiento**:
  ```json
  {
    "type": "about:blank",
    "title": "Forbidden",
    "status": 403,
    "detail": "La comanda no pertenece al establecimiento del usuario autenticado.",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/ready-for-pickup"
  }
  ```
- **409 Conflict — Estado terminal**:
  Retornado si el ticket ya se encuentra en estado `COMPLETED` (entregado y archivado).
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "La orden ya ha sido completada y entregada al cliente.",
    "code": "ORDER_ALREADY_COMPLETED",
    "instance": "/api/v1/kitchen/tickets/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/ready-for-pickup"
  }
  ```

---

## 3. Contrato 02: Consulta de pedidos listos para mostrador

### 3.1 Definición

- **Método y ruta**: `GET /api/v1/counter/orders`
- **Componente responsable**: `CounterOrderController` / `KdsTicketRepository`
- **Consumidores**: Hook de polling en la pantalla de mostrador (`useCounterOrdersPolling` en `CounterWorkspacePage.tsx`)
- **Seguridad**: Requiere rol `ENCARGADO` o `ADMINISTRADOR` (`@PreAuthorize("hasAnyRole('ENCARGADO', 'ADMINISTRADOR')")`).

### 3.2 Parámetros de petición

- **Query parameters**:
  - `status` (`string`, opcional, por defecto: `READY_FOR_PICKUP`): Estado filtrado de las comandas.
  - `establishmentId` (`UUID`, opcional): Si se omite, se infiere del `tenantId` del usuario autenticado.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`
  - `Cache-Control: no-cache`
  - `Pragma: no-cache`

### 3.3 Respuestas

#### Caso 200 OK — Lista ordenada determinista de pedidos listos

Retorna la lista de pedidos en estado `READY_FOR_PICKUP` de la jornada actual (`pickupDate = hoy`), ordenados estrictamente por `readyAt ASC` (mayor tiempo de espera primero) y desempatados por `ticketNumber ASC`.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-cache, no-store, must-revalidate
Date: Sun, 15 Nov 2026 16:35:00 GMT

[
  {
    "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "ticketNumber": 104,
    "timeSlot": {
      "start": "13:00",
      "end": "13:15"
    },
    "readyAt": "2026-11-15T19:30:00Z",
    "customer": {
      "userId": "c71e2289-4b68-45e0-a7d1-e6e7d6928e01",
      "fullName": "Valentino Carmona",
      "photoUrl": "/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar"
    },
    "securityCode": "4821",
    "items": [
      {
        "quantity": 2,
        "dishName": "Hamburguesa Completa con Papas"
      },
      {
        "quantity": 1,
        "dishName": "Limonada con Menta y Jengibre"
      }
    ]
  },
  {
    "ticketId": "a15c329b-81d3-4e89-b620-7f2cb14197e2",
    "orderId": "50e932b1-12c8-4ec7-9104-58a47de0281b",
    "ticketNumber": 105,
    "timeSlot": {
      "start": "13:15",
      "end": "13:30"
    },
    "readyAt": "2026-11-15T19:33:45Z",
    "customer": {
      "userId": "d98f1214-9271-4899-8ab3-772c21980a34",
      "fullName": "Lucía Morales",
      "photoUrl": null
    },
    "securityCode": "9102",
    "items": [
      {
        "quantity": 1,
        "dishName": "Ensalada Caesar con Pollo"
      }
    ]
  }
]
```

*Invariantes del contrato*:
1. **Ausencia absoluta de datos monetarios**: No se transmiten `unitPrice`, `subtotal`, `totalAmount` ni símbolos de moneda (`RF-013`).
2. **Formato de comensal**: Si el usuario no tiene avatar en `user_photos`, `photoUrl` es `null`, permitiendo a la UI ejecutar omisión limpia sin contenedores residuales (`RF-012`, `AC-2`).
3. **Formato de items**: Lista unificada con cantidad entera y nombre del plato.

---

## 4. Contrato 03: Consulta de avatar de comensal para mostrador

### 4.1 Definición

- **Método y ruta**: `GET /api/v1/profile/users/{userId}/avatar`
- **Componente responsable**: `ProfileAvatarController` / `UserPhotoService`
- **Consumidores**: Elemento `<img>` en la tarjeta desplegada de mostrador (`CounterTicketCard.tsx`)
- **Seguridad**: Requiere rol de personal autorizado (`@PreAuthorize("hasAnyRole('ENCARGADO', 'CHEF', 'ADMINISTRADOR')")`).

### 4.2 Parámetros de petición

- **Path parameter**:
  - `userId` (`UUID`, obligatorio): Identificador del comensal.
- **Request Headers**:
  - `Authorization: Bearer <jwt-token>`

### 4.3 Respuestas

#### Caso 200 OK — Imagen binaria transmitida

```http
HTTP/1.1 200 OK
Content-Type: image/jpeg
Cache-Control: private, max-age=3600
Content-Length: 45280

<binary byte stream>
```

#### Caso 404 Not Found — Usuario sin fotografía

```json
{
  "type": "https://easyeat.com/errors/avatar-not-found",
  "title": "Avatar Not Found",
  "status": 404,
  "detail": "No custom avatar configured for user"
}
```
