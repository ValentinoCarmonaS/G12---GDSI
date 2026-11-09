# Contract 02: Estado Pendiente de Pasarela y Resolución Asíncrona

**Endpoint / Operación**: Webhook `POST /api/v1/payments/{provider}/webhook` y Retorno `POST /api/v1/checkout/pending-payment`  
**Consumidor**: Pasarela de pagos externa / Pantalla de checkout de EasyEat  
**Seguridad**: Bearer JWT (`ROLE_COMENSAL`) para retornos; firma HMAC-SHA256 (`x-signature`) para webhooks  
**Aislamiento Financiero**: Cero persistencia o recepción de datos sensibles de tarjeta (PCI-DSS SAQ A)  

---

## 1. Descripción

Este contrato gobierna la respuesta cuando Mercado Pago devuelve un estado pendiente de acreditación (`status = 'pending'`, CB-05, AS-5, FR-006):
1. **Transición a Pendiente**: La orden transiciona a `PENDING_ACCREDITATION`.
2. **Congelamiento de Recursos**: La `TemporaryReservation` se congela (no expira por el barredor programado mientras aguarda resolución).
3. **Inocuidad sobre Inventario y Cocina**: **Cero débito de stock real** en `ingredients` y **cero comandas encoladas en el KDS**.
4. **Resolución Posterior Asíncrona**: Al recibir la notificación definitiva de Mercado Pago:
   - Si `approved`: Se ejecuta el débito atómico de stock, la orden pasa a `IN_PROGRESS` y se envía al KDS.
   - Si `rejected`: La orden pasa a `CANCELLED`, y la reserva temporal de franja y stock se libera inmediatamente (`RELEASED`).

---

## 2. Petición de Notificación de Estado Pendiente

```http
POST /api/v1/checkout/pending-payment
Content-Type: application/json
Authorization: Bearer <jwt-token>

{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "gatewayPaymentId": "9876543210",
  "statusDetail": "pending_contingency"
}
```

### Parámetros de Entrada

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `orderId` | UUID | Sí | Identificador de la orden. |
| `gatewayPaymentId` | String | Sí | Identificador del pago en Mercado Pago. |
| `statusDetail` | String | No | Código descriptivo de la demora (`pending_contingency`, `pending_review_manual`). |

---

## 3. Respuesta de Notificación de Estado Pendiente (`HTTP 200 OK`)

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "orderStatus": "PENDING_ACCREDITATION",
  "paymentStatus": "PENDING",
  "stockDebited": false,
  "kdsQueued": false,
  "reservationFrozen": true,
  "message": "Tu pago está pendiente de confirmación. Tu pedido pasará a cocina tan pronto como se reciba la aprobación final."
}
```

### Campos de Salida

| Campo | Tipo | Descripción |
|---|---|---|
| `orderId` | UUID | Identificador de la orden. |
| `orderStatus` | String | Estado resultante: `PENDING_ACCREDITATION`. |
| `paymentStatus` | String | Estado del cobro: `PENDING`. |
| `stockDebited` | Boolean | Rigurosamente `false` (INV-03: no hay débito real hasta aprobación). |
| `kdsQueued` | Boolean | Rigurosamente `false` (INV-04: no ingresa a cocina). |
| `reservationFrozen` | Boolean | `true` (la retención de stock y cupo de franja se conserva activa). |
| `message` | String | Mensaje explicativo para presentar al comensal. |

---

## 4. Resolución Posterior vía Webhook (Resolución Terminal)

Al emitir Mercado Pago la resolución final, se invoca internamente el procesador de desenlaces:

### Caso A: Aprobación Posterior (`status = 'approved'`)
- Se debita el stock real por receta (`stockDebited = true`).
- `order.status` $\rightarrow$ `IN_PROGRESS`.
- Se genera y encola la comanda en `kds_tickets` (`status = 'QUEUED'`).
- La reserva se marca como `CONFIRMED`.

### Caso B: Rechazo Posterior (`status = 'rejected'`)
- Cero débito de stock.
- `order.status` $\rightarrow$ `CANCELLED`.
- Se libera la reserva de franja y stock (`status = 'RELEASED'`, `releaseReason = 'REJECTED'`).
- Cero comandas emitidas a cocina.

---

## 5. Errores Posibles

| Código HTTP | Código RFC 7807 | Razón y Acción Recomendada |
|---|---|---|
| `401 Unauthorized` | `UNAUTHORIZED` | Credencial inválida o firma digital rechazada. |
| `404 Not Found` | `ORDER_NOT_FOUND` | La orden especificada no existe. |
| `409 Conflict` | `ORDER_ALREADY_RESOLVED` | La orden ya fue confirmada o cancelada previamente. |

