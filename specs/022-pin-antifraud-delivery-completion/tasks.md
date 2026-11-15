# Tareas de implementación: protocolo antifraude de entrega y cierre de orden

**Entrada**: Documentos de diseño desde `/specs/022-pin-antifraud-delivery-completion/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)  
**Prerrequisitos**: Plan técnico aprobado, modelo de datos definido, contratos establecidos.  
**Estrategia de pruebas**: TDD estricto (Red-Green-Refactor). Cada historia de usuario implementa primero las pruebas automatizadas (RED) que deben fallar antes de escribir el código de producción (GREEN).

---

## Formato: `[ID] [P?] [Story] Descripción con ruta de archivo`

- **[P]**: Ejecutable en paralelo (archivos independientes, sin dependencias con tareas pendientes).
- **[Story]**: Historia de usuario a la que pertenece (`[US1]`, `[US2]`, `[US3]`). Omitido en fases de Setup, Foundational y Polish.
- Todas las rutas son absolutas o relativas al repositorio.

---

## Phase 1: Setup (Infraestructura compartida y base)

**Propósito**: Inicialización de esquemas relacionales, contratos y clientes base.

- [ ] T001 Crear migración Flyway `V25__add_security_code_and_completed_at_to_orders.sql` para columnas `security_code` y `completed_at` en `easyeat/backend/src/main/resources/db/migration/V25__add_security_code_and_completed_at_to_orders.sql`
- [ ] T002 [P] Definir tipos TypeScript de mostrador y extender tipos de seguimiento (`CounterReadyOrder`, `securityCode`, `COMPLETED`) en `easyeat/frontend/src/types/counter.ts` y `easyeat/frontend/src/types/orderTracking.ts`
- [ ] T003 [P] Configurar cliente API HTTP de mostrador en `easyeat/frontend/src/api/counterApi.ts`

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**Propósito**: Modelos de datos centrales y componentes base reutilizables. Ninguna historia de usuario puede completarse sin esta fase.

- [ ] T004 [P] Escribir RED test unitario para entidad `Order` (generación de PIN en `@PrePersist` [1000, 9999], inmutabilidad y setter de `completedAt`) en `easyeat/backend/src/test/java/com/easyeat/order/domain/OrderSecurityCodeTest.java`
- [ ] T005 Implementar campos `securityCode`, `completedAt` y hook `@PrePersist` con `ThreadLocalRandom.current().nextInt(1000, 10000)` en `easyeat/backend/src/main/java/com/easyeat/order/domain/Order.java`
- [ ] T006 [P] Escribir RED test de migración Flyway y restricciones de rango relacional (`CHECK >= 1000 AND <= 9999`) en `easyeat/backend/src/test/java/com/easyeat/order/repository/OrderSecurityCodeMigrationTest.java`
- [ ] T007 Validar y aplicar migración Flyway `V25` sobre base de datos relacional
- [ ] T008 [P] Escribir RED test unitario para componente compartido `SecurityPinDisplay` (formato `d d d d`, monospace, tracking amplio y fallback `- - - -`) en `easyeat/frontend/src/test/components/SecurityPinDisplay.test.tsx`
- [ ] T009 Implementar componente visual compartido `SecurityPinDisplay.tsx` con tipografía monospace y tracking amplio en `easyeat/frontend/src/components/common/SecurityPinDisplay.tsx`

**Punto de control**: Fundamentos listos. Las historias de usuario pueden proceder de manera independiente.

---

## Phase 3: User Story 1 - Código numérico de seguridad de retiro oral (Protocolo Antifraude) (Prioridad: P1) 🎯 MVP

**Objetivo**: Generar y persistir un PIN de 4 dígitos en $[1000, 9999]$ al registrar/pagar la orden y exhibirlo en formato monospace amplio (`d d d d`) en la pantalla de seguimiento del comensal y en la tarjeta de mostrador para cotejo oral humano sin hardware óptico (`ENC-3.2.1`).

**Criterio de prueba independiente**: Crear una orden pagada y verificar que almacene un `security_code` en $[1000, 9999]$ en base de datos, visible en `/seguimiento/{orderId}` con espaciado amplio y visible de forma destacada en la tarjeta desplegada de `/mostrador`.

### Pruebas para User Story 1 (TDD RED)

- [ ] T010 [P] [US1] Escribir RED test de integración para persistencia y no nulidad de `security_code` en flujo de checkout en `easyeat/backend/src/test/java/com/easyeat/order/service/OrderPinCreationIntegrationTest.java`
- [ ] T011 [P] [US1] Escribir RED test de contrato/endpoint para `GET /api/v1/orders/{orderId}/live-tracking` (debe exponer `securityCode` cuando `status == READY_FOR_PICKUP`) en `easyeat/backend/src/test/java/com/easyeat/order/controller/OrderLiveTrackingPinContractTest.java`
- [ ] T012 [P] [US1] Escribir RED test de frontend para `ReadyForPickupCallout.tsx` (debe renderizar `<SecurityPinDisplay />` al estar listo) en `easyeat/frontend/src/test/tracking/ReadyForPickupCalloutPin.test.tsx`

### Implementación para User Story 1 (GREEN)

- [ ] T013 [US1] Extender record DTO `OrderLiveTrackingResponse` con campo `Integer securityCode` en `easyeat/backend/src/main/java/com/easyeat/order/dto/OrderLiveTrackingResponse.java`
- [ ] T014 [US1] Exponer `securityCode` en `OrderLiveTrackingService.java` para órdenes en estado `READY_FOR_PICKUP` en `easyeat/backend/src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java`
- [ ] T015 [US1] Integrar `<SecurityPinDisplay />` en `ReadyForPickupCallout.tsx` para exhibición en pantalla de comensal en `easyeat/frontend/src/components/tracking/ReadyForPickupCallout.tsx`
- [ ] T016 [US1] Integrar `<SecurityPinDisplay />` en la tarjeta de mostrador `CounterTicketCard.tsx` para el cotejo oral del operador en `easyeat/frontend/src/components/counter/CounterTicketCard.tsx`

**Punto de control**: User Story 1 completamente funcional y verificable como MVP.

---

## Phase 4: User Story 2 - Marcar pedido como "Entregado" y archivar ciclo de orden (Prioridad: P1)

**Objetivo**: Cierre terminal atómico (`Order.status = COMPLETED`, `KdsTicket.status = COMPLETED`, `completed_at = now`), idempotencia absoluta en reintentos, remoción de mostrador sin layout shift, mensaje final en seguimiento del comensal, purga total del PIN en red y cese de polling (`ENC-3.3.2`).

**Criterio de prueba independiente**: Tomar una orden en `READY_FOR_PICKUP`, invocar `complete-delivery`: comprobar transición atómica e inmutable en BD, 200 OK idempotente ante llamadas repetidas, remoción de la tarjeta en mostrador sin colapsar tarjetas adyacentes, y pantalla de comensal con mensaje final, resumen íntegro, sin PIN y sin sondeos posteriores.

### Pruebas para User Story 2 (TDD RED)

- [ ] T017 [P] [US2] Escribir RED test unitario para transición atómica e idempotencia de `completeDelivery` en `easyeat/backend/src/test/java/com/easyeat/order/service/CounterOrderServiceTest.java`
- [ ] T018 [P] [US2] Escribir RED test de integración con Testcontainers para `POST /api/v1/counter/orders/{orderId}/complete-delivery` (200 inicial, 200 idempotente, 409 si no está lista, 404 si no existe, indivisibilidad con `KdsTicket`) en `easyeat/backend/src/test/java/com/easyeat/order/service/CompleteDeliveryIntegrationTest.java`
- [ ] T019 [P] [US2] Escribir RED test de controlador web MVC para autorización y códigos HTTP en `easyeat/backend/src/test/java/com/easyeat/order/controller/CounterOrderControllerTest.java`
- [ ] T020 [P] [US2] Escribir RED test para `OrderLiveTrackingService` en estado `COMPLETED` (mensaje terminal y `securityCode == null`) en `easyeat/backend/src/test/java/com/easyeat/order/service/OrderLiveTrackingCompletedTest.java`
- [ ] T021 [P] [US2] Escribir RED test de frontend para detención definitiva del polling ante `COMPLETED` en `easyeat/frontend/src/test/hooks/useOrderLiveTrackingPollingCompleted.test.ts`
- [ ] T022 [P] [US2] Escribir RED test de frontend para componente terminal `DeliveredOrderCallout.tsx` (mensaje final, resumen y ausencia de PIN) en `easyeat/frontend/src/test/tracking/DeliveredOrderCallout.test.tsx`

### Implementación para User Story 2 (GREEN)

- [ ] T023 [US2] Crear records DTO `CompleteDeliveryResponse` y `CounterReadyOrderResponse` en `easyeat/backend/src/main/java/com/easyeat/order/dto/`
- [ ] T024 [US2] Implementar `CounterOrderService` con bloqueo pesimista `orderRepository.findByIdForUpdate`, validación de estado previo (409 Conflict), transición atómica e idempotencia en `easyeat/backend/src/main/java/com/easyeat/order/service/CounterOrderService.java`
- [ ] T025 [US2] Implementar `CounterOrderController` exponiendo `POST /api/v1/counter/orders/{orderId}/complete-delivery` y `GET /api/v1/counter/orders` protegido con roles `ENCARGADO` y `ADMINISTRADOR` en `easyeat/backend/src/main/java/com/easyeat/order/controller/CounterOrderController.java`
- [ ] T026 [US2] Adaptar `OrderLiveTrackingService` para retornar `status = COMPLETED`, mensaje terminal `"Pedido Entregado — ¡Que disfrutes tu comida!"` y purgar `securityCode = null` en `easyeat/backend/src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java`
- [ ] T027 [US2] Ajustar `useOrderLiveTrackingPolling.ts` removiendo `READY_FOR_PICKUP` de los estados de detención y abortando el timer definitivamente ante `COMPLETED` en `easyeat/frontend/src/hooks/useOrderLiveTrackingPolling.ts`
- [ ] T028 [US2] Implementar componente terminal `DeliveredOrderCallout.tsx` en `easyeat/frontend/src/components/tracking/DeliveredOrderCallout.tsx`
- [ ] T029 [US2] Integrar `DeliveredOrderCallout` en `LiveTrackingWidget.tsx` y `OrderConfirmationTrackingPage.tsx` garantizando visualización de resumen sin PIN en `easyeat/frontend/src/components/tracking/LiveTrackingWidget.tsx`
- [ ] T030 [US2] Implementar método `completeDelivery(orderId: string)` en `easyeat/frontend/src/api/counterApi.ts`
- [ ] T031 [US2] Integrar remoción de tarjeta entregada en `CounterWorkspacePage.tsx` preservando el estado desplegado de tarjetas adyacentes (`expandedOrderIds`) en `easyeat/frontend/src/pages/CounterWorkspacePage.tsx`

**Punto de control**: User Story 2 completamente funcional y sincronizada de extremo a extremo.

---

## Phase 5: User Story 3 - Confirmación de coincidencia y acción directa de entrega (Prioridad: P2)

**Objetivo**: Incorporar en la tarjeta desplegada de mostrador el botón directo "ENTREGAR PEDIDO", inhabilitación síncrona instantánea contra doble clic (`isSubmitting = true`), retroalimentación verde inmediata ("Entrega Confirmada") sin modales y aviso no intrusivo ante errores de red (`ENC-3.3.1`).

**Criterio de prueba independiente**: Desplegar una tarjeta en `/mostrador`, presionar "ENTREGAR PEDIDO": comprobar inhabilitación instantánea (< 50 ms), distintivo verde y despacho de petición sin modales; simular doble clic rápido verificando un único POST; simular corte de red y constatar aviso no intrusivo con botón rehabilitado.

### Pruebas para User Story 3 (TDD RED)

- [ ] T032 [P] [US3] Escribir RED test para botón "ENTREGAR PEDIDO" en `CounterTicketCard` (acción directa sin modal, inhabilitación instantánea y feedback verde "Entrega Confirmada") en `easyeat/frontend/src/test/counter/CounterTicketCardDeliveryAction.test.tsx`
- [ ] T033 [P] [US3] Escribir RED test para protección anti doble clic (múltiples clics concurrentes generan una sola llamada a `completeDelivery`) en `easyeat/frontend/src/test/counter/CounterTicketCardAntiDoubleClick.test.tsx`
- [ ] T034 [P] [US3] Escribir RED test para manejo de error de red (notificación no intrusiva, botón rehabilitado y tarjeta conservada) en `easyeat/frontend/src/test/counter/CounterTicketCardErrorRecovery.test.tsx`

### Implementación para User Story 3 (GREEN)

- [ ] T035 [US3] Implementar botón "ENTREGAR PEDIDO", estados reactivos `isSubmitting` e `isDelivered`, distintivo verde de feedback y protección anti doble clic en `easyeat/frontend/src/components/counter/CounterTicketCard.tsx`
- [ ] T036 [US3] Implementar notificación no intrusiva de error de comunicación y lógica de rollback ante excepciones de red en `easyeat/frontend/src/components/counter/CounterTicketCard.tsx`

**Punto de control**: User Story 3 completamente funcional, ergonómica y blindada en interfaz.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Propósito**: Verificación de fronteras, pruebas de regresión completas y validación operativa.

- [ ] T037 [P] Escribir test de aislamiento con KDS de cocina (spec 019): verificar exclusión de órdenes `COMPLETED` en `easyeat/backend/src/test/java/com/easyeat/order/controller/KitchenKdsExclusionTest.java`
- [ ] T038 [P] Ejecutar la suite completa de pruebas unitarias y de integración de backend: `./mvnw test`
- [ ] T039 [P] Ejecutar la suite completa de pruebas de frontend: `npm run test`
- [ ] T040 Ejecutar validación end-to-end de los 13 escenarios (a-m) conforme a `specs/022-pin-antifraud-delivery-completion/quickstart.md`

---

## Dependencias y orden de ejecución

### Dependencias entre fases

```
Phase 1: Setup
       ↓
Phase 2: Foundational (Bloqueante)
       ↓
Phase 3: User Story 1 (P1 - MVP: Generación y cotejo de PIN)
       ↓
Phase 4: User Story 2 (P1: Transición atómica, idempotencia y cierre de ciclo)
       ↓
Phase 5: User Story 3 (P2: Botón directo, feedback verde y anti doble clic)
       ↓
Phase 6: Polish & Cross-Cutting (Aislamiento KDS y Quickstart end-to-end)
```

### Oportunidades de ejecución paralela

- **En Phase 1**: `T002` (tipos frontend) y `T003` (cliente API) pueden ejecutarse en paralelo con `T001` (Flyway SQL).
- **En Phase 2**: `T004` (test Order), `T006` (test migración) y `T008` (test componente PIN) pueden escribirse en paralelo.
- **En Phase 3 (US1)**: `T010` (test backend creación), `T011` (test contrato tracking) y `T012` (test frontend PIN) pueden desarrollarse en paralelo.
- **En Phase 4 (US2)**: `T017` (test servicio), `T018` (test integración Testcontainers), `T019` (test controlador), `T020` (test live-tracking service), `T021` (test hook polling) y `T022` (test componente terminal) pueden ejecutarse en paralelo.
- **En Phase 5 (US3)**: `T032` (test botón/badge), `T033` (test anti doble clic) y `T034` (test error recovery) pueden ejecutarse en paralelo.
- **En Phase 6**: `T037`, `T038` y `T039` pueden ejecutarse en paralelo.

---

## Estrategia de implementación

### MVP First (User Story 1 únicamente)
1. Completar Setup (`T001`–`T003`) y Foundational (`T004`–`T009`).
2. Completar User Story 1 (`T010`–`T016`).
3. **Pausar y validar**: Generar orden pagada, verificar PIN en base de datos, en seguimiento comensal y en tarjeta de mostrador. Demostrable como MVP del protocolo antifraude oral.

### Entrega incremental
1. Tras validar US1, avanzar con US2 (`T017`–`T031`): se logra el cierre transaccional completo en base de datos, idempotencia de backend y la vista terminal en comensal.
2. Incorporar US3 (`T032`–`T036`): refina la ergonomía visual en ventanilla con el botón directo, feedback verde inmediato y protección anti doble clic.
3. Ejecutar Polish (`T037`–`T040`): verificación de aislamiento con spec 019 y corrida de los 13 escenarios de `quickstart.md`.
