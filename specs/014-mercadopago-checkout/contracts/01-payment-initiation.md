# Contrato 01: Inicio de Pago con Mercado Pago

**Ruta**: `POST /api/v1/checkout/pay`  
**Autenticación**: Obligatoria comensal (`ROLE_COMENSAL` vía header `Authorization: Bearer <JWT>`).  
**Propósito**: Validar atómicamente la disponibilidad de franja horaria y stock de cocina (INV-01), consolidar la reserva temporal conjunta (TTL 15 min), crear la preferencia en Mercado Pago Checkout Pro y retornar la URL oficial de redirección.  
**Garantía de Aislamiento**: No recibe ni expone ningún dato de tarjeta bancaria (FR-013, INV-11, SC-009).

---

## 1. Request

### Headers
```http
POST /api/v1/checkout/pay HTTP/1.1
Host: api.easyeat.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

### Body (`PaymentInitiationRequest`)
```json
{
  "establishmentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "fulfillmentMode": "SCHEDULED",
  "paymentMethod": "MERCADO_PAGO",
  "pickupDate": "2026-11-10",
  "timeSlotStart": "13:00:00",
  "timeSlotEnd": "13:15:00",
  "items": [
    {
      "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "quantity": 2
    },
    {
      "dishId": "f7e8d9c0-b1a2-3f4e-5d6c-7b8a9f0e1d2c",
      "quantity": 1
    }
  ]
}
```

### Reglas de Validación
1. `establishmentId`: UUID válido, debe pertenecer a un establecimiento activo.
2. `fulfillmentMode`: `'IMMEDIATE'` o `'SCHEDULED'`. Si es `'SCHEDULED'`, `pickupDate`, `timeSlotStart` y `timeSlotEnd` son obligatorios.
3. `paymentMethod`: Opcional. Enum: `MERCADO_PAGO`, `STRIPE`. Si no se especifica, por defecto es `MERCADO_PAGO`. El backend valida que la pasarela esté habilitada para el restaurante (`PaymentMethodService`) y delega al puerto `PaymentGatewayPort`.
4. `items`: Lista no vacía. Cada ítem debe tener `quantity >= 1`.
5. El importe total bruto calculado debe ser estrictamente mayor a cero (FR-001).
6. No debe existir una reserva temporal activa ni un pago en curso para este usuario (INV-05, FR-010).

---

## 2. Response

### 201 Created (Éxito — Reserva temporal establecida y preferencia generada)
```http
HTTP/1.1 201 Created
Content-Type: application/json
```
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "reservationId": "c8b4e721-6548-4e12-b219-918237465abc",
  "paymentId": "481a5e92-d3b1-4f90-bc4a-928173645def",
  "gatewayPreferenceId": "123456789-abc-def-ghi-987654321",
  "redirectUrl": "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=123456789-abc-def-ghi-987654321",
  "sandbox": true,
  "expiresAt": "2026-11-09T02:25:00Z",
  "totalAmount": 15500.00,
  "currency": "ARS"
}
```

---

## 3. Respuestas de Error

### 409 Conflict — Recursos Agotados o Pago en Curso (INV-05, INV-07, CB-10)
- **`SLOT_SOLD_OUT`**: La franja horaria seleccionada acaba de agotarse.
- **`INSUFFICIENT_STOCK`**: Uno o más platos no poseen stock disponible tras evaluar reservas en tránsito.
- **`PAYMENT_IN_PROCESS`**: Ya existe una reserva temporal y un pago en curso para esta orden.

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```
```json
{
  "type": "https://api.easyeat.com/errors/conflict",
  "title": "Conflicto de disponibilidad o estado",
  "status": 409,
  "detail": "La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.",
  "code": "SLOT_SOLD_OUT",
  "timestamp": "2026-11-09T02:10:00Z"
}
```

### 502 Bad Gateway — Fallo en el inicio del pago con Mercado Pago (AS-6, FR-011, CB-06)
Si Mercado Pago experimenta timeout o indisponibilidad de red:
- La reserva temporal iniciada se cancela y revierte inmediatamente.
- No se genera cobro ni se retienen cupos.

```http
HTTP/1.1 502 Bad Gateway
Content-Type: application/problem+json
```
```json
{
  "type": "https://api.easyeat.com/errors/gateway-error",
  "title": "Error de comunicación con la pasarela de pagos",
  "status": 502,
  "detail": "No fue posible iniciar el pago con Mercado Pago. Cualquier reserva iniciada fue cancelada. Por favor, intentá nuevamente en unos instantes.",
  "code": "GATEWAY_UNAVAILABLE",
  "timestamp": "2026-11-09T02:10:00Z"
}
```

