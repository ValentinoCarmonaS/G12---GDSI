# Plan de implementación: unificación de checkout Mercado Pago, webhook a cocina e historial de pedidos

**Rama**: `018-checkout-mp-tracking-orders` (creada desde `017-render-deploy-docker`) | **Fecha**: 11/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Entrada**: Especificación funcional desde `/specs/018-checkout-mp-tracking-orders/spec.md`

---

## Resumen

Resolver de extremo a extremo la integración de pago con Mercado Pago Checkout Pro, la confirmación atómica e idempotente hacia cocina y comprobante, la consistencia del seguimiento según estado real, el desbloqueo del conflicto `409 ACTIVE_RESERVATION_EXISTS` y la visibilidad integral de pedidos para el comensal:

1. **Redirección oficial a Mercado Pago y `notification_url` (`US1` — `FR-001`, `FR-002`)**: Configurar `MercadoPagoProperties`, `application.yml`, `MercadoPagoGatewayAdapter`, `CheckoutPaymentService` y `OrderPaymentStatusController` para priorizar `init_point` tanto al iniciar como al retomar pagos (normalizando cadenas vacías a `null` y manteniendo `sandbox` para trazabilidad), sanear barras finales de `frontendBaseUrl`, enviar `notification_url` pública mediante `MERCADOPAGO_BACKEND_BASE_URL` o respaldo automático `RENDER_EXTERNAL_URL` (omitiendo `localhost`/`127.0.0.1`), proteger `GET /api/v1/orders/{orderId}/payment-status` para `COMENSAL` con validación de ownership y reemplazar su escaneo en memoria por consulta puntual por orden con expiración `expiresAt <= now`.
2. **Confirmación unificada, seguridad de webhook y seguimiento fiel al estado (`US2` — `FR-003`, `FR-004`, `FR-005`)**: Conectar `PaymentWebhookController` con `WebhookProcessingService` (soportando payload JSON y query IPN, ignorando tópicos no-pago como `merchant_order` con `200 OK`, manteniendo validación fail-closed `401` con log explícito si falta `MERCADOPAGO_WEBHOOK_SECRET`, y propagando fallos transitorios de pasarela/BD como `5xx` para reintentos automáticos); unificar en `CheckoutConfirmationServiceImpl` verificación de ownership, contraste con `queryPayment` cuando exista `accessToken`, bloqueo de órdenes `CANCELLED`/`EXPIRED` (`409` al navegador y `200 OK` + log de incidencia al webhook sin debitar stock ni despachar a cocina), idempotencia completa (`IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID`, `CONFIRMED`), aprobación de `Payment`, creación de `KdsTicket` y emisión de `OrderPaymentApprovedEvent` escuchado `@TransactionalEventListener(AFTER_COMMIT)`. En tracking (`OrderConfirmationTrackingService`, `OrderLiveTrackingService`, `OrderConfirmationTrackingPage.tsx` y componentes asociados), obtener `ticketNumber` exclusivamente de `KdsTicketRepository` (`null` sin comanda), corregir `markTicketReadyForPickup` vía `KdsTicketRepository`, mostrar vista provisional en `PENDING_ACCREDITATION` sin banner verde/aviso de comprobante/progreso activo, redirigir retornos aprobados a `/checkout/confirmacion/:orderId` y detener el polling en estados terminales.
3. **Desbloqueo de conflicto `409`, Q5, persistencia de carrito y deep-links (`US3` — `FR-006`, `FR-007`)**: Enriquecer `ActiveReservationExistsException` y `GlobalExceptionHandler` con `orderId` (mapeando además `AccessDeniedException` en lugar de `SecurityException` y `InsufficientIngredientStockException` a `409`); unificar liberación de reserva y pago en `OrderService.cancelOrder`; conectar `CheckoutPage` ante `ACTIVE_RESERVATION_EXISTS` y `PAYMENT_IN_PROCESS` + detección proactiva de `PENDING_PAYMENT` al montar (ocultando el card vacío debajo de `InProcessPaymentPanel` y eliminando el fallback heredado `localStorage('selectedEstablishmentId')`); aplicar Q5 en `InProcessPaymentPanel` (ocultar/deshabilitar `"Retomar pago"` sin `redirectUrl`) y contraste claro; persistir únicamente `easyeat_cart` en `localStorage` dejando sesiones e inquilino activo en `sessionStorage`; y habilitar `/checkout` y `/pedidos` con query/hash en `postAuthRouting` y `RoleRouteGuard`.
4. **Historial `"Mis pedidos"` y navegación responsive (`US4` — `FR-008`, `FR-009`)**: Exponer `GET /api/v1/orders` en `OrderController` y `OrderService` con expiración perezosa y DTO `DinerOrderSummaryResponse` (`ticketNumber` real o `null`, `redirectUrl` priorizando `initPoint` o `null`); organizar `ProfilePage` en pestañas (`Datos y preferencias` y `Mis pedidos` con `#pedidos`), crear `MyOrdersSection` con soporte de Q5, corregir rutas `/catalogo`, y hacer visible en escritorio y móvil el acceso `"Mis pedidos"` (`/perfil#pedidos`) en `WorkspaceLayout`.

---

## Contexto técnico

- **Backend**: Java 21, Spring Boot 3.3.4 (Spring Web, Spring Security, Spring Data JPA, Spring Mail opcional, Flyway), PostgreSQL / Neon (prod) y Testcontainers/PostgreSQL (tests de integración). No requiere nuevas migraciones SQL ni nuevas dependencias.
- **Frontend**: TypeScript, React 18, Vite, React Router DOM 6, Axios, Tailwind CSS, Lucide React, Vitest + React Testing Library (con dos árboles activos: `easyeat/frontend/src/test/` y `easyeat/frontend/tests/`).
- **Seguridad y aislamiento**: JWT (`access_token`) y local activo (`easyeat_active_tenant`) en `sessionStorage` para preservar sesiones multi-rol paralelas; carrito (`easyeat_cart`) en `localStorage`. Endpoints de comensal protegidos con `@PreAuthorize` para `COMENSAL` y validación de pertenencia por `userId`. Endpoint público de webhooks (`/api/v1/payments/*/webhook`) con validación HMAC fail-closed (`401` ante firma inválida o secreto vacío con log explícito) y reconciliación contra la API de Mercado Pago.
- **Diseño detallado y contratos**: Consultar [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md) y [contracts/checkout-orders-contract.md](./contracts/checkout-orders-contract.md).

---

## Verificación constitucional

*(Evaluación frente a `.specify/memory/constitution.md` v2.0.0 tras Fase 0 y Fase 1)*

- **I. Especificación como fuente de verdad**: Cumplido. Todas las aclaraciones Q1–Q5 y brechas detectadas fueron conciliadas explícitamente entre `spec.md`, `contracts/` y este plan tras inspección directa en `HEAD` (`60324415`).
- **II. Especificación antes de implementación**: Cumplido. Ningún archivo fuente fuera de `specs/018-checkout-mp-tracking-orders/` se modifica antes de aprobar especificación, plan y tareas.
- **III. Verificación en capas**: Cumplido. Cada historia incorpora pruebas backend (unitarias, `@WebMvcTest` y Testcontainers PostgreSQL) y frontend (ambas suites `src/test/` y `tests/`), cubriendo casos normales, concurrencia, estados terminales `CANCELLED`/`EXPIRED`, fallos transitorios `5xx`, firma `401` y ownership.
- **IV. Diseño simple, cohesivo y justificado**: Cumplido. Se completan y convergen piezas existentes (`WebhookProcessingService`, `CheckoutConfirmationServiceImpl`, `OrderConfirmationTrackingService`, `OrderLiveTrackingService`, `InProcessPaymentPanel`, `OrderConfirmationTrackingPage`), añadiendo únicamente `DinerOrderSummaryResponse`, `MyOrdersSection` y las pruebas faltantes (`OrderPaymentStatusControllerIntegrationTest` y tests de tracking/perfil/ruteo).
- **V. Alcance acotado y VI. Deuda técnica controlada**: Cumplido. Las correcciones de seguridad y bugs directamente ligados al alcance (IDOR en `OrderPaymentStatusController`, `SecurityException` -> `AccessDeniedException`, `InsufficientIngredientStockException` -> `409`, `markTicketReadyForPickup`, `postAuthRouting`/`RoleRouteGuard`, fallback `selectedEstablishmentId`, contraste en paneles) quedan trazadas explícitamente; `specs/017-render-deploy-docker/` permanece intacto conforme a decisión de usuario.
- **VII. Trazabilidad y documentación**: Cumplido. Cadena completa `spec.md` ↔ `research.md` ↔ `data-model.md` ↔ `contracts/` ↔ `quickstart.md` ↔ `plan.md` ↔ `tasks.md`.
- **VIII. Evidencia sobre supuestos**: Cumplido. Todas las decisiones se fundamentan en código verificado y las ambigüedades quedaron cerradas vía `grill-me`.
- **IX. Ingeniería sobre conveniencia y X. Rebanadas verticales**: Cumplido. Cada historia `US1`–`US4` define cambios end-to-end backend, frontend y verificación; no se omiten controles de seguridad ni propagación de errores `5xx`.
- **Flujo de desarrollo (excepción documentada)**: La Constitución exige una historia de usuario por rama (`Development Workflow`). Este incremento agrupa `US1`–`US4` en la rama `018-checkout-mp-tracking-orders` por autorización explícita (ver `Seguimiento de complejidad`).

---

## Estructura del proyecto y cambios planificados

### Documentación de esta especificación

```text
specs/018-checkout-mp-tracking-orders/
├── spec.md                              # Especificación funcional (Q1-Q5 integradas, limpia de fugas internas)
├── plan.md                              # Este archivo (/speckit-plan)
├── research.md                          # Hallazgos verificados y decisiones técnicas (Fase 0)
├── data-model.md                        # Transiciones de estado, DTOs y almacenamiento cliente (Fase 1)
├── quickstart.md                        # Guía consolidada de entorno local, Render y prueba manual (Fase 1)
├── tasks.md                             # Desglose de tareas sincronizado con el plan
├── checklists/
│   └── requirements.md                  # Checklist de calidad con todos los ítems validados
└── contracts/
    └── checkout-orders-contract.md      # Contratos de API, webhooks, tracking y comportamiento UI
```

### Archivos de código afectados

#### Backend (`easyeat/backend/src/main/java/com/easyeat/` y recursos)

- `order/config/MercadoPagoProperties.java` y `src/main/resources/application.yml`:
  - Añadir `backendBaseUrl` mapeado a `${MERCADOPAGO_BACKEND_BASE_URL:${RENDER_EXTERNAL_URL:}}`, `@ConstructorBinding` en el constructor de 7 argumentos y constructor de compatibilidad de 6 argumentos.
- `order/service/MercadoPagoGatewayAdapter.java`:
  - Sanear barras finales repetidas en `frontendBaseUrl` y `backendBaseUrl`; emitir `notification_url` pública si aplica; normalizar URLs vacías de respuesta de Mercado Pago a `null`.
- `order/service/CheckoutPaymentService.java` y `order/controller/OrderPaymentStatusController.java`:
  - Priorizar `initPoint` no blanco sobre `sandboxInitPoint` y mantener `sandbox = properties.sandbox()`.
  - En `CheckoutPaymentService`: identificar orden activa conflictiva al iniciar pago (`ActiveReservationExistsException(orderId, message)`) y sustituir `SecurityException` por `AccessDeniedException` en `cancelPaymentAttempt`.
  - En `OrderPaymentStatusController`: agregar `@PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")`, validar pertenencia de `orderId` al usuario autenticado, usar `temporaryReservationRepository.findByOrderId(orderId)` en lugar de `findAll()` y expirar perezosamente con `expiresAt <= now`.
- `order/service/WebhookSignatureValidator.java`, `order/controller/PaymentWebhookController.java`, `order/service/WebhookProcessingService.java` y `order/service/PaymentWebhookService.java`:
  - En `WebhookSignatureValidator`: conservar rechazo fail-closed (`401`) cuando llegue `x-signature` sin secreto y emitir `LOGGER.error` explícito; advertir en arranque si `accessToken` está activo sin `webhookSecret`.
  - En `PaymentWebhookController`: inyectar `WebhookProcessingService` (con constructor compatible si aplica para `@WebMvcTest`), aceptar JSON y query IPN (`topic`/`id`/`data.id`), responder `200 OK` en tópicos no-pago (como `merchant_order`), atrapar `InvalidOrderStateException` (`CANCELLED`/`EXPIRED`) retornando `200 OK` con log de incidencia (Q3), y propagar errores transitorios de pasarela/BD como `5xx` (Q4).
  - En `WebhookProcessingService` / `PaymentWebhookService`: proteger rechazos/cancelaciones contra retroceso sobre órdenes ya aprobadas o expiradas y distinguir `REJECTED` vs `CANCELLED`.
- `order/service/CheckoutConfirmationServiceImpl.java` y `order/service/OrderPaymentEventListener.java`:
  - Validar ownership (`dinerId != null` -> `AccessDeniedException`), verificar pago real mediante `queryPayment` cuando `dinerId != null` y `accessToken` está configurado, bloquear `CANCELLED` y `EXPIRED` (`InvalidOrderStateException` + log de conciliación manual), respetar idempotencia en `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID` y `CONFIRMED` sin re-descontar stock, aprobar `Payment` de manera segura si existe registro, y emitir `OrderPaymentApprovedEvent` (pago normal y `$0`) resuelto desde `UserRepository`.
  - Cambiar `OrderPaymentEventListener.onOrderPaymentApproved` a `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- `order/service/OrderConfirmationTrackingService.java`, `order/service/OrderLiveTrackingService.java` y `order/controller/OrderConfirmationTrackingController.java`:
  - Consultar `KdsTicketRepository.findByOrderId(orderId)` para `ticketNumber` (`null` sin comanda, eliminando `hashCode`).
  - En `getConfirmationTracking`: admitir `PENDING_ACCREDITATION` con `approvalStatus = "PENDING"`, aceptar `PAID`/`CONFIRMED` como `"APPROVED"`, rechazar `CANCELLED`, `EXPIRED` y `PENDING_PAYMENT` con `InvalidOrderStateException`, y asegurar validación de ownership compatible con pruebas.
  - En `getLiveTracking`: devolver vista inactiva (`0%`, `null` minutos y `ticketNumber = null`) fuera de estados en cocina; en `markTicketReadyForPickup`, resolver por `kdsTicketRepository.findById(ticketId)` (y actualizar `KdsTicketStatus.READY_FOR_PICKUP`) con respaldo a `orderRepository.findById(ticketId)`.
- `order/exception/ActiveReservationExistsException.java` y `auth/controller/GlobalExceptionHandler.java`:
  - Adjuntar `UUID orderId` en `ActiveReservationExistsException` y exponerlo en `ProblemDetail` (`409 ACTIVE_RESERVATION_EXISTS`); agregar `@ExceptionHandler(InsufficientIngredientStockException.class)` (`409 INSUFFICIENT_STOCK`).
- `order/repository/OrderRepository.java`, `order/service/OrderService.java`, `order/controller/OrderController.java` y `order/dto/DinerOrderSummaryResponse.java`:
  - Agregar `findActivePendingOrders(userId, now)`, constructor compatible / dependencias necesarias en `OrderService`, expiración perezosa y unificación de `cancelOrder` (liberando `TemporaryReservation` y `Payment`), implementación de `getDinerOrders(userId)` con `KdsTicketRepository`, y `@GetMapping` (`GET /api/v1/orders`) en `OrderController`.

#### Frontend (`easyeat/frontend/src/`)

- `context/CartContext.tsx`: persistir únicamente `easyeat_cart` en `localStorage` (conservando `access_token` y `easyeat_active_tenant` en `sessionStorage`).
- `types/order.ts`, `types/payment.ts`, `types/orderTracking.ts` y `api/orderApi.ts`: incorporar `DinerOrderSummaryResponse`, `DinerOrderItemResponse`, `orderId?` en errores `409`, `getMyOrders()` y `ticketNumber: number | null`.
- `pages/PaymentReturnPage.tsx`: ante aprobación (`APPROVED`, `PAID`, `CONFIRMED`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`), vaciar carrito y redirigir a `/checkout/confirmacion/:orderId`; ante `409` en confirmación tardía (Q3) o `EXPIRED`/`CANCELLED`/`REJECTED`, conservar carrito y volver a `/checkout` informando el motivo; ante `PENDING_ACCREDITATION`, ofrecer enlaces a `/pedidos/:orderId/seguimiento` y `/perfil#pedidos`; corregir ruta `/catalogo`.
- `pages/OrderConfirmationTrackingPage.tsx`, `components/tracking/{OrderSuccessGreenBanner,LiveTrackingWidget,ReadyForPickupCallout}.tsx` y `hooks/useOrderLiveTrackingPolling.ts`: añadir vista provisional para `PENDING_ACCREDITATION` (sin banner verde, sin comprobante ni barra activa de cocina, con polling suave), manejar `ticketNumber: number | null` y detener polling en `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED` y `EXPIRED`.
- `pages/CheckoutPage.tsx`, `components/checkout/InProcessPaymentPanel.tsx` y `components/checkout/PendingAccreditationBanner.tsx`: detectar `PENDING_PAYMENT` al montar vía `getMyOrders()`, conectar `409 ACTIVE_RESERVATION_EXISTS` y `PAYMENT_IN_PROCESS` + `onViewExistingOrder` hacia el panel (ocultando card de carrito vacío si el panel está activo), ocultar/deshabilitar `"Retomar pago"` sin `redirectUrl` (Q5), eliminar `localStorage.getItem('selectedEstablishmentId')`, corregir rutas `/catalogo` y ajustar clases de contraste sobre fondo claro.
- `router/postAuthRouting.ts` y `router/RoleRouteGuard.tsx`: habilitar `/checkout` y `/pedidos` para `COMENSAL`, normalizar query/hash al validar prefijos y preservar `pathname + search + hash` tras login.
- `components/profile/MyOrdersSection.tsx` (nuevo), `pages/ProfilePage.tsx` y `components/common/WorkspaceLayout.tsx`: organizar pestañas en Perfil con soporte `#pedidos`, crear `MyOrdersSection` con acciones context-aware y regla Q5, y hacer visible en móvil y escritorio el enlace `"Mis pedidos"` (`/perfil#pedidos`) en `WorkspaceLayout`.

#### Tests y documentación operativa

- **Backend tests (`easyeat/backend/src/test/java/com/easyeat/order/`)**: ampliar `MercadoPagoPropertiesTest`, `MercadoPagoPublicUrlTest`, `CheckoutPaymentIntegrationTest`, `PaymentWebhookSecurityIntegrationTest`, `WebhookSignatureValidatorTest`, `KdsIdempotencyIntegrationTest`, `IndividualCheckoutIntegrationTest`, `OrderReceiptEmailDispatchIntegrationTest`, `OrderControllerIntegrationTest`, `OrderServiceTest`, `PendingPaymentResolutionIntegrationTest`, `OrderConfirmationTrackingControllerTest`, `LiveTrackingScheduledCountdownTest`, `KitchenReadyForPickupIntegrationTest` y crear `OrderPaymentStatusControllerIntegrationTest` más prueba E2E de webhook/Q3/Q4 y servicios de tracking.
- **Frontend tests (`easyeat/frontend/src/test/` y `easyeat/frontend/tests/`)**: actualizar `src/test/cart/CartContext.test.tsx` y `tests/context/CartContext.test.tsx`; actualizar mocks y aserciones en `PaymentReturnPage.test.tsx`, `InProcessPaymentPanel.test.tsx`, `CheckoutPage.test.tsx`, `CheckoutPaymentFlow.test.tsx`, `CheckoutInteractionLock.test.tsx`, `tests/checkout/IndividualCheckoutPayment.test.tsx`, `OrderConfirmationPage.test.tsx`, `OrderConfirmationPersistence.test.tsx`, `LiveTrackingWidget.test.tsx`, `useOrderLiveTrackingPolling.test.ts`, `tests/pages/ProfilePage.test.tsx`, `tests/components/WorkspaceLayout.test.tsx` y `tests/router/AppRoutesProfileCatalog.test.tsx`; agregar pruebas de `MyOrdersSection` y `postAuthRouting`/`RoleRouteGuard`.
- **Documentación**: actualizar `easyeat/backend/.env.example` y `README.md` raíz manteniendo consolidada la guía en [quickstart.md](./quickstart.md), sin modificar `specs/017-render-deploy-docker/`.

---

## Seguimiento de complejidad

| Excepción | Por qué es necesaria | Alternativa más simple descartada |
| --- | --- | --- |
| Agrupación de `US1`–`US4` en la misma rama `018-checkout-mp-tracking-orders` (desvío controlado sobre `Development Workflow` de [constitution.md](../../.specify/memory/constitution.md)) | El bloqueo productivo de Render surge de la interacción acoplada entre preferencia MP (`US1`), webhook/confirmación/tracking (`US2`), conflicto `409`/carrito (`US3`) y visibilidad en Perfil (`US4`); validar una historia aislada no destraba la compra real en Render. | Dividir en 4 ramas secuenciales obligaría a integrar estados intermedios rotos o sin visibilidad para el comensal antes de poder verificar el flujo completo con Mercado Pago (ver `Clarifications` Q1 en [spec.md](./spec.md)). |
