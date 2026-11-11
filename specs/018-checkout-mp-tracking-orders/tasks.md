# Lista de tareas: unificación de checkout Mercado Pago, webhook a cocina e historial de pedidos

**Entrada**: Documentos de diseño desde `/specs/018-checkout-mp-tracking-orders/`  
**Prerrequisitos**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), [contracts/checkout-orders-contract.md](./contracts/checkout-orders-contract.md)

## Formato de tareas: `[ID] [P?] [Historia] Descripción`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos y sin dependencia de tareas incompletas en la misma fase).
- **[USx]**: Historia de usuario a la que pertenece la tarea (`US1`, `US2`, `US4`, `US3`), omitida en preparación compartida y pulido final.
- **Orden de fases**: Se adelanta `US4` antes de `US3` para que `CheckoutPage` cuente desde su implementación con `GET /api/v1/orders`, `DinerOrderSummaryResponse`, `getMyOrders()` y las extensiones de errores en `types/`.

---

## Fase 1: Preparación compartida (Setup)

**Propósito**: Incorporar la configuración base requerida por la pasarela y webhooks públicos antes de implementar las historias.

- [X] T001 Añadir propiedad `backendBaseUrl` (`${MERCADOPAGO_BACKEND_BASE_URL:${RENDER_EXTERNAL_URL:}}`) con `@ConstructorBinding` y constructor secundario compatible de 6 argumentos en `easyeat/backend/src/main/java/com/easyeat/order/config/MercadoPagoProperties.java` y en `easyeat/backend/src/main/resources/application.yml`.

**Checkpoint**: Configuración base de Mercado Pago enlazada y retrocompatible con constructores existentes.

---

## Fase 2: Historia de usuario 1 — Pago en Mercado Pago con redirección oficial y notificación automática (Prioridad: P1)

**Objetivo (MVP mínimo estricto)**: Garantizar que las preferencias enviadas a Mercado Pago utilicen `back_urls` saneadas y `notification_url` pública, priorizando `init_point` al iniciar o retomar el pago, y proteger `GET /api/v1/orders/{orderId}/payment-status` contra acceso ajeno y escaneo en memoria.

**Prueba independiente**: Al crear una preferencia o consultar el estado de pago de una orden con entorno configurado, el payload hacia Mercado Pago y la URL normalizada entregada al cliente cumplen `FR-001`, `FR-002` y `FR-008`, rechazando con `403` consultas de comensales ajenos a la orden.

### Pruebas de US1

> **NOTA TDD**: Escribir o ampliar primero estas pruebas y confirmar que fallan antes de implementar `T006`–`T008`.

- [X] T002 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/config/MercadoPagoPropertiesTest.java` verificando enlace de `backendBaseUrl`, respaldo con `RENDER_EXTERNAL_URL`, constructor compatible de 6 argumentos y valores por defecto.
- [X] T003 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/service/MercadoPagoPublicUrlTest.java` verificando saneamiento de barras finales en `back_urls`, envío condicional de `notification_url` cuando `backendBaseUrl` es público, omisión cuando es local o vacío y normalización de cadenas vacías a `null`.
- [X] T004 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/CheckoutPaymentIntegrationTest.java` verificando que `redirectUrl` prioriza `init_point` incluso cuando `sandbox = true`.
- [X] T005 [P] [US1] Crear `easyeat/backend/src/test/java/com/easyeat/order/OrderPaymentStatusControllerIntegrationTest.java` verificando `@PreAuthorize` para `COMENSAL`, `403 Forbidden` ante usuario distinto al dueño, prioridad de `initPoint` con normalización `"" -> null`, expiración `expiresAt <= now` y booleano `sandbox` desde configuración.

### Implementación de US1

- [X] T006 [P] [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/MercadoPagoGatewayAdapter.java` para sanear barras finales en `frontendBaseUrl` y `backendBaseUrl`, enviar `notification_url` pública al crear la preferencia y normalizar cadenas vacías en `PreferenceResult` a `null`.
- [X] T007 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutPaymentService.java` (depende de `T006`) para priorizar `initPoint` no blanco sobre `sandboxInitPoint` al entregar `redirectUrl`.
- [X] T008 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderPaymentStatusController.java` agregando `@PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")`, validando pertenencia (`AccessDeniedException`), reemplazando `temporaryReservationRepository.findAll()` por `findByOrderId(orderId)`, aplicando expiración `expiresAt <= now`, priorizando `initPoint` normalizado y reflejando `mercadopago.sandbox`.

**Checkpoint**: `US1` funcional y verificable de forma independiente (preferencias limpias, `init_point` prioritario y `/payment-status` asegurado).

---

## Fase 3: Historia de usuario 2 — Confirmación atómica unificada, despacho a cocina, comprobante, webhook seguro y seguimiento real (Prioridad: P1)

**Objetivo**: Unificar el flujo de confirmación entre webhook (`WebhookProcessingService`) y retorno de navegador, aplicar validación fail-closed con logs explícitos y reintentos `5xx` (Q4), bloquear aprobaciones tardías sobre órdenes `CANCELLED`/`EXPIRED` (Q3), emitir `OrderPaymentApprovedEvent` en `AFTER_COMMIT`, mostrar vista provisional en `PENDING_ACCREDITATION` con `ticketNumber` real desde `KdsTicketRepository` (Q2) y redirigir retornos aprobados a `/checkout/confirmacion/:orderId`.

**Prueba independiente**: Invocando webhook válido (JSON e IPN), retorno aprobado contrastado contra la pasarela o llamadas concurrentes sobre la misma orden se produce un único débito de stock, una única comanda KDS con `ticketNumber` real, pago `APPROVED`, un `OrderReceipt`, bloqueo ante `CANCELLED`/`EXPIRED`, `5xx` ante errores transitorios y seguimiento provisional o confirmado acorde en frontend.

### Pruebas de US2

> **NOTA TDD**: Escribir o ampliar primero estas pruebas y confirmar que fallan antes de implementar `T016`–`T025`.

- [X] T009 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/WebhookSignatureValidatorTest.java` verificando rechazo fail-closed (`false`) con registro explícito en logs cuando llega firma sin `MERCADOPAGO_WEBHOOK_SECRET` configurado (Q4).
- [X] T010 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/PaymentWebhookSecurityIntegrationTest.java` incorporando `@MockBean WebhookProcessingService` y probando formato JSON e IPN (`topic`, `id`), filtro `200 OK` en `merchant_order`, `401` ante firma inválida, `200 OK` con log de incidencia ante `InvalidOrderStateException` en `CANCELLED`/`EXPIRED` (Q3) y `5xx` ante error transitorio de pasarela o base de datos (Q4).
- [X] T011 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/KdsIdempotencyIntegrationTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/IndividualCheckoutIntegrationTest.java` verificando concurrencia webhook/retorno, ownership (`403` si `dinerId != order.userId`), contraste con `queryPayment` cuando existe `accessToken`, rechazo `409` en `CANCELLED`/`EXPIRED` sin débito de stock ni KDS, idempotencia multi-estado (`IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID`, `CONFIRMED`), aprobación segura de `Payment`, mapeo `409` de `InsufficientIngredientStockException` y único `OrderPaymentApprovedEvent` tras commit.
- [X] T012 [P] [US2] Crear `easyeat/backend/src/test/java/com/easyeat/order/service/OrderConfirmationTrackingServiceTest.java` y ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderConfirmationTrackingControllerTest.java`, `LiveTrackingScheduledCountdownTest.java`, `LiveTrackingSaturationCalculationTest.java` y `KitchenReadyForPickupIntegrationTest.java` cubriendo `ticketNumber` leído desde `KdsTicketRepository` (`null` sin comanda), vista `PENDING_ACCREDITATION`, aceptación de `PAID`/`CONFIRMED`, bloqueo `CANCELLED`/`EXPIRED`/`PENDING_PAYMENT` y `markTicketReadyForPickup` vía `KdsTicketRepository`.
- [X] T013 [P] [US2] Actualizar `easyeat/frontend/src/test/checkout/PaymentReturnPage.test.tsx` verificando vaciado del carrito y redirección `/checkout/confirmacion/:orderId` en `APPROVED`, `PAID`, `CONFIRMED`, `IN_PROGRESS`, `READY_FOR_PICKUP` y `COMPLETED`; retorno a `/checkout` conservando carrito en `409` tardío (Q3), `EXPIRED`, `CANCELLED` o `REJECTED`; y enlaces a `/pedidos/:orderId/seguimiento` y `/perfil#pedidos` en `PENDING_ACCREDITATION`.
- [X] T014 [P] [US2] Actualizar `easyeat/frontend/src/test/tracking/OrderConfirmationPage.test.tsx` y `easyeat/frontend/src/test/tracking/OrderConfirmationPersistence.test.tsx` verificando la vista provisional `PENDING_ACCREDITATION` (sin banner verde, sin aviso de comprobante ni barra activa de cocina, con `ticketNumber: null` y transición automática por polling suave).
- [X] T015 [P] [US2] Actualizar `easyeat/frontend/src/test/tracking/LiveTrackingWidget.test.tsx` y `easyeat/frontend/src/test/hooks/useOrderLiveTrackingPolling.test.ts` verificando renderizado seguro con `ticketNumber: null` sin números ficticios y detención del polling en estados terminales (`READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `EXPIRED`).

### Implementación backend de US2

- [X] T016 [P] [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/WebhookSignatureValidator.java` para emitir `LOGGER.error` explícito cuando llegue `x-signature` con secreto vacío y advertencia inicial si hay `accessToken` sin `webhookSecret`.
- [X] T017 [P] [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` añadiendo `@ExceptionHandler(InsufficientIngredientStockException.class)` con HTTP `409` y código `INSUFFICIENT_STOCK`.
- [X] T018 [P] [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/PaymentWebhookService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/WebhookProcessingService.java` protegiendo rechazos/cancelaciones contra retroceso desde `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED` o `EXPIRED`, y diferenciando `REJECTED` vs `CANCELLED`.
- [X] T019 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/controller/PaymentWebhookController.java` (depende de `T016`, `T018`) para delegar notificaciones de pago en `WebhookProcessingService`, admitir query params IPN (`topic`, `id`, `data.id`) y JSON, responder `200 OK` ante tópicos no-pago (`merchant_order`), capturar `InvalidOrderStateException` Q3 con log de conciliación y `200 OK`, y propagar errores transitorios de pasarela o persistencia como `5xx` (Q4).
- [X] T020 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutConfirmationServiceImpl.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/OrderPaymentEventListener.java` implementando ownership (`AccessDeniedException`), consulta `queryPayment` cuando existe `accessToken`, bloqueo Q3 en `CANCELLED`/`EXPIRED`, idempotencia multi-estado sin re-descuento de stock, aprobación segura de `Payment`, emisión de `OrderPaymentApprovedEvent` (pago normal y `$0`) resuelto vía `UserRepository`, y escucha `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- [X] T021 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/OrderConfirmationTrackingService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java` consultando `KdsTicketRepository.findByOrderId` con guarda nullable segura (`null` sin comanda, eliminando `hashCode`), habilitando `PENDING_ACCREDITATION` (`approvalStatus = "PENDING"`) y `PAID`/`CONFIRMED` (`"APPROVED"`), bloqueando `CANCELLED`/`EXPIRED`/`PENDING_PAYMENT`, devolviendo progreso inactivo fuera de cocina en `getLiveTracking`, y resolviendo `markTicketReadyForPickup` vía `KdsTicketRepository.findById` (con sincronización `KdsTicketStatus.READY` y fallback a `orderId`).

### Implementación frontend de US2

- [X] T022 [P] [US2] Actualizar `easyeat/frontend/src/types/orderTracking.ts` (`ticketNumber: number | null`) y `easyeat/frontend/src/hooks/useOrderLiveTrackingPolling.ts` deteniendo polling en estados terminales (`READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `EXPIRED`).
- [X] T023 [P] [US2] Actualizar `easyeat/frontend/src/components/checkout/PendingAccreditationBanner.tsx` y `easyeat/frontend/src/components/tracking/{OrderSuccessGreenBanner.tsx,LiveTrackingWidget.tsx,ReadyForPickupCallout.tsx}` para soportar `ticketNumber: null` sin renderizar números ficticios ni caer a `0`, y corregir contraste sobre fondo claro.
- [X] T024 [US2] Actualizar `easyeat/frontend/src/pages/PaymentReturnPage.tsx` (depende de `T023`) redirigiendo pagos confirmados a `/checkout/confirmacion/:orderId`, manejando conflicto `409` (Q3) y `EXPIRED` hacia `/checkout` sin limpiar carrito, ofreciendo enlaces a seguimiento y `/perfil#pedidos` en `PENDING_ACCREDITATION` y corrigiendo navegación `/catalogo`.
- [X] T025 [US2] Actualizar `easyeat/frontend/src/pages/OrderConfirmationTrackingPage.tsx` (depende de `T022`, `T023`) implementando vista provisional para `PENDING_ACCREDITATION` (sin banner verde, sin aviso de comprobante ni barra activa de cocina, con polling suave cada 5 s y enlace a `"Mis pedidos"`).

**Checkpoint**: `US1 + US2` forman el primer corte operativo completo en Render (preferencia, webhook seguro, confirmación atómica, comprobante y seguimiento fiel).

---

## Fase 4: Historia de usuario 4 — Sección "Mis pedidos" en Perfil y acceso rápido en navegación móvil y escritorio (Prioridad: P1)

**Objetivo**: Exponer `GET /api/v1/orders` con expiración perezosa y `ticketNumber` real, definir los DTOs y el cliente `orderApi.getMyOrders()` que también consumirá `US3`, organizar `/perfil` en pestañas con `#pedidos`, mostrar `MyOrdersSection` con regla Q5 y hacer visible `"Mis pedidos"` en `WorkspaceLayout` en todas las resoluciones.

**Prueba independiente**: `GET /api/v1/orders` devuelve únicamente los pedidos del comensal autenticado ordenados descendentemente con expiración perezosa aplicada; la cabecera visible en escritorio y móvil navega a `/perfil#pedidos`, activando `"Mis pedidos"` con acciones funcionales acordes a cada estado.

### Pruebas de US4

> **NOTA TDD**: Escribir o ampliar primero estas pruebas y confirmar que fallan antes de implementar `T030`–`T035`.

- [X] T026 [P] [US4] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderServiceTest.java` probando `getDinerOrders(UUID userId)` (ordenamiento descendente, aislamiento, expiración perezosa `expiresAt <= now` sobre orden/reserva/pago, `ticketNumber` real desde `KdsTicketRepository` o `null`, y `redirectUrl` priorizando `initPoint` o `null`).
- [X] T027 [P] [US4] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderControllerIntegrationTest.java` cubriendo `GET /api/v1/orders` con rol `COMENSAL`, payload `DinerOrderSummaryResponse` y expiración perezosa.
- [X] T028 [P] [US4] Crear `easyeat/frontend/tests/components/MyOrdersSection.test.tsx` probando listado ordenado, badges traducidos, estado vacío hacia `/catalogo`, acciones `"Ver seguimiento"`, `"Retomar pago"` y `"Cancelar intento"`, y regla Q5 sin `redirectUrl` (oculta/deshabilita `"Retomar pago"` informando al usuario).
- [X] T029 [P] [US4] Actualizar `easyeat/frontend/tests/pages/ProfilePage.test.tsx`, `easyeat/frontend/tests/components/WorkspaceLayout.test.tsx` y `easyeat/frontend/tests/router/AppRoutesProfileCatalog.test.tsx` verificando pestañas `"Datos y preferencias"` y `"Mis pedidos"`, activación por `#pedidos`, carga perezosa sin romper pruebas previas de Perfil, y enlace `"Mis pedidos"` visible en escritorio y móvil (`FR-009`) dentro de `WorkspaceLayout`.

### Implementación backend de US4

- [X] T030 [P] [US4] Crear `easyeat/backend/src/main/java/com/easyeat/order/dto/DinerOrderSummaryResponse.java` incluyendo `DinerOrderItemResponse`, `ticketNumber` nullable y `redirectUrl` nullable.
- [ ] T031 [US4] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` (depende de `T030`) conservando el constructor compatible de 5 parámetros junto al constructor completo `@Autowired` (`PaymentRepository`, `KdsTicketRepository`, `TemporaryReservationRepository`, `MercadoPagoProperties`) e implementando `getDinerOrders(UUID userId)` con expiración perezosa y `ticketNumber` real.
- [ ] T032 [US4] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderController.java` (depende de `T031`) exponiendo `@GetMapping` (`GET /api/v1/orders`) autorizado para `COMENSAL`.

### Implementación frontend de US4

- [ ] T033 [P] [US4] Actualizar `easyeat/frontend/src/types/order.ts`, `easyeat/frontend/src/types/payment.ts` y `easyeat/frontend/src/api/orderApi.ts` incorporando `DinerOrderSummaryResponse`, `DinerOrderItemResponse`, `orderId?` en `OrderErrorProblemDetail`, `'ACTIVE_RESERVATION_EXISTS'` en `PaymentErrorCode` y la función `getMyOrders()`.
- [ ] T034 [P] [US4] Crear `easyeat/frontend/src/components/profile/MyOrdersSection.tsx` (depende de `T033`) con tarjetas ordenadas, estado vacío, manejo de error, regla Q5 sin `redirectUrl`, y acciones `"Ver seguimiento"`, `"Retomar pago"` y `"Cancelar intento"`.
- [ ] T035 [US4] Actualizar `easyeat/frontend/src/pages/ProfilePage.tsx` (pestañas con soporte `#pedidos` invocando `getMyOrders` al abrir `"Mis pedidos"`) y `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` (enlace responsive `"Mis pedidos"` hacia `/perfil#pedidos` visible en escritorio y móvil).

**Checkpoint**: `GET /api/v1/orders`, tipos compartidos, sección `"Mis pedidos"` y menú móvil/escritorio completos y verificados.

---

## Fase 5: Historia de usuario 3 — Desbloqueo de conflicto `409 ACTIVE_RESERVATION_EXISTS`, cancelación uniforme, persistencia del carrito y deep-links (Prioridad: P1)

**Objetivo**: Devolver `orderId` en conflictos `409`, detectar proactivamente órdenes `PENDING_PAYMENT` al cargar `/checkout` usando `getMyOrders()` ya disponible, aplicar Q5 cuando falte `redirectUrl`, unificar la cancelación de orden/reserva/pago, persistir `easyeat_cart` en `localStorage` eliminando fugas entre pestañas y preservar rutas `/checkout` y `/pedidos` tras reautenticación.

**Prueba independiente**: Con orden `PENDING_PAYMENT` vigente (con o sin `redirectUrl`), `/checkout` despliega `InProcessPaymentPanel` desde `409` o al montar sin mostrar estado vacío contradictorio; cancelar libera orden, reserva y pago; `easyeat_cart` sobrevive en nueva pestaña manteniendo sesiones por pestaña y deep-links tras login.

### Pruebas de US3

> **NOTA TDD**: Escribir o ampliar primero estas pruebas y confirmar que fallan antes de implementar `T040`–`T046`.

- [ ] T036 [P] [US3] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderControllerIntegrationTest.java`, `PendingPaymentResolutionIntegrationTest.java` y `OrderServiceTest.java` probando propiedad `orderId` en conflictos `409 ACTIVE_RESERVATION_EXISTS` (`POST /orders` y `POST /checkout/pay`), `403 AccessDeniedException` en cancelaciones ajenas, y liberación conjunta de `TemporaryReservation` y `Payment` en `OrderService.cancelOrder`.
- [ ] T037 [P] [US3] Actualizar **ambas** suites `easyeat/frontend/src/test/cart/CartContext.test.tsx` y `easyeat/frontend/tests/context/CartContext.test.tsx` verificando persistencia de `easyeat_cart` en `localStorage` sin afectar `access_token` ni `easyeat_active_tenant` en `sessionStorage`.
- [ ] T038 [P] [US3] Actualizar mocks de `orderApi.getMyOrders` y escenarios en `easyeat/frontend/src/test/checkout/CheckoutPage.test.tsx`, `CheckoutPaymentFlow.test.tsx`, `CheckoutInteractionLock.test.tsx` y `easyeat/frontend/tests/checkout/IndividualCheckoutPayment.test.tsx` cubriendo conflicto `409 ACTIVE_RESERVATION_EXISTS`, acción `"Ver mi reserva"`, detección proactiva al montar con carrito vacío ocultando mensaje vacío contradictorio, y eliminación del fallback `selectedEstablishmentId`.
- [ ] T039 [P] [US3] Ampliar `easyeat/frontend/src/test/checkout/InProcessPaymentPanel.test.tsx` (regla Q5 sin `redirectUrl`), `easyeat/frontend/tests/router/postAuthRouting.test.ts` (rutas `/checkout` y `/pedidos` permitidas con `?` y `#`) y `easyeat/frontend/tests/router/RoleRouteGuard.test.tsx` (preservación de `pathname + search + hash` en `state.from`).

### Implementación backend de US3

- [ ] T040 [P] [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/exception/ActiveReservationExistsException.java` (`UUID orderId` con constructor compatible) y `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` adjuntando la propiedad `orderId` al `ProblemDetail` del `409`.
- [ ] T041 [P] [US3] Añadir `findActivePendingOrders(UUID userId, Instant now)` en `easyeat/backend/src/main/java/com/easyeat/order/repository/OrderRepository.java`.
- [ ] T042 [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutPaymentService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` (depende de `T040`, `T041`) para expirar pendientes vencidas antes del conflicto, lanzar `ActiveReservationExistsException` con `orderId`, usar `AccessDeniedException` en `cancelPaymentAttempt` y liberar reserva y pago en `OrderService.cancelOrder`.

### Implementación frontend de US3

- [ ] T043 [P] [US3] Actualizar `easyeat/frontend/src/context/CartContext.tsx` persistiendo únicamente `easyeat_cart` en `localStorage`.
- [ ] T044 [P] [US3] Actualizar `easyeat/frontend/src/router/postAuthRouting.ts` y `easyeat/frontend/src/router/RoleRouteGuard.tsx` agregando `/checkout` y `/pedidos` para `COMENSAL`, evaluando el path base sin query/hash y preservando `location.pathname + location.search + location.hash` en `state.from`.
- [ ] T045 [P] [US3] Actualizar `easyeat/frontend/src/components/checkout/InProcessPaymentPanel.tsx` implementando la regla Q5 cuando `redirectUrl` es nulo o vacío (ocultar/deshabilitar `"Retomar pago"`, explicar motivo y mantener `"Cancelar intento"`) y corrigiendo contraste claro.
- [ ] T046 [US3] Actualizar `easyeat/frontend/src/pages/CheckoutPage.tsx` (depende de `T043`, `T045`) detectando órdenes `PENDING_PAYMENT` al montar vía `getMyOrders()`, manejando `ACTIVE_RESERVATION_EXISTS` y `PAYMENT_IN_PROCESS` con `data.orderId`, conectando `onViewExistingOrder` hacia el panel con scroll/foco, ocultando `"Tu carrito está vacío"` mientras `InProcessPaymentPanel` esté activo, eliminando `localStorage.getItem('selectedEstablishmentId')`, refrescando al cancelar y corrigiendo rutas `/catalogo`.

**Checkpoint**: Todas las historias de usuario (`US1`, `US2`, `US4`, `US3`) completas y verificables de forma independiente.

---

## Fase 6: Verificación integral, pulido final y sincronización de documentación operativa

**Propósito**: Actualizar configuración documentada de entorno sin tocar `specs/017-render-deploy-docker/`, validar todas las suites backend/frontend y confirmar consistencia completa previa a la prueba manual en Render.

- [ ] T047 [P] Actualizar `easyeat/backend/.env.example` añadiendo `MERCADOPAGO_BACKEND_BASE_URL` y aclarando la obligatoriedad de `MERCADOPAGO_WEBHOOK_SECRET` en entornos con webhooks firmados.
- [ ] T048 [P] Actualizar `README.md` raíz enlazando la configuración de Mercado Pago en Render y [quickstart.md](./quickstart.md), dejando intactos los archivos de `specs/017-render-deploy-docker/`.
- [ ] T049 Ejecutar en `easyeat/backend` la suite de pruebas afectada (`mvn test -Dtest="*Order*,*Payment*,*Webhook*,*Kds*,*MercadoPago*,*Checkout*"`) y la suite de regresión completa (`mvn test`), validando cero fallos.
- [ ] T050 Ejecutar en `easyeat/frontend` todas las pruebas automatizadas (`npm test -- --run`, cubriendo `src/test/` y `tests/`) y la compilación de producción (`npm run build`), validando cero errores.
- [ ] T051 Verificar consistencia cruzada final contra [spec.md](./spec.md), [plan.md](./plan.md), [contracts/checkout-orders-contract.md](./contracts/checkout-orders-contract.md) y [quickstart.md](./quickstart.md) antes de la validación manual con navegador de incógnito en Render.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1 (Setup)**: Sin dependencias externas; desbloquea `Fase 2 (US1)`.
- **Fase 2 (US1)**: Depende de `Fase 1`; establece `redirectUrl` (`initPoint`), `notification_url` y `/payment-status` seguro.
- **Fase 3 (US2)**: Depende de `Fase 1` y reutiliza el flujo de `init_point` de `US1` en retorno; es independiente de `US4` y `US3`.
- **Fase 4 (US4)**: Depende de `Fase 1` (lectura opcional de `MercadoPagoProperties` en `OrderService`) y de `KdsTicketRepository` para `ticketNumber` consistente con `US2`; habilita `GET /api/v1/orders`, `DinerOrderSummaryResponse`, `getMyOrders()` y los tipos extendidos para `US3`.
- **Fase 5 (US3)**: Depende de `Fase 4 (US4)` (consumo directo de `getMyOrders()` y tipos actualizados en `CheckoutPage.tsx`) y de `OrderPaymentStatusController` de `US1`.
- **Fase 6 (Pulido y verificación)**: Depende de que `US1`–`US4` estén implementadas y verificadas localmente.

### Orden interno recomendado dentro de cada historia

1. Escribir o actualizar todas las tareas de prueba `[P]` de la historia y verificar fallo inicial.
2. Implementar modelos, DTOs, excepciones, repositorios y utilidades marcadas `[P]`.
3. Implementar servicios de aplicación y componentes de UI dependientes.
4. Conectar controladores REST, páginas React y validar el checkpoint antes de avanzar.

---

## Ejemplos de ejecución en paralelo por historia

### Historia de usuario 1 (`US1`)

```text
En paralelo (pruebas):       T002, T003, T004, T005
En paralelo (adapter/ctrl):  T006 || T008 (mientras T007 espera T006)
```

### Historia de usuario 2 (`US2`)

```text
En paralelo (pruebas):       T009, T010, T011, T012, T013, T014, T015
En paralelo (backend):       T016, T017, T018, T020, T021 (luego T019 tras T016 y T018)
En paralelo (frontend):      T022, T023 (luego T024 y T025 en paralelo tras T022/T023)
```

### Historia de usuario 4 (`US4`)

```text
En paralelo (pruebas):       T026, T027, T028, T029
En paralelo (código base):   T030 || T033 || T034 (T034 espera T033)
Secuencial backend:          T030 -> T031 -> T032
Secuencial frontend:         T033 -> T034 -> T035
```

### Historia de usuario 3 (`US3`)

```text
En paralelo (pruebas):       T036, T037, T038, T039
En paralelo (backend base):  T040 || T041 -> T042
En paralelo (frontend base): T043 || T044 || T045 -> T046
```

---

## Estrategia de implementación

1. **MVP estricto (`Fase 1 + Fase 2 [US1]`)**: Resuelve la generación limpia de preferencias en Mercado Pago (`init_point`, `back_urls`, `notification_url`) y asegura `/payment-status` desde el primer incremento.
2. **Primer corte operativo completo en Render (`+ Fase 3 [US2]`)**: Completa la confirmación idempotente hacia inventario, KDS y comprobantes (`AFTER_COMMIT`), webhook fail-closed con soporte `5xx` / bloqueo Q3, redirección automática y vista provisional fiel para `PENDING_ACCREDITATION` (Q2).
3. **Visibilidad sin dependencia SMTP (`+ Fase 4 [US4]`)**: Entrega `GET /api/v1/orders`, pestañas en `/perfil#pedidos` y menú móvil/escritorio para consultar y operar pedidos en cualquier estado.
4. **Resiliencia ante abandono y retorno multi-pestaña (`+ Fase 5 [US3]`)**: Elimina bloqueos opacos `409`, aplica Q5 sin `redirectUrl`, unifica cancelación de orden/reserva/pago, persiste el carrito entre pestañas y preserva deep-links post-login antes de cerrar en `Fase 6`.
