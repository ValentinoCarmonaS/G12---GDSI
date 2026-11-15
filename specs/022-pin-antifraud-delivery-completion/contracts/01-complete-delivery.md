# Contrato de API: cierre terminal de entrega (`complete-delivery`)

**Especificación**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `POST /api/v1/counter/orders/{orderId}/complete-delivery`  
**Seguridad**: `@PreAuthorize("hasAnyRole('ROLE_ENCARGADO', 'ENCARGADO', 'ROLE_ADMIN', 'ADMINISTRADOR')")`  

---

## 1. Descripción operativa

Endpoint ejecutado mediante acción directa (un solo clic) desde la tarjeta desplegada de mostrador al contrastar verbalmente la coincidencia del código numérico de seguridad de 4 dígitos recitado por el comensal.

Ejecuta el archivado atómico, indivisible e irreversible del pedido, transicionando simultáneamente `Order.status = COMPLETED`, `KdsTicket.status = COMPLETED` y registrando el instante exacto de entrega `completed_at`.

---

## 2. Petición HTTP

- **Método**: `POST`
- **Ruta**: `/api/v1/counter/orders/{orderId}/complete-delivery`
- **Parámetros de ruta**:
  - `orderId` (UUID, obligatorio): Identificador universal de la orden a completar.
- **Cabeceras obligatorias**:
  - `Authorization: Bearer <JWT_ENCARGADO>`
- **Cuerpo de la petición**: Vacío (`{}` o sin cuerpo).

---

## 3. Respuestas HTTP

### 3.1. Éxito: `200 OK` (Transición efectiva inicial)

Retornado cuando la orden se encontraba en estado `READY_FOR_PICKUP` y la entrega fue completada exitosamente.

```json
{
  "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "ticketNumber": 42,
  "status": "COMPLETED",
  "completedAt": "2026-11-15T18:30:00Z",
  "message": "Entrega completada exitosamente."
}
```

### 3.2. Éxito idempotente: `200 OK` (Reintento de red o petición concurrente)

Retornado cuando la orden **ya se encuentra** en estado `COMPLETED`. El sistema no muta datos, no altera el `completedAt` original y no dispara efectos secundarios.

```json
{
  "orderId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "ticketNumber": 42,
  "status": "COMPLETED",
  "completedAt": "2026-11-15T18:30:00Z",
  "message": "La orden ya se encontraba completada. Respuesta idempotente."
}
```

### 3.3. Conflicto de estado: `409 Conflict` (`INVALID_ORDER_STATE`)

Retornado cuando la orden existe pero se encuentra en un estado previo o inválido para entrega (`PENDING_PAYMENT`, `PENDING_ACCREDITATION`, `IN_PROGRESS`, `CANCELLED`, `EXPIRED`).

```json
{
  "type": "https://easyeat.com/errors/invalid-order-state",
  "title": "Conflict",
  "status": 409,
  "detail": "No se puede entregar una orden que se encuentra en estado IN_PROGRESS. Debe estar en estado READY_FOR_PICKUP.",
  "instance": "/api/v1/counter/orders/b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/complete-delivery",
  "code": "INVALID_ORDER_STATE"
}
```

### 3.4. Recurso no encontrado: `404 Not Found` (`ORDER_NOT_FOUND`)

Retornado si el `orderId` especificado no existe en el sistema.

```json
{
  "type": "https://easyeat.com/errors/order-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Orden no encontrada con identificador b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "instance": "/api/v1/counter/orders/b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/complete-delivery",
  "code": "ORDER_NOT_FOUND"
}
```

### 3.5. No autorizado / Prohibido: `401 Unauthorized` / `403 Forbidden`

- `401`: Token JWT faltante, inválido o expirado.
- `403`: Usuario autenticado sin rol de mostrador o administración (`COMENSAL`, `CHEF` sin permisos cruzados).

---

## 4. Garantías transaccionales y de concurrencia

1. **Aislamiento**: Se ejecuta bajo `@Transactional(isolation = Isolation.READ_COMMITTED)`.
2. **Bloqueo pesimista**: Utiliza `orderRepository.findByIdForUpdate(orderId)` emitiendo `SELECT ... FOR UPDATE`.
3. **Serialización ante carreras concurrentes**: Si dos terminales de mostrador accionan simultáneamente "ENTREGAR PEDIDO", el primer hilo adquiere el bloqueo de fila, muta a `COMPLETED` con su `completed_at` y hace `COMMIT`. El segundo hilo adquiere el bloqueo inmediatamente después, evalúa `order.getStatus() == COMPLETED` y responde con el `200 OK` idempotente devolviendo el `completed_at` original sin realizar escrituras.
4. **Indivisibilidad**: Si ocurre una excepción al persistir `KdsTicket`, la transacción se revierte en su totalidad y `Order` permanece en `READY_FOR_PICKUP`.
