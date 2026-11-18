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

> *Alineado el 16/11/2026 con la forma ya entregada por la spec 020 (`CounterReadyOrderResponse`, ver `specs/020-counter-dispatch-ready-orders/contracts/counter-dispatch-contract.md`). Esta spec no altera la forma de la respuesta; solo exige `securityCode: null` ante PIN ausente (invariante 3).*

```json
[
  {
    "ticketId": "9f8e7d6c-5b4a-3210-fedc-ba9876543210",
    "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "ticketNumber": 42,
    "timeSlot": {
      "start": "13:00",
      "end": "13:15"
    },
    "readyAt": "2026-11-15T18:15:00Z",
    "customer": {
      "userId": "c71e2289-4b68-45e0-a7d1-e6e7d6928e01",
      "fullName": "Valentino Carmona",
      "photoUrl": "/api/v1/profile/users/c71e2289-4b68-45e0-a7d1-e6e7d6928e01/avatar"
    },
    "securityCode": "4821",
    "items": [
      {
        "quantity": 2,
        "dishName": "Milanesa Napolitana con Papas Fritas"
      },
      {
        "quantity": 1,
        "dishName": "Agua Mineral 500ml"
      }
    ]
  }
]
```

El tiempo de espera en mostrador se calcula en el cliente a partir de `readyAt` y la hora del servidor (cabecera `Date`), según la spec 020.

---

## 4. Invariantes del contrato

1. **Agnosticismo monetario estricto**: No expone `unitPrice`, `subtotal`, `totalAmount` ni ningún dato financiero.
2. **Exclusión de órdenes completadas**: Ninguna orden en estado `COMPLETED`, `IN_PROGRESS`, `QUEUED`, `CANCELLED` o `EXPIRED` es retornada en este listado.
3. **Manejo de PIN nulo (Caso Borde 10)**: Si una orden preexistente no cuenta con un `security_code` válido, el campo `securityCode` se devuelve como `null`, lo que activa el renderizado neutral `- - - -` en la interfaz sin impedir la entrega física. El servidor **nunca** devuelve un código sustituto o derivado (por ejemplo, del identificador de la orden): el código exhibido en mostrador es siempre el mismo que ve el comensal.
4. **Respuesta vacía estable**: Cuando no hay órdenes listas, devuelve `[]` con código `200 OK`.
