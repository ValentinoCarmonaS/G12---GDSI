# Lista de tareas: unificación de checkout Mercado Pago, webhook a cocina e historial de pedidos

**Entrada**: Documentos de diseño desde `/specs/018-checkout-mp-tracking-orders/`  
**Prerrequisitos**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), [contracts/checkout-orders-contract.md](./contracts/checkout-orders-contract.md)

---

## Fase 1: Preparación y configuración compartida

- [ ] T001 [Config] Añadir propiedad `backendBaseUrl` (`${MERCADOPAGO_BACKEND_BASE_URL:${RENDER_EXTERNAL_URL:}}`) con `@ConstructorBinding` y constructor secundario compatible de 6 argumentos en `easyeat/backend/src/main/java/com/easyeat/order/config/MercadoPagoProperties.java` y en `easyeat/backend/src/main/resources/application.yml`.

---

## Fase 2: Historia de usuario 1 — Pago en Mercado Pago con redirección oficial y notificación automática (P1)

**Objetivo**: Garantizar que las preferencias enviadas a Mercado Pago usen `back_urls` normalizadas y `notification_url` pública, priorizando `init_point`, y proteger `GET /api/v1/orders/{orderId}/payment-status` contra acceso ajeno y escaneo en memoria.

**Prueba independiente**: Al crear o consultar el estado de una preferencia de pago con entorno configurado, el payload hacia Mercado Pago y la URL normalizada entregada al cliente cumplen `FR-001`, `FR-002` y `FR-008`, rechazando accesos de usuarios que no sean dueños de la orden.

### Pruebas de US1

- [ ] T002 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/config/MercadoPagoPropertiesTest.java` para verificar el enlace de `backendBaseUrl`, respaldo con `RENDER_EXTERNAL_URL`, constructor compatible de 6 argumentos y valores por defecto.
- [ ] T003 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/service/MercadoPagoPublicUrlTest.java` para verificar saneamiento de barras finales en `back_urls`, inclusión de `notification_url` cuando `backendBaseUrl` es público, omisión cuando es local/vacío y normalización de cadenas vacías a `null`.
- [ ] T004 [P] [US1] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/CheckoutPaymentIntegrationTest.java` y crear `easyeat/backend/src/test/java/com/easyeat/order/OrderPaymentStatusControllerIntegrationTest.java` verificando prioridad de `init_point` incluso con `sandbox = true`, normalización de `redirectUrl` vacío a `null`, expiración `expiresAt <= now` y autorización `COMENSAL` + ownership (`403` ante usuario ajeno).

### Implementación de US1

- [ ] T005 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/MercadoPagoGatewayAdapter.java` para sanear barras finales de `frontendBaseUrl` y `backendBaseUrl`, enviar `notification_url` pública al crear la preferencia y normalizar cadenas vacías en `PreferenceResult` a `null`.
- [ ] T006 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutPaymentService.java` y `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderPaymentStatusController.java` para priorizar `initPoint` no blanco sobre `sandboxInitPoint`, usar `mercadopago.sandbox`, proteger `/payment-status` con `@PreAuthorize` de `COMENSAL` y ownership, reemplazar `findAll()` por `temporaryReservationRepository.findByOrderId(orderId)` y aplicar `expiresAt <= now`.

---

## Fase 3: Historia de usuario 2 — Confirmación atómica unificada, despacho a cocina, comprobante, webhook seguro y seguimiento real (P1)

**Objetivo**: Unificar el pipeline de confirmación entre webhook (`WebhookProcessingService`) y retorno de navegador, aplicar validación fail-closed con logs explícitos y reintentos `5xx` (Q4), bloquear aprobaciones tardías sobre órdenes `CANCELLED`/`EXPIRED` (Q3), emitir `OrderPaymentApprovedEvent` en `AFTER_COMMIT`, mostrar vista provisional en `PENDING_ACCREDITATION` con `ticketNumber` real desde `KdsTicketRepository` (Q2) y redirigir retornos aprobados a `/checkout/confirmacion/:orderId`.

**Prueba independiente**: Invocando webhook válido (JSON e IPN), confirmación autenticada (contrastada con pasarela si hay credenciales) o llamadas concurrentes sobre la misma orden se logra único descuento de stock, única comanda KDS con `ticketNumber` real, pago `APPROVED`, un `OrderReceipt`, bloqueo controlado ante `CANCELLED`/`EXPIRED`, `5xx` ante errores transitorios y seguimiento fiel en frontend.

### Pruebas de US2

- [ ] T007 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/WebhookSignatureValidatorTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/PaymentWebhookSecurityIntegrationTest.java` para verificar log explícito + `401` ante `x-signature` sin secreto (Q4), delegación de webhook hacia `WebhookProcessingService`, formato IPN (`topic`, `id`), descarte seguro `200 OK` en `merchant_order`, `200 OK` + log ante orden `CANCELLED`/`EXPIRED` (Q3) y propagación `5xx` ante fallo transitorio de pasarela/BD (Q4).
- [ ] T008 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/KdsIdempotencyIntegrationTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/IndividualCheckoutIntegrationTest.java` (o nueva prueba de confirmación/evento) verificando idempotencia concurrente e individual (`IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`, `PAID`, `CONFIRMED`), ownership, consulta `queryPayment` con `accessToken`, rechazo `409` en `CANCELLED`/`EXPIRED` sin débito de stock ni KDS, aprobación segura de `Payment`, mapeo `409` de `InsufficientIngredientStockException` y emisión única de `OrderPaymentApprovedEvent` tras commit.
- [ ] T009 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderConfirmationTrackingControllerTest.java`, `LiveTrackingScheduledCountdownTest.java`, `KitchenReadyForPickupIntegrationTest.java` y pruebas de servicio de tracking cubriendo `ticketNumber` leído desde `KdsTicketRepository` (`null` sin comanda), vista `PENDING_ACCREDITATION` (`approvalStatus="PENDING"` y progreso en cero), bloqueo `CANCELLED`/`EXPIRED`/`PENDING_PAYMENT`, aceptación de `PAID`/`CONFIRMED` y resolución de `markTicketReadyForPickup` por `KdsTicket`.
- [ ] T010 [P] [US2] Actualizar `easyeat/frontend/src/test/checkout/PaymentReturnPage.test.tsx`, `easyeat/frontend/src/test/tracking/OrderConfirmationPage.test.tsx`, `easyeat/frontend/src/test/tracking/OrderConfirmationPersistence.test.tsx`, `easyeat/frontend/src/test/tracking/LiveTrackingWidget.test.tsx` y `easyeat/frontend/src/test/hooks/useOrderLiveTrackingPolling.test.ts` verificando redirección aprobada a `/checkout/confirmacion/:orderId`, conflicto `409` tardío y `EXPIRED` devueltos a `/checkout` sin borrar carrito, enlaces en `PENDING_ACCREDITATION`, vista provisional sin banner verde/comprobante/barra activa con `ticketNumber: null`, y detención de polling en estados terminales.

### Implementación de US2

- [ ] T011 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/WebhookSignatureValidator.java`, `easyeat/backend/src/main/java/com/easyeat/order/controller/PaymentWebhookController.java`, `easyeat/backend/src/main/java/com/easyeat/order/service/WebhookProcessingService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/PaymentWebhookService.java` implementando log de alerta sin secreto, soporte IPN/JSON, filtro de tópicos no-pago, delegación unificada con protección contra retroceso, captura `200 OK` con log en `InvalidOrderStateException` (Q3) y propagación `5xx` en errores transitorios (Q4).
- [ ] T012 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutConfirmationServiceImpl.java`, `easyeat/backend/src/main/java/com/easyeat/order/service/OrderPaymentEventListener.java` y `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` implementando validación de ownership (`AccessDeniedException`), verificación vía `queryPayment` con `accessToken`, bloqueo Q3 de `CANCELLED`/`EXPIRED`, idempotencia multi-estado, actualización segura de `Payment`, emisión de `OrderPaymentApprovedEvent` escuchado `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, y handler `409` para `InsufficientIngredientStockException`.
- [ ] T013 [US2] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/service/OrderConfirmationTrackingService.java`, `easyeat/backend/src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java` y `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderConfirmationTrackingController.java` para obtener `ticketNumber` desde `KdsTicketRepository` (`null` antes de cocina), soportar `PENDING_ACCREDITATION` / `PAID` / `CONFIRMED`, bloquear `CANCELLED` / `EXPIRED` / `PENDING_PAYMENT`, validar ownership y resolver `markTicketReadyForPickup` vía `KdsTicketRepository`.
- [ ] T014 [US2] Actualizar `easyeat/frontend/src/pages/PaymentReturnPage.tsx`, `easyeat/frontend/src/types/orderTracking.ts`, `easyeat/frontend/src/pages/OrderConfirmationTrackingPage.tsx`, `easyeat/frontend/src/components/tracking/OrderSuccessGreenBanner.tsx`, `easyeat/frontend/src/components/tracking/LiveTrackingWidget.tsx`, `easyeat/frontend/src/components/tracking/ReadyForPickupCallout.tsx`, `easyeat/frontend/src/components/checkout/PendingAccreditationBanner.tsx` y `easyeat/frontend/src/hooks/useOrderLiveTrackingPolling.ts` cumpliendo Q2, `FR-005` y rutas `/catalogo` con contraste legible.

---

## Fase 4: Historia de usuario 3 — Desbloqueo de conflicto `409 ACTIVE_RESERVATION_EXISTS`, cancelación uniforme, persistencia de carrito y deep-links (P1)

**Objetivo**: Devolver `orderId` en conflictos `409`, detectar proactivamente órdenes `PENDING_PAYMENT` al cargar `/checkout`, aplicar la regla Q5 sin `redirectUrl`, unificar la cancelación completa de orden/reserva/pago, conservar `easyeat_cart` en `localStorage` eliminando fugas entre pestañas y preservar rutas `/checkout` y `/pedidos` tras reautenticación.

**Prueba independiente**: Con orden `PENDING_PAYMENT` vigente (con o sin `redirectUrl`), `/checkout` muestra `InProcessPaymentPanel` desde el conflicto `409` o al montar sin mostrar estado de carrito vacío contradictorio; cancelar libera orden, reserva y pago; `easyeat_cart` sobrevive en nueva pestaña preservando tokens por pestaña y deep-links tras login.

### Pruebas de US3

- [ ] T015 [P] [US3] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderControllerIntegrationTest.java`, `easyeat/backend/src/test/java/com/easyeat/order/OrderServiceTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/PendingPaymentResolutionIntegrationTest.java` validando propiedad `orderId` en `409 ACTIVE_RESERVATION_EXISTS`, compatibilidad de constructores, respuesta `403` ante cancelación ajena y liberación conjunta de `TemporaryReservation` y `Payment` en `OrderService.cancelOrder`.
- [ ] T016 [P] [US3] Actualizar **ambas** suites `easyeat/frontend/src/test/cart/CartContext.test.tsx` y `easyeat/frontend/tests/context/CartContext.test.tsx` para verificar persistencia de `easyeat_cart` en `localStorage`; actualizar mocks de `orderApi.getMyOrders` y pruebas en `easyeat/frontend/src/test/checkout/{CheckoutPage.test.tsx,CheckoutPaymentFlow.test.tsx,CheckoutInteractionLock.test.tsx,InProcessPaymentPanel.test.tsx}` y `easyeat/frontend/tests/checkout/IndividualCheckoutPayment.test.tsx` cubriendo `409 ACTIVE_RESERVATION_EXISTS`, detección al montar con carrito vacío, Q5 sin `redirectUrl`, y añadir pruebas de `postAuthRouting` / `RoleRouteGuard` para `/checkout` y `/pedidos` con query/hash.

### Implementación de US3

- [ ] T017 [US3] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/exception/ActiveReservationExistsException.java`, `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java`, `easyeat/backend/src/main/java/com/easyeat/order/repository/OrderRepository.java`, `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutPaymentService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` para adjuntar `orderId` en `409`, usar `AccessDeniedException` en cancelaciones ajenas y liberar reserva + pago en `cancelOrder`.
- [ ] T018 [US3] Actualizar `easyeat/frontend/src/context/CartContext.tsx`, `easyeat/frontend/src/router/postAuthRouting.ts`, `easyeat/frontend/src/router/RoleRouteGuard.tsx`, `easyeat/frontend/src/components/checkout/InProcessPaymentPanel.tsx` y `easyeat/frontend/src/pages/CheckoutPage.tsx` aplicando `localStorage` solo al carrito, eliminando `localStorage.getItem('selectedEstablishmentId')`, manejando `409` + carga inicial + Q5 + ocultación de carrito vacío bajo panel activo + `/catalogo` + contraste claro, y preservando deep-links de `COMENSAL`.

---

## Fase 5: Historia de usuario 4 — Sección "Mis pedidos" en Perfil y acceso rápido en navegación móvil y escritorio (P1)

**Objetivo**: Exponer `GET /api/v1/orders` con expiración perezosa y número real de comanda, organizar `/perfil` en pestañas con `#pedidos`, mostrar `MyOrdersSection` con soporte de Q5 y hacer visible `"Mis pedidos"` en `WorkspaceLayout` en todas las resoluciones.

**Prueba independiente**: `GET /api/v1/orders` devuelve los pedidos del comensal ordenados descendentemente con expiración perezosa; el menú superior accesible en escritorio y móvil lleva a `/perfil#pedidos` activando `"Mis pedidos"` con acciones válidas por estado.

### Pruebas de US4

- [ ] T019 [P] [US4] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/OrderControllerIntegrationTest.java` y `easyeat/backend/src/test/java/com/easyeat/order/OrderServiceTest.java` cubriendo `GET /api/v1/orders` (orden descendente, aislamiento por `userId`, expiración perezosa `expiresAt <= now`, `ticketNumber` real o `null`, `redirectUrl` normalizado y `trackingUrl`).
- [ ] T020 [P] [US4] Actualizar `easyeat/frontend/tests/pages/ProfilePage.test.tsx`, `easyeat/frontend/tests/components/WorkspaceLayout.test.tsx` y `easyeat/frontend/tests/router/AppRoutesProfileCatalog.test.tsx`, y crear `easyeat/frontend/src/test/profile/MyOrdersSection.test.tsx` (o bajo `tests/components/`) verificando pestañas en Perfil, `#pedidos`, acciones de `MyOrdersSection` incluida regla Q5 sin `redirectUrl`, y visibilidad del enlace `"Mis pedidos"` en móvil y escritorio dentro de `WorkspaceLayout`.

### Implementación de US4

- [ ] T021 [US4] Crear `easyeat/backend/src/main/java/com/easyeat/order/dto/DinerOrderSummaryResponse.java` e implementar `getDinerOrders(UUID userId)` y `GET /api/v1/orders` en `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` y `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderController.java`.
- [ ] T022 [US4] Actualizar `easyeat/frontend/src/types/order.ts`, `easyeat/frontend/src/types/payment.ts` y `easyeat/frontend/src/api/orderApi.ts` incorporando `DinerOrderSummaryResponse`, `DinerOrderItemResponse`, `orderId?` en errores `409`, `'ACTIVE_RESERVATION_EXISTS'` en errores de pago y `getMyOrders()`.
- [ ] T023 [US4] Crear `easyeat/frontend/src/components/profile/MyOrdersSection.tsx`, integrar pestañas con soporte `#pedidos` en `easyeat/frontend/src/pages/ProfilePage.tsx` (disparando `getMyOrders` al activar la pestaña sin romper pruebas de perfil) y añadir el enlace responsive `"Mis pedidos"` (`/perfil#pedidos`) en `easyeat/frontend/src/components/common/WorkspaceLayout.tsx`.

---

## Fase 6: Verificación integral y sincronización de documentación operativa

- [ ] T024 Ejecutar en `easyeat/backend` la suite de pruebas afectada y regresión de `order`/`mvn test` validando cero fallos.
- [ ] T025 Ejecutar en `easyeat/frontend` las pruebas completas (`npm test -- --run`, cubriendo `src/test/` y `tests/`) y compilación (`npm run build`) validando cero errores de tipos o compilación.
- [ ] T026 Actualizar `easyeat/backend/.env.example` (incorporando `MERCADOPAGO_BACKEND_BASE_URL` y nota de obligatoriedad para `MERCADOPAGO_WEBHOOK_SECRET`) y `README.md` raíz (enlazando la configuración de `018` y [quickstart.md](./quickstart.md)), manteniendo intactos los archivos de `specs/017-render-deploy-docker/` y verificando consistencia final contra [spec.md](./spec.md) y [plan.md](./plan.md).
