# Quickstart Guide: Validation & End-to-End Scenarios

**Feature**: `010-restaurant-operation-config` | **Date**: 31/10/2026 | **Spec**: [spec.md](./spec.md)

---

## 1. Prerrequisitos y Entorno de Ejecución

Para ejecutar los escenarios de verificación de la feature:

1. **Backend**:
   - JDK 21 instalado y configurado en el `PATH`.
   - Docker daemon en ejecución (para Testcontainers PostgreSQL 16).
   - Ejecutar desde la raíz del backend:
     ```bash
     cd easyeat/backend
     ./mvnw test -Dtest='com.easyeat.operations.**'
     ```
     (the older filter `*OperationConfig*` matches no class of this feature). The seven scenarios below
     run end to end in `QuickstartScenariosTest`: `-Dtest=QuickstartScenariosTest`. This repository has
     no orders module, so in that test "approve an order" is `SlotCapacityService.consumeSlot` and
     "try to create an order" is asking the time slots or `validate-payment`; the order-level
     outcomes are enforced by the future order flow, see [wire-up.md](./wire-up.md).
2. **Frontend**:
   - Node.js 20+ y npm instalados.
   - Ejecutar desde la raíz del frontend:
     ```bash
     cd easyeat/frontend
     npm run test -- src/test/operations/
     ```

---

## 2. Escenarios de Validación Ejecutables de Punta a Punta

### Escenario 1 — Bloqueo automático por cierre a las 16:01 y admisión de reservas futuras
- **Objetivo**: Demostrar el cumplimiento de `AC-1.1`, `INV-1`, `SC-001`, `FR-003` y `EC-017`.
- **Procedimiento**:
  1. Autenticarse como Administrador y configurar el horario comercial del martes de 11:00 a 16:00:
     ```http
     PUT /api/v1/admin/operating-schedules
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     [
       { "dayOfWeek": "TUESDAY", "openingTime": "11:00:00", "closingTime": "16:00:00", "isClosed": false }
     ]
     ```
  2. Simular hora del servidor a las 15:59:59 (hora de Buenos Aires) y consultar las franjas de hoy
     (`GET /api/v1/establishments/{tenantId}/time-slots?date=2026-11-03`):
     - **Resultado esperado**: `isClosed: false` y la franja de las 15:45-16:00 figura en la lista
       (`AVAILABLE` si tiene capacidad configurada; sin capacidad figura `SOLD_OUT`, EC-007).
  3. Simular hora del servidor a las 16:01:00 y consultar las franjas de hoy:
     - **Resultado esperado**: HTTP 200 con `isClosed: true`, `slots: []` y
       `closureReason: "Cocina cerrada para el día de hoy"`. No queda ninguna franja elegible, por lo que
       el flujo de pedidos futuro rechaza todo pedido para hoy (`DinerTimeSlotService.isAdmissible`,
       ver wire-up.md §2). El rechazo del pedido en sí no existe en este módulo.
  4. Simular hora del servidor a las 22:00:00 del martes y consultar las franjas del miércoles (configurado de 11:00 a 16:00):
     - **Resultado esperado**: HTTP 200 OK con `isClosed: false`; la franja de 13:00 figura en la lista porque la franja destino es válida (EC-017).

---

### Escenario 2 — Agotamiento determinístico de franja al aprobar el pedido 50
- **Objetivo**: Demostrar el cumplimiento de `AC-2.1`, `INV-2`, `INV-3`, `SC-003` y `FR-009`.
- **Procedimiento**:
  1. Configurar capacidad máxima de 50 pedidos en la franja de 12:00:
     ```http
     PUT /api/v1/admin/slot-capacities
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     [
       { "slotDate": "2026-11-03", "startTime": "12:00:00", "maxCapacity": 50 }
     ]
     ```
  2. Aprobar secuencialmente 49 pedidos en dicha franja (`SlotCapacityService.consumeSlot`, ver wire-up.md §3).
  3. Consultar el selector de franjas del comensal:
     ```http
     GET /api/v1/establishments/{tenantId}/time-slots?date=2026-11-03
     ```
     - **Resultado esperado**: La franja de 12:00 retorna con `status: "AVAILABLE"` (49/50 ocupados).
  4. Aprobar el pedido número 50. Una aprobación 51 se rechaza con `SlotSoldOutException` (HTTP 409 "Franja horaria agotada").
  5. Consultar nuevamente el selector del comensal:
     - **Resultado esperado**: La franja de 12:00 retorna inmediatamente con `status: "SOLD_OUT"` y queda deshabilitada para nuevas selecciones.

---

### Escenario 3 — Reducción de capacidad por debajo de aprobados y no retroactividad
- **Objetivo**: Demostrar el cumplimiento de `INV-8`, `EC-005` y `SC-011`.
- **Procedimiento**:
  1. Con 40 pedidos ya aprobados en una franja de capacidad 50, el Administrador reduce la capacidad máxima a 30
     (el `expectedVersion` es el `version` que devolvió `GET /api/v1/admin/slot-capacities?date=2026-11-03`):
     ```http
     PUT /api/v1/admin/slot-capacities
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     [
       { "slotDate": "2026-11-03", "startTime": "12:00:00", "maxCapacity": 30, "expectedVersion": 0 }
     ]
     ```
  2. Consultar el estado de la franja (`approvedOrdersCount` en la matriz del administrador):
     - **Resultado esperado**:
       - La franja pasa de inmediato a `status: "SOLD_OUT"` en el selector del comensal ($40 \ge 30$).
       - `approvedOrdersCount` sigue en 40: la actualización no toca los aprobados (`INV-8`). Que cada pedido conserve su estado `APPROVED` lo garantiza el flujo de pedidos futuro, que no existe en este módulo.

---

### Escenario 4 — Control de visibilidad de métodos de pago en checkout
- **Objetivo**: Demostrar el cumplimiento de `AC-3.1`, `INV-4`, `SC-004` y `FR-011`.
- **Procedimiento**:
  1. El Administrador apaga Stripe:
     ```http
     PUT /api/v1/admin/payment-methods/STRIPE
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     { "isEnabled": false, "expectedVersion": 0 }
     ```
     `expectedVersion` es el `version` que devolvió `GET /api/v1/admin/payment-methods`; es `null` solo
     si el método aún no tiene fila (un establecimiento creado después de V15). Sin versión sobre una
     fila existente la respuesta es 409 "Conflicto de concurrencia".
  2. Como comensal, solicitar los métodos de pago en la vista de checkout:
     ```http
     GET /api/v1/establishments/{tenantId}/payment-methods
     ```
     - **Resultado esperado**: La respuesta contiene únicamente `MERCADO_PAGO`; Stripe no está presente en la lista (`AC-3.1`).

---

### Escenario 5 — Todos los métodos de pago deshabilitados
- **Objetivo**: Demostrar el cumplimiento de `EC-009` y `FR-013`.
- **Procedimiento**:
  1. El Administrador deshabilita también Mercado Pago (`PUT /api/v1/admin/payment-methods/MERCADO_PAGO`, `isEnabled: false`, con su `expectedVersion`).
  2. El comensal accede al checkout:
     - **Resultado esperado**: La API retorna lista vacía `[]`. La interfaz despliega el mensaje de indisponibilidad temporal de pagos online y mantiene el botón de confirmación deshabilitado.

---

### Escenario 6 — Revalidación dinámica ante cambio concurrente en checkout
- **Objetivo**: Demostrar el cumplimiento de `INV-9` y `EC-010`.
- **Procedimiento**:
  1. El comensal tiene en pantalla seleccionado Stripe.
  2. Antes de que el comensal confirme la orden, el Administrador apaga Stripe (`isEnabled: false`).
  3. El comensal envía la confirmación de la orden con Stripe:
     - **Resultado esperado**: `POST /api/v1/establishments/{tenantId}/checkout/validate-payment` con `{ "paymentMethod": "STRIPE" }` responde HTTP 409 Conflict, título "Método de pago no disponible" y un detalle que pide elegir una alternativa habilitada.

---

### Escenario 7 — Prevención atómica de sobreventa concurrente en el cupo 50
- **Objetivo**: Demostrar el cumplimiento de `EC-016` bajo concurrencia real.
- **Procedimiento**:
  1. Franja con 49 pedidos aprobados y capacidad 50.
  2. Dos hilos concurrentes ejecutan en simultáneo la aprobación del pedido 50 y pedido 51 (`consumeSlot`):
     - **Resultado esperado**:
       - Exactamente una transacción logra el incremento (`approvedOrdersCount = 50`).
       - La segunda transacción es rechazada con `SlotSoldOutException` (HTTP 409 Conflict, "Franja horaria agotada").
       - El contador final en base de datos es estrictamente 50, nunca 51.

