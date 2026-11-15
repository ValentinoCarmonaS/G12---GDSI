# Plan de implementación: KDS — cola de cocina y preparación activa

**Rama**: `019-kds-queue-preparation` | **Fecha**: 15/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Entrada**: Especificación funcional desde `/specs/019-kds-queue-preparation/spec.md`

---

## Resumen

Implementar la pantalla y lógica operativa del Kitchen Display System (KDS) para la cocina en un tablero Kanban de dos columnas fijas, cubriendo las historias `CHF-3.1.2`, `CHF-3.2.1`, `CHF-4.1.1`, `CHF-4.1.4` y `CHF-4.1.5`:

1. **Ordenamiento determinista de cola activa (`CHF-3.1.2` — `RF-001`, `RF-002`, `RF-003`, `RF-004`, `RF-005`)**:
   - Delimitar el alcance de la cola activa a `pickup_date <= hoy` (excluyendo fechas futuras), otorgando precedencia absoluta a turnos pasados incompletos.
   - Aplicar el orden total estricto: `pickup_date ASC, time_slot_start ASC, queued_at ASC, id ASC`, tratando bajo idéntica regla FIFO a pedidos inmediatos y programados de la misma franja.
   - Normalizar la franja de pedidos `IMMEDIATE` al bloque operativo de 15 minutos en curso: `time_slot_start = floor(M / 15) * 15` y `time_slot_end = time_slot_start + 15 min`.
2. **Inicio atómico e idempotente de preparación (`CHF-4.1.1` — `RF-006`, `RF-007`, `RF-008`)**:
   - Exponer el endpoint `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation` con bloqueo pesimista `SELECT ... FOR UPDATE` sobre la fila de `kds_tickets`.
   - Fijar `started_at = Instant.now()`, transicionar el ticket a `IN_PREPARATION` y asegurar `Order.status = IN_PROGRESS`.
   - Idempotencia estricta: reintentos de red o clics repetidos sobre un ticket ya en preparación responden exitosamente (`200 OK`) preservando el `started_at` original sin duplicar comandas.
3. **Ergonomía táctil y lectura a distancia (`CHF-3.2.1` — `RF-006`, `RF-009`, `RF-010`, `RF-016`)**:
   - Columna 1 ("Pendientes"): tarjeta compacta con `#ticketNumber`, franja `HH:mm - HH:mm`, botón "EMPEZAR" y scroll táctil continuo sin paginación.
   - Columna 2 ("En preparación"): tarjeta expandida con `#ticketNumber`, franja `HH:mm - HH:mm`, cronómetro `MM min` y desglose de platos en formato unificado `[Cantidad]x [Nombre del Plato]`.
   - Ausencia absoluta de placeholders de notas ("Sin notas", "Observaciones: ninguna"). Columnas vacías preservan layout fijo y renderizan mensajes neutrales ("Sin pedidos pendientes" / "Sin pedidos en preparación") sin layout shifts.
4. **Cronómetro Count-Up y alerta ámbar a los 15 minutos (`CHF-4.1.4`, `CHF-4.1.5` — `RF-011`, `RF-012`, `RF-013`, `RF-014`, `RF-015`)**:
   - Cronómetro derivado puramente del `started_at` persistido en el servidor: `floor((now - started_at) / 60)` en `MM min`, sincronizado contra el reloj del servidor para evitar desfasajes locales y persistente ante recargas multi-pantalla.
   - Alerta visual a los 15 minutos exactos (`tiempo >= 15 min`): contorno exterior de la tarjeta e insignia de tiempo conmutan a ámbar (`#EAB308` / `#F59E0B`), manteniendo fondo blanco y texto de platos neutro y nítido.
   - Polling silencioso en frontend cada 4 segundos (dentro del rango 3–5 s), con pausa al ocultar pestaña y backoff exponencial ante fallos.

---

## Contexto técnico

- **Lenguaje / Versión**: Java 21 (Backend), TypeScript 5.x / React 18 (Frontend).
- **Dependencias principales**:
  - Backend: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Validation), PostgreSQL Driver, Flyway.
  - Frontend: React 18, React Router DOM 6, Axios, Tailwind CSS, Lucide React, Vitest.
- **Persistencia**: PostgreSQL (tabla `kds_tickets` con índices `idx_kds_tickets_establishment_status` y nuevo índice compuesto `idx_kds_tickets_active_queue`, tabla `orders`, tabla `order_items`). Sin migraciones destructivas ni cambio de tablas existentes.
- **Pruebas**:
  - Backend: JUnit 5, Mockito, Spring `@WebMvcTest`, Testcontainers PostgreSQL (`BaseIntegrationTest`).
  - Frontend: Vitest + React Testing Library (ejecutando en `easyeat/frontend/tests/` y `easyeat/frontend/src/test/`).
- **Plataforma objetivo**: Entorno Docker / Render (Backend Linux), pantallas táctiles de cocina y navegadores web modernos (Frontend).
- **Tipo de proyecto**: Aplicación Web Full-Stack (Backend REST API + Frontend SPA).
- **Objetivos de rendimiento**:
  - Consulta de cola activa: tiempo de respuesta de base de datos < 20 ms bajo índice compuesto.
  - Mutación `start-preparation`: tiempo de respuesta HTTP p95 < 150 ms con bloqueo pesimista acotado a la transacción.
  - Frontend: renderizado a 60 fps, scroll táctil fluido sin saltos de diseño (*layout shifts*), recálculo de minutero en memoria sin peticiones de red adicionales.
- **Restricciones arquitectónicas**:
  - Sin WebSockets, SSE ni colas de mensajería (estricto polling silencioso en frontend sin estado en backend).
  - Idempotencia garantizada a nivel de base de datos (`PESSIMISTIC_WRITE` + `UNIQUE(order_id)`).
  - Trazabilidad y neutralidad: tipografía de platos no se tiñe bajo alerta ámbar; ausencia de marcadores vacíos de notas.
- **Alcance / Escala**: KDS para estaciones de cocción en restaurantes con cientos de pedidos diarios por establecimiento.

---

## Verificación constitucional

*(Evaluación frente a `.specify/memory/constitution.md` v2.0.0 tras Fase 0 y Fase 1)*

- **I. La especificación es la fuente de verdad**: Cumplido. El ordenamiento FIFO unificado por franja (`RF-003`), la normalización de franja para inmediatos (`RF-005`), el umbral ámbar (`RF-014`) y la exclusión de fechas futuras provienen textualmente de `spec.md`.
- **II. Especificación antes de implementación**: Cumplido. La fase de planificación técnica formaliza el diseño en `research.md`, `data-model.md`, `contracts/` y `quickstart.md` antes de generar tareas o código de producción.
- **III. Verificación en capas proporcional al riesgo**: Cumplido. Se definen pruebas unitarias de cálculo temporal, pruebas `@WebMvcTest` de controladores, pruebas de integración transaccional con concurrencia (Testcontainers) y pruebas frontend de componentes y hooks de polling.
- **IV. Diseño simple, cohesivo y justificado**: Cumplido. Se reutilizan `KdsTicket`, `KdsTicketRepository`, `KdsQueueService` y `KitchenKdsController`, extendiendo los métodos estrictamente necesarios sin introducir patrones ni capas innecesarias.
- **V. Alcance acotado y VI. Deuda técnica controlada**: Cumplido. No se añade funcionalidad fuera de alcance (botón `+5`, despacho a mostrador de Spec 020, edición de ítems, notas libres). Se corrigen las omisiones preexistentes detectadas en código (`startedAt` omitido en DTO y normalización de bloques en pedidos inmediatos) de forma acotada.
- **VII. Trazabilidad, consistencia y documentación**: Cumplido. Trazabilidad completa `spec.md` ↔ `research.md` ↔ `data-model.md` ↔ `contracts/` ↔ `quickstart.md` ↔ `plan.md`.
- **VIII. Evidencia sobre supuestos**: Cumplido. La existencia de tablas, columnas y patrones de bloqueo pesimista fue auditada directamente en el código fuente antes de formular el diseño.
- **IX. Ingeniería sobre conveniencia y X. Rebanadas verticales (Full-Stack)**: Cumplido. El incremento abarca el ciclo vertical completo: capa de datos, lógica de dominio, endpoint REST seguro, cliente API en frontend, componentes UI del tablero Kanban y suite de pruebas en ambas capas.
- **Flujo de desarrollo (excepción justificada)**: Las historias `CHF-3.1.2`, `CHF-3.2.1`, `CHF-4.1.1`, `CHF-4.1.4` y `CHF-4.1.5` forman una unidad operacional inseparable (la visualización de cola y la preparación activa en pantalla), agrupadas en la rama `019-kds-queue-preparation` según lo establecido en la especificación aprobada.

---

## Estructura del proyecto y cambios planificados

### Documentación de esta funcionalidad

```text
specs/019-kds-queue-preparation/
├── spec.md                          # Especificación funcional aprobada
├── plan.md                          # Este archivo (plan técnico de implementación)
├── research.md                      # Decisiones técnicas y análisis de alternativas (Fase 0)
├── data-model.md                    # Entidades, índices, DTOs y máquina de estados (Fase 1)
├── quickstart.md                    # Guía reproducible de validación manual y automatizada (Fase 1)
├── checklists/                      # Checklists de calidad de requerimientos
└── contracts/
    └── kds-queue-contract.md        # Contratos REST de consulta de cola y start-preparation
```

### Archivos de código afectados

#### Backend (`easyeat/backend/src/main/java/com/easyeat/`)

- `order/domain/KdsTicket.java`:
  - Asegurar constructores y setters de `startedAt`, `timeSlotStart` y `timeSlotEnd`.
- `order/repository/KdsTicketRepository.java`:
  - Agregar `findByIdForUpdate(@Param("id") UUID id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  - Agregar `findActiveQueueTickets(UUID establishmentId, Collection<KdsTicketStatus> statuses, LocalDate today)` con ordenamiento `ORDER BY k.pickupDate ASC, k.timeSlotStart ASC, k.queuedAt ASC, k.id ASC`.
- `order/service/KdsQueueService.java` y `order/service/KdsQueueServiceImpl.java`:
  - Agregar método `KdsTicket startTicketPreparation(UUID ticketId, UUID establishmentId)`.
  - Normalizar `timeSlotStart` (`floor(M / 15) * 15`) y `timeSlotEnd` al construir `KdsTicket` para pedidos `IMMEDIATE` en `dispatchOrderToKitchen`.
- `order/service/OrderService.java` y `order/service/CheckoutPaymentService.java`:
  - Normalizar franja horaria en pedidos `IMMEDIATE` a bloque de 15 minutos en creación de órdenes.
- `order/dto/KdsActiveQueueTicketResponse.java` y `order/dto/KdsStartPreparationResponse.java`:
  - DTOs específicos para transporte de comanda activa con `startedAt`, items tipados y desempate total.
- `order/controller/KitchenKdsController.java`:
  - Exponer `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation`.
  - Ampliar `GET /api/v1/kitchen/orders` con soporte de `activeQueue=true` (retornando la lista ordenada de comandas activas con `startedAt` incluido en cada comanda).

#### Frontend (`easyeat/frontend/src/`)

- `types/kds.ts`:
  - Extender interfaces: `KdsTicket`, `KdsOrderItem`, `KdsStartPreparationResponse`, `KdsTicketStatus`.
- `api/kitchenKdsApi.ts`:
  - Incorporar función `startTicketPreparation(ticketId: string): Promise<KdsStartPreparationResponse>`.
  - Adaptar `fetchKitchenOrders` para soportar el parámetro `activeQueue?: boolean` retornando `startedAt`.
- `hooks/useKdsQueuePolling.ts` (nuevo):
  - Hook de polling silencioso cada 4 segundos, pausa al ocultar pestaña (`document.visibilityState === 'hidden'`), cancelación al desmontar y backoff ante errores de red.
- `utils/kdsTimeUtils.ts` (nuevo):
  - Función pura `calculateElapsedMinutes(startedAtIso: string, serverOffsetMs?: number): number`.
  - Función de umbral `isTicketDelayed(elapsedMinutes: number): boolean` (`>= 15 min`).
  - Función de formateo de plato `formatDishItem(quantity: number, name: string): string` (`"[Cantidad]x [Nombre]"`).
- `components/kds/KitchenKanbanBoard.tsx` (nuevo):
  - Tablero de 2 columnas fijas ("Pendientes" y "En preparación") con mensajes neutrales para columnas vacías ("Sin pedidos pendientes" / "Sin pedidos en preparación").
  - Columna 1: Tarjeta compacta con `#ticketNumber`, turno `HH:mm - HH:mm`, botón "EMPEZAR" y scroll táctil continuo.
  - Columna 2: Tarjeta expandida con `#ticketNumber`, turno `HH:mm - HH:mm`, cronómetro `MM min` con conmutación reactiva a alerta ámbar a los 15 min y lista de platos con alto contraste.
- `pages/KitchenWorkspacePage.tsx`:
  - Integrar `KitchenKanbanBoard` como elemento central de la interfaz de cocina `/cocina`.

#### Tests (`easyeat/backend/src/test/` y `easyeat/frontend/tests/`)

- **Backend tests**:
  - `KitchenKdsControllerIntegrationTest.java`: Pruebas de endpoint `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation` (transición `QUEUED -> IN_PREPARATION`, idempotencia en segundo llamado, `404` ticket inexistente, `403` establecimiento incorrecto, `409` estado terminal).
  - `KdsQueueDeterministicOrderTest.java`: Pruebas de repositorio y servicio verificando FIFO unificado entre `IMMEDIATE` y `SCHEDULED`, orden ascendente por `pickup_date`, `time_slot_start`, `queued_at`, precedencia de turnos vencidos y exclusión de fechas futuras.
- **Frontend tests**:
  - `KitchenKanbanBoard.test.tsx` / `tests/pages/KitchenWorkspacePage.test.tsx`: Pruebas de renderizado de dos columnas, botón "EMPEZAR", transición reactiva de tarjeta, formato de platos `[Cantidad]x [Nombre]`, ausencia de notas libres, estado de columnas vacías sin layout shifts.
  - `useKdsQueuePolling.test.ts` y `kdsTimeUtils.test.ts`: Pruebas unitarias de polling, backoff, recálculo reactivo de cronómetro y conmutación perimetral a ámbar a los 15:00 min exactos.

---

## Seguimiento de complejidad

| Excepción / Decisión | Por qué es necesaria | Alternativa más simple descartada |
| --- | --- | --- |
| Agrupación de `CHF-3.1.2`, `CHF-3.2.1`, `CHF-4.1.1`, `CHF-4.1.4` y `CHF-4.1.5` en la rama `019-kds-queue-preparation` | Las 5 historias conforman la rebanada vertical funcional inseparable del tablero KDS de cocina (visualización en cola y preparación activa). Entregar la cola sin botón de inicio o el botón sin visualización de platos dejaría un estado operacional incompleto en cocina. | Dividir en 5 ramas secuenciales generaría interfaces intermedias no operables por el chef y retrabajo en la integración de componentes compartidos. |
| Bloqueo pesimista `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `KdsTicketRepository.findByIdForUpdate` | Garantiza atomicidad estricta e idempotencia absoluta ante peticiones concurrentes o reintentos de red sobre el botón "EMPEZAR". | Bloqueo optimista (`@Version`): descartado porque exigiría añadir una columna de versión a la tabla `kds_tickets` alterando una tabla ya estabilizada en `V23`. |
| Normalización de franja en pedidos inmediatos al momento de creación y despacho | Garantiza que un pedido inmediato confirmado a las 13:04 se asigne al bloque `13:00 - 13:15`, conviviendo en el mismo turno que los programados y compitiendo por FIFO puro por `queued_at`. | Calcular franjas dinámicamente mediante funciones de fecha en SQL durante el `ORDER BY`: descartado porque impediría utilizar los índices de base de datos sobre `time_slot_start`. |
