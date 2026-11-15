# Lista de tareas: seguimiento en vivo y demora granular (+5)

**Entrada**: Documentos de diseño desde `/specs/021-live-tracking-delay-adjustment/`  
**Prerrequisitos**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), [contracts/live-tracking-and-delay-contract.md](./contracts/live-tracking-and-delay-contract.md)

## Formato de tareas: `[ID] [P?] [Historia] Descripción con ruta de archivo`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos y sin dependencia de tareas incompletas en la misma fase).
- **[USx]**: Historia de usuario a la que pertenece la tarea (`US1`, `US2`), omitida en preparación compartida, base fundamental y pulido final.
- **Enfoque TDD**: En cada historia de usuario, las tareas de prueba (*Red Tests*) se redactan y ejecutan primero para constatar el fallo antes de abordar la implementación (*Green Implementation*).

---

## Fase 1: Preparación compartida (Setup) — [GitHub Issue #352](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/352)

**Propósito**: Crear los DTOs y modelos de transporte compartidos requeridos por los contratos HTTP en backend y frontend.

- [ ] T001 [P] Crear DTO de respuesta `KdsDelayIncrementResponse.java` en `easyeat/backend/src/main/java/com/easyeat/order/dto/` con campos `ticketId`, `orderId`, `ticketNumber`, `specificDelayMinutes`, `estimatedTotalMinutes`, `updatedAt`.
- [ ] T002 [P] Extender el DTO `OrderLiveTrackingResponse.java` en `easyeat/backend/src/main/java/com/easyeat/order/dto/` incorporando `positionAhead`, `estimatedTotalMinutes`, `elapsedMinutes`, `remainingMinutes`, `progressPercent`, `specificDelayMinutes`, `securityCode`, `startedAt`, `readyAt`, y record `LiveTrackingItemDto(dishName, quantity)`.
- [ ] T003 [P] Extender interfaces TypeScript de seguimiento en `easyeat/frontend/src/types/orderTracking.ts` y de cocina en `easyeat/frontend/src/types/kds.ts` incorporando `OrderLiveTrackingData`, `KdsDelayIncrementResponse`, `LiveTrackingItem` y tipado `isDisconnected: boolean` en `UseOrderLiveTrackingPollingResult`.
- [ ] T004 [P] Actualizar clientes HTTP en `easyeat/frontend/src/api/orderTrackingApi.ts` y `easyeat/frontend/src/api/kitchenKdsApi.ts` exponiendo `addTicketDelay(ticketId)` y tipando la respuesta de `getLiveTracking`.

**Checkpoint**: Modelos de transporte definidos y consistentes entre backend y frontend.

---

## Fase 2: Base fundamental (Foundational) — [GitHub Issue #353](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/353)

**Propósito**: Establecer la migración de base de datos Flyway V27 (`specific_delay_minutes` en `kds_tickets` y `security_code` en `orders`), mapeo de entidades JPA, métodos de repositorio pesimista y FIFO, y componente de conectividad antes de abordar las historias operativas.

- [ ] T005 [P] Crear migración Flyway `V27__kds_specific_delay_minutes.sql` en `easyeat/backend/src/main/resources/db/migration/` incorporando la columna `specific_delay_minutes INTEGER NOT NULL DEFAULT 0` con restricción `CHECK (specific_delay_minutes >= 0)` en `kds_tickets`, y la columna `security_code VARCHAR(4) NULL` en `orders` de forma idempotente (`ADD COLUMN IF NOT EXISTS`).
- [ ] T006 [P] Extender entidad JPA `KdsTicket.java` mapeando la propiedad `specificDelayMinutes` (default 0, getters/setters) y entidad JPA `Order.java` en `easyeat/backend/src/main/java/com/easyeat/order/domain/` mapeando `securityCode` (`@Column(name = "security_code", length = 4)`, getters/setters).
- [ ] T007 [P] Agregar métodos `countQueuedTicketsAhead` (cómputo FIFO de cola activa para `pickupDate <= :today`) y `findByIdForUpdate(@Param("id") UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `easyeat/backend/src/main/java/com/easyeat/order/repository/KdsTicketRepository.java`.
- [ ] T008 [P] Crear componente de advertencia de conectividad `ConnectionStatusBadge.tsx` en `easyeat/frontend/src/components/common/` exhibiendo la leyenda "Sin conexión — Reintentando..." de forma sutil y no bloqueante.

**Checkpoint**: Base de persistencia (columnas en base de datos, entidades y métodos de repositorio) e infraestructura UI de conectividad listas para implementar historias.

---

## Fase 3: Historia de usuario 1 — Visualización base del seguimiento en vivo por estados y posición en cola (Prioridad: P1) 🎯 MVP — [GitHub Issue #354](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/354)

**Objetivo**: Pantalla del comensal (`/seguimiento/{orderId}`) adaptativa con polling silencioso cada 6 s (cabeceras `no-cache`); en `QUEUED` muestra `#ticketNumber`, "En cola en cocina", "Tenés $N$ pedidos por delante" (o "Tenés 0 pedidos por delante" cuando $N=0$) con minutero y barra ocultos; al pulsar "EMPEZAR" conmuta a `IN_PREPARATION` con icono dinámico, minutero y barra; al pulsar "LISTO" conmuta a `READY_FOR_PICKUP` con panel verde, código de 4 dígitos y platos, purgando minutero y barra; tolera cortes transitorios y despliega insignia sutil ante cortes $>15 \text{ s}$ (`COM-8.1.1` — `RF-001` a `RF-011`, `RF-018` a `RF-026`).

**Prueba independiente**: Cargar orden en cola; verificar en `/seguimiento/{orderId}` visualización de `#ticketNumber`, "En cola en cocina" y "Tenés N pedidos por delante" con temporizador y barra ocultos; comprobar que $N=0$ muestra literalmente "Tenés 0 pedidos por delante"; presionar "EMPEZAR" en cocina y constatar conmutación a "Cocinando / En preparación" con minutero y barra en $\le 8$ s; presionar "LISTO" y constatar paso al panel verde con código de 4 dígitos y purga de minutero/barra; simular corte de red $>15$ s verificando la insignia y su retiro al reconectar.

### Pruebas TDD de US1 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de abordar la implementación `T013`–`T017`.

- [ ] T009 [P] [US1] Crear prueba de integración transaccional en `easyeat/backend/src/test/java/com/easyeat/order/KdsQueueFifoPositionIntegrationTest.java` verificando el conteo determinista de órdenes precedentes en cola (`countQueuedTicketsAhead`) bajo regla FIFO (`pickup_date, time_slot_start, queued_at, id`), exclusión de tickets en `IN_PREPARATION` del conteo de cola previa y caso frontera $N=0$ cuando el ticket está al tope.
- [ ] T010 [P] [US1] Crear prueba `@WebMvcTest` en `easyeat/backend/src/test/java/com/easyeat/order/OrderLiveTrackingControllerIntegrationTest.java` verificando `GET /api/v1/orders/{orderId}/live-tracking` para rol `COMENSAL` en los 3 estados: `QUEUED` (con `positionAhead`, minutero/barra nulos), `IN_PREPARATION` (con minutero activo, barra y leyenda de cola ausente) y `READY_FOR_PICKUP` (con `securityCode`, métricas purgadas y cabeceras `Cache-Control: no-cache, no-store, must-revalidate`).
- [ ] T011 [P] [US1] Actualizar y extender pruebas unitarias en `easyeat/frontend/src/test/hooks/useOrderLiveTrackingPolling.test.ts` adaptando la suite preexistente de Spec 016 para verificar ciclo de polling regular de 6 segundos, cabeceras `no-cache`, cancelación con `AbortController` al desmontar, pausa ante `document.visibilityState === 'hidden'`, activación del flag `isDisconnected = true` tras fallas consecutivas $>15$ segundos y retiro automático al primer éxito.
- [ ] T012 [P] [US1] Actualizar y extender pruebas de componente en `easyeat/frontend/src/test/tracking/LiveTrackingWidget.test.tsx` adaptando la suite preexistente de Spec 016 para verificar renderizado de `QUEUED` (insignia "En cola en cocina", leyenda "Tenés N pedidos por delante", caso literal "Tenés 0 pedidos por delante", ausencia de minutero y barra), renderizado de `IN_PREPARATION` (icono dinámico, temporizador y barra visibles) y renderizado de `READY_FOR_PICKUP` (panel verde de éxito, código de seguridad de 4 dígitos, platos y purga de minutero/barra).

### Implementación de US1 (Green Implementation)

- [ ] T013 [US1] Implementar cálculo adaptativo en `easyeat/backend/src/main/java/com/easyeat/order/service/OrderLiveTrackingService.java` resolviendo `positionAhead` mediante `countQueuedTicketsAhead` en `QUEUED`, activación de métricas temporales en `IN_PREPARATION` y purga visual con provisión de `securityCode` en `READY_FOR_PICKUP`.
- [ ] T014 [US1] Actualizar `getLiveTracking` en `easyeat/backend/src/main/java/com/easyeat/order/controller/OrderConfirmationTrackingController.java` mapeando la orden y comanda a `OrderLiveTrackingResponse` asegurando cabeceras `no-cache, no-store, must-revalidate`.
- [ ] T015 [US1] Implementar hook `useOrderLiveTrackingPolling.ts` en `easyeat/frontend/src/hooks/` con intervalo regular de 6 s, `visibilitychange`, `AbortController`, exposición de `isDisconnected: boolean` tras umbral de 15 s e idempotencia visual con `useRef` para evitar parpadeos y reinicios ante datos idénticos.
- [ ] T016 [US1] Adaptar `LiveTrackingWidget.tsx` en `easyeat/frontend/src/components/tracking/` para renderizar las tres etapas operativas (`QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`) con sus insignias, leyendas, código de 4 dígitos e integración de `ConnectionStatusBadge`.
- [ ] T017 [US1] Configurar la ruta `/seguimiento/:orderId` en `easyeat/frontend/src/router/AppRoutes.tsx` y actualizar `easyeat/frontend/src/pages/OrderConfirmationTrackingPage.tsx` para consumir el widget adaptativo.

**Checkpoint**: `US1` completa e independientemente verificable como MVP (seguimiento adaptativo en vivo con posición FIFO, conmutación de etapas y resiliencia de red).

---

## Fase 4: Historia de usuario 2 — Tiempo estimado en vivo y ajuste granular de demora (+5) (Prioridad: P2) — [GitHub Issue #355](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/355)

**Objetivo**: Control táctil "+5" en la comanda en preparación activa (Columna 2 del KDS) que incrementa atómicamente `specific_delay_minutes` bajo bloqueo pesimista `PESSIMISTIC_WRITE` e informa visualmente la demora acumulada (`+5 min`, `+10 min`...); el comensal refleja reactivamente $M \to M + 5$ en $\le 8$ s recalculando proporcionalmente la barra; cota de piso en 1 minuto y cota de techo de barra en 92% ante exceso de tiempo estimado; purga visual total de demora al conmutar a "LISTO" (`COM-7.3.2 (BIS)` — `RF-012` a `RF-017`, `RF-019`).

**Prueba independiente**: Ubicar comanda en Columna 2 de cocina; pulsar "+5" y verificar insignia contigua `+5 min`; pulsar dos veces más rápido y comprobar consolidación exacta a `+15 min`; verificar en pantalla del comensal que el minutero salta de $M$ a $M + 15$ en $\le 8$ s y la barra recalcula suavemente; dejar transcurrir el tiempo estimado y constatar que el minutero se detiene en "1 min restante" y la barra en 92%; presionar "LISTO" en cocina y comprobar que la demora se purga de la vista del comensal.

### Pruebas TDD de US2 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de abordar la implementación `T022`–`T025`.

- [ ] T018 [P] [US2] Crear prueba de integración transaccional en `easyeat/backend/src/test/java/com/easyeat/order/KdsDelayAdjustmentIntegrationTest.java` verificando `POST /api/v1/kitchen/tickets/{ticketId}/add-delay` bajo concurrencia (bloqueo pesimista `PESSIMISTIC_WRITE`, incremento acumulativo lineal, 3 pulsaciones rápidas = $+15 \text{ min}$, error 404 para ticket inexistente, 403 para otro establecimiento y 409 si el ticket no está en `IN_PREPARATION`).
- [ ] T019 [P] [US2] Crear pruebas unitarias de cálculo temporal en `easyeat/backend/src/test/java/com/easyeat/order/LiveTrackingDelayCalculationTest.java` verificando `remainingMinutes = max(1, (20 + specificDelayMinutes) - elapsedMinutes)`, cota de piso en "1 min restante" al superar el estimado, techo de `progressPercent` en 92% y recálculo proporcional ante adiciones sucesivas de $+5$.
- [ ] T020 [P] [US2] Crear prueba de componente en `easyeat/frontend/src/test/kitchen/KitchenTicketDelayControl.test.tsx` verificando botón táctil "+5" en el componente modular de cocina, debounce/bloqueo momentáneo en vuelo, llamada a la API y renderizado inmediato de la insignia acumulada (`+5 min`, `+10 min`...).
- [ ] T021 [P] [US2] Crear prueba de integración de UI en `easyeat/frontend/src/test/tracking/LiveTrackingWidgetDelay.test.tsx` verificando transición reactiva de $M \to M + 5$, recálculo fluido de la barra de progreso sin parpadeos, persistencia de 1 min y 92% ante tiempo excedido, y purga completa de indicadores de demora al recibir `READY_FOR_PICKUP`.

### Implementación de US2 (Green Implementation)

- [ ] T022 [US2] Implementar método transaccional `addTicketDelay(ticketId, establishmentId)` en `easyeat/backend/src/main/java/com/easyeat/order/service/KdsQueueServiceImpl.java` y `KdsQueueService.java` ejecutando `findByIdForUpdate`, validando estado `IN_PREPARATION`, sumando 5 a `specificDelayMinutes` y persistiendo la comanda.
- [ ] T023 [US2] Exponer endpoint `POST /api/v1/kitchen/tickets/{ticketId}/add-delay` en `easyeat/backend/src/main/java/com/easyeat/order/controller/KitchenKdsController.java` restringido a roles `CHEF` y `ADMINISTRADOR`.
- [ ] T024 [US2] Actualizar `OrderLiveTrackingService.java` en `easyeat/backend/src/main/java/com/easyeat/order/service/` para incorporar `specificDelayMinutes` en el cálculo de `estimatedTotalMinutes = 20 + specificDelayMinutes`, minutero con piso en 1 y barra con techo en 92%.
- [ ] T025 [US2] Crear componente modular `KitchenTicketDelayControl.tsx` en `easyeat/frontend/src/components/kitchen/` (con botón táctil "+5", debounce durante llamada en vuelo e insignia de demora acumulada contigua) e integrarlo en la comanda activa de cocina en `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` (dejándolo listo para su posterior consumo en el tablero KDS).

**Checkpoint**: `US1 + US2` integradas completamente (seguimiento adaptativo en vivo y ajuste granular reactivo de demoras de cocina operativo end-to-end).

---

## Fase 5: Pulido final y aspectos transversales (Polish & Cross-Cutting Concerns) — [GitHub Issue #356](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/356)

**Propósito**: Validar fronteras con especificaciones adyacentes, ejecutar la guía de validación rápida y asegurar la suite completa de pruebas de regresión.

- [ ] T026 [P] Verificar fronteras de aislamiento técnico de dominio en `easyeat/backend/src/test/java/com/easyeat/order/KdsBoundariesTest.java` comprobando que la mutación `addTicketDelay` incrementa exclusivamente `specificDelayMinutes` sin alterar `startedAt` (base inmutable del cronómetro Count-Up de cocina) ni `readyAt` (base inmutable de mostrador).
- [ ] T027 Ejecutar manualmente los 13 escenarios de validación guiada de `quickstart.md` (`specs/021-live-tracking-delay-adjustment/quickstart.md`) verificando transiciones, $N=0$, clics rápidos (+15 min), desconexión >15 s y purga de entrega.
- [ ] T028 [P] Ejecutar la suite completa de pruebas unitarias y de integración de backend (`./mvnw test`) y frontend (`npm test`), asegurando 100% de éxito y ausencia de regresiones.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

```
Fase 1: Preparación compartida (Setup)
               ↓
Fase 2: Base fundamental (Foundational) — [BLOQUEA Historias de Usuario]
               ↓
┌──────────────────────────────────────────────┐
│ Fase 3: Historia de usuario 1 (P1) 🎯 MVP   │
└──────────────────────────────────────────────┘
               ↓ (amplía con control de demora granular)
┌──────────────────────────────────────────────┐
│ Fase 4: Historia de usuario 2 (P2)           │
└──────────────────────────────────────────────┘
               ↓
Fase 5: Pulido final y verificación transversal
```

### Dependencias entre historias de usuario

- **US1 (P1)**: Depende de Fase 1 y Fase 2. Puede implementarse y probarse de forma 100% independiente como MVP de seguimiento en vivo.
- **US2 (P2)**: Depende de Fase 1 y Fase 2. Se apoya en la pantalla y endpoint de seguimiento de US1 para reflejar dinámicamente las demoras añadidas desde cocina, pero su mutación en cocina (`add-delay`) es testeable de forma aislada.

### Flujo interno dentro de cada historia (TDD estricto)

1. Escribir pruebas unitarias y de integración (*Red Tests*) y constatar su fallo.
2. Implementar migraciones, entidades y métodos de repositorio.
3. Implementar servicios de dominio transaccionales y controladores REST.
4. Implementar hooks y componentes de interfaz en frontend.
5. Ejecutar pruebas y validar paso a verde (*Green Implementation*).

---

## Oportunidades de ejecución paralela

### Paralelismo en Fase 1 (Setup)
- `T001` (DTOs backend) y `T002` (DTO live tracking) pueden desarrollarse en paralelo con `T003` (tipos frontend) y `T004` (APIs frontend).

### Paralelismo en Fase 2 (Foundational)
- `T005` (migración V27), `T006` (entidades KdsTicket y Order), `T007` (métodos FIFO y pesimista en KdsTicketRepository) y `T008` (componente ConnectionStatusBadge) operan sobre archivos independientes y pueden crearse simultáneamente.

### Paralelismo en Fase 3 (US1 - Red Tests)
- `T009` (prueba de integración FIFO), `T010` (prueba WebMvcTest), `T011` (prueba hook polling) y `T012` (prueba componente LiveTrackingWidget) pueden redactarse en paralelo.

### Paralelismo en Fase 4 (US2 - Red Tests)
- `T018` (prueba integración add-delay), `T019` (prueba cálculo temporal), `T020` (prueba componente KitchenDelayControl) y `T021` (prueba LiveTrackingWidgetDelay) pueden redactarse en paralelo.

---

## Estrategia de implementación

### Enfoque MVP primero (Entrega de US1)
1. Completar Fase 1 (Setup) y Fase 2 (Foundational).
2. Implementar y verificar Fase 3 (US1): el comensal ya dispone de seguimiento en vivo adaptativo por etapas con recuento FIFO de pedidos por delante y aviso de retiro con código de seguridad.
3. Demostrar y validar US1 de forma aislada como hito funcional completo.

### Incremento funcional (Entrega de US2)
4. Implementar Fase 4 (US2): incorporar el botón táctil "+5" en la Columna 2 de cocina y la propagación reactiva al temporizador del comensal.
5. Ejecutar Fase 5 (Pulido y validación de escenarios de `quickstart.md`).
