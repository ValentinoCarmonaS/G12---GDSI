# Investigación técnica: KDS — cola de cocina y preparación activa

**Rama**: `019-kds-queue-preparation` | **Fecha**: 15/11/2026 | **Especificación**: [spec.md](./spec.md)  
**Identificador de especificación**: `019-kds-queue-preparation`

---

## Resumen de hallazgos en código base

La inspección directa del repositorio reveló el estado técnico real sobre el cual se apoya este incremento:

1. **Persistencia y modelo de datos relacional**:
   - La tabla `kds_tickets` ya existe (creada en migración `V23__individual_checkout_stock_deduction_and_kds.sql`). Contiene las columnas `id`, `order_id`, `establishment_id`, `ticket_number`, `status`, `fulfillment_mode`, `pickup_date`, `time_slot_start`, `time_slot_end`, `queued_at`, `started_at`, `ready_at`, `completed_at`, junto con la restricción `UNIQUE (order_id)` y el índice `idx_kds_tickets_establishment_status`.
   - El enum `KdsTicketStatus` ya define los estados operativos `QUEUED`, `IN_PREPARATION`, `READY_FOR_PICKUP`, `COMPLETED`.
   - La entidad `KdsTicket` ya mapea `startedAt` (`Instant`), pero `KitchenKdsController.mapTicketToKdsResponse` omite actualmente mapear `startedAt` en su payload JSON.

2. **Capa de servicios y controladores**:
   - `KdsQueueService` / `KdsQueueServiceImpl` ya despachan órdenes encoladas al confirmarse el pago (`dispatchOrderToKitchen`).
   - `KitchenTicketDispatchController` expone `POST /api/v1/kitchen/tickets/{ticketId}/ready-for-pickup` y `PUT /api/v1/kitchen/delay`.
   - `KitchenKdsController` expone `GET /api/v1/kitchen/orders` (con paginación estándar `Pageable`) y `GET /api/v1/kitchen/dashboard/summary`. Actualmente no existe el endpoint para iniciar la preparación (`POST /api/v1/kitchen/tickets/{ticketId}/start-preparation`) ni una consulta optimizada para la cola activa sin paginación con ordenamiento determinista por `pickup_date ASC, time_slot_start ASC, queued_at ASC, id ASC` filtrando `pickup_date <= hoy`.

3. **Cálculo de franjas para pedidos inmediatos**:
   - En `Establishment.java`, el método `calculateCurrentOngoingSlot(LocalTime now)` ya implementa la fórmula de bloque de 15 minutos: `LocalTime.of(now.getHour(), (now.getMinute() / 15) * 15)`.
   - No obstante, en `OrderService.java` y `CheckoutPaymentService.java`, los pedidos `IMMEDIATE` venían asignando `nowTime.truncatedTo(ChronoUnit.MINUTES)` en lugar del bloque normalizado, lo que impedía agruparlos en la misma franja horaria que los pedidos programados correspondientes al mismo intervalo.

4. **Frontend y layout de cocina**:
   - La ruta `/cocina` está mapeada a `KitchenWorkspacePage.tsx` bajo la guarda de rol `ROLE_CHEF`.
   - `KitchenWorkspacePage.tsx` contiene actualmente controles de demora global, despacho manual a mostrador y tarjetas resumen métricas, pero carece del tablero Kanban de dos columnas operativas ("Pendientes" y "En preparación").
   - La biblioteca de API `kitchenKdsApi.ts` declara `fetchKitchenOrders`, pero no está consumida por ningún componente de producción ni de test.

---

## Decisiones técnicas resueltas

### 1. Estrategia de atomicidad e idempotencia para `start-preparation` (`CHF-4.1.1`, `RF-007`, `RF-008`, `Invariante 5`)

- **Evidencia observada**:
  - El repositorio utiliza bloqueo pesimista `@Lock(LockModeType.PESSIMISTIC_WRITE)` de manera consistente en operaciones concurrentes críticas (`OrderRepository.findByIdForUpdate`, `IngredientRepository.findWithLockById`, `EstablishmentRepository`).
  - La tabla `kds_tickets` tiene `id` como clave primaria y `order_id` con restricción única.
- **Decisión**:
  - Exponer el endpoint `POST /api/v1/kitchen/tickets/{ticketId}/start-preparation` en `KitchenKdsController`.
  - Crear en `KdsTicketRepository` el método:
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KdsTicket k WHERE k.id = :id")
    Optional<KdsTicket> findByIdForUpdate(@Param("id") UUID id);
    ```
  - Implementar en `KdsQueueService` / `KdsQueueServiceImpl` el método `@Transactional KdsTicket startTicketPreparation(UUID ticketId, UUID establishmentId)`:
    1. Buscar el ticket con bloqueo pesimista `findByIdForUpdate(ticketId)`. Si no existe, lanzar `KdsTicketNotFoundException` (mapeada a `404 Not Found`).
    2. Validar pertenencia del ticket al establecimiento del usuario autenticado; en caso de discrepancia, denegar con `AccessDeniedException` (`403 Forbidden`).
    3. Si `ticket.getStatus() == KdsTicketStatus.QUEUED`:
       - `ticket.setStatus(KdsTicketStatus.IN_PREPARATION)`.
       - `ticket.setStartedAt(Instant.now())`.
       - Buscar `Order order = orderRepository.findById(ticket.getOrderId())`. Si su estado no es terminal ni `IN_PROGRESS`, fijar `order.setStatus(OrderStatus.IN_PROGRESS)` y `order.setUpdatedAt(ticket.getStartedAt())`.
       - Persistir entidades en la misma transacción.
    4. Si `ticket.getStatus() == KdsTicketStatus.IN_PREPARATION`:
       - Retornar el ticket existente de inmediato sin modificar su `startedAt` original (idempotencia pura ante clics repetidos o reintentos concurrentes de red).
    5. Si `ticket.getStatus()` es `READY_FOR_PICKUP` o `COMPLETED`:
       - Rechazar con `InvalidTicketStateException` (`409 Conflict`) informando que la comanda ya superó la fase de preparación activa.
- **Alternativas consideradas**:
  - Bloqueo optimista (`@Version`): descartado porque la tabla `kds_tickets` no cuenta con columna de versión y alterarla requeriría una migración de esquema innecesaria. El bloqueo pesimista garantiza exclusión mutua estricta a nivel de fila durante la transacción.
  - Actualización condicional por SQL nativo (`UPDATE kds_tickets SET status = ... WHERE id = ... AND status = 'QUEUED'`): viable, pero el bloqueo pesimista permite sincronizar la entidad relacional `Order` dentro del mismo contexto de persistencia JPA y devolver el DTO estructurado completo.

---

### 2. Estrategia de polling y refresco reactivo en frontend (`CHF-4.1.4`, `RF-015`, `Invariante 6`, `Clarification 3`)

- **Evidencia observada**:
  - La especificación prohíbe explícitamente el uso de WebSockets, SSE o colas de eventos en backend.
  - El frontend cuenta con patrones de polling en `useOrderLiveTrackingPolling.ts` que se cancelan al desmontar.
- **Decisión**:
  - Implementar el hook especializado `useKdsQueuePolling(establishmentId, intervalMs = 4000)`.
  - Parámetros operativos:
    - Intervalo nominal: 4000 ms (dentro del rango estricto de 3 a 5 segundos).
    - Silencioso: las actualizaciones se realizan en segundo plano sin desplegar spinners bloqueantes ni reiniciar la posición del scroll de los contenedores.
    - Detección de visibilidad: suscripción a `visibilitychange`. Si `document.visibilityState === 'hidden'`, el temporizador se suspende; al retornar a `visible`, se ejecuta una consulta inmediata y se restablece el ciclo.
    - Manejo de fallos con backoff: ante errores de red consecutivos, incrementar el intervalo progresivamente (4s → 8s → 16s, con tope en 30s) restableciéndose a 4s tras la primera respuesta exitosa.
    - Limpieza: cancelación garantizada del `setInterval` al desmontar el componente.
- **Alternativas consideradas**:
  - WebSockets / SSE: descartadas por restricción formal de la especificación funcional y la arquitectura sin estado del backend.
  - Polling a 1 segundo: descartado por saturación innecesaria de tráfico HTTP y CPU del servidor.

---

### 3. Delimitación de la cola activa (`pickup_date <= hoy`) y zona horaria (`CHF-3.1.2`, `RF-002`, `Invariante 1`)

- **Evidencia observada**:
  - La aplicación opera en la zona horaria `America/Argentina/Buenos_Aires` (`ZoneId.of("America/Argentina/Buenos_Aires")`), según consta en `OrderLiveTrackingService` y `OrderService`.
- **Decisión**:
  - El backend calcula la fecha operativa de referencia:
    ```java
    ZoneId zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
    LocalDate today = LocalDate.now(zoneId);
    ```
  - La consulta a base de datos filtra estrictamente:
    `k.pickupDate <= :today AND k.status IN ('QUEUED', 'IN_PREPARATION')`.
  - Esta regla incluye automáticamente:
    1. Comandas incompletas de turnos de fechas pasadas (`pickup_date < today`), ubicándolas al tope absoluto por orden cronológico.
    2. Comandas de todos los turnos del día en curso (`pickup_date == today`), tanto pasados incompletos como en curso y futuros del día.
  - Excluye categóricamente cualquier comanda con `pickup_date > today` (pedidos programados para días posteriores), evitando saturación visual en la cocina.
- **Alternativas consideradas**:
  - Filtrado en el cliente (frontend): descartado porque transferir pedidos de días posteriores desperdicia ancho de banda y traslada una regla crítica de negocio a la capa de presentación.

---

### 4. Ordenamiento determinista y total en backend y frontend (`CHF-3.1.2`, `RF-002`, `RF-003`, `RF-004`, `Invariante 1`)

- **Evidencia observada**:
  - La tabla `kds_tickets` cuenta con el índice `idx_kds_tickets_establishment_status (establishment_id, status, pickup_date, time_slot_start)`.
- **Decisión**:
  - Definir la consulta en `KdsTicketRepository`:
    ```java
    @Query("""
        SELECT k FROM KdsTicket k
        WHERE k.establishment.id = :establishmentId
          AND k.status IN :statuses
          AND k.pickupDate <= :today
        ORDER BY k.pickupDate ASC, k.timeSlotStart ASC, k.queuedAt ASC, k.id ASC
    """)
    List<KdsTicket> findActiveQueueTickets(
        @Param("establishmentId") UUID establishmentId,
        @Param("statuses") Collection<KdsTicketStatus> statuses,
        @Param("today") LocalDate today
    );
    ```
  - El criterio garantiza orden total gracias al desempate final por identificador estable (`k.id ASC`).
  - Documentar en el plan y en migraciones el índice compuesto óptimo:
    `CREATE INDEX IF NOT EXISTS idx_kds_tickets_active_queue ON kds_tickets(establishment_id, status, pickup_date, time_slot_start, queued_at, id);`
  - En frontend, cualquier reordenamiento o caché local aplica exactamente la misma función de comparación determinista:
    `pickupDate` → `timeSlotStart` → `queuedAt` → `ticketId`.
- **Alternativas consideradas**:
  - Ordenar por modalidad (inmediato antes que programado): descartado porque la especificación `019-kds-queue-preparation` unifica formalmente la regla FIFO (`RF-003` e `Invariante 2`), otorgando idéntica prioridad a ambas modalidades dentro de la misma franja horaria.

---

### 5. Normalización de bloques de 15 minutos para pedidos inmediatos (`CHF-3.1.2`, `RF-005`, `Invariante 4`)

- **Evidencia observada**:
  - `Establishment.calculateCurrentOngoingSlot(LocalTime now)` ya contiene el cálculo entero:
    `int minute = (now.getMinute() / 15) * 15; return LocalTime.of(now.getHour(), minute);`
  - Sin embargo, `OrderService.java` asignaba `timeSlotStart = nowTime.truncatedTo(ChronoUnit.MINUTES)` en pedidos `IMMEDIATE`.
- **Decisión**:
  - Al recibir y persistir un pedido con `FulfillmentMode.IMMEDIATE` en `OrderService` y en `CheckoutPaymentService`, normalizar la franja horaria a bloque de 15 minutos:
    - `timeSlotStart = LocalTime.of(nowTime.getHour(), (nowTime.getMinute() / 15) * 15);`
    - `timeSlotEnd = timeSlotStart.plusMinutes(15);`
  - En `KdsQueueServiceImpl.dispatchOrderToKitchen`, asegurar que el `KdsTicket` reciba `timeSlotStart` y `timeSlotEnd` normalizados.
  - Esto garantiza que un pedido inmediato confirmado a las 13:04 quede con franja `13:00 - 13:15`, conviviendo en el mismo bloque operativo que un pedido programado para las 13:00.
- **Alternativas consideradas**:
  - Mantener minutos arbitrarios en la base de datos y truncar dinámicamente en la consulta SQL: descartado porque complica las expresiones de consulta e invalida el aprovechamiento directo de índices sobre `time_slot_start`.

---

### 6. Cronómetro Count-Up y prevención de desfasaje de reloj servidor/cliente (`CHF-4.1.4`, `RF-011`, `RF-012`, `Invariante 6`)

- **Evidencia observada**:
  - La especificación exige que el contador sea `floor((ahora - instante_inicio) / 60)` en `MM min`, derivado exclusivamente del `startedAt` del servidor y persistente ante recargas o accesos multi-pantalla.
- **Decisión**:
  - El DTO de comanda en backend incluye `startedAt` en formato ISO-8601 UTC.
  - La respuesta HTTP o el interceptor de API registra la cabecera `Date` del servidor para computar el skew inicial del reloj cliente:
    `clockOffsetMs = new Date(serverDateHeader).getTime() - Date.now()`.
  - El cálculo de minutos transcurridos en frontend es una función pura:
    ```typescript
    export function calculateElapsedMinutes(startedAtIso: string, clockOffsetMs = 0): number {
      const startedAt = new Date(startedAtIso).getTime();
      const currentServerTime = Date.now() + clockOffsetMs;
      const elapsedSeconds = Math.max(0, Math.floor((currentServerTime - startedAt) / 1000));
      return Math.floor(elapsedSeconds / 60);
    }
    ```
  - Un temporizador ligero local despierta cada 60 segundos (o sincronizado al cambio de minuto) para actualizar el estado reactivo sin realizar peticiones de red adicionales para este fin.
- **Alternativas consideradas**:
  - Incrementar un contador de segundos en memoria con `setInterval(..., 1000)`: descartado porque se resetea al recargar la página y deriva si la pestaña queda en segundo plano.

---

### 7. Alerta visual ámbar perimetral a los 15 minutos (`CHF-4.1.5`, `RF-013`, `RF-014`, `Invariante 7`)

- **Evidencia observada**:
  - La especificación define el umbral en `tiempo >= 15 min` y exige preservar intacto el contraste y tipografía del cuerpo de platos (fondo neutro, texto nítido).
- **Decisión**:
  - Crear la función utilitaria de estilo:
    ```typescript
    export function isTicketDelayed(elapsedMinutes: number): boolean {
      return elapsedMinutes >= 15;
    }
    ```
  - Si `elapsedMinutes < 15`: contorno neutral estándar (`border-gray-200`, insignia de tiempo neutral `bg-gray-100 text-gray-700`).
  - Si `elapsedMinutes >= 15`: contorno ámbar de alerta (`border-amber-500` / `ring-2 ring-amber-500`, insignia ámbar `bg-amber-100 text-amber-800 font-bold`).
  - El cuerpo de la comanda conserva inalterado su fondo blanco/gris neutro (`bg-white`) y la tipografía de platos (`text-gray-900 font-medium`), garantizando legibilidad a un metro de distancia.
- **Alternativas consideradas**:
  - Pintar el fondo completo de la tarjeta de color amarillo: descartado explícitamente por `Invariante 7` y `RF-014` para evitar fatiga visual y pérdida de legibilidad de los platos en la cocina.

---

### 8. Ergonomía táctil, formato de ítems y estado vacío sin layout shifts (`CHF-3.2.1`, `RF-006`, `RF-009`, `RF-010`, `RF-016`)

- **Evidencia observada**:
  - El tablero debe constar de dos columnas operativas fijas: Columna 1 ("Pendientes") y Columna 2 ("En preparación").
- **Decisión**:
  - Columna 1 ("Pendientes"):
    - Contenedor con scroll táctil continuo (`overflow-y-auto`, sin paginación por clics).
    - Tarjeta compacta: `#ticketNumber`, turno `HH:mm - HH:mm`, botón prominente de acción "EMPEZAR".
    - Sin lista de platos ni notas libres.
  - Columna 2 ("En preparación"):
    - Tarjeta expandida: `#ticketNumber`, turno `HH:mm - HH:mm`, cronómetro `MM min` con alerta ámbar condicional, y desglose de platos en renglones dedicados con formato `[Cantidad]x [Nombre del Plato]`.
    - Sin placeholders ni contenedores residuales de notas vacías ("Sin notas", "Observaciones: ninguna").
  - Columnas vacías:
    - Estructura fija de dos columnas con ancho parejo (`grid grid-cols-1 md:grid-cols-2 gap-6`).
    - Si una columna carece de comandas, renderiza un contenedor con mensaje neutral indicativo:
      - Columna 1: `"Sin pedidos pendientes"`
      - Columna 2: `"Sin pedidos en preparación"`
    - Evita cualquier desplazamiento o colapso visual (`layout shift`).
