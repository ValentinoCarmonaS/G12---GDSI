# Contrato 03: Notificación Asíncrona (Webhook de Pasarela de Pago)

**Ruta**: `POST /api/v1/payments/{provider}/webhook` (ej. `/api/v1/payments/mercadopago/webhook`)  
**Autenticación**: Verificación criptográfica obligatoria delegada al puerto `PaymentGatewayPort` (para Mercado Pago: cabecera `x-signature` con HMAC-SHA256).  
**Propósito**: Recibir notificaciones automáticas y asíncronas de la pasarela ante cambios en el estado de pagos (aprobaciones, rechazos, cancelaciones). El controlador extrae `{provider}`, valida que corresponda a un `PaymentMethod` soportado y delega la firma y parseo a su `PaymentGatewayPort`. Asegura la consolidación definitiva o la liberación de la reserva aún si el comensal nunca retorna al sitio (AS-2b, CB-04b, FR-003).  
**Garantía de Aislamiento**: El payload de notificación no contiene ni requiere datos de tarjeta bancaria (FR-013, INV-11, SC-009).

---

## 1. Request Entrante (Originado por Mercado Pago)

### Headers
```http
POST /api/v1/payments/mercadopago/webhook HTTP/1.1
Host: api.easyeat.com
Content-Type: application/json
x-request-id: 79c3b8a1-43e9-4e76-8f37-123456789abc
x-signature: ts=1742505638683,v1=ced36ab6d33566bb1e16c125819b8d840d6b8ef136b0b9127c76064466f5229b
User-Agent: MercadoPago-Webhook/1.0
```

### Body
```json
{
  "action": "payment.updated",
  "api_version": "v1",
  "data": {
    "id": "123456789"
  },
  "date_created": "2026-11-09T02:15:30Z",
  "id": 1058293812,
  "live_mode": false,
  "type": "payment",
  "user_id": 987654321
}
```

---

## 2. Protocolo de Verificación Criptográfica de Origen

Antes de procesar la notificación, el backend aplica la verificación estricta descrita en `research.md`:

1. **Extracción**:
   - `ts` y `v1` desde la cabecera `x-signature`.
   - `x-request-id` desde la cabecera HTTP.
   - `data.id` desde el cuerpo JSON (`123456789`).
2. **Validación de Vigencia**:
   - $\Delta t = |\text{CurrentTimeMillis} - \text{ts}|$. Si $\Delta t > 300\,000\text{ ms}$ (5 minutos), se rechaza con `HTTP 401 Unauthorized` para mitigar ataques de repetición (*replay attacks*).
3. **Cálculo de Hash**:
   - Ensamblado de manifest:
     ```text
     manifest = "id:123456789;request-id:79c3b8a1-43e9-4e76-8f37-123456789abc;ts:1742505638683;";
     ```
   - Cálculo HMAC-SHA256 con el `MERCADOPAGO_WEBHOOK_SECRET`.
4. **Comparación en Tiempo Constante**:
   - `MessageDigest.isEqual(computedHash.getBytes(), v1.getBytes())`.
   - Si no coincide: abortar con `HTTP 401 Unauthorized`.

---

## 3. Reconciliación con Autoridad Externa (INV-08)

El webhook notifica que *algo cambió*, pero el backend no asume el resultado sin consultar la fuente de verdad:
1. El backend invoca a Mercado Pago:
   ```http
   GET https://api.mercadopago.com/v1/payments/123456789
   Authorization: Bearer <MERCADOPAGO_ACCESS_TOKEN>
   ```
2. Mercado Pago responde con el objeto del pago oficial:
   - `status`: `"approved"` | `"rejected"` | `"cancelled"`
   - `status_detail`: `"accredited"` | `"cc_rejected_insufficient_amount"` | etc.
   - `external_reference`: UUID de la orden en EasyEat.
   - `transaction_amount`: Monto abonado.

---

## 4. Procesamiento Idempotente (INV-06, CB-07)

1. Se recupera el registro en la tabla `payments` asociado a `order_id` o `external_reference`.
2. **Chequeo de idempotencia**:
   - Si `payment.status == APPROVED` y el evento recibido es `"approved"`:
     - No se repiten descuentos ni transiciones.
     - Se responde inmediatamente `HTTP 200 OK`.
   - Si `payment.status` ya es terminal (`REJECTED`, `CANCELLED`, `EXPIRED`):
     - No se altera el inventario ni la orden.
     - Se responde `HTTP 200 OK`.
3. **Transición Aprobada (AS-2b, FR-003)**:
   - `payment.setStatus(PaymentStatus.APPROVED)`.
   - `reservation.setStatus(ReservationStatus.CONFIRMED)`.
   - `order.setStatus(OrderStatus.CONFIRMED)`.
   - Se libera o afianza la reserva en definitiva.
4. **Transición Rechazada / Cancelada**:
   - `payment.setStatus(PaymentStatus.REJECTED)`.
   - `reservation.setStatus(ReservationStatus.RELEASED)`.
   - Recursos restituidos inmediatamente a la cocina y a la franja.

---

## 5. Respuestas del Endpoint

### 200 OK (Notificación Procesada o Duplicada Reconocida)
```http
HTTP/1.1 200 OK
Content-Type: application/json
```
```json
{
  "status": "PROCESSED",
  "paymentId": "123456789",
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "outcome": "APPROVED"
}
```

### 401 Unauthorized (Firma Inválida o Falsificada)
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json
```
```json
{
  "type": "https://api.easyeat.com/errors/unauthorized",
  "title": "Firma de webhook inválida",
  "status": 401,
  "detail": "La firma provista en la cabecera x-signature no coincide con el secreto de integración.",
  "code": "INVALID_WEBHOOK_SIGNATURE"
}
```

