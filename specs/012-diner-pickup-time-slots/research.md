# Technical Research: Selección de Modalidad de Retiro y Motor de Reservas por Franja Horaria

**Branch**: `012-diner-pickup-time-slots` | **Fecha**: 01/11/2026 | **Spec**: [spec.md](./spec.md)

---

## 1. Motor de concurrencia y garantía Zero Overbooking (FCFS)

### Contexto y problema
La invariante fundamental de capacidad establece que para cualquier franja horaria $S$ en una fecha $D$, el total de órdenes activas no puede superar la capacidad máxima configurada:
$$\text{Active Orders}(S) \le \text{Max Capacity}(S)$$
Bajo ráfagas de solicitudes concurrentes de múltiples comensales intentando reservar el último cupo de una misma franja, el sistema debe garantizar de forma atómica y determinista el principio First-Come, First-Served (FCFS): exactamente una solicitud debe ser admitida y las demás deben recibir una respuesta de conflicto (`HTTP 409 Conflict`) sin incurrir en sobreventa.

### Decisión técnica
Se implementa un bloqueo pesimista a nivel de base de datos utilizando Spring Data JPA:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Establishment e WHERE e.id = :id")
Optional<Establishment> findByIdWithLock(@Param("id") UUID id);
```
Dentro de la transacción `@Transactional` de creación de la orden (`OrderService.createPendingOrder`):
1. Se adquiere el bloqueo exclusivo sobre el registro del local (`SELECT ... FOR UPDATE` sobre `establishments`).
2. Se ejecuta la consulta agregada de órdenes activas para la franja solicitada:
   ```sql
   SELECT COUNT(o) FROM Order o
   WHERE o.establishment.id = :establishmentId
     AND o.pickupDate = :date
     AND o.timeSlotStart = :timeSlotStart
     AND o.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAID')
     AND (o.status != 'PENDING_PAYMENT' OR o.expiresAt > :now)
   ```
3. Se verifica si el comensal autenticado ya posee una orden activa en `PENDING_PAYMENT`. Si existe, se rechaza la operación con código de negocio `ACTIVE_RESERVATION_EXISTS`.
4. Si `activeOrders < maxCapacity`, se persiste la orden en estado `PENDING_PAYMENT` con su respectivo `expiresAt` (tiempo actual + 15 minutos) y se commitea la transacción liberando el bloqueo.
5. Si `activeOrders >= maxCapacity`, se aborta la transacción y se arroja `SlotCapacityExceededException`, que el manejador global mapea a `HTTP 409 Conflict` con un cuerpo RFC 7807 `ProblemDetail` que incluye el código `SLOT_SOLD_OUT` y las franjas horarias actualizadas.

### Justificación
- Dado que las franjas horarias son proyecciones dinámicas y no existen filas fijas por cada franja de 15 minutos en la base de datos, no es posible aplicar un bloqueo de fila a nivel de franja sin crear una tabla auxiliar artificial.
- La entidad `Establishment` actúa como la raíz de agregación operacional del local. Bloquear el registro del local durante la transacción de creación (operación en memoria y un `INSERT`, con duración inferior a 10 ms) proporciona un ordenamiento serializable estricto y seguro en PostgreSQL.
- Satisface el criterio FCFS de forma nativa a través del motor de bloqueos de PostgreSQL (`row-level exclusive lock`).

### Alternativas evaluadas y descartadas
- **Bloqueo optimista (`@Version`)**: Descartado porque no previene condiciones de carrera basadas en consultas agregadas (`COUNT`) sin una entidad compartida que modifique su versión explícitamente en cada reserva.
- **Tabla de candados dinámicos (`time_slot_locks`)**: Descartado por violar el Principio IV (Simplicidad y Diseño Justificado). Introduciría transacciones distribuidas de dos fases para gestionar la inserción y eliminación de candados efímeros.
- **Advisory Locks nativos de PostgreSQL (`pg_advisory_xact_lock`)**: Si bien ofrecen granularidad por franja, añaden acoplamiento rígido a funciones nativas fuera del ciclo de vida JPA/Hibernate, complicando los entornos de prueba estándar y violando la convención del proyecto.

---

## 2. Ciclo de vida de la reserva y gestión de TTL de 15 minutos (Estrategia Híbrida)

### Contexto y problema
Las órdenes en estado `PENDING_PAYMENT` retienen un cupo en la franja horaria. Si un comensal abandona la sesión o no completa la transacción en 15 minutos, el cupo debe liberarse inmediatamente para que otros usuarios puedan utilizarlo.

### Decisión técnica
Se adopta una arquitectura de liberación híbrida en dos niveles:
1. **Liberación reactiva inmediata a nivel de consulta**:
   Todas las consultas de disponibilidad de franjas horarias y validaciones de checkout filtran de forma determinista las órdenes expiradas:
   ```sql
   AND (o.status != 'PENDING_PAYMENT' OR o.expiresAt > :currentTime)
   ```
   Esto asegura que el cupo se libere en el milisegundo exacto en que transcurre el minuto 15, sin depender de la ejecución o periodicidad de ningún proceso asíncrono.
2. **Barredor de fondo programado (`@Scheduled`)**:
   Un servicio programado de Spring ejecuta una tarea periódica cada 60 segundos (`@Scheduled(fixedRate = 60000)`):
   ```java
   @Transactional
   public int sweepExpiredReservations() {
       return orderRepository.markExpiredOrders(Instant.now(), OrderStatus.EXPIRED);
   }
   ```
   Esta tarea actualiza el estado de las órdenes en la base de datos a `EXPIRED` para mantener la higiene de datos, emitir eventos de auditoría y evitar lecturas degradadas a largo plazo.
3. **Cancelación voluntaria explícita**:
   El comensal dispone del endpoint `POST /api/v1/orders/{orderId}/cancel` que verifica que la orden pertenezca al usuario autenticado, transiciona su estado a `CANCELLED` y libera el cupo de forma inmediata e irreversible.

### Justificación
- Combina la inmediatez de la consulta reactiva con la robustez transaccional del batching en segundo plano.
- Evita el riesgo de sobreventa o bloqueo artificial si el worker en segundo plano sufre retrasos transitorios de CPU o reinicios.

### Alternativas evaluadas y descartadas
- **Eventos Keyspace TTL de Redis**: Descartado por requerir infraestructura externa de memoria y broker que no forma parte del stack aprobado de Sprint 3.
- **DelayQueue / ScheduledExecutorService en memoria JVM**: Descartado porque ante un reinicio del contenedor o balanceo de carga entre múltiples réplicas se perderían los temporizadores activos en memoria.

---

## 3. Modelo de configuración operativa de establecimientos

### Contexto y problema
La especificación exige restringir las reservas a horarios hábiles, validar lead times de preparación de 30 minutos y computar capacidades por franja (por defecto 10 pedidos por turno). Spec 008 ya introdujo en `establishments` las columnas `opening_time`, `closing_time` y `timezone`.

### Decisión técnica
Se extiende la tabla `establishments` existente mediante la migración Flyway `V12__add_time_slot_and_order_fulfillment.sql`:
- `lead_time_minutes`: `INTEGER NOT NULL DEFAULT 30`
- `max_capacity_per_slot`: `INTEGER NOT NULL DEFAULT 10`
- `operating_days`: `VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'`

En la entidad `Establishment`, el campo `operating_days` se mapea bidireccionalmente utilizando un JPA `AttributeConverter<Set<DayOfWeek>, String>`, permitiendo validar de forma idiomática en Java si el local opera en un día determinado (`establishment.isOpenOn(dayOfWeek)`).

### Justificación
- Respeta la estructura de datos existente sin crear tablas 1:1 adicionales redundantes.
- Proporciona valores por defecto consistentes con el dominio que permiten que los locales previamente sembrados sigan funcionando sin regresiones.

---

## 4. Persistencia de la orden comercial y detalle de platos

### Contexto y problema
Aunque la pasarela de pagos se incorpora formalmente en Sprint 4, la orden debe registrarse con su detalle de platos e importes correspondientes para que en la próxima iteración el checkout pueda transicionar fluidamente a `PAID` sin redefinir la arquitectura.

### Decisión técnica
Se definen dos entidades relacionales:
1. `Order`:
   - `id`: UUID (PK)
   - `establishment_id`: UUID (FK a `establishments`, índice para consultas multi-tenant)
   - `user_id`: UUID (FK a `users`, comensal autenticado)
   - `fulfillment_mode`: `VARCHAR(30)` (`IMMEDIATE` o `SCHEDULED`)
   - `pickup_date`: `DATE NOT NULL`
   - `time_slot_start`: `TIME NOT NULL`
   - `time_slot_end`: `TIME NOT NULL`
   - `status`: `VARCHAR(30)` (`PENDING_PAYMENT`, `CONFIRMED`, `PAID`, `CANCELLED`, `EXPIRED`)
   - `total_amount`: `DECIMAL(12, 2) NOT NULL`
   - `expires_at`: `TIMESTAMPTZ` (obligatorio para `PENDING_PAYMENT`)
   - `created_at`, `updated_at`: `TIMESTAMPTZ`
2. `OrderItem`:
   - `id`: UUID (PK)
   - `order_id`: UUID (FK a `orders`, cascade all)
   - `dish_id`: UUID (FK a `dishes`)
   - `dish_name`: `VARCHAR(150) NOT NULL` (snapshot inmutable del nombre)
   - `unit_price`: `DECIMAL(10, 2) NOT NULL` (snapshot inmutable del precio)
   - `quantity`: `INTEGER NOT NULL`
   - `subtotal`: `DECIMAL(12, 2) NOT NULL`

### Justificación
- Los snapshots de nombre y precio garantizan que eventuales variaciones de carta o precios posteriores no alteren el histórico contable de la orden reservada.
- No introduce tablas temporales descartables y se integra de forma directa con `CartItem` de `CartContext`.

---

## 5. Proyección dinámica de franjas horarias (`TimeSlotAvailability`)

### Contexto y problema
Al consultar `GET /api/v1/establishments/{id}/pickup-slots?date=YYYY-MM-DD`, el comensal necesita visualizar las franjas de 15 minutos comprendidas entre la apertura y el cierre del establecimiento, con su estado de demanda y seleccionabilidad.

### Decisión técnica
El servicio `PickupSlotService`:
1. Valida que la fecha solicitada esté dentro de los próximos 7 días operativos a partir de la fecha del servidor en la zona horaria del local (`America/Argentina/Buenos_Aires`). Si la fecha no es operativa, retorna lista vacía o error 422.
2. Genera los intervalos secuenciales de 15 minutos: $[T_0, T_0 + 15), [T_0 + 15, T_0 + 30), \dots, [T_n - 15, T_n]$.
3. Obtiene en una única consulta agrupada el conteo de órdenes activas para la fecha:
   ```sql
   SELECT o.timeSlotStart, COUNT(o) FROM Order o
   WHERE o.establishment.id = :estId
     AND o.pickupDate = :date
     AND o.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAID')
     AND (o.status != 'PENDING_PAYMENT' OR o.expiresAt > :now)
   GROUP BY o.timeSlotStart
   ```
4. Para cada franja horaria:
   - Si la fecha es hoy y `startTime < serverTime.plusMinutes(leadTimeMinutes)`:
     `status = EXPIRED_OR_LEAD_TIME`, `isSelectable = false`.
   - Si `bookedCount >= maxCapacity`:
     `status = SOLD_OUT`, `isSelectable = false`.
   - Si `occupancyPercentage >= 80%`:
     `status = HIGH_DEMAND`, `isSelectable = true`.
   - En otro caso:
     `status = AVAILABLE`, `isSelectable = true`.

### Justificación
- Evita almacenar millones de filas estáticas en PostgreSQL para franjas futuras.
- El cómputo en memoria de 48 a 96 franjas por día toma menos de 2 milisegundos en la JVM.

---

## 6. Sincronización frontend y recuperación ante colisiones concurrentes

### Contexto y problema
El usuario en la pantalla de checkout debe ver las franjas actualizadas periódicamente y, si intenta confirmar una franja que se acaba de agotar (colisión concurrente), el sistema debe informarle claramente y reabrir el selector sin perder el contenido del carrito.

### Decisión técnica
1. **Hook `useTimeSlotPolling`**:
   - Ejecuta polling cada 8 segundos mientras la vista de checkout esté activa.
   - Si la franja que el usuario tenía seleccionada pasa a estado `SOLD_OUT` durante el polling, muestra una advertencia sutil y deselecciona la franja.
2. **Hook `useCheckoutFulfillment`**:
   - Administra el estado de la modalidad (`IMMEDIATE` o `SCHEDULED`), fecha y franja horaria.
   - Al alternar a "Lo antes posible", limpia de forma estricta los campos de fecha y turno.
   - Valida si "Lo antes posible" está habilitado (requiere que el establecimiento esté abierto en el instante actual y que el turno en curso no esté al 100%).
3. **Manejo de conflicto HTTP 409 (`SLOT_SOLD_OUT`)**:
   - La pantalla intercepta el error 409 y presenta `ConflictNotificationModal`.
   - El modal explica: *"La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar."*.
   - El estado del carrito en `CartContext` se mantiene intacto; solo se refrescan las franjas y se permite elegir un nuevo turno.
4. **Pantalla de cuenta regresiva (`PendingOrderCountdown`)**:
   - Una vez confirmada la orden, la pantalla de espera muestra el cronómetro regresivo desde 15:00 hasta 00:00 sincronizado con el reloj del servidor.
   - Cuando restan menos de 2 minutos, despliega un banner de advertencia visual en color rojo/naranja.
   - Proporciona el botón de cancelación voluntaria que invoca `POST /api/v1/orders/{orderId}/cancel`.
   - Al alcanzar 00:00, muestra una alerta modal de expiración por inactividad y redirige al comensal al `/catalogo`.

