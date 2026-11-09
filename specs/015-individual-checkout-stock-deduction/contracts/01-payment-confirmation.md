# Contract 01: Confirmación de Pago Aprobado y Procesamiento Transaccional

**Endpoint / Operación**: Evento Webhook `POST /api/v1/payments/{provider}/webhook` y Retorno `POST /api/v1/checkout/confirm-payment`  
**Consumidor**: Notificación asíncrona de Mercado Pago o cliente frontend al retornar  
**Seguridad**: Autenticación Bearer JWT (`ROLE_COMENSAL`) para retornos; firma criptográfica HMAC-SHA256 (`x-signature`) para webhooks  
**Aislamiento Financiero**: No recibe ni expone datos de tarjeta bancaria (FR-013 de Spec 014, SC-009 de Spec 015)  

---

## 1. Descripción

Este contrato gobierna la recepción de la aprobación de pago emitida por la pasarela externa (Mercado Pago Checkout Pro) o su procesamiento autónomo en servidor sin retorno del comensal (AS-1, AS-1b, CB-11, C5).  
Al verificarse fehacientemente que el pago fue aprobado (`status = 'approved'`, `status_detail = 'accredited'`), el sistema ejecuta de forma indivisible:
1. El débito atómico de todos los insumos de las recetas de los platos del pedido sobre `ingredients`.
2. La transición de la orden al estado operativo `IN_PROGRESS` ("en curso").
3. La inserción de la comanda en la cola del KDS (`kds_tickets`).
4. La marcación de la reserva temporal como confirmada definitiva (`CONFIRMED`).

---

## 2. Petición de Confirmación (Retorno de Checkout)

```http
POST /api/v1/checkout/confirm-payment
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "gatewayPaymentId": "1234567890",
  "status": "approved"
}
```

### Parámetros de Entrada

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `orderId` | UUID | Sí | Identificador único de la orden creada en EasyEat. |
| `gatewayPaymentId` | String | Sí | Identificador oficial del pago generado en Mercado Pago. |
| `status` | String | Sí | Estado del cobro devuelto por la pasarela (`approved`). |

---

## 3. Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "orderStatus": "IN_PROGRESS",
  "paymentStatus": "APPROVED",
  "stockDebited": true,
  "kdsTicket": {
    "ticketId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "ticketNumber": 142,
    "status": "QUEUED",
    "queuedAt": "2026-11-09T13:45:00Z",
    "estimatedPickup": {
      "date": "2026-11-09",
      "slotStart": "14:00:00",
      "slotEnd": "14:30:00"
    }
  },
  "idempotentReplay": false
}
```

### Campos de Salida

| Campo | Tipo | Descripción |
|---|---|---|
| `orderId` | UUID | Identificador de la orden procesada. |
| `orderStatus` | String | Estado resultante de la orden: `IN_PROGRESS`. |
| `paymentStatus` | String | Estado resultante del cobro: `APPROVED`. |
| `stockDebited` | Boolean | Indicador de que el débito físico de ingredientes fue ejecutado. |
| `kdsTicket.ticketId` | UUID | Identificador de la comanda en la cola de cocina. |
| `kdsTicket.ticketNumber` | Integer | Número secuencial legible de la comanda. |
| `kdsTicket.status` | String | Estado inicial en cocina: `QUEUED`. |
| `idempotentReplay` | Boolean | `false` si es la primera confirmación; `true` si es un reintento idempotente neutralizado (INV-07, CB-12). |

---

## 4. Semántica de Idempotencia (INV-07, CB-12)

Si se recibe un reenvío idéntico de webhook o el comensal recarga la pantalla de confirmación:
- El sistema detecta `order.status == OrderStatus.IN_PROGRESS`.
- **No se vuelven a debitar insumos**.
- **No se duplica el ticket en KDS**.
- Responde `HTTP 200 OK` con `idempotentReplay = true`.

---

## 5. Errores Posibles

| Código HTTP | Código RFC 7807 | Razón y Acción Recomendada |
|---|---|---|
| `401 Unauthorized` | `UNAUTHORIZED` | Token JWT ausente o inválido / Firma de webhook inválida. |
| `404 Not Found` | `ORDER_NOT_FOUND` | La orden especificada no existe en EasyEat. |
| `409 Conflict` | `INVALID_ORDER_STATE` | La orden ya se encuentra en un estado terminal incompatible (`CANCELLED`, `EXPIRED`). |

