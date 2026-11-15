# Modelo de datos y transiciones de estado: KDS — cola de cocina y preparación activa

**Especificación**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Fecha**: 15/11/2026  
**Rama**: `019-kds-queue-preparation`

---

## 1. Impacto sobre el esquema de base de datos relacional

No se requieren nuevas migraciones destructivas ni alteración de columnas en `kds_tickets` u `orders`. Todas las entidades necesarias fueron creadas previamente en la migración `V23__individual_checkout_stock_deduction_and_kds.sql`.

### 1.1 Tabla `kds_tickets` (existente)

| Columna | Tipo SQL | Nullable | Restricción / Índice | Propósito de negocio |
| --- | --- | --- | --- | --- |
| `id` | `UUID` | No | Primary Key (`gen_random_uuid()`) | Identificador unívoco del ticket KDS. Desempate final de orden total. |
| `order_id` | `UUID` | No | `UNIQUE (order_id)`, FK `orders(id)` | Garantía estricta de una sola comanda por orden de comensal. |
| `establishment_id` | `UUID` | No | FK `establishments(id)` | Aislamiento multi-tenant por establecimiento gastronómico. |
| `ticket_number` | `INTEGER` | No | `CHECK (ticket_number > 0)` | Número correlativo visible para el personal de cocina y despacho. |
| `status` | `VARCHAR(30)` | No | Default `'QUEUED'`, `CHECK (status IN ('QUEUED', 'IN_PREPARATION', 'READY_FOR_PICKUP', 'COMPLETED'))` | Estado operativo de la comanda en cocina. |
| `fulfillment_mode` | `VARCHAR(30)` | No | `CHECK (fulfillment_mode IN ('IMMEDIATE', 'SCHEDULED'))` | Modalidad de entrega acordada con el comensal. |
| `pickup_date` | `DATE` | No | Indexado | Fecha de retiro asignada (primer criterio de prelación de cola). |
| `time_slot_start` | `TIME` | No | Indexado | Inicio de bloque operativo de 15 min (segundo criterio de prelación). |
| `time_slot_end` | `TIME` | No | Ninguna | Fin de bloque operativo de 15 min (`time_slot_start + 15 min`). |
| `queued_at` | `TIMESTAMPTZ` | No | Default `NOW()` | Timestamp de encolado en cocina (tercer criterio de prelación FIFO). |
| `started_at` | `TIMESTAMPTZ` | Sí | Nullable | Instante exacto en que el chef pulsa "EMPEZAR". Inmutable una vez fijado. |
| `ready_at` | `TIMESTAMPTZ` | Sí | Nullable | Instante en que pasa a mostrador (`READY_FOR_PICKUP`). Fuera de alcance aquí. |
| `completed_at` | `TIMESTAMPTZ` | Sí | Nullable | Instante de entrega final. Fuera de alcance aquí. |

### 1.2 Índices requeridos para la consulta determinista

- **Índice existente (`V23`)**:
  `idx_kds_tickets_establishment_status ON kds_tickets(establishment_id, status, pickup_date, time_slot_start)`
- **Índice optimizado recomendado**:
  Para soportar la consulta de cola activa (`WHERE establishment_id = :id AND status IN ('QUEUED', 'IN_PREPARATION') AND pickup_date <= :today ORDER BY pickup_date ASC, time_slot_start ASC, queued_at ASC, id ASC`) sin necesidad de ordenamiento en memoria (*Sort node* en plan de ejecución PostgreSQL):
  ```sql
  CREATE INDEX IF NOT EXISTS idx_kds_tickets_active_queue 
      ON kds_tickets(establishment_id, status, pickup_date, time_slot_start, queued_at, id);
  ```

---

## 2. Ciclo de vida y máquina de estados

### 2.1 Transiciones de estado de `KdsTicket` y sincronización con `Order`

```text
                  [ Pago Aprobado / Confirmación ]
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │     KdsTicket:        │
                     │       QUEUED          │
                     │  Order: IN_PROGRESS   │
                     │  started_at: null     │
                     └───────────┬───────────┘
                                 │
                   Chef pulsa "EMPEZAR"
                   (POST .../start-preparation)
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │     KdsTicket:        │◀────── Clik repetido / Reintento de red
                     │    IN_PREPARATION     │        (Idempotente: HTTP 200 OK,
                     │  Order: IN_PROGRESS   │         mismo started_at original,
                     │  started_at: NOW()    │         sin duplicar comanda)
                     └───────────┬───────────┘
                                 │
                     (Fuera de alcance de Spec 019:
                      Spec 020: Despacho a mostrador)
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │     KdsTicket:        │
                     │   READY_FOR_PICKUP    │
                     │  Order: READY_...     │
                     └───────────────────────┘
```

### 2.2 Reglas de transición e invariantes de negocio

1. **Transición atómica `QUEUED → IN_PREPARATION`**:
   - Se ejecuta bajo transacción `@Transactional` con bloqueo pesimista `SELECT ... FOR UPDATE` sobre la fila de `kds_tickets`.
   - Fija `started_at = Instant.now()`.
   - Asegura `order.status = OrderStatus.IN_PROGRESS` y `order.updatedAt = started_at`.
   - La tarjeta desaparece de Columna 1 ("Pendientes") y aparece en Columna 2 ("En preparación").
2. **Idempotencia estricta ante reintentos o concurrencia**:
   - Si se invoca `start-preparation` sobre un ticket que ya tiene estado `IN_PREPARATION`:
     - Retorna `200 OK` confirmando el ticket.
     - Preserva inalterado el timestamp `started_at` persistido originalmente.
     - No altera el tiempo transcurrido ni genera una segunda comanda en la Columna 2.
3. **Bloqueo ante estados avanzados o terminales**:
   - Si el ticket ya se encuentra en `READY_FOR_PICKUP` o `COMPLETED`, responder `409 Conflict` con código de error `INVALID_TICKET_STATE`.
4. **Normalización uniforme de pedidos inmediatos (`IMMEDIATE`)**:
   - Para toda comanda generada a partir de un pedido inmediato ingresado en el minuto $M \in [0, 59]$:
     - $\text{timeSlotStart} = \text{LocalTime.of}(\text{hora}, \lfloor M / 15 \rfloor \times 15)$
     - $\text{timeSlotEnd} = \text{timeSlotStart} + 15 \text{ min}$
   - Esto sitúa al pedido en la misma franja horaria que los pedidos programados para ese cuarto de hora, dirimiéndose la prioridad estrictamente por el momento FIFO de confirmación (`queued_at`).

---

## 3. Contratos DTO y modelos de transferencia

### 3.1 Backend (`easyeat/backend/src/main/java/com/easyeat/order/`)

#### 3.1.1 DTO de ítem de plato en comanda (`KdsOrderItemResponse`)

```java
public record KdsOrderItemResponse(
    UUID dishId,
    String dishName,
    Integer quantity
) {}
```

#### 3.1.2 DTO de comanda activa en cola (`KdsActiveQueueTicketResponse`)

```java
public record KdsActiveQueueTicketResponse(
    UUID ticketId,
    Integer ticketNumber,
    UUID orderId,
    KdsTicketStatus status,
    FulfillmentMode fulfillmentMode,
    LocalDate pickupDate,
    LocalTime timeSlotStart,
    LocalTime timeSlotEnd,
    Instant queuedAt,
    Instant startedAt,
    List<KdsOrderItemResponse> items
) {}
```

#### 3.1.3 DTO de respuesta para inicio de preparación (`KdsStartPreparationResponse`)

```java
public record KdsStartPreparationResponse(
    UUID ticketId,
    Integer ticketNumber,
    UUID orderId,
    KdsTicketStatus status,
    Instant startedAt
) {}
```

### 3.2 Frontend (`easyeat/frontend/src/types/kds.ts`)

```typescript
export type KdsTicketStatus = 'QUEUED' | 'IN_PREPARATION' | 'READY_FOR_PICKUP' | 'COMPLETED';

export type FulfillmentMode = 'IMMEDIATE' | 'SCHEDULED';

export interface KdsOrderItem {
  dishId: string;
  dishName: string;
  quantity: number;
}

export interface KdsTicket {
  ticketId: string;
  ticketNumber: number;
  orderId: string;
  status: KdsTicketStatus;
  fulfillmentMode: FulfillmentMode;
  pickupDate: string;        // YYYY-MM-DD
  timeSlotStart: string;     // HH:mm:ss
  timeSlotEnd: string;       // HH:mm:ss
  queuedAt: string;          // ISO-8601 UTC
  startedAt: string | null;  // ISO-8601 UTC
  items: KdsOrderItem[];
}

export interface KdsStartPreparationResponse {
  ticketId: string;
  ticketNumber: number;
  orderId: string;
  status: KdsTicketStatus;
  startedAt: string;         // ISO-8601 UTC
}
```

---

## 4. Invariantes de presentación y ergonomía visual

| Componente visual | Elementos incluidos | Elementos excluidos | Comportamiento reactivo |
| --- | --- | --- | --- |
| **Columna 1 ("Pendientes")** | `#ticketNumber`, franja `HH:mm - HH:mm`, botón "EMPEZAR". Scroll táctil continuo vertical. | Lista de platos, cronómetro, placeholders de notas ("Sin observaciones", etc.). | Al pulsar "EMPEZAR", se deshabilita temporalmente el botón para prevenir doble submit local y la tarjeta transiciona a Columna 2. |
| **Columna 2 ("En preparación")** | `#ticketNumber`, franja `HH:mm - HH:mm`, cronómetro `MM min`, lista completa de platos en formato `[Cantidad]x [Nombre del Plato]`. | Botón "EMPEZAR", placeholders de notas. | Cronómetro recalcula minutos cada 60 s contra `started_at` del servidor. Al alcanzar `tiempo >= 15 min`, conmuta borde e insignia a ámbar. |
| **Columna vacía (cualquiera)** | Contenedor con mensaje neutral: `"Sin pedidos pendientes"` o `"Sin pedidos en preparación"`. | Mensajes de error, colapso de columna. | Mantiene estructura fija de dos columnas idénticas sin saltos visuales (*layout shift*). |
