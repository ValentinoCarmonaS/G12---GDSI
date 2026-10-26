# Data Model and Persistence Schema (Phase 1)

**Feature**: Staff Management, RBAC, and Initial Ingredient Inventory  
**Branch**: `004-staff-mgmt-rbac-inventory`  
**Date**: 2026-10-20  
**Status**: Completed  

---

## 1. Entity-Relationship Diagram and Conceptual Structure

The data model extends the foundational authentication and multi-tenant schema by introducing two operational domains:
1. **Staff & Roles**: Restaurant employees associated with an establishment (`tenant_id`) with system roles and initial login controls.
2. **Inventory & Kitchen Master Catalog**: Tenant-specific ingredient catalog, lifecycle management, exact stock balances, and an immutable audit trail.

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
│ password_hash : VARCHAR(255)         │                 │ batch_id (FK, NULL) : UUID ───┐   │  │
│ role : VARCHAR(50)                   │                 │ adjustment_mode : VARCHAR(20) │   │  │
│ status : VARCHAR(50)                 │                 │ previous_stock : NUMERIC(12,4)│   │  │
│ must_change_password : BOOLEAN       │                 │ new_stock : NUMERIC(12,4)     │   │  │
│ token_version : INTEGER              │                 │ net_delta : NUMERIC(12,4)     │   │  │
│ created_at : TIMESTAMPTZ             │                 │ unit_cost : NUMERIC(12,4)     │   │  │
│ updated_at : TIMESTAMPTZ             │                 │ financial_loss : NUMERIC(12,4)│   │  │
└──────────────────────────────────────┘                 │ justification : TEXT          │   │  │
                   │ 1                                   │ created_at : TIMESTAMPTZ      │   │  │
                   │                                     └───────────────────────────────┘   │  │
                   │ N                                                                       │  │
┌──────────────────▼───────────────────┐       1       N ┌──────────────────────────────────────┐  │  │
│             ingredients              │◄────────────────┤          ingredient_batches          │  │  │
├──────────────────────────────────────┤                 ├──────────────────────────────────────┤  │  │
│ id (PK) : UUID                       │                 │ id (PK) : UUID                       │◄─┘  │
│ tenant_id (FK) : UUID                │                 │ tenant_id (FK) : UUID                │     │
│ name : VARCHAR(255)                  │                 │ ingredient_id (FK) : UUID            │     │
│ category : VARCHAR(50)               │                 │ initial_quantity : NUMERIC(12,4)     │     │
│ unit_of_measure : VARCHAR(20)        │                 │ remaining_quantity : NUMERIC(12,4)   │     │
│ unit_purchase_cost : NUMERIC(12,4)   │                 │ unit_cost : NUMERIC(12,4)            │     │
│ status : VARCHAR(50)                 │                 │ created_at : TIMESTAMPTZ             │     │
│ current_stock : NUMERIC(12,4)        │◄────────────────┴──────────────────────────────────────┘     │
│ created_at : TIMESTAMPTZ             │                                                              │
│ updated_at : TIMESTAMPTZ             │◄─────────────────────────────────────────────────────────────┘
└──────────────────────────────────────┘
```

---

## 2. Detailed Entity Definitions

### 2.1. Entity `User` (Staff Member Extension)

Represents any individual operating the platform. For restaurant staff (`ADMIN`, `CHEF`, `COUNTER_CLERK`), the `tenant_id` attribute is mandatory.

**Table**: `users`

| Column | SQL Type | Modifiers | Description & Constraints |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Unique user identifier. |
| `tenant_id` | `UUID` | NULLABLE (mandatory for staff roles) | Identifier of the establishment the staff member belongs to. |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Normalized email address (lowercase, trimmed). |
| `full_name` | `VARCHAR(255)` | NOT NULL | Staff member's full name. |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt hash (work factor 12) of the credential. |
| `role` | `VARCHAR(50)` | NOT NULL | Assigned role: `'ADMIN'`, `'CHEF'`, `'COUNTER_CLERK'`, `'DINER'`. |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'ACTIVE'` | Account status: `'PENDING_FIRST_LOGIN'`, `'ACTIVE'`, `'SUSPENDED'`. |
| `must_change_password` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Flag forcing a password change on the first login. |
| `token_version` | `INTEGER` | NOT NULL, DEFAULT `0` | Credential version for atomic JWT session invalidation. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Account creation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Timestamp of last profile or status update. |

**Constraints and Indexes**:
- `uq_users_email`: `UNIQUE (email)` (Enforces global case-insensitive email uniqueness).
- `chk_users_role`: `CHECK (role IN ('ADMIN', 'CHEF', 'COUNTER_CLERK', 'DINER', 'ADMINISTRADOR', 'ENCARGADO', 'COMENSAL'))`.
- `chk_users_status`: `CHECK (status IN ('PENDING_FIRST_LOGIN', 'ACTIVE', 'SUSPENDED', 'UNVERIFIED'))`.
- `idx_users_tenant_role`: `CREATE INDEX idx_users_tenant_role ON users(tenant_id, role)` (Optimizes staff roster queries and admin counts).

**User Account Status Transitions**:
```
[Staff Registration by Admin]
             │
             ▼
   PENDING_FIRST_LOGIN  (must_change_password = true)
             │
             │ (First login + mandatory password change)
             ▼
          ACTIVE ────────► SUSPENDED (Administrative suspension)
             ▲                 │
             │                 │ (Reinstatement)
             └─────────────────┘
```

---

### 2.2. Entity `Ingredient` (Kitchen Master Raw Material)

Represents a raw material in a restaurant's kitchen catalog. Ingredients belong strictly to the `tenant_id` of the registering restaurant.

**Table**: `ingredients`

| Column | SQL Type | Modifiers | Description & Constraints |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Unique ingredient identifier. |
| `tenant_id` | `UUID` | NOT NULL | Identifier of the owning establishment. |
| `name` | `VARCHAR(255)` | NOT NULL | Ingredient name (unique per tenant, case-insensitive). |
| `category` | `VARCHAR(50)` | NOT NULL | Category: `'Produce'`, `'Meat'`, `'Dairy'`, `'Dry Goods'`, `'Spices'`. |
| `unit_of_measure` | `VARCHAR(20)` | NOT NULL | Accepted unit of measure: `'g'`, `'kg'`, `'ml'`, `'l'`, `'u'`. |
| `unit_purchase_cost` | `NUMERIC(12, 4)` | NOT NULL | Unit purchase cost (exact decimal, `>= 0.0000`). |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'ACTIVE'` | Lifecycle: `'ACTIVE'`, `'DISCONTINUED'`. |
| `current_stock` | `NUMERIC(12, 4)` | NOT NULL, DEFAULT `0.0000` | Physical stock balance (invariant: `>= 0.0000`). |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Registration timestamp. |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Timestamp of last detail or stock modification. |

**Integrity Constraints and Indexes**:
- `uq_ingredient_tenant_lower_name`: `CREATE UNIQUE INDEX uq_ingredient_tenant_lower_name ON ingredients (tenant_id, LOWER(name))` (Enforces unique name per establishment).
- `chk_ingredient_cost_positive`: `CHECK (unit_purchase_cost >= 0)`.
- `chk_ingredient_stock_non_negative`: `CHECK (current_stock >= 0)` (Database-level enforcement of the non-negative stock invariant).
- `chk_ingredient_category`: `CHECK (category IN ('Produce', 'Meat', 'Dairy', 'Dry Goods', 'Spices'))`.
- `chk_ingredient_unit`: `CHECK (unit_of_measure IN ('g', 'kg', 'ml', 'l', 'u'))`.
- `chk_ingredient_status`: `CHECK (status IN ('ACTIVE', 'DISCONTINUED'))` (Simplified reversible lifecycle).
- `idx_ingredient_chef_catalog`: `CREATE INDEX idx_ingredient_chef_catalog ON ingredients (tenant_id, status, name)` (Composite index for Chef paginated queries O(log n)).

**Ingredient Lifecycle State Machine**:
```
            [Created by Admin]
                     │
                     ▼
                  ACTIVE  (Visible to Chef and Admin; accepts additions and subtractions)
                  │    ▲
(Discontinue)     │    │ (Explicit reactivation by Admin)
                  ▼    │
               DISCONTINUED (Hidden from Chef; visible to Admin; accepts subtractions only)
```

**Transition Rules**:
1. `ACTIVE` → `DISCONTINUED`: Allowed at any time by an Administrator (hides from Chef, blocks new stock additions).
2. `DISCONTINUED` → `ACTIVE`: Allowed at any time by an Administrator (restores to Chef, unblocks stock additions).
3. Unit of Measure Modification: Blocked if stock records exist or if `current_stock != 0` (FR-013).

---

### 2.3. Entity `IngredientBatch` (Stock Batches / Lotes de Compra)

Represents an individual physical shipment or lot of an ingredient, preserving its specific acquisition cost and tracking its remaining balance.

**Table**: `ingredient_batches`

| Column | SQL Type | Modifiers | Description & Constraints |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Unique batch identifier. |
| `tenant_id` | `UUID` | NOT NULL | Establishment owning the batch (multi-tenant boundary). |
| `ingredient_id` | `UUID` | NOT NULL, FK → `ingredients(id)` | Parent ingredient. |
| `initial_quantity` | `NUMERIC(12, 4)` | NOT NULL | Quantity received at batch creation (`> 0`). |
| `remaining_quantity` | `NUMERIC(12, 4)` | NOT NULL | Current remaining unconsumed balance (`>= 0`, `<= initial_quantity`). |
| `unit_cost` | `NUMERIC(12, 4)` | NOT NULL | Unit acquisition price for this specific shipment (`>= 0`). |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Timestamp when the shipment/lot was received and recorded. |

**Integrity Constraints and Indexes**:
- `chk_batch_initial_qty`: `CHECK (initial_quantity > 0)`.
- `chk_batch_remaining_qty`: `CHECK (remaining_quantity >= 0)`.
- `chk_batch_remaining_le_initial`: `CHECK (remaining_quantity <= initial_quantity)`.
- `chk_batch_unit_cost`: `CHECK (unit_cost >= 0)`.
- `idx_ingredient_batches_active`: `CREATE INDEX idx_ingredient_batches_active ON ingredient_batches (tenant_id, ingredient_id, remaining_quantity, created_at ASC)` (Optimized for active lot selection and FIFO recipe consumption).

---

### 2.4. Entity `StockAuditEntry` (Immutable Stock Audit Trail)

Represents every manual physical stock adjustment performed on an ingredient. This table is strictly **append-only**.

**Table**: `stock_audit_entries`

| Column | SQL Type | Modifiers | Description & Constraints |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Unique audit event identifier. |
| `tenant_id` | `UUID` | NOT NULL | Establishment where the adjustment occurred. |
| `ingredient_id` | `UUID` | NOT NULL, FK → `ingredients(id)` | Targeted ingredient. |
| `user_id` | `UUID` | NOT NULL, FK → `users(id)` | Administrator who executed the adjustment. |
| `batch_id` | `UUID` | NULL, FK → `ingredient_batches(id)` | Associated batch for lot creation or subtraction. |
| `adjustment_mode` | `VARCHAR(20)` | NOT NULL | Mode: `'ADD'` or `'SUBTRACT'` (or legacy `'RELATIVE'`). |
| `previous_stock` | `NUMERIC(12, 4)` | NOT NULL | Global stock immediately prior to the adjustment (`>= 0`). |
| `new_stock` | `NUMERIC(12, 4)` | NOT NULL | Resulting global stock after applying the adjustment (`>= 0`). |
| `net_delta` | `NUMERIC(12, 4)` | NOT NULL | Algebraic difference (`new_stock - previous_stock`, `<> 0`). |
| `unit_cost` | `NUMERIC(12, 4)` | NULL | Unit cost applied to the operation (`>= 0`). |
| `financial_loss` | `NUMERIC(12, 4)` | NULL | Exact financial loss for subtractions (`abs(net_delta) * unit_cost`). |
| `justification` | `TEXT` | NOT NULL | Mandatory justification note entered by the Administrator. |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Timestamp when the adjustment was committed. |

**Integrity Constraints and Indexes**:
- `chk_audit_mode`: `CHECK (adjustment_mode IN ('ADD', 'SUBTRACT', 'ABSOLUTE', 'RELATIVE'))`.
- `chk_audit_previous_stock`: `CHECK (previous_stock >= 0)`.
- `chk_audit_new_stock`: `CHECK (new_stock >= 0)`.
- `chk_audit_nonzero_delta`: `CHECK (net_delta <> 0)` (Rejects zero-delta non-operations).
- `chk_audit_financial_loss`: `CHECK (financial_loss IS NULL OR financial_loss >= 0)`.
- `chk_audit_justification_nonempty`: `CHECK (LENGTH(TRIM(justification)) > 0)`.
- `idx_stock_audit_ingredient_history`: `CREATE INDEX idx_stock_audit_ingredient_history ON stock_audit_entries (tenant_id, ingredient_id, created_at DESC)`.

---

## 3. Database Migration Scripts (Flyway)

### `V6__create_ingredient_batches_and_refactor_lifecycle.sql`
```sql
-- 1. Create ingredient_batches table
CREATE TABLE ingredient_batches (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    ingredient_id      UUID           NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    initial_quantity   NUMERIC(12, 4) NOT NULL,
    remaining_quantity NUMERIC(12, 4) NOT NULL,
    unit_cost          NUMERIC(12, 4) NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_batch_initial_qty           CHECK (initial_quantity > 0),
    CONSTRAINT chk_batch_remaining_qty         CHECK (remaining_quantity >= 0),
    CONSTRAINT chk_batch_remaining_le_initial CHECK (remaining_quantity <= initial_quantity),
    CONSTRAINT chk_batch_unit_cost             CHECK (unit_cost >= 0)
);

CREATE INDEX idx_ingredient_batches_active 
    ON ingredient_batches (tenant_id, ingredient_id, remaining_quantity, created_at ASC);

-- 2. Extend stock_audit_entries with batch and financial tracking
ALTER TABLE stock_audit_entries
    ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES ingredient_batches(id),
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS financial_loss NUMERIC(12, 4);

-- Update audit mode check constraint
ALTER TABLE stock_audit_entries DROP CONSTRAINT IF EXISTS chk_audit_mode;
ALTER TABLE stock_audit_entries ADD CONSTRAINT chk_audit_mode 
    CHECK (adjustment_mode IN ('ADD', 'SUBTRACT', 'ABSOLUTE', 'RELATIVE'));

-- 3. Refactor ingredients lifecycle constraint (remove ARCHIVED)
ALTER TABLE ingredients DROP CONSTRAINT IF EXISTS chk_ingredient_status;
ALTER TABLE ingredients ADD CONSTRAINT chk_ingredient_status 
    CHECK (status IN ('ACTIVE', 'DISCONTINUED'));
```

### `V3__extend_users_staff.sql`
```sql
-- Extend users table for staff membership and first login password change
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255) NOT NULL DEFAULT 'Unnamed User',
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Update role constraint to support standard English and Spanish role constants
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role 
    CHECK (role IN ('ADMIN', 'CHEF', 'COUNTER_CLERK', 'DINER', 'ADMINISTRADOR', 'ENCARGADO', 'COMENSAL'));

-- Update status constraint to support pending first login status
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE users ADD CONSTRAINT chk_users_status 
    CHECK (status IN ('PENDING_FIRST_LOGIN', 'ACTIVE', 'SUSPENDED', 'UNVERIFIED'));

CREATE INDEX IF NOT EXISTS idx_users_tenant_role ON users(tenant_id, role);
```

### `V4__create_ingredients_and_stock_audit.sql`
```sql
-- Create ingredients master table
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

-- Create immutable stock adjustments audit table
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

-- Strict immutability rules: prevent UPDATE and DELETE on stock_audit_entries
CREATE OR REPLACE RULE no_update_stock_audit AS ON UPDATE TO stock_audit_entries DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_stock_audit AS ON DELETE TO stock_audit_entries DO INSTEAD NOTHING;
```

---

## 4. Requirement Traceability Matrix

| Functional Requirement | Entity & Attributes | Enforcement Mechanism |
|---|---|---|
| **FR-001** (Staff registration) | `users` (`full_name`, `email`, `role`, `tenant_id`) | Validated DTO + tenant FK |
| **FR-002** (Email uniqueness & normalization) | `users.email` | `uq_users_email` + lowercase normalization in service |
| **FR-003** (Single-use temporary credential) | `users.password_hash`, `users.status` | BCrypt hash, `PENDING_FIRST_LOGIN` status |
| **FR-004** (Staff roster view) | `users` filtered by `tenant_id` | `idx_users_tenant_role` |
| **FR-005** (Staff member editing) | `users` (`full_name`, `email`, `role`) | `PUT` endpoint with uniqueness check |
| **FR-006** (Orphaned admin prevention) | `users` (`tenant_id`, `role`, `status`) | Admin count query in `StaffService` before demotion |
| **FR-007**, **FR-008** (RBAC & Multi-tenant isolation) | `users.role`, `TenantContextHolder` | `@PreAuthorize` + tenant query parameter |
| **FR-009** (Audit logging on access denied) | `SecurityAuditLogger` | Structured SLF4J log entries on `AccessDeniedException` |
| **FR-010** (Ingredient creation with cost & unit) | `ingredients` | `chk_ingredient_cost_positive`, `chk_ingredient_unit` |
| **FR-011** (Fixed ingredient categories) | `ingredients.category` | `chk_ingredient_category` |
| **FR-012** (Ingredient detail editing) | `ingredients` (`name`, `category`, `unit_purchase_cost`) | Transactional update |
| **FR-013** (Unit of measure change restriction) | `ingredients.unit_of_measure` | Service check: blocked if `current_stock != 0` |
| **FR-014** (Ingredient lifecycle & reactivation) | `ingredients.status` | State machine in `IngredientService` |
| **FR-015** (Absolute & Relative adjustment modes) | `stock_audit_entries.adjustment_mode` | `chk_audit_mode` |
| **FR-016** (Non-negative stock invariant) | `ingredients.current_stock` | `chk_ingredient_stock_non_negative` + service calculation |
| **FR-017** (Zero-delta rejection) | `stock_audit_entries.net_delta` | `chk_audit_nonzero_delta` |
| **FR-018** (Mandatory justification) | `stock_audit_entries.justification` | `chk_audit_justification_length` |
| **FR-019** (Immutable stock audit trail) | `stock_audit_entries` | Append-only table + PostgreSQL `no_update`/`no_delete` rules |
| **FR-020** (Concurrent stock adjustment isolation) | `ingredients` | Pessimistic locking (`SELECT ... FOR UPDATE`) |
| **FR-021**, **FR-022** (Chef paginated catalog) | `ingredients` (`status = 'ACTIVE'`) | `idx_ingredient_chef_catalog` (20 items, A-Z) |
| **FR-023** (Category filter and text search) | `ingredients` (`category`, `LOWER(name)`) | Parameterized JPA query with `Pageable` |
| **FR-024** (Cost hiding from Chef) | Projection `ChefIngredientResponseDto` | Dedicated DTO omitting purchase cost and audit trail |
| **FR-025** (Exact decimal accounting precision) | `NUMERIC(12, 4)` / `BigDecimal` | Exact native numeric types avoiding binary float errors |
| **FR-026** (Client-side real-time validation) | Form state in `StaffFormModal`, `IngredientFormModal` | Real-time field validation schemas before submit |
| **FR-027** (Live delta & projected calculation) | Modal state in `StockAdjustmentModal` | Dynamic delta calculation and zero/negative blocking |
| **FR-028** (Admin staff roster view) | React View `StaffManagementPage` (`/admin/staff`) | Protected route layout with `StaffRosterTable` |
| **FR-029** (Single-use temporary credential modal)| React Component `TemporaryCredentialsModal` | One-time modal with clipboard copy button |
| **FR-030** (Mandatory first-login password change)| React View `FirstLoginPasswordPage` (`/first-login-password-change`) | Guard interception on `must_change_password = true` |
| **FR-031** (Admin ingredient inventory view) | React View `InventoryManagementPage` (`/admin/inventory`) | Protected route layout with `IngredientCatalogTable` |
| **FR-032** (Chef read-only ingredient catalog) | React View `ChefIngredientsPage` (`/cocina/ingredientes`) | Paginated read-only table with cost suppression |
