# Contrato 06: Formato Uniforme de Errores (RFC 7807) y Códigos de Dominio

**Estándar**: RFC 7807 (`ProblemDetail` / `application/problem+json`).  
**Propósito**: Estandarizar la comunicación de fallos, excepciones de concurrencia y rechazos entre el backend de EasyEat y la SPA de checkout, garantizando diagnósticos unívocos y respuestas tipadas.  
**Garantía de Aislamiento**: Ninguna traza, mensaje de error ni cabecera incluye tokens de acceso de Mercado Pago, firmas privadas ni datos bancarios (FR-013, INV-11, SC-009).

---

## 1. Esquema Base del Formato de Error

```json
{
  "type": "string (URI de referencia del tipo de error)",
  "title": "string (resumen legible del problema)",
  "status": "integer (código de estado HTTP)",
  "detail": "string (descripción explicativa para el comensal)",
  "code": "string (código interno tipado del dominio EasyEat)",
  "timestamp": "string (formato ISO-8601 UTC)",
  "invalidParams": [
    {
      "name": "string (campo afectado si aplica)",
      "reason": "string (motivo de rechazo)"
    }
  ]
}
```

---

## 2. Catálogo de Códigos de Error de la Integración

| Código (`code`) | HTTP Status | Escenario Desencadenante | Detalle para el Comensal |
|---|---|---|---|
| **`SLOT_SOLD_OUT`** | `409 Conflict` | La franja horaria seleccionada fue copada concurrentemente por otro comensal (CB-10, INV-07). | *"La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar."* |
| **`INSUFFICIENT_STOCK`** | `409 Conflict` | Uno o más platos se quedaron sin stock remanente tras considerar reservas en tránsito (CB-09, CB-10). | *"Uno o más platos de tu pedido no cuentan con stock suficiente en cocina. Por favor, revisá tu carrito."* |
| **`DISH_PAUSED_BY_CHEF`** | `409 Conflict` | La cocina pausó manualmente un plato durante el servicio (Spec 013, FR-019). | *"El plato '{nombre}' ha sido pausado temporalmente por la cocina. Por favor, retiralo del carrito para continuar."* |
| **`ACTIVE_PAYMENT_EXISTS`** | `409 Conflict` | El comensal intenta iniciar una nueva reserva teniendo un pago en curso (AS-5, FR-010, INV-05). | *"Ya poseés un pago en curso para este pedido. Podés retomarlo o cancelar el intento actual."* |
| **`ORDER_ALREADY_CONFIRMED`** | `409 Conflict` | Se intenta cancelar o reintentar una orden que ya fue pagada y confirmada. | *"La orden ya se encuentra confirmada y en proceso de elaboración."* |
| **`RESERVATION_EXPIRED`** | `410 Gone` | Se intenta retomar o procesar un pago cuya ventana de 15 minutos ya venció (INV-04). | *"El plazo de 15 minutos para completar el pago ha expirado. Por favor, iniciá un nuevo pedido."* |
| **`GATEWAY_UNAVAILABLE`** | `502 Bad Gateway` | La API de Mercado Pago responde con error 5xx o es inalcanzable (AS-6, FR-011, CB-06). | *"No fue posible iniciar la comunicación con Mercado Pago. Cualquier reserva iniciada fue cancelada. Por favor, intentá nuevamente en unos instantes."* |
| **`GATEWAY_TIMEOUT`** | `504 Gateway Timeout` | La llamada saliente hacia Mercado Pago excedió el timeout de conexión o lectura (AS-6). | *"Tiempo de espera agotado al conectar con Mercado Pago. No se generó cobro. Por favor, reintentá la operación."* |
| **`INVALID_WEBHOOK_SIGNATURE`**| `401 Unauthorized` | El hash HMAC de la cabecera `x-signature` del webhook no coincide con el secreto (INV-08). | *"La firma de autenticación del webhook de Mercado Pago es inválida o ha expirado."* |
| **`ZERO_AMOUNT_NOT_PERMITTED`**| `422 Unprocessable` | El carrito de compras posee monto cero (FR-001). | *"El importe de la orden debe ser mayor a cero para procesar el pago con Mercado Pago."* |
| **`ORDER_NOT_FOUND`** | `404 Not Found` | El identificador de orden no existe o no pertenece al comensal autenticado. | *"No se encontró la orden especificada para el usuario autenticado."* |

---

## 3. Ejemplo Concreto: Fallo de Inicio con Mercado Pago (AS-6, CB-06)

```http
HTTP/1.1 502 Bad Gateway
Content-Type: application/problem+json
```
```json
{
  "type": "https://api.easyeat.com/errors/gateway-error",
  "title": "Error de pasarela de pago externa",
  "status": 502,
  "detail": "No fue posible iniciar el pago con Mercado Pago. Cualquier reserva iniciada fue cancelada. Por favor, intentá nuevamente en unos instantes.",
  "code": "GATEWAY_UNAVAILABLE",
  "timestamp": "2026-11-09T02:14:30Z"
}
```

