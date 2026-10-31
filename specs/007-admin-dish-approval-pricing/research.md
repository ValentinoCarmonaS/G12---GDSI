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
| No optimistic locking anywhere | `grep -rln "@Version" src/main/java` | 0 files. **Re-read 2026-10-28**: this is no longer a gap this feature fills. FR-025 is satisfied by conditional `UPDATE`s, so the count stays 0 and the project still has no row-version column (see §3.11 and `R-005-4`, withdrawn) |
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

### 1.2. CRITICAL — superseded twice; both specs 005 and 006 are now integrated

**Superseded 2026-10-27.** The original finding (verified 2026-10-26 across the working tree and every branch) was that `com.easyeat.dish` did not exist, nor did `V6__chef_dish_management.sql`, `dish_recipe_ingredients`, `DishStatus` or `dishes.status/category/allergens_confirmed/created_by`.

Spec 005 has since delivered all of it as **`V7__chef_dish_management.sql`** on `origin/005-chef-dish-management`, together with `com.easyeat.dish.domain` (`DishStatus`, `DishCategory`, `DishPhoto`, `DishRecipeIngredient`), the repositories and Testcontainers migration tests. Issue #137 and PR #138 additionally added `dishes.submitted_at`, widened `chk_dishes_status` to admit `'PUBLISHED'`, and created `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)`.

Two things then remained true: that branch was **not integrated into `develop/sprint-2`** and no pull request was open for it, and **spec 006 had produced no code** (`FoodCostEngine`, `GET /dishes/{id}/cost-audit`). This feature therefore kept designing against the documented contracts and kept every needed object as a numbered Upstream Requirement in [plan.md](./plan.md#upstream-requirements).

**Superseded again 2026-10-28, and both clauses are now false.** Spec 005 merged into `develop/sprint-2` via PR #160, and spec 006 via PR #170 at 17:20Z. `FoodCostEngine`, `FoodCostAuditService`, `AdminDishCostAuditController` and their four test suites are on the integration branch. The intermediate state measured earlier the same day — PR #155 closed without merging, no pull request open, zero Food Cost files on the integration branch — was accurate when taken and expired within the hour. **Implementation is no longer blocked by either spec**; what remains is recorded in §2(a)'s gate table.

---

## 2. Open points resolved

### (a) Sequencing — what the implementation phase must verify first

**Decision**: implementation opens with a non-negotiable *Upstream Readiness Gate* executed before the first line of production code, asserting: (1) 005's migration (`V7__chef_dish_management.sql`) is present on the integration branch and applies; (2) `DishStatus` and `DishCategory` exist in `com.easyeat.dish.domain`, and the single `Dish` entity — `com.easyeat.catalog.domain.Dish`, not a back-office-only one; see §3.11 — carries `status`, `category`, `allergens_confirmed` and `created_by`; (3) `dish_recipe_ingredients` exists; (4) `FoodCostEngine` and `FoodCostAuditService` exist in `com.easyeat.dish.service`; (5) `dishes.submitted_at` exists, `idx_dishes_review_inbox_pending` is present, and the submit transition assigns the timestamp (R-005-1, fully satisfied 2026-10-28 — no fallback remains authorised); (6) `DishStatus` admits `PUBLISHED` under an agreed owner (R-005-3); (7) **withdrawn** — this check asked the owners of specs 003 and 005 to acknowledge `@Version` on the shared entity (R-005-4); no acknowledgement is needed now that the guard is a conditional `UPDATE`; (8) a single authoritative per-unit acquisition cost has been named (R-004-1); (9) 006's cost computation is reachable without a `PENDING_PRICE` gate (R-006-1) — if not, the fallback of point (e) applies.

**Gate status, measured 2026-10-28 on this branch at `e934713`** (fast-forwarded to `develop/sprint-2`, 0 behind and 0 ahead):

| Check | Status | Evidence |
|---|---|---|
| (1) `V7` present and applies | **PASS** | `db/migration/V7__chef_dish_management.sql`, landed via PR #160 |
| (2) enums and the single `Dish` entity carry the upstream columns | **PASS** | `catalog/domain/Dish.java` is the only `@Entity` over `dishes` and carries all five |
| (3) `dish_recipe_ingredients` exists | **PASS** | declared in `V7`, alongside `dish_photos` |
| (4) `FoodCostEngine` and `FoodCostAuditService` exist | **PASS** | both in `com.easyeat.dish.service`, landed via PR #170 (merged 17:20Z) |
| (5) `submitted_at`, its index, and the submit write | **PASS** | `V7` lines 13 and 33–34; `DishService` assigns it on the transition |
| (6) `DishStatus` admits `PUBLISHED` under an agreed owner | **this feature's to execute** | the enum still declares two constants; the D1 amendment of 2026-10-28 authorises this feature to add the third |
| (7) acknowledgement of `@Version` on the shared entity | **WITHDRAWN** | R-005-4 withdrawn; no shared entity is modified beyond this feature's own columns |
| (8) authoritative acquisition cost named | **PASS** | issue #146, confirmed in `FoodCostEngine` |
| (9) cost computation reachable for any status | **PASS** | zero status references in `FoodCostAuditService`; gate at `AdminDishCostAuditController:39`. The fallback of point (e) is **not** activated |

One item remains, and it is this feature's own work under a recorded agreement rather than a wait on another owner.

**Status 2026-10-28**: checks (1) to (5) pass on `develop/sprint-2` after PR #160. Checks (6), (7), (8) and (9) remain open and are the only things standing between this plan and its first line of production code.
**Rationale**: makes the dependency a mechanical check with a recorded outcome instead of a surprise discovered mid-implementation, and forces each fallback to be a conscious, logged decision.
**Alternatives considered**: starting implementation against stubs of 005's objects (rejected: creates a second, competing dish domain, exactly what D1 forbids); waiting idle for 005 to merge (rejected: design work does not need to wait, only coding does).

### (b) Migration ownership and version number

**Decision**: this feature owns exactly one migration, whose version is **not reserved in advance**. It owns: `dishes.retail_price NUMERIC(10,2) NULL` + its own `CHECK` constraint, `dishes.published_at TIMESTAMPTZ NULL`, the `retail_price` backfill, `dish_review_history`, `dish_price_history`, the history indexes and `idx_dishes_review_inbox_published`. It does **not** own `idx_dishes_review_inbox_pending` (created by 005's `V7`) nor the `PUBLISHED` status domain (delivered by 005 under R-005-2).

**Rationale, corrected 2026-10-27 — a reservation in a planning document reserves nothing.** The original rationale read: *"`V6` is reserved by spec 005, `V7` is already reserved by spec 008, `V8` is the lowest free integer."* Every clause of that failed within a day. Spec 004 wrote and merged `V6__create_ingredient_batches_and_refactor_lifecycle.sql`, so 005 moved to `V7`; that collides in turn with spec 008's documented claim on `V7`, which must move to `V8`. The version a feature gets is decided by **whoever writes and merges the file first**, never by the order in which plans claimed numbers.

**Registry created 2026-10-28**: [`.specify/memory/migrations.md`](../../.specify/memory/migrations.md) now holds the authoritative list of consumed versions and in-flight claims, updated in the same PR that adds a migration. That closes the gap this section left open — the rule below was checkable only against a directory nobody was named to watch.

**Rule adopted**: the filename is assigned when the migration is authored, as the lowest integer strictly greater than every migration present in `db/migration/` on the integration branch, and verified against that directory rather than against any plan. The migration's *content* is version-agnostic; only the filename changes. Safe because Flyway runs with `outOfOrder` disabled (default) and no environment beyond dev has applied these migrations, so no checksum of an applied script is disturbed. Tests are named without a version (`AdminPricingMigrationTest`) so a renumbering cannot invalidate them.

**Number confirmed 2026-10-28: `V8`.** The integration branch holds `V1`–`V7`, `V7` having landed with spec 005 (PR #160), so `V8` is the lowest free integer and the rule yields it directly. The earlier expectation of `V9` in this document and in `data-model.md` is corrected.

**On spec 008's competing claim, and why deferring to it would backfire.** Spec 008 has written `V8__create_establishments.sql` on its own feature branch (PRs #168 and #169, merged into `008-diner-catalog-multi-tenant`, neither into the integration branch). Registry rule 4 makes that a courtesy, not a reservation: whichever branch merges first keeps the number. Yielding `V9` to be polite would *not* spare spec 008 — with `outOfOrder` disabled, a later `V8` sitting below an applied `V9` is rejected on every database not recreated, so 008 would have to renumber to `V10` instead of `V9`, and a gap would be left behind for nothing. The two files are independent in content: this one alters `dishes`; 008's creates `establishments` and, verified on its branch, does **not** touch `dishes.status` or `chk_dishes_status` despite what `008/data-model.md` plans. Renumbering either is a rename plus a registry row.

**Resolved 2026-10-31, on integration into `develop/sprint-2` (PR #182).** Spec 008 merged first, confirmed present on `develop/sprint-2` as `V8__create_establishments.sql` before this feature's four stacked phase PRs (#178-#181) were merged into `007-admin-dish-approval-pricing` and that branch was then integrated. Per the rule adopted above, this feature's file was renamed from `V8__admin_dish_approval_pricing.sql` to `V9__admin_dish_approval_pricing.sql` during that integration, content unchanged, `AdminPricingMigrationTest` unaffected (no version in its name, as designed), and `.specify/memory/migrations.md` updated in the same merge.

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
**Outcome 2026-10-28 — accepted by 006, fallback never activated.** The gate moved to `AdminDishCostAuditController`; `FoodCostAuditService` holds zero references to `DishStatus` and both of its `auditDishCost` overloads compute for any lifecycle state. This feature injects the service directly and the debt item is removed rather than incurred. Two consequences of reading the merged code, neither of which the decision above anticipated, are recorded as `data-model.md §3.5` and `§3.6`: the service raises `EmptyRecipeException` instead of returning a zero cost, and its response payload does not carry the ingredient's `IngredientStatus`, which FR-015 condition 2 needs.
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
**R-005-3 — RESOLVED 2026-10-28 by a recorded amendment to D1.** The application enum `com.easyeat.dish.domain.DishStatus` declares only `DRAFT` and `PENDING_PRICE`, so the database admits a value the code cannot express. Spec 005 asked this feature to add the constant, and the escalation filed on issue #133 drew no reply. The resolution is the second of the two admissible paths: `DishStatus` is declared **jointly owned by specs 005 and 007**, recorded as an amendment in plan.md Complexity Tracking and in `R-005-3`, and this feature adds the constant. The amendment covers that one enum, authorises addition only — the enum is persisted by name through `@Enumerated(EnumType.STRING)`, so reordering or renaming would be a data migration — and requires no change in spec 005. What makes this acceptable under Principle VIII is not that the change is small: it is that the written agreement changed before the file did.

### 3.3. FR-001 names a role that does not exist — **AMENDMENT APPLIED 2026-10-28**

FR-001 restricts access to role `ADMIN` and names `COUNTER` among the rejected roles. `UserRole` has neither: `ADMIN` is an accepted **alias** of `ADMINISTRADOR` (both appear in `SecurityConfig:78` and in the `@PreAuthorize` convention), and the real non-admin staff role is **`ENCARGADO`**. **Amendment**: FR-001 should read `ADMINISTRADOR` (alias `ADMIN`) and list `CHEF`, `ENCARGADO`, `COMENSAL` as rejected. This plan uses `ENCARGADO` everywhere, including the RBAC test matrix. **Applied to `spec.md` on 2026-10-28** (FR-001, the cross-tenant edge case and the spec-004 precondition in Assumptions), recorded in that document's Clarifications session for the same date.

### 3.4. Validation failures: the spec says 400, the code says 422

`GlobalExceptionHandler#handleMethodArgumentNotValid` maps every `@Valid` bean-validation failure to **422** with an `errors[]` extension, whereas FR-013 and FR-021 demand **400** for an invalid price and an invalid rejection comment.
**Decision**: honour the specification (Principle I). Price precision/sign and comment length/blankness are validated in this feature's domain layer, which throws `InvalidRetailPriceException` / `InvalidRejectionCommentException`, mapped to **400** with a field-bound `errors[]` extension matching the existing 422 body shape. These two fields are therefore **not** annotated with bean-validation constraints that would pre-empt the domain check and yield 422. Structural request validation (missing body, unparseable UUID, `expectedRetailPrice` absent from the body) keeps the project's existing behaviour. Note that `expectedRetailPrice` is required **and** nullable: an explicit `null` is a valid assertion that the dish was unpriced when read, so only the absence of the key is a structural failure.
**Alternatives considered**: amending the spec to 422 for consistency with the codebase (defensible, and cheaper — recorded here as the runner-up, rejected because the spec is explicit and 400 is a correct reading of "the client sent an invalid value"); a second `@RestControllerAdvice` to override the status (rejected outright: one advice exists and must stay the only one).

### 3.5. Spec 005's plan claims React 19

`005/plan.md` states React 19; `easyeat/frontend/package.json` pins `react@18.3.1`. **Code wins**: this feature is designed for React 18.3.1 with `react-router-dom` 6 and Vitest 2. Correction noted for 005.

### 3.6. Seed reconciliation against spec 005 (D5)

005's plan intends the five seeded dishes to carry `PENDING_PRICE`. That would empty the diner catalog under D4's predicate and break spec 003's catalog tests (`CatalogControllerTest`, `CatalogComplianceServiceTest`, `CatalogComplianceIntegrationTest`) and `DevSeedDishesMigrationTest`. **D5 originally overrode it**: the five rows were to land `status = 'PUBLISHED'`, `is_active = TRUE`, `retail_price` backfilled from `price`, `published_at = NOW()`, `allergens_confirmed = TRUE`, plus one **synthetic** `dish_review_history` approval row per dish, with `created_by` left `NULL` because no truthful author exists in that tenant.

**D5 WITHDRAWN 2026-10-28, and its premise was false.** The override above rested on one claim: that leaving the seed in `PENDING_PRICE` "would break spec 003's catalog tests (`CatalogControllerTest`, `CatalogComplianceServiceTest`, `CatalogComplianceIntegrationTest`) and `DevSeedDishesMigrationTest`". Measured on the merged code, it breaks none of them:

- **no test references the seeded dish identifiers** (`d1eebc99-…` … `d5eebc99-…`); spec 003's catalog tests build their own fixtures;
- `DevSeedDishesMigrationTest` asserts only that at least five dishes exist in the tenant and that certain restriction codes are mapped. It never reads `status`, `retail_price` or `created_by`;
- the whole suite is green at 575 tests **with** the seed in `PENDING_PRICE`.

**And carrying out D5 would have broken a green test of spec 005.** `V7MigrationAndSeedDevTest:143-147` asserts, for every dish in that tenant, `status = 'PENDING_PRICE'` **and** `created_by IS NOT NULL`. D5 violates both. Weakening another spec's passing test to accommodate a seed is precisely what this section rejected when the roles were reversed, so the same reasoning retires D5 rather than 005's assertions.

**Two further reasons `PENDING_PRICE` is the better state for this feature.** Its primary surface is a review inbox of dishes *awaiting* a decision, so five pending dishes are exactly the fixture it needs; and its own `quickstart.md` walks the Administrator through **approving** a seeded dish, which requires one to be pending in the first place. D5 would have left that flow with nothing to approve.

**What the seed does instead** (T015): `status`, `is_active`, `allergens_confirmed` and `created_by` are left exactly as spec 005 wrote them, and the seed states `retail_price` and `published_at` explicitly as `NULL` so the five dishes are deterministically *unpriced and never approved* on every developer's database. Populating the diner catalog with published dev data, if it is wanted, is a decision for specs 003 and 008 and not for this feature.

### 3.7. Cross-spec conflict with spec 008 (diner catalog)

`008/data-model.md:38,149-155` declares the published state as **`'ACTIVE'`** and redefines `chk_dishes_status CHECK (status IN ('DRAFT','PENDING_PRICE','ACTIVE','DECOMMISSIONED'))` in its own migration (claimed as `V7` at the time; spec 008 has since written `V8__create_establishments.sql`, which — verified on `origin/008-diner-catalog-multi-tenant` — creates only `establishments` and leaves `dishes.status` and `chk_dishes_status` untouched, so the redefinition its data-model plans has not been written. See §2(b) for the competing claim on `V8`), and `008/data-model.md:36` has its catalog read `price`. Spec 007 uses `'PUBLISHED'` and, per D2, `retail_price`.
**Resolution — decidable from 008's own text, no escalation needed**: `008/spec.md:168,198` explicitly defers naming to the upstream feature — *"A dish lifecycle state representing 'published and visible to Diners' (e.g., ACTIVE or PUBLISHED) is defined by a prior spec that extends the state machine… This spec consumes that terminal state as a prerequisite."* Spec 007 is that prior spec, so **`PUBLISHED` stands** and 008's `data-model.md` carries the amendment: use `PUBLISHED`, do not redefine `chk_dishes_status` (it belongs to 005 per R-005-2), read `retail_price`, and adopt the D4 predicate. Recorded as a note to 008. The two migrations remain disjoint otherwise (008 owns `establishments`, `photo_url`); only the status domain and the price column overlap.

### 3.8. Spec 006 maps its recipe to the wrong `Dish` entity — **WITHDRAWN 2026-10-28**

The original finding read: *"`006/data-model.md:108,132` has `DishRecipeIngredient` associate `com.easyeat.catalog.domain.Dish`. Under D3 the catalog entity becomes read-only and the writable owner is `com.easyeat.dish.domain.Dish`. Amendment to 006: the association must target `com.easyeat.dish.domain.Dish`."*

Both halves of that reasoning are gone:

- **D3 is withdrawn** (§3.11 below). There is one entity over `dishes` and it is the catalog one, so there was never a wrong class to point at.
- **The class as merged has no such association.** `com.easyeat.dish.domain.DishRecipeIngredient` uses an `@EmbeddedId` (`DishRecipeIngredientId`) and declares no `@ManyToOne` to `Dish`, so there is nothing to redirect.

No amendment to spec 006 is required on this point. `R-006-2` in [plan.md](./plan.md#upstream-requirements) is withdrawn accordingly.

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

**RESOLVED 2026-10-28 — answered by spec 006's owner on issue #146, and verified in merged code.** The rule, with the verification beside each clause rather than after it:

- **`ingredients.unit_purchase_cost` is authoritative.** The stated ground: that column is the standard recipe-costing figure used for menu engineering and base PVP; `ingredient_batches.unit_cost` values the physical stock consumed under FIFO. The two answer different questions and both remain correct for their own. Verified: `FoodCostAuditService` resolves cost through `IngredientRepository` and `Ingredient.getUnitPurchaseCost()`, and never reads `ingredient_batches`.
- **The no-remaining-batch case is moot**, because the computation never consults batches.
- **"Unpriced" means `NULL` or `<= 0`.** The line contributes `$0.00`, the ingredient is appended to `warning_notes`, and `has_unpriced_ingredients` is set; the subtotal accumulates and the computation continues. Verified at `FoodCostEngine:39`.
- **Scale 4 becomes scale 2 at each line subtotal**, `quantity × unit_purchase_cost` rounded `HALF_UP`, and the total is the sum of the already-rounded subtotals. Verified at `FoodCostEngine:48` and `:53`.

**Two consequences worth stating, because neither is obvious from the rule.** First, the Food Cost reaches this feature **already at scale 2**, so `dish_price_history.food_cost_snapshot NUMERIC(10,2)` stores it without loss and this feature rounds the Food Cost nowhere — the knock-on above is answered upstream rather than here. Second, rounding per line before summing means the total can differ from the unrounded sum by up to half a cent per line. That is spec 006's deliberate choice and it is the right one for cent-exact reporting; FR-008's prohibition on intermediate rounding is untouched by it, because FR-008 governs the margin arithmetic, of which this value is an input.

### 3.11. D3 withdrawn: spec 005 consolidated the entity instead of splitting it (discovered 2026-10-28)

**What changed.** Spec 005 merged into `develop/sprint-2` through PR #160, together with its phase 6 and 7 branches. The readiness gate of this feature then passed on four of its nine checks (eight now that check 7 is withdrawn; the full table is in §2(a)): `V7__chef_dish_management.sql` is present with `status`, `category`, `allergens_confirmed`, `created_by` and `submitted_at` plus `idx_dishes_review_inbox_pending`; `dish_recipe_ingredients` and `dish_photos` exist; and `DishService` assigns `submitted_at` on the submit transition, which closes the residual verification dependency of R-005-1.

**What was not delivered, and will not be.** `com.easyeat.dish.domain.Dish` does not exist. Spec 005 added its five columns to the pre-existing `com.easyeat.catalog.domain.Dish` and imports `DishStatus` from `com.easyeat.dish.domain`. The `dish/domain/` package holds enums and auxiliary entities — `DishCategory`, `DishCategoryConverter`, `DishPhoto`, `DishRecipeIngredient`, `DishRecipeIngredientId`, `DishStatus` — but no `Dish` entity. There is exactly one `@Entity` over `dishes`, and four classes write through it: `DishService`, `DishPhotoService`, `CatalogComplianceService` and `DishRepository`. Verified on `origin/develop/sprint-2`, where that entity carries no `@Version`, no `@Immutable`, no `retail_price` and no `published_at`.

**Why this is not a defect in spec 005.** Consolidating onto one aggregate is a defensible design: it avoids two mappings over one table, which is the very confusion D1 exists to prevent. The defect was in this plan's assumption, which treated a second entity as a deliverable rather than as a request.

**First revised decision (2026-10-28, superseded the same day)**: add `retailPrice`, `publishedAt` and `@Version` to `catalog.domain.Dish`, abandon D3's `@Immutable` guard because applying it to the only writable entity would break spec 005's submit path, and escalate the behavioural consequence for the other writers as **R-005-4** rather than absorb it silently.

**Final decision**: `retailPrice` and `publishedAt` only — **no `@Version`**, and `R-005-4` withdrawn. The last line of the alternatives below said "forgoing `@Version` (rejected outright: FR-025 cannot be satisfied without it)". That was wrong, and it is the error worth recording: it treated one implementation of optimistic concurrency as the definition of it. FR-025 asks that the first committed operation win and the rest receive 409. A conditional `UPDATE` that restates the state the Administrator acted upon delivers exactly that, for each of the three operations the requirement names:

- approval and rejection carry `AND status = 'PENDING_PRICE'`, which closes both the sequential and the overlapping race. Under PostgreSQL's default `READ COMMITTED` the second statement blocks on the row lock and then re-evaluates its predicate against the committed row, so it affects zero rows — the case a version column was being introduced to catch;
- repricing carries `AND retail_price IS NOT DISTINCT FROM :expectedRetailPrice`, the compare-and-set form, `IS NOT DISTINCT FROM` rather than `=` so that the first assignment can compare against `NULL`.

**Why this is better and not merely adequate**: it guards the three operations FR-025 names instead of changing failure behaviour on every write path through a shared entity, it needs no acknowledgement from two other owners, it adds no column, and it stays inside D1's boundary. **What it gives up**: the guard is per operation rather than per row, so a write path added to `dishes` later inherits nothing from it — registered as debt in `plan.md` Complexity Tracking beside the loss of D3's structural guard.

**Alternatives considered**: asking spec 005 to extract a writable entity now that its spec is merged and closed (rejected: it is a refactor of another feature's delivered code, for a guarantee we can obtain more cheaply, and Principle V forbids unsolicited refactoring); keeping two mappings by adding our own (rejected: duplicates the aggregate 005 just consolidated and reopens the two-owner problem); `@Version` on the shared entity (rejected on 2026-10-28 as above — it was the plan of record for one day); an `If-Match`/`ETag` header for repricing (rejected: no ETag infrastructure exists in the codebase, and a body field is visible in the OpenAPI schema and consistent with existing request DTOs).

---

## 4. Notes on the closed decisions D1–D6 (record only, not re-opened)

- **D1 (hard precondition)** — Alternative: vendoring 005's objects into this feature's migration. Rejected: two owners of `dishes.status` guarantees a merge conflict and a double `V6`.
- **D2 (`retail_price` is new)** — Alternative: reuse `price`. Rejected: `NOT NULL` cannot express "unpriced", and the spec names `retail_price`.
- **D3 (entity ownership)** — **WITHDRAWN 2026-10-28; the rejected alternative is now the only one available.** D3 originally rejected "a single entity shared by catalog and back-office" on the grounds that one mapping cannot be both read-only for diners and version-locked for writers. Spec 005 merged (PR #160) having added its five columns to `catalog.domain.Dish` rather than creating `dish.domain.Dish`, and `DishService` now writes `submitted_at` through that entity. So the single shared entity is what exists, `@Immutable` on it would break spec 005's submit path, and this feature adds no version column at all — the guard is the conditional `UPDATE` of §3.11, and `R-005-4` is withdrawn. The guarantee lost is structural enforcement, replaced by review; the guarantee gained is that no other spec's write path changes behaviour so that this one can be safe.
- **D4 (visibility predicate)** — Alternative: `status` alone. Rejected: Sprint 3 needs an orthogonal pause flag.
- **D5 (dev seed published)** — **WITHDRAWN 2026-10-28; the rejected alternative was the right one all along.** D5 rejected "leave the seed in `PENDING_PRICE`" on the grounds that it would force 003's tests to be relaxed. Measured on the merged code, nothing needed relaxing: no test reads the seeded dishes' status and none references their identifiers. Meanwhile D5 itself would have required relaxing `V7MigrationAndSeedDevTest`, a green test of spec 005. The principle the original note invoked — weakening green tests to accommodate a seed is backwards — is what retires it. See §3.6.
- **D6 (English artifacts)** — Alternative: Spanish. Rejected: 005 and 006 are English; Spanish stays in UI copy and quoted backlog text.
