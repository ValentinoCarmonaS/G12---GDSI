# Investigación técnica: unificación de checkout Mercado Pago, webhook a cocina e historial de pedidos

**Rama**: `018-checkout-mp-tracking-orders` | **Fecha**: 11/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Commit base auditado**: `60324415` (creado desde `017-render-deploy-docker`)

---

## Resumen de hallazgos en código base

La auditoría directa del repositorio confirmó las causas raíz de los fallos reportados en Render y reveló brechas adicionales de seguridad, concurrencia, seguimiento y ruteo que deben quedar cubiertas antes de implementar.

---

## Decisiones técnicas resueltas

### 1. Configuración y creación de preferencias en Mercado Pago (`FR-001`, `FR-002`)

- **Evidencia observada**:
  - `MercadoPagoProperties` define únicamente 6 campos (`accessToken`, `publicKey`, `webhookSecret`, `sandbox`, `baseUrl`, `frontendBaseUrl`); no existe `backendBaseUrl`. `MercadoPagoPublicUrlTest` construye el récord con el constructor de 6 argumentos.
  - `application.yml` define `mercadopago.*` sin `backend-base-url`.
  - `MercadoPagoGatewayAdapter.createPreference` concatena `properties.frontendBaseUrl() + "/checkout/payment-return"` sin sanear barras finales, no envía `notification_url`, y extrae `init_point` y `sandbox_init_point` con `resBody.path(...).asText()`, lo que produce cadena vacía (`""`) en lugar de `null` cuando falta el campo.
  - `CheckoutPaymentService` (`initiatePayment`) y `OrderPaymentStatusController` (`getOrderPaymentStatus`) priorizan `sandboxInitPoint` cuando `sandbox` es verdadero o no nulo, y `OrderPaymentStatusController` expone el booleano `sandbox` como `p.getSandboxInitPoint() != null` en lugar de leer la propiedad de configuración.
- **Decisión**:
  - Añadir `String backendBaseUrl` al récord `MercadoPagoProperties` en la séptima posición con `@ConstructorBinding` sobre el constructor canónico y un constructor secundario de compatibilidad de 6 argumentos (con `backendBaseUrl = ""`) para no romper instancias directas existentes.
  - Mapear en `application.yml`: `mercadopago.backend-base-url: ${MERCADOPAGO_BACKEND_BASE_URL:${RENDER_EXTERNAL_URL:}}`.
  - En `MercadoPagoGatewayAdapter`: normalizar `frontendBaseUrl` y `backendBaseUrl` quitando barras finales repetidas (`replaceAll("/+$", "")`), normalizar URLs vacías o en blanco de la respuesta de Mercado Pago a `null`, y enviar `notification_url = backendBase + "/api/v1/payments/mercadopago/webhook"` solo cuando `backendBaseUrl` empiece con `http://` o `https://` y su host no sea `localhost` ni `127.0.0.1`.
  - Unificar la resolución de `redirectUrl` priorizando `initPoint` (no nulo ni en blanco) y recurriendo a `sandboxInitPoint` únicamente si `initPoint` falta; mantener `sandbox = properties.sandbox()`.
- **Alternativas descartadas**:
  - Cambiar las firma de `MercadoPagoPublicUrlTest` rompiendo el constructor de 6 argumentos sin compatibilidad: descartado porque un constructor secundario con `@ConstructorBinding` preserva compatibilidad binaria sin complejidad accidental.

---

### 2. Seguridad y convergencia del webhook con la confirmación transaccional (`Q3`, `Q4`, `FR-003`, `FR-004`)

- **Evidencia observada**:
  - `SecurityConfig` (`com.easyeat.config`) ya permite `permitAll()` en `/api/v1/payments/*/webhook` y `/api/v1/payments/mercadopago/webhook`.
  - `PaymentWebhookController` inyecta únicamente `(PaymentGatewayResolver, PaymentWebhookService)` y llama a `paymentWebhookService.processPaymentUpdate(...)`. Como resultado, un webhook `"approved"` deja la orden en `OrderStatus.PAID`, confirma la reserva y aprueba `Payment`, pero **no descuenta stock**, **no crea `KdsTicket`**, **no pasa la orden a `IN_PROGRESS`** ni **publica `OrderPaymentApprovedEvent`**.
  - En contraste, `WebhookProcessingService` ya existe sin llamadores en `src/main` (usado solo en `KdsIdempotencyIntegrationTest`) y delega `"approved"` a `CheckoutConfirmationService.confirmPayment(null, ...)` y `"pending"`/`"in_process"` a `registerPendingPayment(null, ...)`. Sin embargo, `PaymentWebhookProcessingIntegrationTest` y `PaymentRejectionAndCancellationIntegrationTest` prueban directamente `PaymentWebhookService.processPaymentUpdate(...)` esperando `OrderStatus.PAID` para `"approved"`.
  - `PaymentWebhookController` no acepta parámetros query IPN (`topic`, `id`), no ignora tópicos no correspondientes a pago (como `merchant_order`), y envuelve el flujo en un `catch (Exception)` que devuelve `200 OK` incluso ante fallos transitorios de red hacia Mercado Pago o errores de persistencia en PostgreSQL.
  - `WebhookSignatureValidator` devuelve `false` en silencio cuando `mercadopago.webhook-secret` está vacío, dificultando diagnosticar rechazos `401` en Render.
  - `CheckoutConfirmationServiceImpl.confirmPayment` no valida `dinerId`, no rechaza órdenes en `CANCELLED` ni `EXPIRED`, solo considera idempotente `OrderStatus.IN_PROGRESS` (pudiendo re-descontar stock si la orden está en `READY_FOR_PICKUP`, `COMPLETED`, `PAID` o `CONFIRMED`, ya que `StockDeductionServiceImpl` no es idempotente por `orderId`), nunca aprueba la entidad `Payment`, y nunca publica `OrderPaymentApprovedEvent` (ni en `confirmZeroAmountOrder`).
  - `OrderPaymentEventListener` utiliza `@EventListener` en lugar de `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, arriesgando disparar `OrderReceiptEmailService` antes de que la transacción de confirmación haga commit.
- **Decisión**:
  - Rutear `PaymentWebhookController` hacia `WebhookProcessingService` (conservando un constructor secundario de compatibilidad con `PaymentWebhookService` si un test `@WebMvcTest` puntual lo requiere, o ampliando `@MockBean` en `PaymentWebhookSecurityIntegrationTest`) y mantener `PaymentWebhookService` para rechazos/cancelaciones (incorporando guarda contra retroceso desde estados `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `EXPIRED` y diferenciación entre `CANCELLED` y `REJECTED`).
  - En `PaymentWebhookController`: admitir `type`/`data.id` JSON y query params `topic`/`id`/`data.id`; retornar `200 OK` inmediatamente para tópicos distintos de `payment` (por ejemplo, `merchant_order`) o sin identificador de pago cuando no hay error de firma; atrapar `InvalidOrderStateException` (Q3, orden `CANCELLED` o `EXPIRED`) registrando log estructurado de incidencia para conciliación/reembolso manual y retornando `200 OK`; propagar errores transitorios de `queryPayment` o persistencia para que HTTP devuelva `5xx` y dispare reintentos automáticos de Mercado Pago.
  - En `WebhookSignatureValidator`: mantener retorno `false` (`401`) cuando llegue `x-signature` con secreto vacío o blanco (fail-closed) y emitir `LOGGER.error` explícito; emitir también advertencia de arranque si existe `accessToken` sin `webhookSecret`.
  - En `CheckoutConfirmationServiceImpl`: validar ownership (`dinerId != null && !order.getUserId().equals(dinerId)` -> `AccessDeniedException`); bloquear `CANCELLED` y `EXPIRED` tanto en `confirmPayment` como en `registerPendingPayment` lanzando `InvalidOrderStateException`; aplicar idempotencia para `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID` y `CONFIRMED`; actualizar `Payment` mediante `findTopByOrderIdOrderByCreatedAtDesc` de forma segura si existe fila en `payments` (preservando compatibilidad con `KdsIdempotencyIntegrationTest`); publicar `OrderPaymentApprovedEvent` tras aprobar orden paga o `$0` (resolviendo correo en `UserRepository`); y cambiar `OrderPaymentEventListener` a `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- **Alternativas descartadas**:
  - Agregar migración Flyway para marcar conciliación tardía en BD: descartada en común acuerdo para cumplir Q3 mediante logs estructurados y trazabilidad en `Payment`/`Order` sin tocar esquema SQL.
  - Migrar `PaymentWebhookService.processPaymentUpdate("approved")` rompiendo tests históricos que esperan `PAID`: descartada en favor de canalizar el controlador real por `WebhookProcessingService`.

---

### 3. Seguridad de retorno de pago y estado de orden (`OrderPaymentStatusController` y `confirm-payment`)

- **Evidencia observada**:
  - `OrderPaymentStatusController.getOrderPaymentStatus` (`GET /api/v1/orders/{orderId}/payment-status`) carece de `@PreAuthorize` y de verificación contra el usuario autenticado (IDOR). Además escanea `temporaryReservationRepository.findAll()` en memoria y aplica expiración perezosa con `isBefore(now)` en lugar de `expiresAt <= now`.
  - `POST /api/v1/checkout/confirm-payment` confía ciegamente en el parámetro `status` enviado por el navegador.
  - `CheckoutPaymentService.cancelPaymentAttempt` lanza `SecurityException` ante usuario distinto al dueño, e `InsufficientIngredientStockException` tampoco tiene handler en `GlobalExceptionHandler`, provocando HTTP `500`.
- **Decisión**:
  - Proteger `OrderPaymentStatusController` con `@PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")`, resolver `userId` desde el principal autenticado y exigir pertenencia de la orden (`AccessDeniedException` -> `403`, consistente con `GlobalExceptionHandler`). Reemplazar `findAll().stream()` por `temporaryReservationRepository.findByOrderId(orderId)` y unificar la condición `expiresAt <= now`.
  - En `CheckoutConfirmationServiceImpl.confirmPayment`, cuando `dinerId != null` (llamada de navegador) y `mercadopago.accessToken` está configurado (no vacío ni blanco), consultar `paymentGatewayResolver.resolve(PaymentMethod.MERCADO_PAGO).queryPayment(gatewayPaymentId)` validando estado `APPROVED` y coincidencia de `externalReference` con `orderId` antes de confirmar (en modo local sin `accessToken` mantiene soporte para pruebas locales/emuladas).
  - Reemplazar `SecurityException` por `org.springframework.security.access.AccessDeniedException` (`403`) y agregar `@ExceptionHandler(InsufficientIngredientStockException.class)` (`409 INSUFFICIENT_STOCK`) en `GlobalExceptionHandler`.

---

### 4. Seguimiento provisional en `PENDING_ACCREDITATION` y `ticketNumber` real (`Q2`, `FR-005`)

- **Evidencia observada**:
  - `OrderConfirmationTrackingService` y `OrderLiveTrackingService` calculan `Math.abs(order.getId().hashCode() % 900) + 100` en lugar de consultar `KdsTicketRepository.findByOrderId(orderId)`.
  - `OrderConfirmationTrackingService.getConfirmationTracking` no rechaza `EXPIRED` ni `PENDING_PAYMENT`, ni considera `PAID`/`CONFIRMED` como aprobadas (`isApproved`).
  - `OrderLiveTrackingService.getLiveTracking` deja caer órdenes en `PENDING_ACCREDITATION`, `PENDING_PAYMENT`, `CANCELLED` y `EXPIRED` en el cálculo de progreso activo (`25%` / `20 min`). Además `markTicketReadyForPickup` busca `orderRepository.findById(ticketId)` confundiendo el ID del ticket KDS con el ID de la orden.
  - En frontend, `OrderConfirmationTrackingResponse` y `OrderLiveTrackingResponse` tipan `ticketNumber: number`, y `OrderConfirmationTrackingPage.tsx` siempre dibuja `OrderSuccessGreenBanner`, aviso de comprobante enviado y `LiveTrackingWidget`. `useOrderLiveTrackingPolling.ts` detiene el polling únicamente en `READY_FOR_PICKUP`.
- **Decisión**:
  - Obtener `ticketNumber` exclusivamente mediante `kdsTicketRepository.findByOrderId(order.getId()).map(KdsTicket::getTicketNumber).orElse(null)` tanto en seguimiento como en el nuevo listado `GET /api/v1/orders`.
  - En `OrderConfirmationTrackingService`: rechazar `CANCELLED`, `EXPIRED` y `PENDING_PAYMENT` con `InvalidOrderStateException`; devolver `approvalStatus = "PENDING"` y `ticketNumber = null` para `PENDING_ACCREDITATION`; tratar `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID` y `CONFIRMED` como `"APPROVED"`. (Mantener validación de ownership si aplica o preservar firma compatible con el test unitario existente exponiendo sobrecarga o verificando `OrderConfirmationTrackingController`).
  - En `OrderLiveTrackingService`: retornar estado inactivo coherente (`ticketNumber = null`, `isCountdownActive = false`, `estimatedMinutesRemaining = null`, `progressPercentage = 0`, `isReadyForPickup = false`) cuando la orden esté en `PENDING_ACCREDITATION`, `PENDING_PAYMENT`, `CANCELLED` o `EXPIRED`. En `markTicketReadyForPickup`, resolver primero por `kdsTicketRepository.findById(ticketId)` (sincronizando `KdsTicketStatus.READY_FOR_PICKUP`) con fallback a `orderRepository.findById(ticketId)`.
  - En frontend: actualizar `types/orderTracking.ts` (`ticketNumber: number | null`), adaptar `OrderSuccessGreenBanner`, `LiveTrackingWidget` y `ReadyForPickupCallout` para tolerar `ticketNumber == null` (sin inventar números ni caer a `0`), agregar vista provisional específica en `OrderConfirmationTrackingPage.tsx` cuando `status === 'PENDING_ACCREDITATION'` (sin banner verde, sin aviso de comprobante enviado ni barra de cocina, con polling suave de acreditación y enlaces a `"Mis pedidos"`), y detener el polling de seguimiento en estados terminales (`READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `EXPIRED`).

---

### 5. Desbloqueo `409`, cancelación uniforme, Q5 y deep-links (`FR-006`, `FR-007`, `FR-008`, `FR-009`)

- **Evidencia observada**:
  - `ActiveReservationExistsException` no transporta `UUID orderId`. `CheckoutPaymentService` (`hasActivePendingOrder`) y `OrderService.createOrder` no identifican la orden conflictiva, y `OrderServiceTest` instancia `OrderService` con 5 parámetros.
  - `OrderService.cancelOrder` actualiza `Order` a `CANCELLED` pero deja intactas las entidades `TemporaryReservation` y `Payment`, a diferencia de `CheckoutPaymentService.cancelPaymentAttempt`.
  - `InProcessPaymentPanel.tsx` mantiene habilitado `"Retomar pago"` aun cuando `redirectUrl` es nulo o vacío (`handleResume` silencioso), y utiliza clases oscuras (`bg-amber-950/40`, `text-white`) sobre un layout claro (`bg-gray-50`), al igual que `PendingAccreditationBanner`.
  - `CheckoutPage.tsx` ignora `ACTIVE_RESERVATION_EXISTS`, no consulta `getMyOrders()` al montar, lee `localStorage.getItem('selectedEstablishmentId')` (rompiendo aislamiento multi-pestaña), redirige a `/catalog` en lugar de `/catalogo`, y deja visible `"Tu carrito está vacío"` debajo de `InProcessPaymentPanel`.
  - `postAuthRouting.ts` (`ROLE_ALLOWED_PREFIXES.COMENSAL`) omite `/checkout` y `/pedidos`, y `RoleRouteGuard.tsx` guarda `location.pathname` descartando `search` y `hash`. `WorkspaceLayout.tsx` aplica `hidden sm:flex` en la barra del comensal, ocultando enlaces en móvil.
- **Decisión**:
  - Enriquecer `ActiveReservationExistsException` y `GlobalExceptionHandler` con `orderId`; agregar `OrderRepository.findActivePendingOrders(userId, now)` expirando perezosamente órdenes vencidas antes de reportar conflicto `409`; unificar liberación de reserva y pago en `OrderService.cancelOrder`; conservar constructor de 5 parámetros (o actualizar pruebas explícitamente junto con dependencias opcionales/completas) en `OrderService`.
  - Mover únicamente `easyeat_cart` en `CartContext.tsx` a `localStorage`; eliminar `localStorage.getItem('selectedEstablishmentId')` de `CheckoutPage.tsx`; ocultar el card `"Tu carrito está vacío"` mientras haya un `InProcessPaymentPanel` activo; aplicar Q5 en `InProcessPaymentPanel` y `MyOrdersSection` (ocultar/deshabilitar `"Retomar pago"` con mensaje claro cuando no haya `redirectUrl`); ajustar contraste claro y rutas `/catalogo`.
  - Agregar `/checkout` y `/pedidos` en `ROLE_ALLOWED_PREFIXES.COMENSAL` (normalizando ruta base antes del prefijo), preservar `pathname + search + hash` en `RoleRouteGuard`, y hacer visible la navegación horizontal de `COMENSAL` en móvil (`WorkspaceLayout`).

---

### 6. Suites de pruebas y documentación en Render (`quickstart.md`)

- **Evidencia observada**:
  - Frontend ejecuta en Vitest **ambos** directorios `easyeat/frontend/src/test/` y `easyeat/frontend/tests/`. Existen duplicados (`CartContext.test.tsx` en ambos), pruebas exclusivas en `tests/` (`ProfilePage.test.tsx`, `WorkspaceLayout.test.tsx`, `AppRoutesProfileCatalog.test.tsx`, `IndividualCheckoutPayment.test.tsx`) y 4 suites de checkout que mockean `orderApi` sin `getMyOrders`. En backend no existe todavía test para `OrderPaymentStatusController`.
  - No existe `render.yaml` ni guía previa sobre `MERCADOPAGO_BACKEND_BASE_URL` / `MERCADOPAGO_WEBHOOK_SECRET` obligatorio en `easyeat/backend/.env.example` o `README.md` raíz. Por definición de alcance, `specs/017-render-deploy-docker/` permanecerá intacto.
- **Decisión**:
  - Registrar explícitamente en `tasks.md` tanto `src/test/` como `tests/` (y el nuevo test de integración `OrderPaymentStatusControllerIntegrationTest`), consolidar la guía de configuración y prueba en Render dentro de [quickstart.md](./quickstart.md) y planificar la actualización de `easyeat/backend/.env.example` y `README.md` raíz en la fase final.

