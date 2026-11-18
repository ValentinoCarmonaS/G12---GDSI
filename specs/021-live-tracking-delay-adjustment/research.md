# Investigación técnica y decisiones de diseño: seguimiento en vivo y demora granular (+5)

**Identificador de especificación**: `021-live-tracking-delay-adjustment`  
**Rama de trabajo**: `021-live-tracking-delay-adjustment`  
**Fecha de elaboración**: 15/11/2026  
**Estado**: Completo  

---

## 1. Resumen y objetivos de investigación

El objetivo de esta fase de investigación técnica es resolver exhaustivamente las incógnitas arquitectónicas, de persistencia, de sincronización y de consistencia visual identificadas para la implementación de las historias `COM-8.1.1` y `COM-7.3.2 (BIS)`.

Todas las decisiones registradas en este documento fueron validadas contra la evidencia directa del código fuente del repositorio (`easyeat/backend` y `easyeat/frontend`) y acordadas mediante la sesión de clarificación técnica interactiva (`grill-me`).

---

## 2. Decisiones técnicas estructuradas

### Decisión 01: Persistencia de demora granular (`specific_delay_minutes`) y cálculo del tiempo total estimado en `IN_PREPARATION`

- **Contexto**:  
  En el repositorio existente, `KdsTicket` (creado en la migración `V23`) almacena el ciclo de vida de cocina (`started_at`, `ready_at`, `completed_at`, `ticket_number`, `status`), pero no posee una columna para la demora específica por ticket. A su vez, `OrderLiveTrackingService` define una constante de preparación base de `20 minutos` (`baseMinutes = 20`) para pedidos `IMMEDIATE`.
- **Decisión**:  
  1. Crear la migración Flyway incremental `V27__kds_specific_delay_minutes.sql` que añade la columna `specific_delay_minutes INTEGER NOT NULL DEFAULT 0` con restricción de integridad `CONSTRAINT chk_kds_specific_delay_minutes CHECK (specific_delay_minutes >= 0)` a la tabla `kds_tickets`.
  2. Mapear `specificDelayMinutes` en la entidad `KdsTicket.java` con getter y setter.
  3. Establecer el tiempo base de preparación estándar en 20 minutos fijos a nivel de lógica de dominio (coherente con `OrderLiveTrackingService`), calculando el tiempo total estimado como:
     $$\text{tiempo\_total\_estimado} = 20 + \text{specific\_delay\_minutes}$$
- **Justificación**:  
  Respeta el principio KISS y la convención preexistente del servicio de tracking sin sobrecargar el modelo relacional con campos redundantes ni alterar tablas centrales de catálogo. Mantiene la trazabilidad directa de cuánta demora sumó la cocina a cada ticket.
- **Alternativas consideradas**:  
  - *Agregar columna `estimated_total_minutes`*: Descartada por introducir redundancia derivable ($\text{estimado} = 20 + \text{demora}$) y requerir doble actualización atómica.
  - *Configurar tiempo base por plato o por establecimiento*: Descartada por exceder el alcance funcional aprobado de la Spec 021 y violar el Principio V (Alcance acotado).

---

### Decisión 02: Estrategia de atomicidad y concurrencia para el incremento acumulativo "+5"

- **Contexto**:  
  El chef puede pulsar el control "+5" en rápida sucesión (por ejemplo, tres veces seguidas ante un imprevisto mayor). El sistema debe consolidar determinísticamente cada pulsación ($3 \text{ pulsaciones} = +15 \text{ min}$) sin pérdidas por actualización concurrente (*lost updates*) ni condiciones de carrera.
- **Decisión**:  
  Implementar la mutación en `POST /api/v1/kitchen/tickets/{ticketId}/add-delay` dentro de un método transaccional `@Transactional` en `KdsQueueService` que adquiere un bloqueo pesimista de escritura sobre la fila de la comanda mediante `findByIdForUpdate` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) en `KdsTicketRepository`. El método incrementa `specificDelayMinutes += 5` y persiste la entidad.
- **Justificación**:  
  Alineado estrictamente a los patrones ya establecidos en Spec 019 (`start-preparation`) y Spec 020 (`ready-for-pickup`). El bloqueo a nivel de fila (`SELECT ... FOR UPDATE`) garantiza que las peticiones concurrentes se encolen en el motor PostgreSQL y se ejecuten secuencialmente, acumulando los incrementos de manera lineal y exacta.
- **Alternativas consideradas**:  
  - *Actualización atómica directa por SQL (`UPDATE kds_tickets SET specific_delay_minutes = specific_delay_minutes + 5`)*: Descartada porque omite las validaciones de ciclo de vida del ticket en memoria y desentona con los servicios JPA existentes en el backend.
  - *Bloqueo optimista con `@Version`*: Descartada porque ante clics rápidos generaría excepciones de concurrencia (`OptimisticLockException`) que obligarían al cliente a reintentar, degradando la experiencia táctil del chef.

---

### Decisión 03: Comportamiento ante reintentos de red y deduplicación del "+5"

- **Contexto**:  
  Cada pulsación de "+5" es una acción aditiva deliberada. Se requiere definir si un reintento a nivel de red (por ejemplo, timeout de socket o cliente inestable) debe deduplicarse o tratarse de forma acumulativa.
- **Decisión**:  
  1. En el cliente HTTP de frontend (`apiClient.ts` / `kitchenKdsApi.ts`), desactivar los reintentos automáticos para peticiones POST y aplicar una deshabilitación momentánea por debounce en la interfaz táctil durante la llamada en vuelo.
  2. En el backend, tratar cada petición HTTP válida recibida como un incremento intencional de 5 minutos, sin implementar tabla de idempotencia por clave temporal.
- **Justificación**:  
  El botón "+5" no es una operación de reemplazo de estado (como "EMPEZAR" o "LISTO"), sino un operador aditivo ($+5$). Introducir una tabla o caché de claves de idempotencia para descartar pulsaciones cercanas crearía falsos positivos que bloquearían pulsaciones intencionales legítimas y rápidas del chef (ej. querer sumar 15 minutos en 3 segundos). La protección visual contra rebote de hardware táctil es suficiente y mantiene la arquitectura simple (KISS).
- **Alternativas consideradas**:  
  - *Header `Idempotency-Key` por clic*: Descartada porque penaliza la ergonomía táctil rápida del chef y agrega sobreingeniería de almacenamiento temporal.

---

### Decisión 04: Algoritmo y consulta determinista para el cálculo de posición FIFO ($N$) en `QUEUED`

- **Contexto**:  
  Mientras la orden está en estado `QUEUED`, el comensal debe ver "Tenés $N$ pedidos por delante". Cuando el pedido está al tope de la cola para ser el siguiente en iniciar, $N$ debe ser estrictamente 0 ("Tenés 0 pedidos por delante"). Se debe reutilizar la misma regla FIFO de `CHF-3.1.2` (Spec 019).
- **Decisión**:  
  1. Computar $N$ como el recuento de comandas del mismo establecimiento que se encuentran en estado `QUEUED`, con fecha `pickup_date <= hoy`, y que preceden a la comanda actual en el orden lexicográfico total:
     $$(\text{pickup\_date}, \text{time\_slot\_start}, \text{queued\_at}, \text{id})$$
  2. Consulta JPQL optimizada en `KdsTicketRepository`:
     ```sql
     SELECT COUNT(k) FROM KdsTicket k
     WHERE k.establishment.id = :establishmentId
       AND k.status = com.easyeat.order.domain.KdsTicketStatus.QUEUED
       AND k.pickupDate <= :today
       AND (
            (k.pickupDate < :targetPickupDate) OR
            (k.pickupDate = :targetPickupDate AND k.timeSlotStart < :targetTimeSlotStart) OR
            (k.pickupDate = :targetPickupDate AND k.timeSlotStart = :targetTimeSlotStart AND k.queuedAt < :targetQueuedAt) OR
            (k.pickupDate = :targetPickupDate AND k.timeSlotStart = :targetTimeSlotStart AND k.queuedAt = :targetQueuedAt AND k.id < :targetId)
       )
     ```
  3. Cuando no hay órdenes previas pendientes en la cola, el resultado es 0 y la interfaz renderiza literalmente "Tenés 0 pedidos por delante".
- **Justificación**:  
  Excluye las órdenes en preparación (`IN_PREPARATION`) del conteo de cola previa porque una orden en preparación ya está en los fuegos y no bloquea el inicio de la siguiente en cola. De esta forma, cuando una orden es la primera de la Columna 1 ("Pendientes") para el chef, el comensal visualiza exactamente $N = 0$. La consulta se apoya en el índice `idx_kds_tickets_active_queue` de Spec 019, ejecutándose en $<5 \text{ ms}$.
- **Alternativas consideradas**:  
  - *Contar órdenes en `IN_PREPARATION` dentro de $N$*: Descartada porque violaría la especificación (`RF-004`, Invariante 1 y Grill-Me de Spec 021), ya que una orden que es la próxima en iniciar en cocina mostraría $N > 0$ si hay fuegos ocupados.

---

### Decisión 05: Fórmula matemática del temporizador, ancla de tiempo y cotas de avance en `IN_PREPARATION`

- **Contexto**:  
  El temporizador dinámico y la barra porcentual solo aplican en `IN_PREPARATION`. Se requiere definir el ancla temporal para evitar desfasajes por reloj de cliente, la regla de piso a 1 minuto y la regla de techo de barra al 92%.
- **Decisión**:  
  1. **Ancla temporal**: El servidor calcula los minutos transcurridos utilizando `started_at` persistido en `kds_tickets` y el reloj del servidor (`Instant.now()`):
     $$\text{elapsed\_minutes} = \max\left(0, \left\lfloor \frac{\text{now} - \text{started\_at}}{60} \right\rfloor \right)$$
  2. **Minutos restantes**:
     $$\text{raw\_remaining} = (20 + \text{specific\_delay\_minutes}) - \text{elapsed\_minutes}$$
     $$\text{remaining\_minutes} = \max(1, \text{raw\_remaining})$$
     *Regla de piso*: Nunca desciende por debajo de 1 min restante ni muestra 0 o valores negativos mientras la orden continúe en `IN_PREPARATION`.
  3. **Barra de progreso porcentual**:
     $$\text{ratio} = \frac{\text{elapsed\_minutes}}{20 + \text{specific\_delay\_minutes}}$$
     $$\text{raw\_progress} = \lfloor 15 + (\text{ratio} \times 77) \rfloor$$
     $$\text{progress\_percent} = \min(92, \max(15, \text{raw\_progress}))$$
     *Regla de techo*: La barra se mueve entre 15% (arranque) y un tope infranqueable del 92%. El 100% queda reservado para `READY_FOR_PICKUP` y coincide con la purga visual.
  4. **Recálculo proporcional ante "+5"**:
     Al sumarse 5 minutos a `specific_delay_minutes`, el denominador aumenta en 5, lo que expande el horizonte temporal: $\text{remaining\_minutes}$ aumenta automáticamente en 5 en el siguiente ciclo y $\text{progress\_percent}$ recalcula suavemente con el nuevo denominador sin saltos bruscos.
- **Justificación**:  
  Coherente con la fórmula probada en Spec 016 y los requerimientos funcionales `RF-010`, `RF-011`, `RF-016` y `RF-017`. Provee al comensal una expectativa realista y previene la ansiedad de ver "0 minutos" cuando la comida aún está en los fuegos.
- **Alternativas consideradas**:  
  - *Temporizador decreciente en memoria en el cliente (tick cada segundo)*: Descartada porque ante desfasajes de red o latencia el cliente mostraría información desalineada con la cocina. El valor nominal en minutos provisto por el backend garantiza determinismo absoluto.

---

### Decisión 06: Purga visual vs. persistencia relacional al pasar a `READY_FOR_PICKUP`

- **Contexto**:  
  Al presionar "LISTO" en cocina, la orden pasa a `READY_FOR_PICKUP`. Se requiere definir qué ocurre con `specific_delay_minutes`, el temporizador y la barra de progreso.
- **Decisión**:  
  1. **Purga de presentación (Frontend & DTO)**: La respuesta de `GET /api/v1/orders/{orderId}/live-tracking` devuelve `remainingMinutes = null`, `progressPercent = null`, `specificDelayMinutes = null`, `positionAhead = null` y habilita `securityCode` (4 dígitos). El cliente de frontend purga de inmediato el minutero, la barra y cualquier mensaje de demora, conmutando al panel verde de éxito.
  2. **Persistencia intacta (Backend relacional)**: La columna `kds_tickets.specific_delay_minutes` **no se borra ni se resetea a cero** en la base de datos.
- **Justificación**:  
  La purga es puramente visual y de presentación (`RF-019`, Invariante 8). Conservar el valor acumulado en `kds_tickets` es indispensable para métricas históricas, auditoría de tiempos operativos de cocina y control de calidad gastronómico.
- **Alternativas consideradas**:  
  - *Resetear `specific_delay_minutes = 0` en base de datos*: Descartada porque destruiría la evidencia histórica de que la orden sufrió demoras operativas.

---

### Decisión 07: Estrategia de sincronización periódica, resiliencia y estabilidad visual

- **Contexto**:  
  La pantalla del comensal debe actualizarse de forma silenciosa cada 5–8 segundos con cabeceras `no-cache`, detectar cortes de red y no parpadear ante datos idénticos.
- **Decisión**:  
  1. Hook de polling reactivo `useLiveTrackingPolling(orderId)` con cadencia regular de 6 segundos (dentro del rango 5–8 s).
  2. Encabezados HTTP estrictos: `Cache-Control: no-cache, no-store, must-revalidate` y `Pragma: no-cache`.
  3. Pausa automática al ocultar la pestaña mediante escucha de `document.visibilityState === 'hidden'`, reanudando inmediatamente al volver al primer plano.
  4. Cancelación de peticiones en vuelo al desmontar el componente mediante `AbortController`.
  5. Registro de marca temporal del último éxito (`lastSuccessfulPollTime`). Si `Date.now() - lastSuccessfulPollTime > 15000 ms`, desplegar insignia sutil en cabecera: `"Sin conexión — Reintentando..."`.
  6. Idempotencia visual: Almacenar la última respuesta en referencia (`useRef`). Si los datos recibidos son idénticos a los vigentes, no disparar re-renderizados estructurales, preservando el estado de animaciones y evitando cualquier parpadeo de interfaz.
- **Justificación**:  
  Garantiza el cumplimiento estricto de `RF-022`, `RF-023`, `RF-024`, `RF-025`, `RF-026` y la consistencia con el patrón de resiliencia adoptado en Spec 020.
- **Alternativas consideradas**:  
  - *WebSockets / SSE*: Descartada expresamente por la especificación y las restricciones constitucionales (KISS, polling HTTP sin sobrecarga de estado en servidor).

---

### Decisión 08: Fronteras operativas con Spec 019 (KDS), Spec 020 (Mostrador) y Spec 022 (PIN)

- **Frontera con Spec 019 (KDS Cocina)**:
  - El botón "+5" táctil y la insignia de demora acumulada se incorporan en el pie de la tarjeta de la Columna 2 de cocina (`KitchenWorkspacePage.tsx` / `KitchenKanbanBoard.tsx`).
  - El cronómetro Count-Up de la Columna 2 de cocina (que mide el tiempo físico transcurrido desde `started_at`) **no se modifica**: sigue contando hacia arriba normalmente. `specific_delay_minutes` solo afecta al horizonte del comensal.
- **Frontera con Spec 020 (Mostrador de despacho)**:
  - La pantalla `/mostrador` consume `GET /api/v1/counter/orders` y computa el tiempo en mostrador a partir de `ready_at`. No lee ni se ve afectada por `specific_delay_minutes`.
  - La transición a `READY_FOR_PICKUP` en Spec 021 dispara la conmutación visual en la pantalla del comensal sin necesidad de acoplamiento ni eventos cruzados con el mostrador.
- **Frontera con Spec 022 (PIN y entrega final)**:
  - La pantalla del comensal en `READY_FOR_PICKUP` únicamente exhibe el código de 4 dígitos para recitado verbal. No incluye campos de entrada de PIN, botones de confirmación de entrega ni flujos de archivado.

---

## 3. Matriz de trazabilidad técnica

| Requerimiento Spec 021 | Decisión técnica adoptada | Componente / Archivo responsable |
| :--- | :--- | :--- |
| `RF-001`, `RF-002`, `RF-003`, `RF-004`, `RF-005` (Cola y $N$) | Decisión 04 (Consulta FIFO determinista sobre `QUEUED`) | `KdsTicketRepository`, `OrderLiveTrackingService` |
| `RF-006`, `RF-007`, `RF-008`, `RF-009`, `RF-010`, `RF-011` (Prep y cotas) | Decisión 01, Decisión 05 (Piso 1 min, techo 92%, ancla en servidor) | `OrderLiveTrackingService`, `LiveTrackingWidget` |
| `RF-012`, `RF-013`, `RF-014`, `RF-015` (Control táctil "+5") | Decisión 01, Decisión 02, Decisión 03 (Bloqueo pesimista, DB migration V27) | `KitchenKdsController`, `KdsQueueService`, `KitchenKanbanBoard` |
| `RF-016`, `RF-017` (Reflejo dinámico en comensal) | Decisión 05 (Recálculo proporcional de horizonte temporal) | `OrderLiveTrackingService`, `LiveTrackingWidget` |
| `RF-018`, `RF-019`, `RF-020`, `RF-021` (Listo y purga) | Decisión 06 (Purga visual en DTO, persistencia relacional intacta) | `OrderLiveTrackingService`, `LiveTrackingWidget` |
| `RF-022` a `RF-026` (Polling y resiliencia) | Decisión 07 (Polling cada 6s, umbral >15s, idempotencia visual) | `useLiveTrackingPolling`, `ConnectionStatusBadge` |
