# Research & Decisions: Administrative Back-Office for Dish Audit, Retail Pricing (PVP) and Catalog Approval

**Feature**: `007-admin-dish-approval-pricing`
**Date**: 2026-10-26
**Specification**: [spec.md](./spec.md)
**Status**: Phase 0 complete — 0 unresolved `[NEEDS CLARIFICATION]`

Precedence applied throughout: for anything **already implemented**, the code wins; for objects a previous spec **owns but has not yet implemented**, that spec wins. Every conflict found is recorded here rather than resolved silently (Principle VII).

---

## 1. Verified repository baseline (evidence, not assumption)

| Claim | Verification command | Result |
|---|---|---|
| Highest Flyway migration is `V5` | `ls db/migration/` on `HEAD` and `git ls-tree origin/main` | `V1..V5`; highest `V5__extend_users_profile_and_dietary.sql` |
| `dishes` DDL as it exists today | `V5__…sql:70-82` | `id, tenant_id, name VARCHAR(150), description, price NUMERIC(10,2) NOT NULL CHECK(price >= 0), is_active DEFAULT TRUE, created_at, updated_at` + `idx_dishes_tenant(tenant_id, is_active)` |
| No `com.easyeat.dish` package | `find src/main/java/com/easyeat -maxdepth 1 -type d` | `auth, catalog, common, config, inventory, profile, staff` only |
| Only one `@RestControllerAdvice` | `grep -rl "@RestControllerAdvice"` | `com.easyeat.auth.controller.GlobalExceptionHandler` |
| No optimistic locking anywhere | `grep -rln "@Version" src/main/java` | 0 files — this feature introduces it to the project |
| Real role enum | `cat auth/domain/UserRole.java` | `COMENSAL, ENCARGADO, CHEF, ADMINISTRADOR` |
| `/api/v1/admin/**` role-gated at the filter chain | `SecurityConfig:78` | `.hasAnyRole("ADMINISTRADOR","ADMIN")` |
| Pagination convention | `ChefCatalogController`, `ChefIngredientPageResponse` | `@PageableDefault(size = 20)` + immutable record page DTO `(items, page, size, totalElements, totalPages, hasNext)` |
| Money convention | `inventory/entity/Ingredient.java:56,74-92`, `StockAuditEntry.java:55-57` | `BigDecimal` + `setScale(4, HALF_UP)` normalised inside the entity; DTOs expose bare `BigDecimal` with no Jackson annotation |
| Dev seed | `db/dev/R__seed_dev_dishes_and_dietary.sql` | 5 dishes for tenant `a0eebc99-…-a11`, prices 4500.00–9500.00, covered by `DevSeedDishesMigrationTest` |
| Staff deactivation is a status, never a row delete | `V3__…sql:13`, `StaffService:170-177` | `users.status ∈ {PENDING_FIRST_LOGIN, ACTIVE, SUSPENDED, UNVERIFIED}` |
| Frontend React version | `easyeat/frontend/package.json` | `react 18.3.1` (not 19) |
| `/admin/*` wildcard route | `src/router/AppRoutes.tsx` | `/admin/staff` and `/admin/inventory` are declared **before** the `/admin/*` catch-all; new admin routes must follow that ordering |

### 1.1. Upstream drift (Principle XI)

**Re-measured 2026-10-27; the 2026-10-26 reading below is superseded and any later reader must re-measure before relying on this section.**

`git fetch origin` executed 2026-10-27. `HEAD` = `c3536e0`. The sprint's integration branch is `origin/develop/sprint-2`, not `main`: against it this branch is **33 commits behind and 0 ahead**, so every local commit is already integrated and nothing local is at risk. `origin/develop/sprint-2` is 0 behind and 22 ahead of `origin/main`. A staleness alert was raised; integration remains the developer's decision and no `pull`/`merge`/`rebase` was run.

**This time the baseline did move.** `V6__create_ingredient_batches_and_refactor_lifecycle.sql` (spec 004, PR #126) landed on `main` and on `develop/sprint-2`, consuming the migration version this plan had attributed to spec 005 and forcing the renumbering recorded in §2(b). The refactor also decoupled purchase cost from the ingredient form and table, so `Ingredient.unitPurchaseCost` was re-verified byte-for-byte across both branches: unchanged (`nullable = false`, `precision = 12`, `scale = 4`, `HALF_UP`), therefore the Food Cost and `has_unpriced_ingredients` premises of §1.1 hold.

*Superseded reading, kept for the record*: 2026-10-26, `HEAD d89cf47`, `origin/main c29728e`, merge-base `daa0f97`, 12 behind / 17 ahead; the 12 commits touched only `auth/service/EmailService.java`, `application*.yml`, `.env.example`, `README.md` and spec 001 documents.

### 1.2. CRITICAL — spec 005 has landed on its own branch; spec 006 is still design-only

**Superseded 2026-10-27.** The original finding (verified 2026-10-26 across the working tree and every branch) was that `com.easyeat.dish` did not exist, nor did `V6__chef_dish_management.sql`, `dish_recipe_ingredients`, `DishStatus` or `dishes.status/category/allergens_confirmed/created_by`.

Spec 005 has since delivered all of it as **`V7__chef_dish_management.sql`** on `origin/005-chef-dish-management`, together with `com.easyeat.dish.domain` (`DishStatus`, `DishCategory`, `DishPhoto`, `DishRecipeIngredient`), the repositories and Testcontainers migration tests. Issue #137 and PR #138 additionally added `dishes.submitted_at`, widened `chk_dishes_status` to admit `'PUBLISHED'`, and created `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)`.

Two things remain true: that branch is **not integrated into `develop/sprint-2`** and no pull request is open for it, and **spec 006 has still produced no code** (`FoodCostEngine`, `GET /dishes/{id}/cost-audit`). This feature therefore keeps designing against the documented contracts and keeps every needed object as a numbered Upstream Requirement in [plan.md](./plan.md#upstream-requirements). Implementation is blocked until 005's branch and 006's engine reach the integration branch; design is not.

---

## 2. Open points resolved

### (a) Sequencing — what the implementation phase must verify first

**Decision**: implementation opens with a non-negotiable *Upstream Readiness Gate* executed before the first line of production code, asserting: (1) 005's migration (`V7__chef_dish_management.sql`) is present on the integration branch and applies; (2) `com.easyeat.dish.domain.Dish` + `DishStatus` exist and `dishes` carries `status`, `category`, `allergens_confirmed`, `created_by`; (3) `dish_recipe_ingredients` exists; (4) `FoodCostEngine` and `FoodCostAuditService` exist in `com.easyeat.dish.service`; (5) `dishes.submitted_at` exists and `idx_dishes_review_inbox_pending` is present (R-005-1, satisfied in schema 2026-10-27 — no fallback remains authorised); (6) `DishStatus` admits `PUBLISHED` under an agreed owner (R-005-3); (7) 006's cost computation is reachable without a `PENDING_PRICE` gate (R-006-1) — if not, the fallback of point (e) applies.
**Rationale**: makes the dependency a mechanical check with a recorded outcome instead of a surprise discovered mid-implementation, and forces each fallback to be a conscious, logged decision.
**Alternatives considered**: starting implementation against stubs of 005's objects (rejected: creates a second, competing dish domain, exactly what D1 forbids); waiting idle for 005 to merge (rejected: design work does not need to wait, only coding does).

### (b) Migration ownership and version number

**Decision**: this feature owns exactly one migration, whose version is **not reserved in advance**. It owns: `dishes.retail_price NUMERIC(10,2) NULL` + its own `CHECK` constraint, `dishes.version BIGINT NOT NULL DEFAULT 0`, `dishes.published_at TIMESTAMPTZ NULL`, the `retail_price` backfill, `dish_review_history`, `dish_price_history`, the history indexes and `idx_dishes_review_inbox_published`. It does **not** own `idx_dishes_review_inbox_pending` (created by 005's `V7`) nor the `PUBLISHED` status domain (delivered by 005 under R-005-2).

**Rationale, corrected 2026-10-27 — a reservation in a planning document reserves nothing.** The original rationale read: *"`V6` is reserved by spec 005, `V7` is already reserved by spec 008, `V8` is the lowest free integer."* Every clause of that failed within a day. Spec 004 wrote and merged `V6__create_ingredient_batches_and_refactor_lifecycle.sql`, so 005 moved to `V7`; that collides in turn with spec 008's documented claim on `V7`, which must move to `V8`. The version a feature gets is decided by **whoever writes and merges the file first**, never by the order in which plans claimed numbers.

**Rule adopted**: the filename is assigned when the migration is authored, as the lowest integer strictly greater than every migration present in `db/migration/` on the integration branch, and verified against that directory rather than against any plan. The migration's *content* is version-agnostic; only the filename changes. Queue as of 2026-10-27: `V6` (004, on `main`), `V7` (005, on `005-chef-dish-management`), `V8` (claimed by 008, unwritten) → this feature expects **`V9`**. Safe because Flyway runs with `outOfOrder` disabled (default) and no environment beyond dev has applied these migrations, so no checksum of an applied script is disturbed. Tests are named without a version (`AdminPricingMigrationTest`) so a renumbering cannot invalidate them.

**Alternatives considered**: keeping a number reserved in the plan (rejected — this is precisely the mechanism that just failed twice); a timestamp-based version (rejected: breaks the project's `V<n>` convention, and with `outOfOrder` disabled it only relocates the failure to any branch that merges late).

### (c) Submission timestamp for FIFO ordering (FR-003)

**Decision**: emit **R-005-1** — spec 005 must add `dishes.submitted_at TIMESTAMPTZ NULL`, written to `NOW()` by its `POST /api/v1/dishes/{id}/submit-review` transition on **every** `DRAFT → PENDING_PRICE` transition, including resubmissions. This feature reads it and never writes it.
**Rationale**: the instant of submission is produced by a transition that 005 owns; under D1 this feature cannot add a column representing someone else's event. `dish_review_history` cannot substitute: it records *administrative decisions*, so a first-time submission leaves no row at all and is unorderable from it.
**Outcome (2026-10-27) — R-005-1 satisfied in schema, fallbacks withdrawn**: spec 005's `V7` creates `submitted_at TIMESTAMPTZ NULL` plus the covering index `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)` (issue #137, PR #138). FR-003 orders by `submitted_at ASC` against that index. The two degraded fallbacks previously recorded here — `created_at ASC` and `updated_at ASC`, neither of which satisfied FR-003 as written — are **withdrawn and no longer authorised**: there is no longer a condition under which they apply.
**Residual dependency**: the *write* belongs to 005's US4 (issue #133, open). Until it lands the column is `NULL` on every row, so the ordering is correct by construction but unverifiable end to end. This is a verification dependency, not a design fallback.

### (d) Resubmission indicator (FR-004)

**Decision**: derived from this feature's own `dish_review_history`; no upstream requirement needed. `rejectedCount(dish) = COUNT(*) WHERE event_type = 'REJECTED'`; `isResubmission = rejectedCount > 0`; `currentCycleNumber = rejectedCount + 1`.
**Query shape (no N+1)**: the page query returns at most 20 dish ids; a **single** aggregate query then resolves all of them —
`SELECT dish_id, COUNT(*) FROM dish_review_history WHERE dish_id IN (:ids) AND event_type = 'REJECTED' GROUP BY dish_id` — yielding exactly 2 statements per page (plus the pagination count), independent of page size. Computed in `DishReviewInboxService` while assembling the page DTO, never in the entity or the controller.
**Alternatives considered**: a correlated subquery inside the page query (rejected: entangles the projection with pagination and the count query for no measurable gain); an `@ElementCollection` on the dish (rejected: eager child loading per row is the N+1 this decision exists to avoid).

### (e) Food Cost consumption contract with spec 006

**Conflict**: 006 gates `GET /dishes/{id}/cost-audit` and its `FoodCostAuditService` to `status = 'PENDING_PRICE'` (`006/data-model.md §4`, `FR-007`), but FR-011/FR-012 of this feature recompute the prevailing margin on consultation for **`PUBLISHED`** dishes as well, and repricing applies to published dishes.
**Decision**: consume spec 006 **in-process**, and emit **R-006-1** — 006 must move its status gate out of `FoodCostAuditService` and into `AdminDishCostAuditController`, leaving the service callable for any status. This feature then injects `FoodCostAuditService` and never re-implements the arithmetic. This feature's own detail endpoint embeds 006's `FoodCostAuditResponse` payload, so the Back-Office makes one call that works in both `PENDING_PRICE` and `PUBLISHED`.
**Rationale**: a state gate is an API-surface policy, not a domain rule of cost arithmetic; pushing it to the controller is where it belongs regardless of this feature. Calling 006's HTTP endpoint from the server would mean in-process HTTP (absurd), and 006's endpoint cannot answer for published dishes at all.
**Degraded fallback if 006 refuses**: consume the pure `FoodCostEngine` plus `DishRecipeIngredientRepository` directly and assemble the audit DTO here. Cost: the *assembly* of the audit payload is duplicated (one debt item, registered in plan.md Complexity Tracking). The *calculation* is never duplicated under either branch — 006 owns it (FR-006, SC-003 of 006).
**Alternatives considered**: relaxing 006's gate to `{PENDING_PRICE, PUBLISHED}` (acceptable but inferior: the next state added forces another edit to 006); duplicating the engine (rejected outright, violates Principle IV and 006's ownership).

### (f) Legacy `dishes.price` deprecation path

**Decision**: per D2, `retail_price` is the PVP and `price` becomes legacy. After this feature **no application code reads or writes `dishes.price`**. Concretely, the single current reader — `CatalogComplianceService#getEnrichedCatalog` / `#evaluateCartCompliance` via `catalog.domain.Dish#getPrice` — is migrated to `retail_price` in the same change that adds the D4 visibility predicate. The wire contract is preserved: `EnrichedDishResponse.price` keeps its JSON field name (spec 003 and spec 008 consumers depend on it) but is sourced from `retail_price`.
**What still touches `price` afterwards**: the column itself (kept, `NOT NULL` relaxed by 005's `V7`), the dev seed `R__seed_dev_dishes_and_dietary.sql` (which writes both columns while 003's tests exist), `FlywayMigrationV5Test` assertions about V5's DDL, and 005's `Dish` mapping which declares it nullable. Nothing writes both columns in the same operation; `retail_price` is the only column any pricing path writes.
**Deprecation note** (to be placed on the column via SQL `COMMENT` and on the entity field): *"Deprecated by spec 007. Superseded by `retail_price`. Retained only for backward compatibility with spec 003 artifacts; never written by application code after 007. Removal owned by the Sprint 3 catalog-lifecycle feature (CHF-2.2.5 pause/unpublish), which must also drop `chk_dishes_price`."*
**Alternatives considered**: reusing `price` as the PVP (rejected by D2 — it is `NOT NULL CHECK(price >= 0)` and so cannot express "not yet priced", and spec 007's Key Entities name `retail_price`); dropping `price` in this migration (rejected: out of scope, and it would break spec 003's tests and seed in the same breath).

---

## 3. Amendments required to other artifacts

### 3.1. FR-008 and SC-002 — the ±0.01 pp round-trip tolerance is not universally achievable — **AMENDMENT APPLIED 2026-10-28**

**Finding (derived symbolically, then verified with exact decimal arithmetic).** With $p$ the exact price $fc/(1-m/100)$ and $p'$ its HALF_UP 2-decimal rounding,
$$m' - m = 100\,fc\left(\frac{1}{p} - \frac{1}{p'}\right) = \frac{100\,fc\,(p'-p)}{p\,p'},\qquad |p'-p| \le 0.005$$
hence the **exact bound** $|m'-m| \le \dfrac{0.5\,fc}{p'(p'-0.005)}$ and the first-order estimate $|m'-m| \approx \dfrac{0.5\,fc}{p'^2}$ percentage points. Requiring the first-order bound $\le 0.01$ pp gives a closed-form threshold:
$$p' \ge 50\left(1 - \frac{m}{100}\right) \quad\Longleftrightarrow\quad p' \ge \sqrt{50\,fc}$$
**Verification** (exact `Decimal`, HALF_UP, 2-decimal prices): `fc=3.33, m=60.00% → PVP 8.33, m'=60.0240%` (**0.0240 pp**); `fc=1.25, m=70.00% → 4.17, 70.0240%` (**0.0240 pp**); `fc=0.07, m=60.00% → 0.18, 61.1111%` (**1.1111 pp**). The two examples carried by the spec (`4.00 @ 60% → 10.00`, `3.50 ↔ 14.00`) are **exact divisions** and therefore unrepresentative. A brute-force sweep of 2,608,175 (food cost, margin) pairs across margins −500.00%…99.99% found **zero** violations of ±0.01 pp whenever $p' \ge 50(1-m/100)$, and violations below it. Note the first-order estimate *understates* the error at pathological prices (observed 1.1111 pp vs estimate 1.0802 pp at `fc=0.07`); only the exact bound above holds universally.
**Threshold for the margin band this business uses** (55–75%): ±0.01 pp is guaranteed for PVP ≥ **\$22.50** at 55%, ≥ **\$20.00** at 60%, ≥ **\$15.00** at 70%, ≥ **\$12.50** at 75%. The dev seed's prices (4500.00–9500.00 ARS) sit far above any of these, so in the project's actual currency the defect is latent — but it is real for sub-\$20 unit prices and must not be papered over.
**Amendment requested** (to be applied to `spec.md` via `/speckit-clarify` or direct edit before `/speckit-tasks`): FR-008 and SC-002 must replace the unconditional "±0.01 percentage points" with the two invariants the implementation will actually enforce and test:
- **INV-1 (unconditional)**: `retail_price = HALF_UP₂(food_cost / (1 − margin_pct/100))` — the derivation is correctly rounded. This is exact, testable and always holds.
- **INV-2 (conditional)**: re-deriving the margin from the stored 2-decimal price satisfies `|m' − m| ≤ 0.5·food_cost / (p'·(p' − 0.005))` pp, which is `≤ 0.01` pp **if and only if** `retail_price ≥ 50·(1 − margin_pct/100)`. Below that threshold the UI discloses the achievable precision instead of promising ±0.01 pp.
**This plan does not restate ±0.01 pp as an unconditional guarantee anywhere.**

**Applied to `spec.md` on 2026-10-28**: FR-008, SC-002 and the US2 acceptance scenario now carry the threshold condition, the 2026-10-25 clarification that fixed the tolerance unconditionally is marked superseded, and the contract fields `marginTolerancePp` and `toleranceThresholdPrice` consequently have requirement backing. Recorded in that document's Clarifications session for the same date.

### 3.2. Status domain extension — resolved at the database level, open in the enum

Spec 005's migration originally declared `CONSTRAINT chk_dishes_status CHECK (status IN ('DRAFT','PENDING_PRICE'))`. `PUBLISHED` could not be stored without changing that constraint, and Flyway forbids editing an applied migration.
**Decision**: emit **R-005-2** — 005 widens its own `chk_dishes_status` to include `'PUBLISHED'`. Primary path, and the only one that respects D1 literally.
**Outcome (2026-10-27)**: accepted and delivered. 005's `V7` now declares `CHECK (status IN ('DRAFT','PENDING_PRICE','PUBLISHED'))` (issue #137, PR #138). The fallback — having this feature drop and replace a 005-owned constraint as the single deliberate D1 exception — is **withdrawn** and removed from plan.md Complexity Tracking. The column, its type, nullability and default were untouched.
**Still open — R-005-3**: the application enum `com.easyeat.dish.domain.DishStatus` declares only `DRAFT` and `PENDING_PRICE`, so the database admits a value the code cannot express. Spec 005 has asked this feature to add the constant, but D1 assigns that package to 005. Either 005 adds it, or `DishStatus` becomes jointly owned by an **explicitly recorded amendment to D1** — not by a commit that quietly crosses the boundary. Registered in plan.md Complexity Tracking as pending a human decision (Principle VIII).

### 3.3. FR-001 names a role that does not exist — **AMENDMENT APPLIED 2026-10-28**

FR-001 restricts access to role `ADMIN` and names `COUNTER` among the rejected roles. `UserRole` has neither: `ADMIN` is an accepted **alias** of `ADMINISTRADOR` (both appear in `SecurityConfig:78` and in the `@PreAuthorize` convention), and the real non-admin staff role is **`ENCARGADO`**. **Amendment**: FR-001 should read `ADMINISTRADOR` (alias `ADMIN`) and list `CHEF`, `ENCARGADO`, `COMENSAL` as rejected. This plan uses `ENCARGADO` everywhere, including the RBAC test matrix. **Applied to `spec.md` on 2026-10-28** (FR-001, the cross-tenant edge case and the spec-004 precondition in Assumptions), recorded in that document's Clarifications session for the same date.

### 3.4. Validation failures: the spec says 400, the code says 422

`GlobalExceptionHandler#handleMethodArgumentNotValid` maps every `@Valid` bean-validation failure to **422** with an `errors[]` extension, whereas FR-013 and FR-021 demand **400** for an invalid price and an invalid rejection comment.
**Decision**: honour the specification (Principle I). Price precision/sign and comment length/blankness are validated in this feature's domain layer, which throws `InvalidRetailPriceException` / `InvalidRejectionCommentException`, mapped to **400** with a field-bound `errors[]` extension matching the existing 422 body shape. These two fields are therefore **not** annotated with bean-validation constraints that would pre-empt the domain check and yield 422. Structural request validation (missing body, unparseable UUID, absent `expectedVersion`) keeps the project's existing behaviour.
**Alternatives considered**: amending the spec to 422 for consistency with the codebase (defensible, and cheaper — recorded here as the runner-up, rejected because the spec is explicit and 400 is a correct reading of "the client sent an invalid value"); a second `@RestControllerAdvice` to override the status (rejected outright: one advice exists and must stay the only one).

### 3.5. Spec 005's plan claims React 19

`005/plan.md` states React 19; `easyeat/frontend/package.json` pins `react@18.3.1`. **Code wins**: this feature is designed for React 18.3.1 with `react-router-dom` 6 and Vitest 2. Correction noted for 005.

### 3.6. Seed reconciliation against spec 005 (D5)

005's plan intends the five seeded dishes to carry `PENDING_PRICE`. That would empty the diner catalog under D4's predicate and break spec 003's catalog tests (`CatalogControllerTest`, `CatalogComplianceServiceTest`, `CatalogComplianceIntegrationTest`) and `DevSeedDishesMigrationTest`. **This feature overrides it**: the five rows land `status = 'PUBLISHED'`, `is_active = TRUE`, `retail_price` backfilled from `price`, `version = 0`, `published_at = NOW()`, `allergens_confirmed = TRUE`, plus one **synthetic** `dish_review_history` approval row per dish (`cycle_number = 1`, `event_type = 'APPROVED'`, comment explicitly marked `[SEED SINTÉTICO]`). Recorded as a note to 005. `created_by` stays `NULL`: the only dev `CHEF` (`b2eebc99-…-a24`) belongs to tenant `9cc23d9b-…`, not to the seeded dishes' tenant `a0eebc99-…-a11`, so no truthful author exists; the UI renders such an author as *"Autor no registrado"* (see §3.8).

### 3.7. Cross-spec conflict with spec 008 (diner catalog)

`008/data-model.md:38,149-155` declares the published state as **`'ACTIVE'`** and redefines `chk_dishes_status CHECK (status IN ('DRAFT','PENDING_PRICE','ACTIVE','DECOMMISSIONED'))` in its own migration (claimed as `V7`, which must move to `V8` now that spec 005 has taken `V7` — see §2(b)), and `008/data-model.md:36` has its catalog read `price`. Spec 007 uses `'PUBLISHED'` and, per D2, `retail_price`.
**Resolution — decidable from 008's own text, no escalation needed**: `008/spec.md:168,198` explicitly defers naming to the upstream feature — *"A dish lifecycle state representing 'published and visible to Diners' (e.g., ACTIVE or PUBLISHED) is defined by a prior spec that extends the state machine… This spec consumes that terminal state as a prerequisite."* Spec 007 is that prior spec, so **`PUBLISHED` stands** and 008's `data-model.md` carries the amendment: use `PUBLISHED`, do not redefine `chk_dishes_status` (it belongs to 005 per R-005-2), read `retail_price`, and adopt the D4 predicate. Recorded as a note to 008. The two migrations remain disjoint otherwise (008 owns `establishments`, `photo_url`); only the status domain and the price column overlap.

### 3.8. Spec 006 maps its recipe to the wrong `Dish` entity

`006/data-model.md:108,132` has `DishRecipeIngredient` associate `com.easyeat.catalog.domain.Dish`. Under D3 the catalog entity becomes read-only and the writable owner is `com.easyeat.dish.domain.Dish`. **Amendment to 006**: the association must target `com.easyeat.dish.domain.Dish`. Left as a note; this feature does not edit 006's artifacts.

### 3.9. Approved dishes must be made visible: `is_active` at publication

005's data model defaults `is_active = FALSE` on new drafts (`005/data-model.md:23`), while D4's diner predicate is `is_active = true AND status = 'PUBLISHED'`. A dish that only transitioned `PENDING_PRICE → PUBLISHED` would therefore stay invisible and SC-001 would be unprovable.
**Decision**: the approval transition sets `is_active = TRUE` as part of the same atomic update that sets `status = 'PUBLISHED'` and `published_at`. This writes *data* into a spec-003 column (V5), not DDL, so D1 is untouched, and it is the first and only activation this feature performs. `is_active` keeps its meaning and remains reserved for the Sprint 3 pause feature (CHF-2.2.5), which will flip it to `FALSE` without changing `status`. No other path in this feature writes `is_active`.
**Alternatives considered**: requiring 005 to default `is_active = TRUE` (rejected: drafts would be catalog-visible the moment `status` admits `PUBLISHED` by mistake — defaulting to invisible is the safe direction); dropping `is_active` from the predicate (rejected: it is D4, and Sprint 3 needs it).

### 3.10. The per-unit acquisition cost now exists in three places (discovered 2026-10-28)

Spec 004's inventory refactor landed on the integration branch after this plan was written. `V6__create_ingredient_batches_and_refactor_lifecycle.sql` records a per-unit cost in three columns: `ingredients.unit_purchase_cost NUMERIC(12,4) NOT NULL` (V4, untouched — re-verified byte-for-byte), the new `ingredient_batches.unit_cost NUMERIC(12,4) NOT NULL`, and the new `stock_audit_entries.unit_cost` beside `financial_loss`. The index `idx_ingredient_batches_active (tenant_id, ingredient_id, remaining_quantity, created_at ASC)` shows batches are drawn down FIFO, so two batches of one ingredient may hold different costs by design.

§1.1 of this document records that the refactor left `Ingredient.unitPurchaseCost` unchanged, and that remains true. What it did not anticipate is that the refactor **added** cost-bearing columns rather than moving the existing one, so the premise "there is one acquisition cost per ingredient" silently stopped holding.

**Why this matters here**: FR-012 requires the margin to be recomputed from "up-to-date inventory acquisition costs". With three candidates — the master ingredient's cost, the oldest remaining batch under FIFO, or the weighted average over batches with remaining quantity — that phrase has three referents and three different margins. `has_unpriced_ingredients`, detected today as a zero master cost, acquires the same ambiguity: an ingredient may carry a zero master cost while its batches hold real ones.

**Decision**: none is taken here. Spec 004 owns the columns and spec 006 owns the Food Cost computation that must choose among them; the choice is theirs jointly. Emitted as **R-004-1** in [plan.md](./plan.md#upstream-requirements) as a blocking upstream requirement with no safe degraded path — assuming the master cost would make this feature publish margins that contradict the inventory's own valuation of the stock consumed, and that contradiction surfaces as a financial discrepancy rather than as a visible failure. Escalated per Principle VIII.

**Knock-on for this feature's own schema**: `dish_price_history.food_cost_snapshot` is `NUMERIC(10,2)` while every cost column above is scale 4, so whatever rule is chosen must also state where the reduction to scale 2 occurs.

---

## 4. Notes on the closed decisions D1–D6 (record only, not re-opened)

- **D1 (hard precondition)** — Alternative: vendoring 005's objects into this feature's migration. Rejected: two owners of `dishes.status` guarantees a merge conflict and a double `V6`.
- **D2 (`retail_price` is new)** — Alternative: reuse `price`. Rejected: `NOT NULL` cannot express "unpriced", and the spec names `retail_price`.
- **D3 (entity ownership)** — Alternative: a single entity shared by catalog and back-office. Rejected: one mapping cannot be both read-only for diners and version-locked for writers; `@Immutable` on the catalog mapping is what structurally prevents a future write from bypassing `@Version`. Verified today: no write path exists through `catalog.domain.Dish` (`DishRepository` exposes only `findByTenantIdAndIsActiveTrue`, `findByIdIn`, and `CatalogComplianceService` only reads).
- **D4 (visibility predicate)** — Alternative: `status` alone. Rejected: Sprint 3 needs an orthogonal pause flag.
- **D5 (dev seed published)** — Alternative: leave the seed in `PENDING_PRICE` and relax 003's tests. Rejected: weakening green tests to accommodate a seed is backwards.
- **D6 (English artifacts)** — Alternative: Spanish. Rejected: 005 and 006 are English; Spanish stays in UI copy and quoted backlog text.
