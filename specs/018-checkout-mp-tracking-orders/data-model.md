# Modelo de datos y transiciones de estado: unificación de checkout Mercado Pago, webhook a cocina e historial de pedidos

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Fecha**: 11/11/2026

---

## 1. Impacto sobre esquema de base de datos

No se requieren nuevas migraciones Flyway ni modificaciones de tablas o columnas (`orders`, `order_items`, `payments`, `temporary_reservations`, `kds_tickets`, `order_receipts`). Todas las garantías de este incremento operan sobre entidades y restricciones existentes (incluidas las restricciones únicas `uq_kds_tickets_order_id` y `order_receipts.order_id` / `idempotency_key`).

---

## 2. Ciclo de vida unificado de `Order`, `Payment`, `TemporaryReservation` y `KdsTicket`

### 2.1 Transiciones de estado permitidas y bloqueos de seguridad

```text
[PENDING_PAYMENT]
   ├── webhook/confirm (approved + verificación) ──▶ [IN_PROGRESS]
   │      + TemporaryReservation -> CONFIRMED
   │      + Payment -> APPROVED (detail: accredited)
   │      + StockDeductionService (una única vez)
   │      + KdsTicket -> QUEUED (ticketNumber secuencial persistido)
   │      + OrderPaymentApprovedEvent (AFTER_COMMIT) -> OrderReceipt + correo
   │
   ├── webhook/retorno (pending / in_process) ─────▶ [PENDING_ACCREDITATION]
   │      + TemporaryReservation (permanece activa/retenida)
   │      + Payment -> IN_PROCESS (statusDetail actualizado)
   │      + Sin débito de stock, sin KdsTicket (ticketNumber = null)
   │
   ├── cancelar intento (comensal) ────────────────▶ [CANCELLED]
   │      + TemporaryReservation -> RELEASED (CANCELLED_BY_USER)
   │      + Payment -> CANCELLED
   │
   ├── rechazo pasarela (webhook/retorno) ─────────▶ [CANCELLED]
   │      + TemporaryReservation -> RELEASED (REJECTED)
   │      + Payment -> REJECTED
   │
   └── expiración (scheduler o perezosa <= now) ───▶ [EXPIRED]
          + TemporaryReservation -> EXPIRED (EXPIRED)
          + Payment (si IN_PROCESS) -> EXPIRED

[PENDING_ACCREDITATION]
   ├── webhook/confirm (approved) ─────────────────▶ [IN_PROGRESS] (transición completa arriba)
   └── webhook (rejected / cancelled) ─────────────▶ [CANCELLED] (+ liberación reserva y pago)

[IN_PROGRESS | READY_FOR_PICKUP | COMPLETED | PAID | CONFIRMED]
   ├── confirmPayment / webhook approved replay ───▶ Respuesta idempotente (sin re-descontar stock ni duplicar KdsTicket)
   └── webhook rejected / cancelled tardío ────────▶ Sin retroceso de estado (registro ignorado)

[CANCELLED | EXPIRED]
   ├── confirmPayment (retorno navegador) ─────────▶ HTTP 409 INVALID_ORDER_STATE (bloquea stock/KDS)
   ├── webhook approved tardío (Q3) ───────────────▶ Bloquea stock/KDS + Log incidencia manual + HTTP 200 OK al webhook
   └── registerPendingPayment ─────────────────────▶ HTTP 409 INVALID_ORDER_STATE (sin retroceso)
```

### 2.2 Invariantes de integridad y auditoría

1. **Propiedad de la orden**: Toda operación iniciada por un comensal autenticado (`confirmPayment`, `registerPendingPayment`, `cancelPaymentAttempt`, `cancelOrder`, `getOrderPaymentStatus`, `getDinerOrders`) exige coincidencia `order.userId == authenticatedUserId`; la ausencia de coincidencia responde `403` (`AccessDeniedException`) o `404` (`OrderNotFoundException`) sin fugar datos ni devolver `500`.
2. **Fuente exclusiva de `ticketNumber`**: Ningún servicio (`OrderConfirmationTrackingService`, `OrderLiveTrackingService`, `OrderService`) calcula números de comanda por hash; el valor proviene exclusivamente de `KdsTicketRepository.findByOrderId(orderId)` y vale `null` hasta el ingreso real a cocina.
3. **Normalización de enlaces de pago**: Tanto en `PaymentGatewayPort.PreferenceResult` como en `OrderPaymentStatusResponse` y `DinerOrderSummaryResponse`, toda cadena vacía o compuesta solo por espacios en `initPoint` / `sandboxInitPoint` / `redirectUrl` se normaliza a `null`.

---

## 3. Contratos DTO y extensiones de modelos

### 3.1 Backend (`easyeat/backend/src/main/java/com/easyeat/order/`)

| Artefacto | Tipo / Cambio | Campos clave | Notas |
| --- | --- | --- | --- |
| `MercadoPagoProperties` | Extensión de `record` | `+ String backendBaseUrl` (7.º parámetro) | Constructor secundario de 6 args + `@ConstructorBinding` en el canónico |
| `ActiveReservationExistsException` | Extensión de excepción | `+ UUID orderId` (nullable en constructor 1-arg) | Expuesto como propiedad `orderId` en `ProblemDetail` (`409`) |
| `DinerOrderSummaryResponse` | Nuevo DTO (`record`) | `orderId`, `ticketNumber` (`Integer`), `establishmentId`, `establishmentName`, `status`, `fulfillmentMode`, `pickupDate`, `timeSlotStart`, `timeSlotEnd`, `totalAmount`, `expiresAt`, `createdAt`, `redirectUrl` (`String` nullable), `trackingUrl`, `items` (`List<DinerOrderItemResponse>`) | `redirectUrl` solo en `PENDING_PAYMENT`; `ticketNumber` solo con `KdsTicket` |
| `OrderConfirmationTrackingResponse` | Record existente (sin cambio de tipo) | `ticketNumber` (`Integer`, nullable), `approvalStatus` (`"PENDING"` o `"APPROVED"`) | Permite `ticketNumber = null` en `PENDING_ACCREDITATION` |
| `OrderLiveTrackingResponse` | Record existente (sin cambio de tipo) | `ticketNumber` (`Integer`, nullable), `isCountdownActive`, `estimatedMinutesRemaining`, `progressPercentage` | Inactivo (`0%`, `null` min/ticket) fuera de estados en cocina |

### 3.2 Frontend (`easyeat/frontend/src/types/`)

| Archivo | Interfaz | Cambios | Notas |
| --- | --- | --- | --- |
| `order.ts` | `OrderErrorProblemDetail` | `+ orderId?: string` | Permite leer `orderId` de `409 ACTIVE_RESERVATION_EXISTS` |
| `order.ts` | `DinerOrderItemResponse`, `DinerOrderSummaryResponse` | Nuevas interfaces espejadas con el contrato `GET /api/v1/orders` | `ticketNumber: number | null`, `redirectUrl: string | null` |
| `payment.ts` | `PaymentErrorCode` | Asegurar soporte de `'ACTIVE_RESERVATION_EXISTS'` | Alineado con `OrderErrorCode` |
| `orderTracking.ts` | `OrderConfirmationTrackingResponse`, `OrderLiveTrackingResponse`, `ReadyForPickupResponse` | `ticketNumber: number | null` | Elimina suposición de entero siempre presente antes de cocina |

---

## 4. Almacenamiento en cliente y aislamiento multi-pestaña (`FR-007`, `SC-005`)

| Clave de almacenamiento | Mecanismo | Alcance | Propósito y regla |
| --- | --- | --- | --- |
| `easyeat_cart` | `window.localStorage` | Compartido entre pestañas del origen | Persiste los ítems y metadatos del carrito al regresar en una nueva pestaña desde Mercado Pago |
| `access_token` | `window.sessionStorage` | Aislado por pestaña | Preserva sesiones paralelas sin sobrescribir tokens entre roles (`ADMINISTRADOR`, `CHEF`, `COMENSAL`) |
| `easyeat_active_tenant` | `window.sessionStorage` | Aislado por pestaña | Mantiene el establecimiento activo de cada pestaña |
| `selectedEstablishmentId` (legado) | Eliminado de `CheckoutPage` | N/A | Se remueve su lectura en `localStorage` para impedir fugas entre pestañas |

