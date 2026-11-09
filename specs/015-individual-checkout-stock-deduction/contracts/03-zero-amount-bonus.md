# Contract 03: Confirmación de Orden Bonificada al 100% (Monto Cero)

**Endpoint**: `POST /api/v1/checkout/confirm-zero-amount`  
**Consumidor**: Frontend de comensal solitario (`ROLE_COMENSAL`)  
**Seguridad**: Autenticación Bearer JWT (`ROLE_COMENSAL`)  
**Aislamiento Financiero**: Cero interacción con pasarelas externas; cero datos de tarjeta (FR-005, INV-06, CB-04)  

---

## 1. Descripción

Este contrato gobierna la confirmación de pedidos individuales cuyo monto total es exactamente igual a cero (`total_amount == 0.00`) debido a una bonificación completa del 100% (AS-4, AS-6b, CB-04, FR-005).  
Por regla de negocio:
1. **Omisión de Pasarela (FR-005)**: No se genera ninguna preferencia ni se redirige al comensal a Mercado Pago.
2. **Débito Atómico de Stock (AS-6b, C1)**: La bonificación extingue la obligación de pago dinerario pero no la necesidad física de cocinar el plato. Se restan las materias primas exactas de las recetas de los platos de forma atómica sobre `ingredients`.
3. **Paso a En Curso y Despacho a KDS (AS-4, INV-06)**: La orden transiciona de inmediato a `IN_PROGRESS` y se genera su comanda en la cola del KDS (`kds_tickets`).
4. **Vaciado de Carrito**: El comensal recibe confirmación exitosa directa y se limpia su carrito local.

---

## 2. Petición de Confirmación de Orden Bonificada

```http
POST /api/v1/checkout/confirm-zero-amount
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

### Parámetros de Entrada

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `orderId` | UUID | Sí | Identificador de la orden con monto total cero. |

---

## 3. Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "orderStatus": "IN_PROGRESS",
  "totalAmount": 0.00,
  "gatewayBypassed": true,
  "stockDebited": true,
  "kdsTicket": {
    "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "ticketNumber": 143,
    "status": "QUEUED",
    "queuedAt": "2026-11-09T13:46:12Z",
    "estimatedPickup": {
      "date": "2026-11-09",
      "slotStart": "14:00:00",
      "slotEnd": "14:30:00"
    }
  },
  "message": "¡Orden 100% bonificada confirmada! Tu pedido ya fue enviado a la cocina."
}
```

### Campos de Salida

| Campo | Tipo | Descripción |
|---|---|---|
| `orderId` | UUID | Identificador de la orden confirmada. |
| `orderStatus` | String | Estado resultante: `IN_PROGRESS`. |
| `totalAmount` | Decimal | Rigurosamente `0.00`. |
| `gatewayBypassed` | Boolean | `true` (la pasarela externa fue totalmente omitida). |
| `stockDebited` | Boolean | `true` (los ingredientes de la receta fueron descontados). |
| `kdsTicket` | Object | Datos de la comanda encolada en KDS para preparación física. |
| `message` | String | Mensaje de éxito para el comensal. |

---

## 4. Errores Posibles

| Código HTTP | Código RFC 7807 | Razón y Acción Recomendada |
|---|---|---|
| `400 Bad Request` | `NON_ZERO_AMOUNT_ORDER` | La orden tiene un monto superior a cero; debe pagar mediante pasarela estándar. |
| `401 Unauthorized` | `UNAUTHORIZED` | Token JWT inválido o no autenticado. |
| `404 Not Found` | `ORDER_NOT_FOUND` | La orden no existe. |
| `409 Conflict` | `INSUFFICIENT_STOCK` | Uno o más ingredientes de la receta se agotaron concurrentemente; no se debita stock ni se envía a KDS (AS-2, INV-01). |
| `409 Conflict` | `SLOT_CAPACITY_EXCEEDED` | La franja horaria seleccionada superó su capacidad. |

