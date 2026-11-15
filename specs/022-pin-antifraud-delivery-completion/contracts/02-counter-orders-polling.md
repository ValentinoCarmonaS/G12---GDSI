# Contrato de API: consulta y sincronización de mostrador (`counter-orders`)

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `GET /api/v1/counter/orders`  
**Seguridad**: `@PreAuthorize("hasAnyRole('ROLE_ENCARGADO', 'ENCARGADO', 'ROLE_ADMIN', 'ADMINISTRADOR')")`  

---

## 1. Descripción operativa

Endpoint consumido periódicamente por la pantalla de mostrador (`/mostrador`) cada 3 a 5 segundos para mantener sincronizada la lista de pedidos listos para retiro.

**Regla de exclusión terminal**: Este endpoint filtra de manera estricta `status = 'READY_FOR_PICKUP'`. Tan pronto como una orden es completada mediante `POST complete-delivery`, su estado pasa a `COMPLETED` y deja de ser incluida en la respuesta del sondeo subsecuente, garantizando su remoción definitiva del tablero operativo.

---

## 2. Petición HTTP

- **Método**: `GET`
- **Ruta**: `/api/v1/counter/orders`
- **Parámetros de consulta**:
  - `establishmentId` (UUID, opcional si el usuario cuenta con `tenantId` en su token JWT).
  - `pickupDate` (DATE, opcional; por defecto toma la fecha del día actual `hoy` en la zona horaria del establecimiento `America/Argentina/Buenos_Aires`).
- **Cabeceras**:
  - `Authorization: Bearer <JWT_ENCARGADO>`
  - `Cache-Control: no-cache, no-store, must-revalidate`

---

## 3. Respuesta HTTP `200 OK`

Retorna un array JSON con los pedidos listos ordenados de manera determinista: mayor tiempo de espera en mostrador primero (`ready_at ASC`), desempatando por número de ticket correlativo (`ticket_number ASC`).

```json
[
  {
    "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "ticketId": "9f8e7d6c-5b4a-3210-fedc-ba9876543210",
    "ticketNumber": 42,
    "status": "READY_FOR_PICKUP",
    "securityCode": 4821,
    "timeSlot": {
      "start": "13:00",
      "end": "13:15"
    },
    "readyAt": "2026-11-15T18:15:00Z",
    "waitMinutes": 15,
    "customer": {
      "fullName": "Valentino Carmona",
      "avatarUrl": "https://easyeat.com/api/v1/profile/users/user-123/avatar"
    },
    "items": [
      {
        "dishId": "a1b2c3d4-0000-0000-0000-000000000001",
        "dishName": "Milanesa Napolitana con Papas Fritas",
        "quantity": 2
      },
      {
        "dishId": "a1b2c3d4-0000-0000-0000-000000000002",
        "dishName": "Agua Mineral 500ml",
        "quantity": 1
      }
    ]
  }
]
```

---

## 4. Invariantes del contrato

1. **Agnosticismo monetario estricto**: No expone `unitPrice`, `subtotal`, `totalAmount` ni ningún dato financiero.
2. **Exclusión de órdenes completadas**: Ninguna orden en estado `COMPLETED`, `IN_PROGRESS`, `QUEUED`, `CANCELLED` o `EXPIRED` es retornada en este listado.
3. **Manejo de PIN nulo (Caso Borde 10)**: Si una orden preexistente no cuenta con `security_code`, el campo `securityCode` se devuelve como `null`, lo que activa el renderizado neutral `- - - -` en la interfaz sin impedir la entrega física.
4. **Respuesta vacía estable**: Cuando no hay órdenes listas, devuelve `[]` con código `200 OK`.
