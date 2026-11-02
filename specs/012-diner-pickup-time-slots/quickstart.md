# Quickstart & Verification Guide: Selección de Modalidad de Retiro y Motor de Reservas por Franja Horaria

**Branch**: `012-diner-pickup-time-slots` | **Fecha**: 01/11/2026 | **Spec**: [spec.md](./spec.md)

---

## 1. Prerrequisitos del entorno

Para ejecutar la suite de pruebas unitarias, de integración y de concurrencia:
- **Java**: OpenJDK 21 o superior.
- **Node.js**: v18.x o v20.x (con npm).
- **Docker**: Motor Docker activo (requerido por Testcontainers para PostgreSQL 16).
- **Maven**: 3.9+ (o wrapper `./mvnw` si está disponible).

---

## 2. Comandos de preparación y verificación inicial

Desde el directorio raíz del repositorio:

```bash
# 1. Verificar compilación y pruebas del backend
cd easyeat/backend
mvn clean test-compile

# 2. Verificar dependencias y build del frontend
cd ../frontend
npm install
npm run build
```

---

## 3. Escenarios de validación automatizada

### Escenario 1: Proyección de franjas, márgenes de preparación y clasificación de demanda (Unit Tests)

Verifica que el servicio `PickupSlotService` genere intervalos homogéneos de 15 minutos, aplique el lead time de 30 minutos sobre el reloj del servidor y clasifique correctamente los estados `AVAILABLE`, `HIGH_DEMAND` y `SOLD_OUT`.

```bash
cd easyeat/backend
mvn test -Dtest=PickupSlotServiceTest
```

**Criterios de éxito esperados**:
- Las franjas pasadas o que inician en un margen menor a 30 minutos respecto a la hora del servidor resultan con `status = EXPIRED_OR_LEAD_TIME` e `isSelectable = false`.
- Franjas con ocupación entre el 80% y 99% retornan `status = HIGH_DEMAND` e `isSelectable = true`.
- Franjas con ocupación al 100% retornan `status = SOLD_OUT` e `isSelectable = false`.
- Días fuera de la ventana de 7 días o días no laborables devuelven error o lista vacía de turnos.

---

### Escenario 2: Prueba de estrés y concurrencia Zero Overbooking (FCFS con Testcontainers)

Simula 20 solicitudes concurrentes asíncronas disputando el último cupo disponible de una misma franja horaria en PostgreSQL real.

```bash
cd easyeat/backend
mvn test -Dtest=ZeroOverbookingConcurrencyIntegrationTest
```

**Criterios de éxito esperados**:
- Exactamente 1 solicitud tiene éxito y retorna `HTTP 201 Created` con estado `PENDING_PAYMENT`.
- Las restantes 19 solicitudes reciben `HTTP 409 Conflict` con cuerpo RFC 7807 `ProblemDetail` conteniendo `code = "SLOT_SOLD_OUT"`.
- El total de pedidos activos en la base de datos para dicha franja es exactamente igual a la capacidad máxima (`max_capacity = 10`), sin ningún cupo excedente.

---

### Escenario 3: Ciclo de vida TTL de 15 minutos y barrido de expiración

Valida la estrategia híbrida de expiración: liberación reactiva a nivel de consulta y actualización en base de datos mediante la tarea programada.

```bash
cd easyeat/backend
mvn test -Dtest=OrderExpirationIntegrationTest
```

**Criterios de éxito esperados**:
- Una orden en `PENDING_PAYMENT` con `expires_at` en el pasado deja de computar para la capacidad de la franja en la consulta inmediata siguiente.
- Al disparar la tarea `@Scheduled` `sweepExpiredReservations()`, las órdenes vencidas transicionan efectivamente a estado `EXPIRED` en PostgreSQL.

---

### Escenario 4: Cancelación voluntaria de reserva y liberación inmediata

Valida el endpoint `POST /api/v1/orders/{orderId}/cancel`.

```bash
cd easyeat/backend
mvn test -Dtest=OrderCancellationIntegrationTest
```

**Criterios de éxito esperados**:
- La orden transiciona de `PENDING_PAYMENT` a `CANCELLED`.
- La franja horaria recupera de inmediato el cupo liberado, reflejándose en consultas subsiguientes en menos de 200 ms.
- Comensales no propietarios de la orden reciben `HTTP 403 Forbidden`.

---

### Escenario 5: Comportamiento interactivo de la interfaz de usuario (Frontend Tests)

Prueba el comportamiento de los componentes React, transiciones entre modalidades y cuenta regresiva.

```bash
cd easyeat/frontend
npm test src/test/checkout/
```

**Criterios de éxito esperados**:
- `FulfillmentModeSelector.test.tsx`: Alternar a "Lo antes posible" limpia y colapsa el selector de fechas y turnos. Deshabilita "Lo antes posible" si el local está cerrado o con turno actual al 100%.
- `TimeSlotGrid.test.tsx`: Despliega insignia "Alta Demanda" en ámbar entre 80-99% e inhabilita con badge "Agotado" al 100%.
- `PendingOrderCountdown.test.tsx`: Ejecuta cuenta regresiva desde 15:00, muestra alerta destacada bajo 2 minutos y dispara redirección/modal al alcanzar 00:00.
- `ConflictNotificationModal.test.tsx`: Al capturar error 409 `SLOT_SOLD_OUT`, reabre el selector de turnos con datos actualizados sin alterar los platos del carrito.

---

### Escenario 6: Validación cruzada de platos pausados por chef y stock (Specs 009 y 013)

Valida que el checkout en backend rechace pedidos si un plato fue pausado por la cocina o no cuenta con inventario suficiente.

```bash
cd easyeat/backend
mvn test -Dtest=OrderCheckoutValidationIntegrationTest
```

**Criterios de éxito esperados**:
- Si un plato del carrito tiene `paused_by_chef = true` (Spec 013), `POST /api/v1/orders` retorna `HTTP 422 Unprocessable Entity` con código `DISH_PAUSED_BY_CHEF`.
- Si un plato no tiene stock suficiente en cocina (Spec 009), retorna `HTTP 422` con código `DISH_OUT_OF_STOCK`.
- La transacción aborta limpiamente sin reservar cupo ni persistir la orden.

---

### Escenario 7: Articulación con horarios y capacidades de operaciones (Spec 010)

Valida que `PickupSlotService` respete las tablas de Spec 010 cuando estén presentes o aplique los valores por defecto del establecimiento.

```bash
cd easyeat/backend
mvn test -Dtest=PickupSlotOperationsIntegrationTest
```

**Criterios de éxito esperados**:
- Si `establishment_operating_schedules` tiene configurado un día como cerrado (`is_closed = true`), ese día se reporta como no operativo.
- Si una franja tiene un cupo personalizado en `establishment_slot_capacities` (ej. `max_capacity = 5`), la saturación se evalúa sobre 5 en lugar del default de 10.
- Si no existen filas en las tablas de 010, el servicio aplica fluidamente `opening_time`, `closing_time` y `max_capacity_per_slot = 10` de `establishments`.

---

## 4. Validación manual end-to-end (Flujo visual)

1. **Iniciar el backend localmente**:
   ```bash
   cd easyeat/backend
   mvn spring-boot:run
   ```
2. **Iniciar el frontend**:
   ```bash
   cd easyeat/frontend
   npm run dev
   ```
3. **Flujo de prueba manual**:
   - Iniciar sesión como comensal (`ROLE_COMENSAL`).
   - Seleccionar un establecimiento activo y agregar dos platos al carrito desde `/catalogo`.
   - Abrir el carrito y presionar "Continuar al checkout" para navegar a `/checkout`.
   - Probar la alternancia entre "Lo antes posible" y "Para más tarde".
   - Seleccionar una franja horaria en "Para más tarde". Notar si muestra badge de "Alta Demanda".
   - Confirmar el pedido: verificar la redirección a la pantalla de espera de pago con el cronómetro regresivo de 15 minutos activo.
   - Probar el botón "Cancelar reserva": comprobar que la orden pase a cancelada y regrese al catálogo con el cupo restablecido.

