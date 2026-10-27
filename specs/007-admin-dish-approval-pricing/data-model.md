# Data Model & Persistence: Administrative Back-Office for Dish Audit, Retail Pricing (PVP) and Catalog Approval

**Feature**: `007-admin-dish-approval-pricing`
**Date**: 2026-10-26
**Specification**: [spec.md](./spec.md) · **Decisions**: [research.md](./research.md) · **Contracts**: [contracts/openapi.yaml](./contracts/openapi.yaml)

Scope of ownership: this feature owns `dishes.retail_price`, `dishes.version`, `dishes.published_at`, the `dish_review_history` and `dish_price_history` tables, and the indexes serving the inbox and history queries. It owns **no** object of spec 005's `V6` migration. Everything it needs from 005 or 006 and does not own is an [Upstream Requirement](./plan.md#upstream-requirements).

---

## 1. Entities

### 1.1. `Dish` — extended, not redefined (`com.easyeat.dish.domain.Dish`, spec 005)

Columns added by **this** feature:

| Attribute | Type | Nullable | Rules |
|---|---|---|---|
| `retail_price` | `NUMERIC(10,2)` | Yes | The PVP. `NULL` means "not yet priced". When present MUST be `> 0` (FR-013). Written only by the pricing path; scale 2, `HALF_UP`. |
| `version` | `BIGINT` | No | Optimistic-locking token, `DEFAULT 0`, incremented by Hibernate `@Version`. First use of optimistic locking in the project (FR-025). |
| `published_at` | `TIMESTAMPTZ` | Yes | Set on the `PENDING_PRICE → PUBLISHED` transition. `NULL` for every dish never approved. Never cleared by this feature (unpublishing is Sprint 3). |

Columns **consumed** from upstream (never created here): `status`, `category`, `allergens_confirmed`, `created_by` (005's `V6`); `submitted_at` (**R-005-1**); `name`, `description`, `tenant_id`, `is_active`, `created_at`, `updated_at`, `price` (V5, spec 003).

Fields added to the entity class (ordinary incremental work on a class, permitted by D1): `retailPrice`, `version` (`@Version`), `publishedAt`.

### 1.2. `catalog.domain.Dish` — demoted to read-only (D3)

The second JPA mapping over `dishes` (spec 003) is annotated `@org.hibernate.annotations.Immutable` and keeps serving diner reads. Verified today: no write path exists through it — `DishRepository` declares only `findByTenantIdAndIsActiveTrue` and `findByIdIn`, and `CatalogComplianceService` only reads. The annotation is what **structurally** prevents a future write from bypassing `@Version`: Hibernate ignores dirty state on an `@Immutable` entity instead of silently issuing an `UPDATE` without the version predicate. `com.easyeat.dish.domain.Dish` is the single writable owner of the table.

### 1.3. `DishReviewHistory` (`dish_review_history`) — immutable administrative decisions

| Attribute | Type | Nullable | Rules |
|---|---|---|---|
| `id` | `UUID` | No | PK, `gen_random_uuid()`. |
| `dish_id` | `UUID` | No | FK → `dishes(id)` `ON DELETE CASCADE` (§4.1). |
| `tenant_id` | `UUID` | No | Denormalised from the dish so every audit query is tenant-filtered without a join and a cross-tenant read is impossible even if a query forgets the join. |
| `cycle_number` | `INTEGER` | No | Monotonic from 1 per dish. `CHECK (cycle_number >= 1)`, `UNIQUE (dish_id, cycle_number)` (§4.2). |
| `event_type` | `VARCHAR(20)` | No | `CHECK (event_type IN ('APPROVED','REJECTED'))` (§4.3). |
| `admin_id` | `UUID` | No | FK → `users(id)` `ON DELETE RESTRICT` — an audit row never loses its actor. |
| `comment` | `TEXT` | Yes | Mandatory for `REJECTED`, 10–1000 characters after trimming, never whitespace-only (FR-021). Optional for `APPROVED`. Enforced in the domain and by a table `CHECK`. Stored as literal text; any HTML/script markup is never interpreted (the frontend renders it as text, React escapes by default). |
| `created_at` | `TIMESTAMPTZ` | No | `DEFAULT NOW()`. Sole temporal field: a row that cannot change has no `updated_at`. |

### 1.4. `DishPriceHistory` (`dish_price_history`) — immutable PVP audit trail

| Attribute | Type | Nullable | Rules |
|---|---|---|---|
| `id` | `UUID` | No | PK, `gen_random_uuid()`. |
| `dish_id` | `UUID` | No | FK → `dishes(id)` `ON DELETE CASCADE`. |
| `tenant_id` | `UUID` | No | Denormalised, as above. |
| `admin_id` | `UUID` | No | FK → `users(id)` `ON DELETE RESTRICT`. |
| `previous_price` | `NUMERIC(10,2)` | Yes | `NULL` on the first pricing of a dish (FR-010). |
| `new_price` | `NUMERIC(10,2)` | No | `CHECK (new_price > 0)`. |
| `food_cost_snapshot` | `NUMERIC(10,2)` | No | The prevailing Food Cost at the instant of the change, obtained from spec 006 inside the same transaction (§5.4). `CHECK (food_cost_snapshot >= 0)` — 0.00 is legitimate for a fully unpriced recipe. |
| `created_at` | `TIMESTAMPTZ` | No | `DEFAULT NOW()`. |

### 1.5. Relationships

```mermaid
erDiagram
    dishes ||--o{ dish_review_history : "reviewed in cycles (0..*)"
    dishes ||--o{ dish_price_history  : "repriced (0..*)"
    users  ||--o{ dish_review_history : "decided by administrator"
    users  ||--o{ dish_price_history  : "priced by administrator"
    users  ||--o{ dishes              : "authored by chef (created_by, spec 005)"
    dishes ||--o{ dish_recipe_ingredients : "recipe (specs 005/006)"

    dishes {
        uuid id PK
        uuid tenant_id
        varchar_50 status "DRAFT, PENDING_PRICE, PUBLISHED"
        numeric_10_2 retail_price "OWNED BY 007, nullable, > 0"
        bigint version "OWNED BY 007"
        timestamptz published_at "OWNED BY 007"
        timestamptz submitted_at "R-005-1, read-only here"
        numeric_10_2 price "LEGACY (spec 003), never written after 007"
        boolean is_active
        boolean allergens_confirmed
        uuid created_by FK
    }
    dish_review_history {
        uuid id PK
        uuid dish_id FK
        uuid tenant_id
        int cycle_number
        varchar_20 event_type "APPROVED, REJECTED"
        uuid admin_id FK
        text comment
        timestamptz created_at
    }
    dish_price_history {
        uuid id PK
        uuid dish_id FK
        uuid tenant_id
        uuid admin_id FK
        numeric_10_2 previous_price
        numeric_10_2 new_price
        numeric_10_2 food_cost_snapshot
        timestamptz created_at
    }
```

---

## 2. Extended state machine (`DishStatus`)

`DRAFT` and `PENDING_PRICE` and their transitions belong to spec 005. This feature adds `PUBLISHED` and the three transitions out of `PENDING_PRICE`.

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Chef creates technical file (005)
    DRAFT --> PENDING_PRICE : Chef submits for review (005) / writes submitted_at (R-005-1)
    PENDING_PRICE --> PUBLISHED : Administrator approves (007) [publication gate + acknowledgements]
    PENDING_PRICE --> DRAFT : Administrator rejects (007) [comment 10..1000 chars]
    PENDING_PRICE --> PENDING_PRICE : Administrator saves retail price (007) [status unchanged]
    PUBLISHED --> PUBLISHED : Administrator reprices (007) [status, recipe, visibility unchanged]
```

| # | Transition | Trigger | Guard (evaluated server-side, inside the transaction) | Effects | Loser / failure |
|---|---|---|---|---|---|
| T1 | `PENDING_PRICE → PUBLISHED` | `POST /admin/dishes/{id}/approval` | `status = PENDING_PRICE`; **publication gate** (FR-015): `retail_price > 0` **and** ≥ 1 recipe line whose ingredient is `ACTIVE` **and** `allergens_confirmed = TRUE`; required acknowledgements present (FR-017) | `status = PUBLISHED`, `published_at = NOW()`, `is_active = TRUE` (§3.4), `version++`, one `dish_review_history` row (`APPROVED`, `cycle_number = MAX+1`) | gate unmet → **422** `publication-gate-failed` with `unmetConditions[]`; missing acknowledgement → **422** `acknowledgement-required`; `status = DRAFT` → **409** `dish-invalid-state` (FR-019); `status = PUBLISHED` → **200 idempotent no-op** (FR-018) |
| T2 | `PENDING_PRICE → DRAFT` | `POST /admin/dishes/{id}/rejection` | `status = PENDING_PRICE`; comment valid | `status = DRAFT`, `version++`, one `dish_review_history` row (`REJECTED`, `cycle_number = MAX+1`, comment). `published_at` untouched; recipe, labels and `retail_price` untouched | invalid comment → **400** `invalid-rejection-comment` (dish stays `PENDING_PRICE`); `status = PUBLISHED` → **409** `dish-invalid-state` (FR-024); `status = DRAFT` → **409** `dish-invalid-state` (FR-019) |
| T3 | pricing, status-preserving | `PUT /admin/dishes/{id}/retail-price` | `status ∈ {PENDING_PRICE, PUBLISHED}` (FR-009); `expectedVersion` matches; price valid; negative margin acknowledged (FR-014) | `retail_price` set, `version++`, one `dish_price_history` row. Status, recipe, `is_active` and `published_at` unchanged (FR-011) | `status = DRAFT` → **409** `dish-invalid-state`; stale `expectedVersion` → **409** `dish-stale-version`; invalid price → **400** `invalid-retail-price`; unacknowledged negative margin → **422** `acknowledgement-required` |

**Idempotent approval (FR-018)**: when `status` is already `PUBLISHED`, the transaction writes nothing — no status change, no `version` bump, no audit row — and returns `200 OK` with `alreadyPublished: true` plus the original `publishedAt`, so the UI reports "ya estaba publicado" instead of a fresh publication.

---

## 3. Flyway migration owned by this feature

**File**: `easyeat/backend/src/main/resources/db/migration/V8__admin_dish_approval_pricing.sql`
**Version rationale and renumbering rule**: see [research.md §2(b)](./research.md) — `V6` is reserved by spec 005, `V7` by spec 008.
**Applies on top of**: `V1..V5` (present) + 005's `V6` + 008's `V7` when they land.

```sql
-- ====================================================================
-- EasyEat V8: Administrative Back-Office - PVP, Approval and Audit
-- Owns: dishes.retail_price / version / published_at, dish_review_history,
--       dish_price_history, inbox indexes, retail_price backfill.
-- Owns NOTHING of V6 (spec 005) or V7 (spec 008).
-- ====================================================================

-- 1. Columns owned by this feature
ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS retail_price NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS version      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

ALTER TABLE dishes
    ADD CONSTRAINT chk_dishes_retail_price_positive
        CHECK (retail_price IS NULL OR retail_price > 0);

COMMENT ON COLUMN dishes.price IS
    'DEPRECATED by spec 007: superseded by retail_price. Retained for backward '
    'compatibility with spec 003 artifacts; never written by application code '
    'after 007. Removal owned by the Sprint 3 catalog-lifecycle feature (CHF-2.2.5).';

-- 2. Backfill the PVP from the legacy price (D2).
--    price may be NULL (005 drops NOT NULL) and may be 0.00 (V5 allows >= 0);
--    neither can become a retail_price, so those rows stay unpriced.
UPDATE dishes
   SET retail_price = price
 WHERE retail_price IS NULL
   AND price IS NOT NULL
   AND price > 0;

-- 3. Status domain extension -- see research.md 3.2.
--    PRIMARY PATH (R-005-2): spec 005 widens its own chk_dishes_status to include
--    'PUBLISHED' and this block ships empty.
--    FALLBACK (requires human approval, registered in Complexity Tracking):
-- ALTER TABLE dishes DROP CONSTRAINT IF EXISTS chk_dishes_status;
-- ALTER TABLE dishes ADD  CONSTRAINT chk_dishes_status
--     CHECK (status IN ('DRAFT', 'PENDING_PRICE', 'PUBLISHED'));

-- 4. Immutable administrative review history
CREATE TABLE IF NOT EXISTS dish_review_history (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dish_id      UUID         NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    tenant_id    UUID         NOT NULL,
    cycle_number INTEGER      NOT NULL,
    event_type   VARCHAR(20)  NOT NULL,
    admin_id     UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_drh_dish_cycle UNIQUE (dish_id, cycle_number),
    CONSTRAINT chk_drh_cycle_positive CHECK (cycle_number >= 1),
    CONSTRAINT chk_drh_event_type CHECK (event_type IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_drh_rejection_comment CHECK (
        event_type <> 'REJECTED'
        OR (comment IS NOT NULL AND LENGTH(BTRIM(comment)) BETWEEN 10 AND 1000)
    )
);

CREATE INDEX IF NOT EXISTS idx_drh_dish_created
    ON dish_review_history (dish_id, created_at);
CREATE INDEX IF NOT EXISTS idx_drh_tenant_created
    ON dish_review_history (tenant_id, created_at DESC);

-- 5. Immutable retail price history
CREATE TABLE IF NOT EXISTS dish_price_history (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dish_id            UUID         NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    tenant_id          UUID         NOT NULL,
    admin_id           UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_price     NUMERIC(10, 2),
    new_price          NUMERIC(10, 2) NOT NULL,
    food_cost_snapshot NUMERIC(10, 2) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dph_new_price_positive CHECK (new_price > 0),
    CONSTRAINT chk_dph_food_cost_non_negative CHECK (food_cost_snapshot >= 0)
);

CREATE INDEX IF NOT EXISTS idx_dph_dish_created
    ON dish_price_history (dish_id, created_at DESC);

-- 6. Indexes for the segmented inbox.
--    FIFO ordering column depends on R-005-1: submitted_at when provided,
--    created_at under the degraded fallback (research.md 2(c)).
CREATE INDEX IF NOT EXISTS idx_dishes_review_inbox_pending
    ON dishes (tenant_id, status, submitted_at);
CREATE INDEX IF NOT EXISTS idx_dishes_review_inbox_published
    ON dishes (tenant_id, status, published_at DESC);
```

### 3.1. Index justification

`idx_dishes_tenant (tenant_id, is_active)` (V5) and `idx_dishes_chef_list (tenant_id, status, updated_at DESC)` (005's V6) serve neither the FIFO pending query nor the published tab's chronology. Two composite indexes are added, one per ordering, because a single index cannot serve both sort directions on different columns. The history tab needs no new index: it is an `EXISTS` over `dish_review_history` resolved by `uq_drh_dish_cycle`.

### 3.2. `cycle_number` without a race

The value is computed as `COALESCE(MAX(cycle_number), 0) + 1` for the dish **inside the same transaction** that performs the status transition. Two concurrent decisions cannot both commit, because each one also updates the `dishes` row with the `@Version` predicate, so the second `UPDATE` matches zero rows and its transaction is rolled back before its audit row can commit. `UNIQUE (dish_id, cycle_number)` is therefore the **proof** of the invariant rather than its mechanism: if it is ever violated, a write path exists that bypasses the dish-row version guard, which is exactly the defect we want to fail loudly.

### 3.3. How immutability is actually enforced

Append-only service paths: `DishReviewHistoryRepository` and `DishPriceHistoryRepository` expose only `save` and query methods — no `delete*`, no `@Modifying` update. The entities expose no setters after construction and declare no `@PreUpdate`. Neither table has an `updated_at` column, so an update has nothing to say. **A database trigger or a `REVOKE UPDATE` is deliberately *not* added**: a single application writes this schema with one role, and a rule enforced in two places that can disagree is worse than one enforced where the writes happen (Principle IV). This is recorded as the chosen trade-off, not an oversight.

### 3.4. `is_active` at publication

The approval transition sets `is_active = TRUE` in the same atomic update, because 005 defaults new drafts to `FALSE` and D4's diner predicate requires both flags. See [research.md §3.9](./research.md). No other path in this feature writes `is_active`; Sprint 3's pause flips it without touching `status`.

---

## 4. Deletion, typing and author semantics

### 4.1. `ON DELETE CASCADE` on `dish_id`, and its cost

Spec 005 permits physical deletion of a dish only while in `DRAFT`. A chef may therefore delete a draft that was previously rejected, and the cascade erases that dish's review history with it. This is chosen deliberately: orphaned audit rows would describe an artifact that no longer exists and would surface a deleted dish's identity in administrative views. **Consequence stated plainly**: SC-005's "100% reconstructible" holds for every dish that exists; it does not survive the author deleting the dish. If the business needs audit immutability across deletion, 005 must move to soft deletion — recorded as a note to 005, not as a blocking upstream requirement for this feature.

### 4.2. Event typing, and why price changes are not review events

`dish_review_history.event_type ∈ {APPROVED, REJECTED}` — administrative *decisions* only. Price changes live exclusively in `dish_price_history`. Mixing them would make `cycle_number` mean two different things (a review round and a price revision) and would make the review-cycle chronology unreadable for the Chef-facing feedback view. The two tables are joined only at the presentation layer, in the administrative history view.

### 4.3. FR-026 — a deactivated author must not invalidate a dish

Deactivation sets `users.status = 'SUSPENDED'`; it never deletes the row (verified: `V3__extend_users_staff.sql:13`, `StaffService:170-177`). Consequences: `dishes.created_by` (FK to `users`, 005) never breaks; **no query in this feature filters, joins or orders by the author's status**; the inbox and detail views render the author's name with a neutral *"cuenta desactivada"* chip so the Administrator knows who to chase, and every action — audit, price, approve, reject — stays enabled. `admin_id` on both audit tables is `ON DELETE RESTRICT` so a decision keeps its attribution permanently. When `created_by` is `NULL` (possible for pre-005 rows and for the dev seed, see §5.3) the UI renders *"Autor no registrado"*.

---

## 5. Money, precision and the margin

### 5.1. Conventions

`java.math.BigDecimal` everywhere, `RoundingMode.HALF_UP`; scale **2** for monetary values (`retail_price`, `food_cost`, line subtotals, both history tables), scale **4** for recipe quantities and unit acquisition costs (spec 006's convention, already implemented in `Ingredient`/`StockAuditEntry`). No binary floating point touches a monetary or margin invariant on the server. DTOs expose bare `BigDecimal` with no Jackson annotation, matching the inventory and catalog DTOs; Jackson serialises them as JSON numbers preserving scale.

### 5.2. The two derivations, and which one is lossy

$$\text{margin\_pct} = \frac{\text{retail\_price} - \text{food\_cost}}{\text{retail\_price}} \times 100 \qquad \text{retail\_price} = \frac{\text{food\_cost}}{1 - \text{margin\_pct}/100}$$

- **price → margin is exact**: both inputs are already 2-decimal values; `margin_pct` is computed and returned at **scale 4** (`HALF_UP`) and formatted to 2 decimals for display.
- **margin → price is lossy**: the quotient is rounded to scale 2, perturbing any margin re-derived from the stored price.

### 5.3. The invariants this feature actually enforces (replacing FR-008's unconditional claim)

- **INV-1, unconditional**: `retail_price = HALF_UP₂(food_cost / (1 − margin_pct/100))`.
- **INV-2, conditional**: `|m' − m| ≤ 0.5·food_cost / (p'·(p' − 0.005))` pp, which is `≤ 0.01` pp **iff** `retail_price ≥ 50·(1 − margin_pct/100)` (equivalently `retail_price ≥ √(50·food_cost)`). Derivation, verified counter-examples (`3.33 @ 60% → 8.33 → 60.0240%`, `1.25 @ 70% → 4.17 → 70.0240%`, `0.07 @ 60% → 0.18 → 61.1111%`) and the per-margin thresholds are in [research.md §3.1](./research.md). FR-008 and SC-002 are flagged for amendment; **±0.01 pp is not an unconditional guarantee**.

### 5.4. Boundaries, and why the Food Cost snapshot does not contradict FR-012

`margin_pct ≥ 100.00` → derivation blocked (division by zero / negative price). `margin_pct ≤ −500.00` → blocked. `margin_pct = 0` → `retail_price = food_cost`. Negative margin → permitted behind explicit acknowledgement (FR-014). `food_cost = 0.00` with `has_unpriced_ingredients` → margin-to-price derivation disabled, direct price entry required (FR-013).

FR-012 forbids **persisting the margin** or caching it: the displayed margin is always recomputed from the live Food Cost on every consultation. `dish_price_history.food_cost_snapshot` is a different thing — an audit fact answering *"what did raw materials cost when this price was decided?"*, obtained from spec 006 inside the pricing transaction and **never read back to compute a displayed margin**. No margin value is stored anywhere in the schema.

### 5.5. Price validation ownership

More-than-two-decimals is detected as `price.stripTrailingZeros().scale() > 2` on the parsed `BigDecimal` — never by string inspection. Sign, zero and precision are validated in the domain layer, which throws `InvalidRetailPriceException` → **400** with a field-bound `errors[]` extension (see [research.md §3.4](./research.md) for why not the project's default 422).

---

## 6. Dev seed and its test (D5)

`R__seed_dev_dishes_and_dietary.sql` is extended so its five dishes land `status = 'PUBLISHED'`, `is_active = TRUE`, `allergens_confirmed = TRUE`, `category` set per dish, `retail_price` = the existing `price` value, `version = 0`, `published_at = NOW()`, `created_by = NULL`, plus one synthetic `dish_review_history` row per dish (`cycle_number = 1`, `APPROVED`, `admin_id = b0eebc99-…-a22`, comment prefixed `[SEED SINTÉTICO]`). Being a repeatable migration it runs after all versioned ones, so it may reference both 005's and this feature's columns; it therefore becomes dependent on `V6` and `V8` together.

**Effect on `DevSeedDishesMigrationTest`**: its existing assertions (≥ 5 dishes for the tenant, dietary mappings present) keep passing unchanged. It is extended to assert the new seeded invariants — all five rows `PUBLISHED` with `retail_price = price` and `retail_price > 0`, one `APPROVED` review row each — so that SC-001 holds in the dev database with **no exception**, and spec 003's catalog tests (`CatalogControllerTest`, `CatalogComplianceServiceTest`, `CatalogComplianceIntegrationTest`) continue to see exactly the same five visible dishes under D4's stricter predicate.

---

## 7. Traceability: FR → artifact

| FR | Satisfied by |
|---|---|
| FR-001 | `/api/v1/admin/**` filter-chain gate (`SecurityConfig:78`) + `@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")`; `TenantContextHolder.getRequiredTenantId()` on every query; `TenantViolationException` → 403 |
| FR-002 | `GET /admin/dishes/review-inbox?tab=PENDING\|PUBLISHED\|HISTORY` |
| FR-003 | `pendingCount` in the page payload; `ORDER BY submitted_at ASC` (R-005-1) with `idx_dishes_review_inbox_pending`; `@PageableDefault(size = 20)` |
| FR-004 | `DishReviewInboxItemResponse` (id, name, category, authorName, authorDeactivated, submittedAt, status, isResubmission, reviewCycleCount) |
| FR-005 | Empty `items[]` with accurate metadata; per-tab empty states in the UI |
| FR-006 | `GET /admin/dishes/{id}/review-detail` embedding spec 006's `FoodCostAuditResponse` (R-006-1) |
| FR-007 | `RetailPricingCalculator` (pure component) + mirrored formulas in the UI; server authoritative |
| FR-008 | §5.1–5.3 — INV-1 unconditional, INV-2 conditional; **amendment requested** |
| FR-009 | `dishes.retail_price`; `PUT /admin/dishes/{id}/retail-price` guard `status ∈ {PENDING_PRICE, PUBLISHED}` |
| FR-010 | `dish_price_history` row per change (actor, timestamp, previous, new, `food_cost_snapshot`) |
| FR-011 | T3: status, recipe, `is_active`, `published_at` untouched |
| FR-012 | No margin column anywhere; margin recomputed per consultation from 006's live cost; §5.4 |
| FR-013 | `chk_dishes_retail_price_positive` + domain validation → 400 (§5.5) |
| FR-014 | `acknowledgeNegativeMargin` in the pricing request; absent → 422 `acknowledgement-required` |
| FR-015 | T1 publication gate, re-evaluated at approval time, incl. recipes emptied or discontinued after submission |
| FR-016 | T1 single atomic transaction: `status`, `published_at`, `is_active`, `version`, audit row |
| FR-017 | `acknowledgeUnpricedIngredients` / `acknowledgeNegativeMargin` in the approval request |
| FR-018 | T1 idempotent branch: 200 OK, `alreadyPublished: true`, no audit row, no `version` bump |
| FR-019 | T1/T2 guard on `DRAFT` → 409 `dish-invalid-state` |
| FR-020 | T2: `status = DRAFT`, dish leaves the pending tab, 005's edit lock releases |
| FR-021 | `chk_drh_rejection_comment` + domain validation (trim, 10..1000) → 400 |
| FR-022 | `dish_review_history` (dish, actor, timestamp, reason, `cycle_number`), append-only (§3.3) |
| FR-023 | `GET /dishes/{id}/review-feedback` (CHEF) — response type carries **no** financial field at all (structural omission, §contracts) |
| FR-024 | T2 guard on `PUBLISHED` → 409 `dish-invalid-state` |
| FR-025 | `dishes.version` `@Version` + per-operation status guards + `expectedVersion` on repricing; 409 taxonomy |
| FR-026 | §4.3 — no author-status filter anywhere; FK intact; `RESTRICT` on audit actors |
