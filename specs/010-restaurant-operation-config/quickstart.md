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
     ./mvnw test -Dtest=*OperationConfig*
     ```
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
  2. Simular hora del servidor a las 15:59:59 (hora de Buenos Aires) e iniciar un pedido para hoy:
     - **Resultado esperado**: La franja de las 15:45-16:00 es admitida exitosamente.
  3. Simular hora del servidor a las 16:01:00 e intentar generar un pedido para hoy:
     - **Resultado esperado**: HTTP 400 Bad Request / ProblemDetail indicando que el restaurante se encuentra cerrado para el día de hoy y no admite nuevas reservas.
  4. Simular hora del servidor a las 22:00:00 del martes e intentar reservar para el miércoles en la franja de 13:00 (con miércoles configurado de 11:00 a 16:00):
     - **Resultado esperado**: HTTP 200 OK, la reserva futura es admitida normalmente porque la franja destino es válida.

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
  2. Aprobar secuencialmente 49 pedidos en dicha franja.
  3. Consultar el selector de franjas del comensal:
     ```http
     GET /api/v1/establishments/{tenantId}/time-slots?date=2026-11-03
     ```
     - **Resultado esperado**: La franja de 12:00 retorna con `status: "AVAILABLE"` (49/50 ocupados).
  4. Aprobar el pedido número 50.
  5. Consultar nuevamente el selector del comensal:
     - **Resultado esperado**: La franja de 12:00 retorna inmediatamente con `status: "SOLD_OUT"` y queda deshabilitada para nuevas selecciones.

---

### Escenario 3 — Reducción de capacidad por debajo de aprobados y no retroactividad
- **Objetivo**: Demostrar el cumplimiento de `INV-8`, `EC-005` y `SC-011`.
- **Procedimiento**:
  1. Con 40 pedidos ya aprobados en una franja de capacidad 50, el Administrador reduce la capacidad máxima a 30:
     ```http
     PUT /api/v1/admin/slot-capacities
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     [
       { "slotDate": "2026-11-03", "startTime": "12:00:00", "maxCapacity": 30 }
     ]
     ```
  2. Consultar el estado de la franja y de los pedidos:
     - **Resultado esperado**:
       - La franja pasa de inmediato a `status: "SOLD_OUT"` en el selector del comensal ($40 \ge 30$).
       - El 100% de los 40 pedidos previamente aprobados conservan su estado `APPROVED` intacto sin cancelaciones retroactivas (`INV-8`).

---

### Escenario 4 — Control de visibilidad de métodos de pago en checkout
- **Objetivo**: Demostrar el cumplimiento de `AC-3.1`, `INV-4`, `SC-004` y `FR-011`.
- **Procedimiento**:
  1. El Administrador apaga Stripe:
     ```http
     PUT /api/v1/admin/payment-methods/STRIPE
     Authorization: Bearer <ADMIN_TOKEN>
     Content-Type: application/json

     { "isEnabled": false }
     ```
  2. Como comensal, solicitar los métodos de pago en la vista de checkout:
     ```http
     GET /api/v1/establishments/{tenantId}/payment-methods
     ```
     - **Resultado esperado**: La respuesta contiene únicamente MercadoPago; Stripe no está presente en la lista (`AC-3.1`).

---

### Escenario 5 — Todos los métodos de pago deshabilitados
- **Objetivo**: Demostrar el cumplimiento de `EC-009` y `FR-013`.
- **Procedimiento**:
  1. El Administrador deshabilita también MercadoPago (`isEnabled: false`).
  2. El comensal accede al checkout:
     - **Resultado esperado**: La API retorna lista vacía `[]`. La interfaz despliega el mensaje de indisponibilidad temporal de pagos online y mantiene el botón de confirmación deshabilitado.

---

### Escenario 6 — Revalidación dinámica ante cambio concurrente en checkout
- **Objetivo**: Demostrar el cumplimiento de `INV-9` y `EC-010`.
- **Procedimiento**:
  1. El comensal tiene en pantalla seleccionado Stripe.
  2. Antes de que el comensal confirme la orden, el Administrador apaga Stripe (`isEnabled: false`).
  3. El comensal envía la confirmación de la orden con Stripe:
     - **Resultado esperado**: HTTP 409 Conflict con mensaje explicativo solicitando elegir una alternativa habilitada.

---

### Escenario 7 — Prevención atómica de sobreventa concurrente en el cupo 50
- **Objetivo**: Demostrar el cumplimiento de `EC-016` bajo concurrencia real.
- **Procedimiento**:
  1. Franja con 49 pedidos aprobados y capacidad 50.
  2. Dos hilos concurrentes ejecutan en simultáneo la aprobación del pedido 50 y pedido 51:
     - **Resultado esperado**:
       - Exactamente una transacción logra el incremento (`approvedOrdersCount = 50`).
       - La segunda transacción es rechazada con `SlotCapacityExceededException` (HTTP 409 Conflict).
       - El contador final en base de datos es estrictamente 50, nunca 51.

