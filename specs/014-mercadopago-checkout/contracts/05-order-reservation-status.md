# Contrato 05: Consulta de Estado de Orden, Reserva y Pago

**Ruta**: `GET /api/v1/orders/{orderId}/payment-status`  
**Autenticación**: Obligatoria comensal (`ROLE_COMENSAL` vía header `Authorization: Bearer <JWT>`).  
**Propósito**: Proveer al frontend el estado consolidado de la orden, de la reserva temporal de recursos y de la transacción en Mercado Pago para soportar los escenarios AS-1 a AS-6. Ejecuta evaluación perezosa de expiración (*lazy expiration*) si la ventana de 15 minutos fue rebasada.  
**Garantía de Aislamiento**: No recibe ni expone ningún dato de tarjeta bancaria (FR-013, INV-11, SC-009).

---

## 1. Request

### Headers
```http
GET /api/v1/orders/3fa85f64-5717-4562-b3fc-2c963f66afa6/payment-status HTTP/1.1
Host: api.easyeat.com
Authorization: Bearer <JWT>
Accept: application/json
```

---

## 2. Response DTO (`OrderPaymentStatusResponse`)

### 200 OK — Estado del Flujo

```http
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "orderStatus": "PENDING_PAYMENT",
  "reservation": {
    "id": "c8b4e721-6548-4e12-b219-918237465abc",
    "status": "ACTIVE",
    "expiresAt": "2026-11-09T02:25:00Z",
    "remainingSeconds": 642,
    "releaseReason": null
  },
  "payment": {
    "id": "481a5e92-d3b1-4f90-bc4a-928173645def",
    "status": "IN_PROCESS",
    "statusDetail": null,
    "redirectUrl": "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=123456789-abc",
    "sandbox": true
  },
  "fulfillment": {
    "mode": "SCHEDULED",
    "pickupDate": "2026-11-10",
    "timeSlotStart": "13:00:00",
    "timeSlotEnd": "13:15:00"
  },
  "totalAmount": 15500.00,
  "currency": "ARS",
  "serverTime": "2026-11-09T02:14:18Z"
}
```

---

## 3. Mapeo por Escenario de Negocio

| Escenario | `orderStatus` | `reservation.status` | `payment.status` | Efecto en Frontend |
|---|---|---|---|---|
| **AS-1 (Iniciado / Redirigiendo)** | `PENDING_PAYMENT` | `ACTIVE` | `IN_PROCESS` | Redirección hacia Mercado Pago en curso. |
| **AS-2 / AS-2b (Aprobado)** | `CONFIRMED` | `CONFIRMED` | `APPROVED` | Pantalla de confirmación, vaciado del carrito (`clearCart()`). |
| **AS-3 (Rechazado)** | `PENDING_PAYMENT` | `RELEASED` | `REJECTED` | Checkout re-habilitado con alerta explicativa, carrito y franja intactos. |
| **AS-4 (Abandono voluntario)** | `PENDING_PAYMENT` | `RELEASED` | `CANCELLED` | Checkout re-habilitado con banner informativo, carrito y franja intactos. |
| **AS-4b (Vencimiento de ventana)** | `EXPIRED` | `EXPIRED` | `EXPIRED` | Checkout informa que el tiempo expiró; recursos ya fueron liberados. |
| **AS-5 (Pago en curso detectado)** | `PENDING_PAYMENT` | `ACTIVE` | `IN_PROCESS` | Despliega panel "Pago en curso", botón "Pagar" deshabilitado, acciones Retomar y Cancelar. |
| **AS-6 (Fallo en inicio)** | `PENDING_PAYMENT` | `RELEASED` | `FAILED` | Alerta de indisponibilidad de red; recursos devueltos. |

---

## 4. Respuestas de Error

### 404 Not Found (Orden inexistente o no perteneciente al usuario autenticado)
```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
```
```json
{
  "type": "https://api.easyeat.com/errors/not-found",
  "title": "Orden no encontrada",
  "status": 404,
  "detail": "No se encontró una orden con el identificador solicitado asociada al comensal autenticado.",
  "code": "ORDER_NOT_FOUND"
}
```

