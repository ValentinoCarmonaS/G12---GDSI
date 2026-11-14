# Contratos de integración y API: unificación de checkout Mercado Pago, seguimiento e historial de pedidos

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md) | **Fecha**: 11/11/2026

---

## 1. Contrato de preferencia en Mercado Pago (`MercadoPagoGatewayAdapter`)

- **Endpoint saliente**: `POST ${mercadopago.base-url}/checkout/preferences`
- **Normalización de `back_urls`**:
  - `frontBase` sanea barras finales repetidas (`frontendBaseUrl.replaceAll("/+$", "")`).
  - `success`, `pending` y `failure` apuntan a `${frontBase}/checkout/payment-return`.
- **Envío condicional de `notification_url`**:
  - Toma `mercadopago.backend-base-url` (resuelto desde `MERCADOPAGO_BACKEND_BASE_URL` o respaldo automático `RENDER_EXTERNAL_URL`), sanea barras finales y verifica que comience con `http://` o `https://` y que el host no contenga `localhost` ni `127.0.0.1`.
  - Cuando es válido, envía `notification_url: "${backendBase}/api/v1/payments/mercadopago/webhook"`.
  - En entorno local sin URL pública (por ejemplo `localhost`), omite `notification_url` sin fallar.
- **Selección y normalización de URL de redirección (`CheckoutPaymentService` y `OrderPaymentStatusController`)**:
  - Toda cadena vacía o en blanco devuelta por Mercado Pago en `init_point` o `sandbox_init_point` se normaliza a `null` antes de persistir o responder.
  - Prioriza siempre `initPoint` cuando no es nulo ni en blanco; recurre a `sandboxInitPoint` únicamente si `initPoint` está ausente.
  - El campo booleano `sandbox` refleja `mercadopago.sandbox` para trazabilidad del entorno configurado.

---

## 2. Contrato de conflicto `409`, estado de pago y confirmación desde navegador

### 2.1 Conflicto `409 ACTIVE_RESERVATION_EXISTS` (`POST /api/v1/checkout/pay` y `POST /api/v1/orders`)

Aplicable cuando el comensal autenticado posee una orden vigente en estado `PENDING_PAYMENT` (`expiresAt` nulo o posterior a `now`), tras expirar perezosamente cualquier orden vencida (`expiresAt <= now`):

```json
{
  "type": "https://easyeat.com/errors/active-reservation-exists",
  "title": "Conflict",
  "status": 409,
  "detail": "Ya tenés un pedido o pago en curso pendiente.",
  "instance": "/api/v1/checkout/pay",
  "code": "ACTIVE_RESERVATION_EXISTS",
  "orderId": "UUID-de-la-orden-pendiente"
}
```

### 2.2 Consulta de estado `GET /api/v1/orders/{orderId}/payment-status`

- **Seguridad**: `@PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")`; extrae `userId` del token autenticado y valida `order.userId.equals(userId)` (responde `403 Forbidden` si pertenece a otro usuario, `404` si no existe).
- **Resolución de reserva y expiración**:
  - Consulta la reserva mediante `temporaryReservationRepository.findByOrderId(orderId)` (sin escaneo completo en memoria).
  - Si `order.status == PENDING_PAYMENT` y `expiresAt != null && !expiresAt.isAfter(now)` (`expiresAt <= now`), aplica expiración perezosa (`EXPIRED`) en orden, reserva activa y pago `IN_PROCESS`.
- **Respuesta `200 OK`**:
  - `payment.redirectUrl` normalizado (`null` si vacío) priorizando `initPoint` sobre `sandboxInitPoint`; `payment.sandbox` refleja `mercadopago.sandbox`.

### 2.3 Confirmación y registro pendiente (`POST /api/v1/checkout/confirm-payment` y `/pending-payment`)

- **Seguridad y verificación de pasarela**:
  - Validan `@PreAuthorize` de `COMENSAL` y pertenencia `order.userId.equals(dinerId)` (`403` ante acceso ajeno).
  - En `confirmPayment`: si `dinerId != null` y `mercadopago.access-token` está configurado (no vacío ni en blanco), consulta `gatewayPort.queryPayment(gatewayPaymentId)` exigiendo estado `APPROVED` y coincidencia de `externalReference` con `orderId`; rechaza discrepancias o estados no aprobados con `409 INVALID_ORDER_STATE`.
- **Protección contra aprobación tardía en estados cerrados (`Q3`) y stock insuficiente**:
  - Si la orden ya se encuentra en `CANCELLED` o `EXPIRED`, rechaza la confirmación sin debitar stock ni crear `KdsTicket`, registra incidencia de nivel `WARN`/`ERROR` con `orderId` y `gatewayPaymentId` para conciliación manual y responde `409 INVALID_ORDER_STATE`.
  - Si la orden ya fue confirmada o está en cocina (`IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID` o `CONFIRMED`), retorna respuesta idempotente (`idempotentReplay: true`) sin re-descontar stock ni duplicar `KdsTicket`.
  - Si falta stock de ingredientes en una confirmación nueva, `InsufficientIngredientStockException` mapea en `GlobalExceptionHandler` a HTTP `409` (`INSUFFICIENT_STOCK`), haciendo rollback completo.
  - En `registerPendingPayment`, rechaza retroceso sobre órdenes `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID`, `CONFIRMED`, `CANCELLED` o `EXPIRED` (`409 INVALID_ORDER_STATE`).

### 2.4 Cancelación unificada de intento (`POST /api/v1/checkout/cancel-payment` y `POST /api/v1/orders/{orderId}/cancel`)

- Ambos endpoints validan ownership (`AccessDeniedException` -> `403`, nunca `SecurityException` -> `500`), cancelan la orden `PENDING_PAYMENT`, liberan la `TemporaryReservation` activa (`RELEASED`, motivo `CANCELLED_BY_USER`) y marcan el `Payment` en curso como `CANCELLED`.

---

## 3. Contrato de recepción de webhook (`POST /api/v1/payments/{provider}/webhook`)

- **Acceso**: Público sin JWT (`permitAll()` en `SecurityConfig`). Si `{provider}` carece de adaptador registrado, retorna `404`.
- **Entradas aceptadas**:
  - Cuerpo JSON de Mercado Pago (`type` o `topic`, `data.id`) y/o parámetros query IPN (`data.id`, `id`, `topic`, `type`).
- **Validación de firma fail-closed (`Q4`)**:
  - Cuando está presente el encabezado `x-signature`, invoca `WebhookSignatureValidator`.
  - Si `MERCADOPAGO_WEBHOOK_SECRET` está vacío o en blanco, registra un log explícito de error/alerta operativa y retorna `401 Unauthorized`.
  - Si la firma HMAC no coincide, retorna `401 Unauthorized` con cuerpo vacío.
- **Filtrado de tópicos**:
  - Si `topic`/`type` corresponde a un tópico distinto de pagos (por ejemplo `"merchant_order"`) o no existe `dataId` de pago sin error de firma, responde `200 OK` sin consultar `/v1/payments/{id}`.
- **Reconciliación y manejo de respuestas (`Q3` y `Q4`)**:
  - Con `dataId` de pago, consulta `gatewayPort.queryPayment(dataId)` como fuente de verdad y delega en `WebhookProcessingService.processAsynchronousNotification(...)`:
    - `APPROVED` -> `CheckoutConfirmationService.confirmPayment(null, ...)` (stock por receta, `IN_PROGRESS`, reserva `CONFIRMED`, pago `APPROVED`, `KdsTicket` en cocina y publicación de `OrderPaymentApprovedEvent` escuchado en `AFTER_COMMIT`).
    - `IN_PROCESS` / `PENDING` -> `CheckoutConfirmationService.registerPendingPayment(null, ...)`.
    - `REJECTED` / `CANCELLED` -> `PaymentWebhookService.processPaymentUpdate(...)` (solo si la orden no está ya confirmada ni `EXPIRED`; distingue `REJECTED` vs `CANCELLED`).
  - **Pago tardío `approved` sobre `CANCELLED` o `EXPIRED` (`Q3`)**: captura `InvalidOrderStateException`, registra incidencia crítica en logs para conciliación o reembolso operativo sin descontar stock ni enviar a cocina, y responde **`200 OK`** al webhook para evitar reintentos infinitos.
  - **Errores transitorios de red, pasarela o base de datos (`Q4`)**: **no** se capturan devolviendo `200 OK`; propagan respuesta **`5xx`** (`502`/`500`) para activar reintentos automáticos de Mercado Pago.

---

## 4. Contrato `GET /api/v1/orders` (historial del comensal)

- **Seguridad**: `@PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")`, extrae `userId` exclusivamente del usuario autenticado.
- **Comportamiento**:
  - Ejecuta expiración perezosa sobre cualquier orden del usuario en `PENDING_PAYMENT` con `expiresAt <= now` (orden `EXPIRED`, reserva activa `EXPIRED` y pago `IN_PROCESS` a `EXPIRED`).
  - Retorna JSON array `200 OK` ordenado por `createdAt` descendente (`DinerOrderSummaryResponse`):

```json
[
  {
    "orderId": "UUID",
    "ticketNumber": 12,
    "establishmentId": "UUID",
    "establishmentName": "Restaurante Demo",
    "status": "PENDING_PAYMENT",
    "fulfillmentMode": "IMMEDIATE",
    "pickupDate": "2026-11-11",
    "timeSlotStart": "14:00:00",
    "timeSlotEnd": "14:30:00",
    "totalAmount": 4500.00,
    "expiresAt": "2026-11-11T17:15:00Z",
    "createdAt": "2026-11-11T17:00:00Z",
    "redirectUrl": "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=...",
    "trackingUrl": "/pedidos/UUID/seguimiento",
    "items": [
      {
        "dishId": "UUID",
        "dishName": "Milanesa a caballo",
        "quantity": 1,
        "unitPrice": 4500.00,
        "subtotal": 4500.00
      }
    ]
  }
]
```

- **Reglas de campos derivables**:
  - `redirectUrl`: presente únicamente cuando `status == PENDING_PAYMENT` y el último `Payment` tiene URL válida (priorizando `initPoint` no blanco); `null` en cualquier otro caso (`Q5`).
  - `ticketNumber`: tomado exclusivamente de `KdsTicketRepository.findByOrderId(orderId)`; `null` si la orden aún no ingresó a cocina (`Q2`).
  - `trackingUrl`: `"/pedidos/" + orderId + "/seguimiento"`.

---

## 5. Contrato de seguimiento (`/confirmation-tracking`, `/live-tracking` y KDS ready)

- **`GET /api/v1/orders/{orderId}/confirmation-tracking`**:
  - Requiere rol `COMENSAL`.
  - Rechaza con `409 INVALID_ORDER_STATE` órdenes en `CANCELLED`, `EXPIRED` o `PENDING_PAYMENT`.
  - Para `PENDING_ACCREDITATION`: retorna `200 OK` con `approvalStatus = "PENDING"` y `ticketNumber = null`.
  - Para `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID` y `CONFIRMED`: retorna `200 OK` con `approvalStatus = "APPROVED"` y `ticketNumber` leído exclusivamente de `KdsTicketRepository` (`null` si no existe comanda).
- **`GET /api/v1/orders/{orderId}/live-tracking`**:
  - Cuando `status` es `PENDING_ACCREDITATION`, `PENDING_PAYMENT`, `CANCELLED` o `EXPIRED`: retorna `ticketNumber = null`, `isCountdownActive = false`, `estimatedMinutesRemaining = null`, `progressPercentage = 0` e `isReadyForPickup = false`, sin inventar progreso ni número de comanda por hash.
  - Cuando está en cocina (`IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`), usa el `ticketNumber` persistido de `KdsTicket`.
- **Despacho listo para retirar (`OrderLiveTrackingService.markTicketReadyForPickup`)**:
  - Resuelve primero `kdsTicketRepository.findById(ticketId)` (actualizando también `KdsTicketStatus.READY_FOR_PICKUP`) y solo recurre a `orderRepository.findById(ticketId)` como compatibilidad secundaria; incluye `ticketNumber` real desde `KdsTicket`.

---

## 6. Contratos de interfaz, ruteo y almacenamiento en navegador

- **Retorno de pago (`PaymentReturnPage.tsx`)**:
  - Estado aprobado (`payment.status === 'APPROVED'` u orden `IN_PROGRESS` / `READY_FOR_PICKUP` / `COMPLETED` / `PAID` / `CONFIRMED`): ejecuta `clearCart()` y `navigate(\`/checkout/confirmacion/\${orderId}\`, { replace: true })`.
  - Conflicto `409` en `confirmPayment` (por ejemplo orden `CANCELLED` o `EXPIRED`, `Q3`) o respuesta `CANCELLED` / `EXPIRED` / `REJECTED`: conserva el carrito y redirige a `/checkout` mostrando el mensaje controlado al comensal.
  - Estado `PENDING_ACCREDITATION` / `PENDING` / `in_process`: conserva carrito y muestra enlaces directos `"Ver seguimiento"` (`/pedidos/:orderId/seguimiento`) e `"Ir a Mis pedidos"` (`/perfil#pedidos`).
- **Seguimiento en vivo (`OrderConfirmationTrackingPage.tsx` y `LiveTrackingWidget.tsx`) (`Q2`)**:
  - Ante `status === 'PENDING_ACCREDITATION'`: renderiza vista provisional de acreditación pendiente **sin** `OrderSuccessGreenBanner`, **sin** texto de comprobante enviado y **sin** barra de progreso ni tiempos de cocina activos, ejecutando polling suave cada 5 segundos hasta transicionar a aprobado o estado terminal, con acceso a `"Mis pedidos"`.
  - Ante estado aprobado: renderiza `OrderSuccessGreenBanner` y `LiveTrackingWidget` tolerando `ticketNumber: number | null`. `useOrderLiveTrackingPolling` detiene el polling en estados terminales (`READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `EXPIRED`).
- **Checkout (`CheckoutPage.tsx` y `InProcessPaymentPanel.tsx`) (`Q5`, `FR-006`, `FR-007`)**:
  - Al cargar `/checkout` llama a `getMyOrders()` para detectar orden `PENDING_PAYMENT` activa. Cuando `InProcessPaymentPanel` está visible, oculta el mensaje genérico `"Tu carrito está vacío"` y al cancelar el intento refresca el estado pendiente.
  - Si `redirectUrl` es nulo o vacío (`Q5`), `InProcessPaymentPanel` y `MyOrdersSection` ocultan o deshabilitan `"Retomar pago"`, informan al comensal que no existe preferencia de pago activa y conservan habilitado `"Cancelar intento"`.
  - Elimina la lectura heredada de `localStorage.getItem('selectedEstablishmentId')` y corrige redirecciones `/catalog` a `/catalogo`.
- **Ruteo post-login y navegación (`postAuthRouting.ts`, `RoleRouteGuard.tsx`, `WorkspaceLayout.tsx`) (`FR-007`, `FR-009`)**:
  - `ROLE_ALLOWED_PREFIXES.COMENSAL` incluye `/checkout` y `/pedidos`; `isRouteAllowedForRole` evalúa el path sin query/hash; `RoleRouteGuard` preserva `location.pathname + location.search + location.hash` en `state.from`.
  - `WorkspaceLayout` muestra de forma visible en escritorio y móvil la barra de navegación del `COMENSAL` con `"Catálogo"` (`/catalogo`), `"Mis pedidos"` (`/perfil#pedidos`) y `"Mi perfil"` (`/perfil`).
