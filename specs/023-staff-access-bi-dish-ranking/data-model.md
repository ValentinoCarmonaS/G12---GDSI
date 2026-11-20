# Modelo de datos: control de acceso de personal, salud financiera y ranking de platos

**Rama**: `023-staff-access-bi-dish-ranking` | **Fecha**: 20/11/2026 | **Especificacion**: [spec.md](./spec.md)

---

## 1. Vision general

Este documento define las entidades, atributos, restricciones de integridad, transiciones de estado y esquemas de base de datos necesarios para soportar las tres historias de usuario de la especificacion 023.

El modelo aprovecha las entidades existentes (`User`, `Order`, `OrderItem`, `Dish`, `Establishment`), introduce una migracion Flyway puntual (`V29`) para incorporar el hito de acreditacion temporal `paid_at` en `orders`, y formaliza las agregaciones analiticas para Business Intelligence.

---

## 2. Entidades de persistencia

### 2.1. Entidad `User` (Tabla `users`)

Representa la identidad digital y relacion de un empleado o usuario con el establecimiento.

| Campo | Tipo SQL | Tipo Java | Nulable | Descripcion |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | No | Clave primaria. |
| `email` | `VARCHAR(255)` | `String` | No | Correo electronico unico y normalizado. |
| `password_hash` | `VARCHAR(255)` | `String` | No | Hash de contrasena BCrypt. |
| `role` | `VARCHAR(50)` | `UserRole` | No | Rol del usuario (`COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`). |
| `status` | `VARCHAR(50)` | `UserStatus` | No | Estado operativo (`ACTIVE`, `SUSPENDED`, `PENDING_FIRST_LOGIN`, `UNVERIFIED`). |
| `tenant_id` | `UUID` | `UUID` | Si | Identificador del establecimiento al que pertenece el personal. |
| `token_version` | `INTEGER` | `Integer` | No | Contador de invalidacion de tokens (inicializado en 0). |
| `full_name` | `VARCHAR(255)` | `String` | No | Nombre completo del empleado. |
| `created_at` | `TIMESTAMPTZ` | `Instant` | No | Instante de creacion del usuario. |
| `updated_at` | `TIMESTAMPTZ` | `Instant` | No | Instante de ultima actualizacion. |

#### Ciclo de vida y transiciones de estado del personal

```
[Registro inicial]
       ↓
PENDING_FIRST_LOGIN
       ↓ (Primer cambio de contrasena)
     ACTIVE <═══════════════════════════════╗
       ↓                                    ║
       ↓ (Accion de suspension administrativa) ║ (Reactivacion)
       ↓                                    ║
   SUSPENDED ═══════════════════════════════╝
```

#### Reglas de validacion e invariantes de `User`
1. **Invariante de bloqueo en login**: Si `status` es `SUSPENDED` o `UNVERIFIED`, el login es rechazado de inmediato tras verificar la contrasena, emitiendo error 401 generico.
2. **Invariante de proteccion contra orfandad administrativa**: Un usuario con `role = ADMINISTRADOR` y `status = ACTIVE` no puede transicionar a `SUSPENDED` si `count(tenant_id, ADMINISTRADOR, ACTIVE) <= 1`.
3. **Inmutabilidad de auditoria historica**: La transicion de estado no altera la relacion de auditoria en pedidos o comandas previamente despachadas por el usuario.

---

### 2.2. Entidad `Order` (Tabla `orders`)

Representa la orden comercial emitida por un comensal.

| Campo | Tipo SQL | Tipo Java | Nulable | Descripcion |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | No | Clave primaria. |
| `establishment_id` | `UUID` | `UUID` | No | FK a `establishments(id)`. |
| `user_id` | `UUID` | `UUID` | No | FK a `users(id)`. |
| `fulfillment_mode` | `VARCHAR(30)` | `FulfillmentMode` | No | Modalidad (`IMMEDIATE`, `SCHEDULED`). |
| `pickup_date` | `DATE` | `LocalDate` | No | Fecha de retiro pactada. |
| `time_slot_start` | `TIME` | `LocalTime` | No | Inicio de la franja horaria. |
| `time_slot_end` | `TIME` | `LocalTime` | No | Fin de la franja horaria. |
| `status` | `VARCHAR(30)` | `OrderStatus` | No | Estado del ciclo de vida del pedido. |
| `total_amount` | `DECIMAL(12, 2)` | `BigDecimal` | No | Importe monetario total del pedido. |
| `paid_at` | `TIMESTAMPTZ` | `Instant` | Si | **Nuevo hito temporal (V29)**: Instante exacto en que el pago quedo acreditado. |
| `ready_at` | `TIMESTAMPTZ` | `Instant` | Si | Instante en que la cocina marco el pedido listo. |
| `completed_at` | `TIMESTAMPTZ` | `Instant` | Si | Instante en que mostrador entrego el pedido (V28). |
| `security_code` | `VARCHAR(4)` | `String` | Si | Codigo PIN de seguridad de 4 digitos. |
| `expires_at` | `TIMESTAMPTZ` | `Instant` | Si | Vencimiento de reserva para pago pendiente. |
| `created_at` | `TIMESTAMPTZ` | `Instant` | No | Instante de creacion de la orden. |
| `updated_at` | `TIMESTAMPTZ` | `Instant` | No | Instante de modificacion. |

#### Definicion canonica de "Pedido concretado"
Una orden se considera concretada si y solo si su estado pertenece a:
$$\text{Status} \in \{\text{PAID}, \text{IN\_PROGRESS}, \text{READY\_FOR\_PICKUP}, \text{COMPLETED}\}$$

Estados expresamente excluidos de cualquier calculo analitico:
$$\text{Status} \in \{\text{PENDING\_PAYMENT}, \text{PENDING\_ACCREDITATION}, \text{CANCELLED}, \text{EXPIRED}\}$$

#### Invariante de anclaje temporal de ingresos
Para las granularidades (dia, semana, mes), el pedido se ubica en el tiempo segun su marca `paid_at`. Una orden sin `paid_at` o con pago no acreditado no computa para ingresos ni para conteo de pedidos concretados.

---

### 2.3. Entidad `OrderItem` (Tabla `order_items`)

Representa la linea de detalle de un plato adquirido en una orden.

| Campo | Tipo SQL | Tipo Java | Nulable | Descripcion |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | No | Clave primaria. |
| `order_id` | `UUID` | `UUID` | No | FK a `orders(id)` con `ON DELETE CASCADE`. |
| `dish_id` | `UUID` | `UUID` | No | FK a `dishes(id)` con `ON DELETE RESTRICT`. |
| `dish_name` | `VARCHAR(150)` | `String` | No | Denominacion comercial del plato al momento de la venta. |
| `unit_price` | `DECIMAL(10, 2)` | `BigDecimal` | No | Precio unitario cobrado. |
| `quantity` | `INTEGER` | `Integer` | No | Cantidad fisica de unidades adquiridas. |
| `subtotal` | `DECIMAL(12, 2)` | `BigDecimal` | No | Subtotal de la linea (`unit_price * quantity`). |

#### Invariante de unidades vendidas para el ranking
Las unidades vendidas de un plato corresponden estrictamente a la suma acumulada de las cantidades fisicas de sus lineas en pedidos concretados:
$$\text{UnitsSold}(d) = \sum_{oi \in \text{OrderItems}(d, \text{ConcretedOrders})} oi.\text{quantity}$$

---

### 2.4. Entidad `Dish` (Tabla `dishes`)

Representa el plato en el catalogo del establecimiento.

| Campo | Tipo SQL | Tipo Java | Nulable | Descripcion |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | `UUID` | No | Clave primaria. |
| `tenant_id` | `UUID` | `UUID` | No | Establecimiento propietario. |
| `name` | `VARCHAR(100)` | `String` | No | Nombre del plato en catalogo. |
| `retail_price` | `DECIMAL(10, 2)` | `BigDecimal` | Si | Precio de venta al publico actual. |
| `status` | `VARCHAR(50)` | `DishStatus` | No | Estado del plato (`DRAFT`, `PUBLISHED`, `INACTIVE`, etc.). |

#### Regla de inclusion en el ranking
El ranking se calcula a partir de los `order_items` de pedidos concretados. Platos con cero ventas en el periodo consultado son omitidos del resultado (no se realiza cruce forzado para incluirlos con valor cero).

---

## 3. Agregaciones y modelos de transferencia (DTOs)

### 3.1. DTO `FinancialHealthResponse`

```java
public record FinancialHealthResponse(
    String granularity,        // "day", "week", "month"
    String periodStart,        // ISO-8601 con zona horaria (ej: "2026-11-20T00:00:00-03:00")
    String periodEnd,          // ISO-8601 con zona horaria (ej: "2026-11-20T23:59:59.999999999-03:00")
    BigDecimal revenue,        // Total acumulado monetario (escala 2)
    Long orderCount            // Total de pedidos concretados
) {}
```

### 3.2. DTO `DishRankingItemResponse`

```java
public record DishRankingItemResponse(
    UUID dishId,               // Identificador del plato
    String dishName,           // Nombre comercial del plato
    Long unitsSold             // Cantidad total de unidades vendidas
) {}
```

### 3.3. DTO `StaffStatusUpdateRequest`

```java
public record StaffStatusUpdateRequest(
    @NotNull(message = "El estado de cuenta es obligatorio")
    StaffAccountStatus status  // "ACTIVE" o "SUSPENDED"
) {}
```

---

## 4. Migracion de base de datos Flyway: `V29__add_paid_at_to_orders.sql`

```sql
-- ====================================================================
-- EasyEat V29: Paid accreditation timestamp on orders (Spec 023)
-- Adds explicit paid_at lifecycle timestamp to decouple BI analytics
-- from payment gateways and guarantee high-performance queries.
-- ====================================================================

-- 1. Agregar columna paid_at
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN orders.paid_at IS
    'Instante inmutable de la acreditacion efectiva del pago de la orden (Spec 023).';

-- 2. Backfill para ordenes existentes desde payments.approved_at o updated_at
UPDATE orders o
SET paid_at = COALESCE(p.approved_at, o.updated_at, o.created_at)
FROM payments p
WHERE p.order_id = o.id
  AND p.status = 'APPROVED'
  AND o.paid_at IS NULL;

-- Para ordenes historicas en estado concretado sin registro en payments
UPDATE orders
SET paid_at = COALESCE(updated_at, created_at)
WHERE status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')
  AND paid_at IS NULL;

-- 3. Indice parcial compuesto para consultas analiticas sub-milisegundo
CREATE INDEX IF NOT EXISTS idx_orders_analytics
    ON orders (establishment_id, paid_at)
    WHERE status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED');
```