# Behavioral Contracts: Restaurant Operation Configuration

**Feature**: `010-restaurant-operation-config` | **Date**: 31/10/2026 | **Spec**: [spec.md](../spec.md)

---

## 1. Configurar Horarios Comerciales Semanales
- **Identificador funcional**: `ConfigureWeeklyOperatingSchedules` (`PUT /api/v1/admin/operating-schedules`)
- **Actor**: Administrador (`ROLE_ADMINISTRADOR`, `ROLE_ADMIN`).
- **Entrada**:
  - `List<UpdateScheduleRequest>`:
    - `dayOfWeek`: `DayOfWeek` (Obligatorio, uno de los 7 días).
    - `openingTime`: `LocalTime` (`HH:mm`, requerido si `isClosed == false`).
    - `closingTime`: `LocalTime` (`HH:mm`, requerido si `isClosed == false`).
    - `isClosed`: `boolean` (true para día no laborable).
    - `expectedVersion`: `Long` (opcional, para concurrencia optimista).
- **Salida**:
  - `List<OperatingScheduleResponse>` con `id`, `dayOfWeek`, `openingTime`, `closingTime`, `isClosed`, `version`.
- **Invariantes garantizados**:
  - `INV-7`: Si `isClosed == false`, $\text{openingTime} < \text{closingTime}$.
  - `INV-8`: No altera pedidos ya aprobados previamente.
- **Errores**:
  - $\text{openingTime} \ge \text{closingTime}$ (cuando `isClosed == false`) $\rightarrow$ HTTP 400 Bad Request, `ProblemDetail` con campo `openingTime`.
  - Intento de acceso sin rol Administrador $\rightarrow$ HTTP 403 Forbidden.
  - Conflicto de versión optimista $\rightarrow$ HTTP 409 Conflict.
- **Precondiciones**: Administrador autenticado con tenant activo en `TenantContextHolder`.
- **Postcondiciones**: Horarios persistidos en `establishment_operating_schedules`. Efecto inmediato en la grilla y selector del comensal.
- **Trazabilidad**: Mapea a `ADM-2.2.1`, `AC-1.1`, `INV-1`, `INV-7`, `INV-8`, `FR-001`, `FR-002`, `FR-014`, `SC-005`, `SC-008`, `EC-003`, `EC-008`, `EC-012`.

---

## 2. Configurar Capacidad Máxima de Franja Horaria
- **Identificador funcional**: `UpdateSlotCapacityMatrix` (`PUT /api/v1/admin/slot-capacities`)
- **Actor**: Administrador.
- **Entrada**:
  - `List<UpdateSlotCapacityRequest>`:
    - `slotDate`: `LocalDate` (formato `YYYY-MM-DD`, obligatorio).
    - `startTime`: `LocalTime` (formato `HH:mm`, obligatorio).
    - `maxCapacity`: `int` (entero $\ge 0$, obligatorio).
    - `expectedVersion`: `Long` (opcional).
- **Salida**:
  - `List<SlotCapacityAdminResponse>` con `slotDate`, `startTime`, `endTime`, `maxCapacity`, `approvedOrdersCount`, `isSoldOut`, `version`.
- **Invariantes garantizados**:
  - `INV-6`: $\text{maxCapacity} \ge 0$.
  - `INV-3`: Si $\text{approvedOrdersCount} \ge \text{maxCapacity}$, `isSoldOut = true`.
  - `INV-8`: Reducción por debajo de pedidos ya aprobados no cancela los aprobados.
- **Errores**:
  - `maxCapacity < 0` o valor no entero $\rightarrow$ HTTP 400 Bad Request con `detail: "La capacidad máxima debe ser un entero mayor o igual a 0"`.
  - Conflicto de versión optimista $\rightarrow$ HTTP 409 Conflict.
- **Precondiciones**: Administrador autenticado.
- **Postcondiciones**: Fila actualizada o insertada atómicamente en `establishment_slot_capacities`.
- **Trazabilidad**: Mapea a `ADM-2.2.2`, `AC-2.1`, `INV-2`, `INV-3`, `INV-6`, `INV-8`, `FR-006`, `FR-007`, `FR-008`, `FR-009`, `FR-014`, `SC-003`, `SC-005`, `SC-007`, `EC-004`, `EC-005`, `EC-006`, `EC-012`.

---

## 3. Consultar Selector de Franjas y Disponibilidad (Comensal)
- **Identificador funcional**: `GetDinerTimeSlots` (`GET /api/v1/establishments/{tenantId}/time-slots?date=YYYY-MM-DD`)
- **Actor**: Comensal (`ROLE_COMENSAL`).
- **Entrada**:
  - `tenantId`: UUID (path).
  - `date`: `LocalDate` (query, obligatorio).
- **Salida**:
  - `DinerTimeSlotsResponse`:
    - `date`: `LocalDate`.
    - `isClosed`: `boolean`.
    - `closureReason`: `String` (opcional).
    - `timezone`: `String`.
    - `slots`: Lista de bloques de 15 min con `startTime`, `endTime`, y `status` (`AVAILABLE` o `SOLD_OUT`).
- **Invariantes garantizados**:
  - `INV-1`: Si fecha es hoy y hora actual del establecimiento $\ge$ hora de cierre, todas las franjas de hoy están bloqueadas/cerradas (`AC-1.1`). Si la fecha es futura, se evalúa contra el horario y cupo de ese día futuro.
  - `INV-5`: Partición determinística en bloques de 15 minutos desde `openingTime` hasta `closingTime`.
  - `INV-3`: Bloques con $\text{approvedOrdersCount} \ge \text{maxCapacity}$ o no configurados (`maxCapacity=0`) se marcan como `SOLD_OUT`.
- **Errores**:
  - `tenantId` inválido o no existe $\rightarrow$ HTTP 404 Not Found.
  - Formato de fecha inválido $\rightarrow$ HTTP 400 Bad Request.
- **Precondiciones**: Comensal autenticado.
- **Postcondiciones**: Consulta idempotente de solo lectura. Refleja estado vivo en $\le 2\text{ s}$ (`SC-006`).
- **Trazabilidad**: Mapea a `ADM-2.2.1`, `ADM-2.2.2`, `AC-1.1`, `AC-2.1`, `INV-1`, `INV-2`, `INV-3`, `INV-5`, `FR-003`, `FR-004`, `FR-005`, `FR-008`, `FR-009`, `FR-015`, `FR-016`, `FR-017`, `SC-001`, `SC-002`, `SC-003`, `SC-006`, `EC-001`, `EC-007`, `EC-008`, `EC-013`, `EC-014`, `EC-015`, `EC-017`, `EC-018`.

---

## 4. Habilitar o Deshabilitar Método de Pago Online (Admin)
- **Identificador funcional**: `TogglePaymentMethod` (`PUT /api/v1/admin/payment-methods/{paymentMethod}`)
- **Actor**: Administrador.
- **Entrada**:
  - `paymentMethod`: Enum (`MERCADO_PAGO`, `STRIPE`).
  - `TogglePaymentMethodRequest`:
    - `isEnabled`: `boolean` (true o false).
    - `expectedVersion`: `Long` (opcional).
- **Salida**:
  - `PaymentMethodResponse` con `paymentMethod`, `displayName`, `isEnabled`, `version`.
- **Invariantes garantizados**:
  - `INV-4`: Visibilidad inmediata en checkout gobernada por `isEnabled`.
  - Costo cero: Operación puramente sobre flag booleano.
- **Errores**:
  - Método no soportado $\rightarrow$ HTTP 400 Bad Request.
  - Conflicto de versión optimista $\rightarrow$ HTTP 409 Conflict.
- **Trazabilidad**: Mapea a `ADM-2.2.3`, `AC-3.1`, `INV-4`, `FR-010`, `FR-011`, `SC-004`, `SC-005`, `EC-012`.

---

## 5. Consultar Métodos de Pago Visibles en Checkout (Comensal)
- **Identificador funcional**: `GetDinerPaymentMethods` (`GET /api/v1/establishments/{tenantId}/payment-methods`)
- **Actor**: Comensal.
- **Entrada**:
  - `tenantId`: UUID.
- **Salida**:
  - `List<DinerPaymentMethodResponse>` conteniendo exclusivamente los métodos donde `isEnabled == true`.
- **Invariantes garantizados**:
  - `INV-4`: Omisión estricta de cualquier método apagado. Si todos están apagados, devuelve lista vacía `[]`, provocando que el checkout muestre mensaje informativo y deshabilite la compra (`EC-009`, `FR-013`).
- **Trazabilidad**: Mapea a `ADM-2.2.3`, `AC-3.1`, `INV-4`, `FR-011`, `FR-013`, `SC-004`, `EC-009`.

---

## 6. Revalidar Método de Pago al Confirmar Orden (Comensal)
- **Identificador funcional**: `ValidatePaymentMethodAtConfirmation` (`POST /api/v1/establishments/{tenantId}/checkout/validate-payment`)
- **Actor**: Comensal / Sistema de Checkout.
- **Entrada**:
  - `tenantId`: UUID.
  - `paymentMethod`: `PaymentMethodEnum`.
- **Salida**:
  - `{ "valid": true }` si el método continúa activo.
- **Invariantes garantizados**:
  - `INV-9`: Evaluación dinámica en tiempo real.
- **Errores**:
  - Si el método fue apagado mientras el comensal navegaba (`EC-010`) $\rightarrow$ HTTP 409 Conflict, `detail: "El método de pago seleccionado ya no se encuentra disponible. Por favor elija otra opción."`.
- **Trazabilidad**: Mapea a `ADM-2.2.3`, `AC-3.1`, `INV-9`, `FR-012`, `EC-010`.

---

## 7. Aprobar Pedido con Enforcement Atómico de Capacidad (Punto de Integración)
- **Identificador funcional**: `ApproveSlotOrderWithCapacityEnforcement` (Servicio interno de backend)
- **Actor**: Sistema / Flujo de Aprobación de Pedidos (`A-002`).
- **Entrada**:
  - `establishmentId`: UUID.
  - `slotDate`: `LocalDate`.
  - `startTime`: `LocalTime`.
  - `orderId`: UUID.
- **Salida**:
  - Confirmación de cupo reservado atómicamente y franja actualizada.
- **Invariantes y Atomicidad**:
  - `INV-2`: Solo incrementa si $\text{approvedOrdersCount} < \text{maxCapacity}$.
  - `EC-016`: Ejecuta `UPDATE establishment_slot_capacities SET approved_orders_count = approved_orders_count + 1 WHERE id = :id AND approved_orders_count < maxCapacity`. Si filas afectadas == 0, rechaza la aprobación con `SlotCapacityExceededException` (HTTP 409 Conflict).
  - `INV-3`: Si el contador alcanza `maxCapacity`, la franja pasa a "Agotada" en el selector.
- **Trazabilidad**: Mapea a `ADM-2.2.2`, `AC-2.1`, `INV-2`, `INV-3`, `FR-008`, `FR-009`, `SC-003`, `EC-016`.

