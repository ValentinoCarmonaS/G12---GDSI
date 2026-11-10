# Contrato 01: Pantalla Verde de Confirmación de Pago y Número de Orden

**Ruta de Frontend**: `/checkout/confirmacion/:orderId` (y `/pedidos/:orderId/seguimiento`)  
**Endpoint Backend**: `GET /api/v1/orders/{orderId}/confirmation-tracking`  
**Consumidores**: Comensal tras retorno exitoso de pasarela o reingreso desde correo/historial  
**Seguridad**: Autenticación JWT Bearer (`ROLE_COMENSAL`) con verificación de titularidad (`order.user_id == current_user.id`)  
**Trazabilidad**: Cubre **AS-1**, **AS-3**, **FR-001**, **FR-003**, **INV-001**, **INV-004**, **CB-001**, **CB-012**, **SC-001**, **SC-003**.

---

## 1. Descripción

Este contrato gobierna la respuesta integral que el backend provee para desplegar la pantalla verde de confirmación de compra y el enlace directo al seguimiento en vivo.
- **Invariante INV-001**: Solo se presenta estado verde de aprobación si la orden tiene estado `IN_PROGRESS`, `READY_FOR_PICKUP` o `COMPLETED`.
- **Invariante INV-004**: La respuesta incluye de forma simultánea e indivisible el identificador de orden, número de ticket para retiro, detalle de franja pactada, importe total y el estado actual de elaboración.
- **Aislamiento PCI-DSS**: No expone datos de tarjetas bancarias.

---

## 2. Definición del Endpoint HTTP

```http
GET /api/v1/orders/{orderId}/confirmation-tracking
Authorization: Bearer <jwt-token-comensal>
Accept: application/json
```

### Parámetros de Ruta
- `orderId` (UUID): Identificador de la orden generada.

---

## 3. Respuestas

### 3.1. Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "ticketNumber": 142,
  "status": "IN_PROGRESS",
  "approvalStatus": "APPROVED",
  "establishmentId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "establishmentName": "Café Central Palermo",
  "fulfillmentMode": "IMMEDIATE",
  "pickupDate": "2026-11-09",
  "timeSlotStart": "14:00:00",
  "timeSlotEnd": "14:30:00",
  "totalAmount": 4850.00,
  "items": [
    {
      "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "dishName": "Hamburguesa Clásica EasyEat",
      "quantity": 2,
      "unitPrice": 2100.00,
      "subtotal": 4200.00
    },
    {
      "dishId": "f7e8d9c0-b1a2-3f4e-5d6c-7b8a9f0e1d2c",
      "dishName": "Agua Mineral sin Gas 500ml",
      "quantity": 1,
      "unitPrice": 650.00,
      "subtotal": 650.00
    }
  ],
  "tracking": {
    "isCountdownActive": true,
    "estimatedMinutesRemaining": 18,
    "progressPercentage": 25,
    "isReadyForPickup": false,
    "message": "Tu comida se está preparando en la cocina."
  },
  "branding": {
    "fantasyName": "Café Central Palermo",
    "description": "Especialistas en café de autor y comida artesanal",
    "logoUrl": "/uploads/logos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo.png",
    "primaryColor": "#059669",
    "secondaryColor": "#064E3B",
    "accentColor": "#D97706",
    "primaryContrastColor": "#FFFFFF",
    "secondaryContrastColor": "#FFFFFF",
    "accentContrastColor": "#000000"
  }
}
```

---

## 4. Manejo de Errores Estandarizado (RFC 7807)

### 4.1. Orden No Encontrada (`HTTP 404 Not Found`)
```json
{
  "type": "https://easyeat.com/errors/order-not-found",
  "title": "Orden No Encontrada",
  "status": 404,
  "detail": "No existe la orden indicada o no pertenece al usuario autenticado."
}
```

### 4.2. Orden Aún No Aprobada (`HTTP 409 Conflict`)
Si el comensal intenta acceder a la confirmación de una orden que continúa en `PENDING_PAYMENT`:
```json
{
  "type": "https://easyeat.com/errors/order-payment-unconfirmed",
  "title": "Pago No Confirmado",
  "status": 409,
  "detail": "La orden aún no registra una confirmación de pago autorizada por la pasarela."
}
```

