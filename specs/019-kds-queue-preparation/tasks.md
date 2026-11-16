# Lista de tareas: KDS — cola de cocina y preparación activa

**Entrada**: Documentos de diseño desde `/specs/019-kds-queue-preparation/`  
**Prerrequisitos**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md), [contracts/kds-queue-contract.md](./contracts/kds-queue-contract.md)

## Formato de tareas: `[ID] [P?] [Historia] Descripción con ruta de archivo`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos y sin dependencia de tareas incompletas en la misma fase).
- **[USx]**: Historia de usuario a la que pertenece la tarea (`US1`, `US2`, `US3`, `US4`, `US5`), omitida en preparación compartida, base fundamental y pulido final.
- **Enfoque TDD**: En cada historia de usuario, las tareas de prueba (*Red Tests*) se redactan y ejecutan primero para constatar el fallo antes de abordar la implementación (*Green Implementation*).

---

## Fase 1: Preparación compartida (Setup) — [GitHub Issue #337](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/337)

**Propósito**: Crear los DTOs y tipos de dominio base requeridos por los contratos HTTP en backend y frontend.

- [X] T001 [P] Crear DTOs de comanda activa `KdsActiveQueueTicketResponse.java` y `KdsStartPreparationResponse.java` en `easyeat/backend/src/main/java/com/easyeat/order/dto/`.
- [X] T002 [P] Extender tipos de dominio de cocina en `easyeat/frontend/src/types/kds.ts` incorporando `KdsTicket`, `KdsOrderItem`, `KdsStartPreparationResponse` y `KdsTicketStatus`.

**Checkpoint**: Modelos de transporte definidos y consistentes entre backend y frontend.

---

## Fase 2: Base fundamental (Foundational) — [GitHub Issue #338](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/338)

**Propósito**: Establecer el índice optimizado de cola, el bloqueo pesimista en persistencia y las utilidades compartidas de tiempo antes de implementar las historias operativas.

- [X] T002b [P] Crear migración Flyway `V25__idx_kds_tickets_active_queue.sql` en `easyeat/backend/src/main/resources/db/migration/` y mapear el `@Index` en `easyeat/backend/src/main/java/com/easyeat/order/domain/KdsTicket.java`.
- [X] T003 [P] Agregar método `findByIdForUpdate(@Param("id") UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `easyeat/backend/src/main/java/com/easyeat/order/repository/KdsTicketRepository.java`.
- [X] T004 [P] Crear utilitario de cálculo temporal y formateo en `easyeat/frontend/src/utils/kdsTimeUtils.ts` (`calculateElapsedMinutes`, `isTicketDelayed` y `formatDishItem`).

**Checkpoint**: Base de persistencia (índice y bloqueo pesimista) y utilidades de tiempo disponibles para las historias.

---

## Fase 3: Historia de usuario 1 — Priorización automática de la cola de preparación: FIFO unificado por turno (Prioridad: P1) 🎯 MVP — [GitHub Issue #339](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/339)

**Objetivo**: Ordenar determinísticamente la cola activa (`pickup_date ASC, time_slot_start ASC, queued_at ASC, id ASC`), excluir pedidos futuros (`pickup_date > hoy`), otorgar precedencia a turnos vencidos, unificar FIFO entre inmediatos y programados, y normalizar pedidos `IMMEDIATE` al bloque de 15 minutos en curso.

**Prueba independiente**: Cargar pedidos inmediatos y programados en distintas franjas y fechas; verificar que la consulta y la pantalla los listan en orden cronológico estricto, normalizan `IMMEDIATE` a `floor(M / 15) * 15` y omiten pedidos de fechas posteriores.

### Pruebas TDD de US1 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de implementar `T008`–`T013`.

- [X] T005 [P] [US1] Crear prueba de integración `easyeat/backend/src/test/java/com/easyeat/order/KdsQueueDeterministicOrderTest.java` verificando orden determinista total (`pickupDate`, `timeSlotStart`, `queuedAt`, `id`), exclusión de `pickupDate > hoy`, precedencia de turnos vencidos y FIFO unificado.
- [X] T006 [P] [US1] Crear prueba `@WebMvcTest` en `easyeat/backend/src/test/java/com/easyeat/order/KitchenKdsControllerIntegrationTest.java` verificando `GET /api/v1/kitchen/orders?activeQueue=true` con rol `CHEF`, filtro `pickup_date <= hoy` y mapeo de `startedAt`.
- [X] T007 [P] [US1] Crear prueba unitaria `easyeat/frontend/src/test/hooks/useKdsQueuePolling.test.ts` verificando polling silencioso cada 4 segundos, suspensión al ocultar pestaña (`document.visibilityState === 'hidden'`), cancelación al desmontar y backoff ante fallos de red.

### Implementación de US1 (Green Implementation)

- [X] T008 [P] [US1] Agregar método `findActiveQueueTickets` con orden determinista y filtro `pickupDate <= :today` en `easyeat/backend/src/main/java/com/easyeat/order/repository/KdsTicketRepository.java`.
- [X] T009 [US1] Normalizar franja de pedidos `IMMEDIATE` (`floor(M / 15) * 15`) en `easyeat/backend/src/main/java/com/easyeat/order/service/OrderService.java` y en `easyeat/backend/src/main/java/com/easyeat/order/service/CheckoutPaymentService.java`.
- [X] T010 [US1] Asegurar normalización de `timeSlotStart` y `timeSlotEnd` para `FulfillmentMode.IMMEDIATE` al construir la comanda en `easyeat/backend/src/main/java/com/easyeat/order/service/KdsQueueServiceImpl.java`.
- [X] T011 [US1] Actualizar `easyeat/backend/src/main/java/com/easyeat/order/controller/KitchenKdsController.java` para exponer `GET /api/v1/kitchen/orders` con parámetro `activeQueue=true`, mapeo de `startedAt` y consumo de `findActiveQueueTickets`, restringido al rol `CHEF`.
- [X] T012 [P] [US1] Actualizar `fetchKitchenOrders` en `easyeat/frontend/src/api/kitchenKdsApi.ts` para soportar parámetro `activeQueue` y retorno tipado de `startedAt`.
- [X] T013 [US1] Implementar hook de polling silencioso en `easyeat/frontend/src/hooks/useKdsQueuePolling.ts` consumiendo `fetchKitchenOrders`.

**Checkpoint**: `US1` completa e independientemente verificable como MVP (cola unificada en backend y polling funcional en frontend).

---

## Fase 4: Historia de usuario 2 — Iniciar preparación de comanda: transición a Columna 2 (Prioridad: P1) — [GitHub Issue #340](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/340)

**Objetivo**: Transicionar comanda de `QUEUED` a `IN_PREPARATION` al pulsar "EMPEZAR", fijar `started_at = Instant.now()`, asegurar `Order.status = IN_PROGRESS` y garantizar idempotencia atómica ante reintentos o concurrencia.

**Prueba independiente**: Pulsar "EMPEZAR" sobre una comanda en Columna 1; verificar que transiciona a Columna 2, registra `started_at`, asegura orden en progreso y responde `200 OK` sin duplicar comanda ni alterar `started_at` ante pulsaciones repetidas o concurrentes.

### Pruebas TDD de US2 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de implementar `T017`–`T020`.

- [X] T014 [P] [US2] Ampliar `easyeat/backend/src/test/java/com/easyeat/order/KitchenKdsControllerIntegrationTest.java` verificando `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation` (`200 OK` en éxito, `404` ticket inexistente, `403` establecimiento ajeno, `409` comanda en estado terminal).
- [X] T015 [P] [US2] Crear prueba de concurrencia e idempotencia en `easyeat/backend/src/test/java/com/easyeat/order/KdsStartPreparationIdempotencyTest.java` verificando que peticiones concurrentes ejecutan un único cambio de estado y preservan el `started_at` original.
- [X] T016 [P] [US2] Crear prueba de componente en `easyeat/frontend/tests/components/KitchenKanbanBoard.test.tsx` verificando pulsación de botón "EMPEZAR", deshabilitación durante la mutación, llamada a la API y traslado reactivo de la tarjeta a Columna 2.

### Implementación de US2 (Green Implementation)

- [X] T017 [US2] Agregar método `startTicketPreparation(UUID ticketId, UUID establishmentId)` en `easyeat/backend/src/main/java/com/easyeat/order/service/KdsQueueService.java` y `easyeat/backend/src/main/java/com/easyeat/order/service/KdsQueueServiceImpl.java` con bloqueo pesimista, idempotencia y sincronización de `Order.status = IN_PROGRESS`.
- [X] T018 [US2] Exponer endpoint `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation` en `easyeat/backend/src/main/java/com/easyeat/order/controller/KitchenKdsController.java` protegido exclusivamente con `@PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF')")`.
- [X] T019 [P] [US2] Agregar función `startTicketPreparation(ticketId: string)` en `easyeat/frontend/src/api/kitchenKdsApi.ts`.
- [X] T020 [US2] Conectar la acción de inicio de preparación en `easyeat/frontend/src/components/kds/KitchenKanbanBoard.tsx` (deshabilitación preventiva de botón y llamada a `startTicketPreparation`).

**Checkpoint**: `US1 + US2` completamente integradas (flujo transaccional e idempotente de inicio de elaboración entre Columna 1 y Columna 2).

---

## Fase 5: Historia de usuario 3 — Lectura ergonómica de comanda táctil (Prioridad: P2) — [GitHub Issue #341](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/341)

**Objetivo**: Renderizar tarjeta compacta en Columna 1 (scroll táctil continuo sin paginación), tarjeta expandida en Columna 2 con formato `[Cantidad]x [Nombre del Plato]`, omitir placeholders de notas vacías y mantener layout fijo de dos columnas con mensajes neutrales para estados vacíos.

**Prueba independiente**: Renderizar tarjetas con múltiples platos en Columna 2 comprobando legibilidad del formato `[Cantidad]x [Nombre]`, ausencia de textos "Sin notas", y mensajes neutrales "Sin pedidos pendientes" / "Sin pedidos en preparación" sin colapsar el ancho de las columnas.

### Pruebas TDD de US3 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de implementar `T023`–`T025`.

- [X] T021 [P] [US3] Crear pruebas unitarias en `easyeat/frontend/src/test/utils/kdsFormatters.test.ts` verificando el formateo unificado de ítems `formatDishItem` (`"[Cantidad]x [Nombre]"`) y manejo de cantidades múltiples.
- [X] T022 [P] [US3] Ampliar `easyeat/frontend/tests/components/KitchenKanbanBoard.test.tsx` verificando la visualización de platos en renglones dedicados, ausencia total de placeholders de notas ("Sin notas", "Observaciones: ninguna"), scroll vertical en Columna 1 y mensajes neutrales de columna vacía.

### Implementación de US3 (Green Implementation)

- [X] T023 [P] [US3] Crear subcomponentes de tarjeta `KdsQueuedCard.tsx` (compacta) y `KdsInPreparationCard.tsx` (expandida) en `easyeat/frontend/src/components/kds/`.
- [X] T024 [US3] Implementar estructura Kanban fija de 2 columnas con contenedor de scroll táctil continuo y mensajes neutrales de estado vacío en `easyeat/frontend/src/components/kds/KitchenKanbanBoard.tsx`.
- [X] T025 [US3] Integrar `KitchenKanbanBoard` dentro de `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` como vista operativa central.

**Checkpoint**: Ergonomía visual de cocina verificada en ambas columnas y adaptada a monitores táctiles.

---

## Fase 6: Historia de usuario 4 — Cronómetro progresivo de preparación en minutos: Count-Up (Prioridad: P2) — [GitHub Issue #342](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/342)

**Objetivo**: Exhibir en la cabecera de comanda de Columna 2 el tiempo transcurrido en `MM min`, calculado contra `started_at` del servidor, con refresco cada 60 s y persistencia ante recarga o visualización multi-dispositivo sin reiniciarse.

**Prueba independiente**: Observar una comanda en Columna 2 al minuto 0 (`0 min`), verificar que pasa a `1 min` a los 60 s, recargar la página y constatar que el valor se preserva contra el instante de inicio del servidor.

### Pruebas TDD de US4 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de implementar `T028`–`T029`.

- [X] T026 [P] [US4] Crear pruebas unitarias en `easyeat/frontend/src/test/utils/kdsCountUpUtils.test.ts` verificando el cálculo de minutos enteros `floor((now - startedAt) / 60)`, tolerancia a desfasaje de reloj (`clockOffsetMs`) y persistencia ante diferentes timestamps.
- [X] T027 [P] [US4] Crear prueba de hook en `easyeat/frontend/src/test/hooks/useKdsCountUp.test.ts` verificando el incremento periódico cada 60 segundos sin realizar peticiones HTTP de red adicionales.

### Implementación de US4 (Green Implementation)

- [X] T028 [P] [US4] Implementar hook reactivo `useKdsCountUp.ts` en `easyeat/frontend/src/hooks/useKdsCountUp.ts` sincronizado contra `startedAt` y el reloj local ajustado por la cabecera `Date` del servidor.
- [X] T029 [US4] Integrar `useKdsCountUp` en la cabecera de `KdsInPreparationCard` dentro de `easyeat/frontend/src/components/kds/` renderizando el formato `MM min`.

**Checkpoint**: Cronómetro progresivo funcional, sereno y sincronizado entre múltiples terminales.

---

## Fase 7: Historia de usuario 5 — Alerta visual por demora excesiva en KDS: umbral de 15 minutos (Prioridad: P2) — [GitHub Issue #343](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/343)

**Objetivo**: Conmutar reactivamente a color ámbar (`#EAB308` / `#F59E0B`) el borde exterior y la insignia de tiempo de la tarjeta de Columna 2 cuando `tiempo >= 15 min`, conservando el cuerpo de platos con tipografía y fondo neutros.

**Prueba independiente**: Evaluar comanda en minuto 14 (estilo perimetral neutral) y al alcanzar minuto 15: el borde e insignia conmutan a ámbar sin teñir el fondo de los platos.

### Pruebas TDD de US5 (Red Tests)

> **NOTA TDD**: Escribir primero estas pruebas y confirmar que fallan antes de implementar `T032`.

- [X] T030 [P] [US5] Crear pruebas unitarias en `easyeat/frontend/src/test/utils/kdsAlertThreshold.test.ts` verificando la función `isTicketDelayed` (`false` en 14 min 59 s, `true` en 15 min 00 s y superior).
- [X] T031 [P] [US5] Ampliar `easyeat/frontend/tests/components/KitchenKanbanBoard.test.tsx` verificando que al superar los 15 minutos solo el borde perimetral y la insignia conmutan a ámbar, mientras el cuerpo de platos mantiene tipografía y fondo neutros.

### Implementación de US5 (Green Implementation)

- [X] T032 [US5] Aplicar clases de advertencia perimetral ámbar (`border-amber-500`, `ring-2 ring-amber-500`, insignia `bg-amber-100 text-amber-800`) en `KdsInPreparationCard` dentro de `easyeat/frontend/src/components/kds/` condicionadas a `isTicketDelayed`.

**Checkpoint**: Alerta visual preventiva activa y verificada sin degradar legibilidad en producción.

---

## Fase 8: Pulido y validación cross-cutting — [GitHub Issue #344](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/344)

**Propósito**: Ejecución integral de suites automatizadas y validación manual de escenarios operativos.

- [X] T033 [P] Ejecutar la suite completa de pruebas backend `mvn test -Dtest="*Kds*,*Kitchen*"` en `easyeat/backend/`.
- [ ] T034 [P] Ejecutar la suite completa de pruebas frontend `npm test -- --run` y verificar compilación limpia `npm run build` en `easyeat/frontend/`.
- [ ] T035 Ejecutar el protocolo manual de validación end-to-end según [quickstart.md](./quickstart.md) (verificación de los 9 escenarios operativos).

---

## Dependencias y orden de ejecución

### Dependencias entre fases

1. **Setup (Fase 1)**: Sin dependencias. Puede iniciarse de inmediato.
2. **Foundational (Fase 2)**: Depende de la Fase 1. Bloquea el inicio de las historias de usuario.
3. **Historias de usuario (Fases 3 a 7)**:
   - `US1` (Fase 3): Puede comenzar tras la Fase 2. Establece la base de consulta de cola.
   - `US2` (Fase 4): Depende de `US1` (requiere que existan comandas encoladas para transicionar).
   - `US3` (Fase 5): Depende de `US2` para montar el tablero completo con tarjetas compactas y expandidas.
   - `US4` (Fase 6): Depende de `US3` (agrega el cronómetro en la cabecera de la tarjeta expandida).
   - `US5` (Fase 7): Depende de `US4` (el umbral de alerta reacciona a los minutos de preparación acumulados).
4. **Pulido (Fase 8)**: Depende de la conclusión de todas las historias de usuario.

### Dependencias internas por historia de usuario (TDD)

- Las pruebas unitarias y de integración (*Red Tests*) deben escribirse y confirmar su fallo antes de implementar los servicios, repositorios o componentes de la historia.
- Los modelos y DTOs preceden a los métodos de servicio.
- Los métodos de servicio preceden a los controladores REST.
- Los clientes API en frontend preceden a los hooks de polling y componentes de interfaz.

---

## Oportunidades de paralelización

```bash
# Paralelo Fase 1 (Setup):
T001: "Crear DTOs KdsActiveQueueTicketResponse y KdsStartPreparationResponse en backend"
T002: "Extender tipos de dominio en frontend/src/types/kds.ts"

# Paralelo Fase 2 (Foundational):
T002b: "Crear migración Flyway V25 y mapear @Index en KdsTicket.java"
T003: "Agregar findByIdForUpdate en KdsTicketRepository.java"
T004: "Crear kdsTimeUtils.ts en frontend/src/utils/kdsTimeUtils.ts"

# Paralelo Pruebas TDD de US1:
T005: "KdsQueueDeterministicOrderTest.java en backend"
T006: "KitchenKdsControllerIntegrationTest.java en backend"
T007: "useKdsQueuePolling.test.ts en frontend"

# Paralelo Pruebas TDD de US2:
T014: "start-preparation en KitchenKdsControllerIntegrationTest.java"
T015: "KdsStartPreparationIdempotencyTest.java en backend"
T016: "KitchenKanbanBoard.test.tsx acción EMPEZAR en frontend"
```

---

## Estrategia de entrega incremental (MVP)

1. **MVP mínimo (Fase 1 + 2 + 3)**:
   - Entrega la consulta de cola activa ordenada determinísticamente y el polling silencioso en frontend. Permite a cocina observar la cola priorizada por turno y FIFO unificado.
2. **Incremento 2 (Fase 4)**:
   - Habilita la acción "EMPEZAR" con persistencia de `started_at` y transición transaccional a la Columna 2.
3. **Incremento 3 (Fase 5)**:
   - Perfecciona la ergonomía táctil con el listado de platos `[Cantidad]x [Nombre]` y mensajes de columnas vacías.
4. **Incremento 4 y 5 (Fase 6 + 7)**:
   - Incorpora el Count-Up sereno en minutos enteros y la alerta ámbar a los 15 minutos sin teñir los textos.
5. **Cierre (Fase 8)**:
   - Validación integral automatizada y manual.
