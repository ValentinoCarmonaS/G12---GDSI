# Modelo de Datos y Esquema de Persistencia (Phase 1)

**Característica**: Gestión de Personal, RBAC e Inventario Inicial de Ingredientes  
**Rama**: `004-staff-mgmt-rbac-inventory`  
**Fecha**: 2026-10-20  
**Estado**: Completado  

---

## 1. Diagrama Entidad-Relación y Estructura Conceptual

El modelo de datos extiende el núcleo de autenticación y multi-tenant previo introduciendo dos dominios operativos:
1. **Personal y Roles (Staff)**: Empleados vinculados a un establecimiento (`tenant_id`) con roles de sistema y control de primer inicio de sesión.
2. **Inventario e Insumos (Inventory)**: Catálogo de ingredientes por local, control de ciclo de vida, stock físico exacto y pista de auditoría inmutable.

```
┌──────────────────────────────────────┐
│            establishments            │
├──────────────────────────────────────┤
│ id (PK) : UUID                       │
│ name : VARCHAR(255)                  │
│ created_at : TIMESTAMPTZ             │
└──────────────────┬───────────────────┘
                   │ 1
                   │
                   │ N
┌──────────────────▼───────────────────┐       1       N ┌──────────────────────────────────────┐
│                users                 │◄────────────────┤          stock_audit_entries         │
├──────────────────────────────────────┤                 ├──────────────────────────────────────┤
│ id (PK) : UUID                       │                 │ id (PK) : UUID                       │
│ tenant_id (FK) : UUID                │                 │ tenant_id (FK) : UUID                │
│ email : VARCHAR(255) [UNIQUE]        │                 │ ingredient_id (FK) : UUID ────────┐  │
│ full_name : VARCHAR(255)             │                 │ user_id (FK) : UUID               │  │
│ password_hash : VARCHAR(255)         │                 │ adjustment_mode : VARCHAR(20)     │  │
│ role : VARCHAR(50)                   │                 │ previous_stock : NUMERIC(12,4)    │  │
│ status : VARCHAR(50)                 │                 │ new_stock : NUMERIC(12,4)         │  │
│ must_change_password : BOOLEAN       │                 │ net_delta : NUMERIC(12,4)         │  │
│ token_version : INTEGER              │                 │ justification : TEXT              │  │
│ created_at : TIMESTAMPTZ             │                 │ created_at : TIMESTAMPTZ          │  │
│ updated_at : TIMESTAMPTZ             │                 └───────────────────────────────────┘  │
└──────────────────────────────────────┘                                                        │
                   │ 1                                                                          │
                   │                                                                            │
                   │ N                                                                          │
┌──────────────────▼───────────────────┐                                                        │
│             ingredients              │◄───────────────────────────────────────────────────────┘
├──────────────────────────────────────┤
│ id (PK) : UUID                       │
│ tenant_id (FK) : UUID                │
│ name : VARCHAR(255)                  │
│ category : VARCHAR(50)               │
│ unit_of_measure : VARCHAR(20)        │
│ unit_purchase_cost : NUMERIC(12,4)   │
│ status : VARCHAR(50)                 │
│ current_stock : NUMERIC(12,4)        │
│ created_at : TIMESTAMPTZ             │
│ updated_at : TIMESTAMPTZ             │
└──────────────────────────────────────┘
```

---

## 2. Definición Detallada de Entidades

### 2.1. Entidad `User` (Extensión para Personal de Establecimiento)

Representa a cualquier usuario que opera en la plataforma. Para el personal del restaurante (`ADMIN`, `CHEF`, `COUNTER_CLERK`), el campo `tenant_id` es estrictamente obligatorio.

**Tabla SQL**: `users`

| Columna | Tipo SQL | Modificadores | Descripción y Restricciones |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Identificador único del usuario. |
| `tenant_id` | `UUID` | NULLABLE (obligatorio para roles staff) | Identificador del establecimiento al que pertenece el empleado. |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Correo electrónico normalizado (minúsculas, sin espacios externos). |
| `full_name` | `VARCHAR(255)` | NOT NULL | Nombre y apellido completo del empleado. |
| `password_hash` | `VARCHAR(255)` | NOT NULL | Hash BCrypt (costo 12) de la contraseña. |
| `role` | `VARCHAR(50)` | NOT NULL | Rol asignado: `'ADMIN'`, `'CHEF'`, `'COUNTER_CLERK'`, `'DINER'`. |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'ACTIVE'` | Estado de la cuenta: `'PENDING_FIRST_LOGIN'`, `'ACTIVE'`, `'SUSPENDED'`. |
| `must_change_password` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Bandera que fuerza el cambio de contraseña en el primer inicio de sesión. |
| `token_version` | `INTEGER` | NOT NULL, DEFAULT `0` | Versión de credenciales para invalidación atómica de sesiones JWT. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Fecha y hora de creación de la cuenta. |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Fecha y hora de última modificación. |

**Restricciones e Índices**:
- `uq_users_email`: `UNIQUE (email)` (Garantiza unicidad global en minúsculas).
- `chk_users_role`: `CHECK (role IN ('ADMIN', 'CHEF', 'COUNTER_CLERK', 'DINER', 'ADMINISTRADOR', 'ENCARGADO', 'COMENSAL'))`.
- `chk_users_status`: `CHECK (status IN ('PENDING_FIRST_LOGIN', 'ACTIVE', 'SUSPENDED', 'UNVERIFIED'))`.
- `idx_users_tenant_role`: `CREATE INDEX idx_users_tenant_role ON users(tenant_id, role)` (Optimiza listado de personal y conteo de administradores).

**Transiciones de Estado de Usuario**:
```
[Alta de Staff por Admin]
           │
           ▼
 PENDING_FIRST_LOGIN  (must_change_password = true)
           │
           │ (Primer login exitoso + cambio de clave)
           ▼
        ACTIVE ────────► SUSPENDED (Inhabilitación administrativa)
           ▲                 │
           │                 │ (Reactivación)
           └─────────────────┘
```

---

### 2.2. Entidad `Ingredient` (Maestro de Insumos de Cocina)

Representa una materia prima en el catálogo de un establecimiento gastronómico. Los ingredientes pertenecen de forma exclusiva al `tenant_id` del restaurante que los registra.

**Tabla SQL**: `ingredients`

| Columna | Tipo SQL | Modificadores | Descripción y Restricciones |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Identificador único del ingrediente. |
| `tenant_id` | `UUID` | NOT NULL | Identificador del establecimiento propietario. |
| `name` | `VARCHAR(255)` | NOT NULL | Nombre del ingrediente (único dentro del tenant, insensible a mayúsculas). |
| `category` | `VARCHAR(50)` | NOT NULL | Familia o categoría: `'Produce'`, `'Meat'`, `'Dairy'`, `'Dry Goods'`, `'Spices'`. |
| `unit_of_measure` | `VARCHAR(20)` | NOT NULL | Unidad de medida aceptada: `'g'`, `'kg'`, `'ml'`, `'l'`, `'u'`. |
| `unit_purchase_cost` | `NUMERIC(12, 4)` | NOT NULL | Costo unitario de compra (exacto, `>= 0.0000`). |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'ACTIVE'` | Ciclo de vida: `'ACTIVE'`, `'DISCONTINUED'`, `'ARCHIVED'`. |
| `current_stock` | `NUMERIC(12, 4)` | NOT NULL, DEFAULT `0.0000` | Existencia física actual (invariante: `>= 0.0000`). |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Fecha y hora de alta del insumo. |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Fecha y hora de última actualización de ficha o stock. |

**Restricciones de Integridad e Índices**:
- `uq_ingredient_tenant_lower_name`: `CREATE UNIQUE INDEX uq_ingredient_tenant_lower_name ON ingredients (tenant_id, LOWER(name))` (Garantiza unicidad de nombre por local).
- `chk_ingredient_cost_positive`: `CHECK (unit_purchase_cost >= 0)`.
- `chk_ingredient_stock_non_negative`: `CHECK (current_stock >= 0)` (Enforzamiento a nivel de motor del invariante de no negatividad).
- `chk_ingredient_category`: `CHECK (category IN ('Produce', 'Meat', 'Dairy', 'Dry Goods', 'Spices'))`.
- `chk_ingredient_unit`: `CHECK (unit_of_measure IN ('g', 'kg', 'ml', 'l', 'u'))`.
- `chk_ingredient_status`: `CHECK (status IN ('ACTIVE', 'DISCONTINUED', 'ARCHIVED'))`.
- `idx_ingredient_chef_catalog`: `CREATE INDEX idx_ingredient_chef_catalog ON ingredients (tenant_id, status, name)` (Índice compuesto para consulta paginada del Chef O(log n)).

**Máquina de Estados del Ciclo de Vida del Ingrediente**:
```
          [Creación por Admin]
                    │
                    ▼
                 ACTIVE  (Visible para Chef y Admin; admite ajustes)
                 │    ▲
(Baja con stock) │    │ (Reactivación explícita por Admin)
                 ▼    │
              DISCONTINUED (Oculto para Chef; visible Admin; admite ajustes)
                 │
                 │ (Archivo definitivo; requiere stock == 0)
                 ▼
              ARCHIVED (Inmutable; excluido de vistas operativas)
```

**Reglas de Transición**:
1. `ACTIVE` → `DISCONTINUED`: Solo si `current_stock > 0`.
2. `ACTIVE` → `ARCHIVED`: Solo si `current_stock == 0`.
3. `DISCONTINUED` → `ACTIVE`: Permitido en cualquier momento por el Administrador.
4. `DISCONTINUED` → `ARCHIVED`: Permitido únicamente cuando `current_stock == 0`.
5. Cambio de `unit_of_measure`: Denegado si existen registros de stock o si `current_stock != 0` (FR-013).

---

### 2.3. Entidad `StockAuditEntry` (Pista de Auditoría Inmutable de Stock)

Representa cada modificación física de stock realizada sobre un ingrediente. Esta tabla es estrictamente de solo anexado (**append-only**).

**Tabla SQL**: `stock_audit_entries`

| Columna | Tipo SQL | Modificadores | Descripción y Restricciones |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Identificador único del evento de auditoría. |
| `tenant_id` | `UUID` | NOT NULL | Establecimiento donde ocurrió el ajuste. |
| `ingredient_id` | `UUID` | NOT NULL, FK → `ingredients(id)` | Ingrediente afectado. |
| `user_id` | `UUID` | NOT NULL, FK → `users(id)` | Administrador que ejecutó el ajuste. |
| `adjustment_mode` | `VARCHAR(20)` | NOT NULL | Modo: `'ABSOLUTE'` o `'RELATIVE'`. |
| `previous_stock` | `NUMERIC(12, 4)` | NOT NULL | Stock inmediatamente previo al ajuste (`>= 0`). |
| `new_stock` | `NUMERIC(12, 4)` | NOT NULL | Stock resultante tras la aplicación del ajuste (`>= 0`). |
| `net_delta` | `NUMERIC(12, 4)` | NOT NULL | Diferencia algebraica neta (`new_stock - previous_stock`, `<> 0`). |
| `justification` | `TEXT` | NOT NULL | Motivo obligatorio ingresado por el Administrador. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Timestamp de persistencia del ajuste. |

**Restricciones de Integridad e Índices**:
- `chk_audit_mode`: `CHECK (adjustment_mode IN ('ABSOLUTE', 'RELATIVE'))`.
- `chk_audit_previous_stock`: `CHECK (previous_stock >= 0)`.
- `chk_audit_new_stock`: `CHECK (new_stock >= 0)`.
- `chk_audit_nonzero_delta`: `CHECK (net_delta <> 0)` (Rechazo formal de ajustes sin efecto físico).
- `chk_audit_justification_nonempty`: `CHECK (LENGTH(TRIM(justification)) > 0)`.
- `idx_stock_audit_ingredient_history`: `CREATE INDEX idx_stock_audit_ingredient_history ON stock_audit_entries (tenant_id, ingredient_id, created_at DESC)`.

---

## 3. Scripts de Migración de Base de Datos (Flyway)

### `V3__extend_users_staff.sql`
```sql
-- Extensión de la tabla users para personal de establecimiento y primer acceso
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre',
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Actualización del constraint de roles para soportar nomenclatura formal en inglés y español
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role 
    CHECK (role IN ('ADMIN', 'CHEF', 'COUNTER_CLERK', 'DINER', 'ADMINISTRADOR', 'ENCARGADO', 'COMENSAL'));

-- Actualización del constraint de estados para soportar primer login pendiente
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE users ADD CONSTRAINT chk_users_status 
    CHECK (status IN ('PENDING_FIRST_LOGIN', 'ACTIVE', 'SUSPENDED', 'UNVERIFIED'));

CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON users(tenant_id, role);
```

### `V4__create_ingredients_and_stock_audit.sql`
```sql
-- Creación de la tabla de ingredientes
CREATE TABLE ingredients (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    name               VARCHAR(255)   NOT NULL,
    category           VARCHAR(50)    NOT NULL,
    unit_of_measure    VARCHAR(20)    NOT NULL,
    unit_purchase_cost NUMERIC(12, 4) NOT NULL,
    status             VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    current_stock      NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ingredient_cost_positive      CHECK (unit_purchase_cost >= 0),
    CONSTRAINT chk_ingredient_stock_non_negative CHECK (current_stock >= 0),
    CONSTRAINT chk_ingredient_category           CHECK (category IN ('Produce', 'Meat', 'Dairy', 'Dry Goods', 'Spices')),
    CONSTRAINT chk_ingredient_unit               CHECK (unit_of_measure IN ('g', 'kg', 'ml', 'l', 'u')),
    CONSTRAINT chk_ingredient_status             CHECK (status IN ('ACTIVE', 'DISCONTINUED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_ingredient_tenant_lower_name ON ingredients (tenant_id, LOWER(name));
CREATE INDEX idx_ingredient_chef_catalog ON ingredients (tenant_id, status, name);

-- Creación de la tabla de auditoría inmutable de stock
CREATE TABLE stock_audit_entries (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    ingredient_id   UUID           NOT NULL REFERENCES ingredients(id),
    user_id         UUID           NOT NULL REFERENCES users(id),
    adjustment_mode VARCHAR(20)    NOT NULL,
    previous_stock  NUMERIC(12, 4) NOT NULL,
    new_stock       NUMERIC(12, 4) NOT NULL,
    net_delta       NUMERIC(12, 4) NOT NULL,
    justification   TEXT           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_mode                  CHECK (adjustment_mode IN ('ABSOLUTE', 'RELATIVE')),
    CONSTRAINT chk_audit_previous_stock       CHECK (previous_stock >= 0),
    CONSTRAINT chk_audit_new_stock            CHECK (new_stock >= 0),
    CONSTRAINT chk_audit_nonzero_delta        CHECK (net_delta <> 0),
    CONSTRAINT chk_audit_justification_length CHECK (LENGTH(TRIM(justification)) > 0)
);

CREATE INDEX idx_stock_audit_ingredient_history ON stock_audit_entries (tenant_id, ingredient_id, created_at DESC);

-- Regla de inmutabilidad estricta: prevenir UPDATE o DELETE en stock_audit_entries
CREATE OR REPLACE RULE no_update_stock_audit AS ON UPDATE TO stock_audit_entries DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_stock_audit AS ON DELETE TO stock_audit_entries DO INSTEAD NOTHING;
```

---

## 4. Matriz de Trazabilidad Requisitos vs. Modelo de Datos

| Requisito Funcional | Entidad y Atributos | Mecanismo de Validación |
|---|---|---|
| **FR-001** (Alta de staff) | `users` (`full_name`, `email`, `role`, `tenant_id`) | Inserción en DTO `@Valid` + FK tenant |
| **FR-002** (Unicidad y normalización de correo) | `users.email` | `uq_users_email` + lowercase en `AuthService` |
| **FR-003** (Credencial temporal de un solo uso) | `users.password_hash`, `users.status` | BCrypt hash, status `PENDING_FIRST_LOGIN` |
| **FR-004** (Nómina de personal del local) | `users` filtrado por `tenant_id` | `idx_users_tenant_role` |
| **FR-005** (Edición de datos de empleado) | `users` (`full_name`, `email`, `role`) | `PUT` con validación de unicidad |
| **FR-006** (Prevención de admin huérfano) | `users` (`tenant_id`, `role`, `status`) | Query de conteo en `StaffService` antes de mutación |
| **FR-007**, **FR-008** (RBAC y Aislamiento multi-tenant) | `users.role`, `TenantContextHolder` | `@PreAuthorize` + inyección de `tenant_id` |
| **FR-009** (Log de auditoría en acceso denegado) | `SecurityAuditLogger` | Eventos SLF4J estructurados ante `AccessDeniedException` |
| **FR-010** (Creación de ingrediente con costo y unidad) | `ingredients` | `chk_ingredient_cost_positive`, `chk_ingredient_unit` |
| **FR-011** (Categorías fijas de ingredientes) | `ingredients.category` | `chk_ingredient_category` |
| **FR-012** (Edición de ficha de ingrediente) | `ingredients` (`name`, `category`, `unit_purchase_cost`) | Actualización transaccional |
| **FR-013** (Bloqueo de cambio de unidad con stock) | `ingredients.unit_of_measure` | Validación en `IngredientService` (`current_stock == 0`) |
| **FR-014** (Ciclo de vida y reactivación) | `ingredients.status` | Máquina de estados en `IngredientService` |
| **FR-015** (Modos de ajuste Absoluto y Relativo) | `stock_audit_entries.adjustment_mode` | `chk_audit_mode` |
| **FR-016** (Invariante de stock no negativo) | `ingredients.current_stock` | `chk_ingredient_stock_non_negative` + check en servicio |
| **FR-017** (Rechazo de delta cero) | `stock_audit_entries.net_delta` | `chk_audit_nonzero_delta` |
| **FR-018** (Justificación obligatoria) | `stock_audit_entries.justification` | `chk_audit_justification_length` |
| **FR-019** (Auditoría inmutable de stock) | `stock_audit_entries` | Tabla append-only + reglas PostgreSQL `no_update`/`no_delete` |
| **FR-020** (Aislamiento concurrente de stock) | `ingredients` | Bloqueo pesimista `SELECT ... FOR UPDATE` |
| **FR-021**, **FR-022** (Catálogo del Chef paginado) | `ingredients` (`status = 'ACTIVE'`) | `idx_ingredient_chef_catalog` (20 items, A-Z) |
| **FR-023** (Filtro por categoría y búsqueda textual) | `ingredients` (`category`, `LOWER(name)`) | Query parametrizada con `Pageable` |
| **FR-024** (Ocultamiento de costos al Chef) | Proyección `ChefIngredientResponseDto` | DTO específico sin campos `unitPurchaseCost` ni auditoría |
| **FR-025** (Precisión decimal contable exacta) | `NUMERIC(12, 4)` / `BigDecimal` | Tipos nativos sin redondeo binario de coma flotante |
