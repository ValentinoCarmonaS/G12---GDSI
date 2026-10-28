# Tasks: Administrative Back-Office for Dish Audit, Retail Pricing (PVP) and Catalog Approval

**Input**: Design documents from `/specs/007-admin-dish-approval-pricing/`

**Prerequisites**: plan.md, spec.md, data-model.md, research.md, quickstart.md, contracts/openapi.yaml

**Tests**: TDD is requested for this feature. Every production task is preceded by its own test task. Test class names are reused verbatim from `plan.md` §Testing strategy wherever that section already fixes them.

**Organization**: Tasks are grouped by user story. Phases 1 and 2 absorb `plan.md` §Implementation phases 0–2 as **shared** work carrying no `[US]` label, because all four stories depend on the migration, the audit entities and the pricing calculator. Phases 3–6 are one per story in spec priority order. Phase 7 holds the cross-story suites that no single story owns.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: may run in parallel — disjoint files, no dependency on an incomplete task
- **[US1]–[US4]**: the user story the task serves; absent on shared phases

## Path Conventions

- Backend production: `easyeat/backend/src/main/java/com/easyeat/`
- Backend tests: `easyeat/backend/src/test/java/com/easyeat/`
- Backend resources: `easyeat/backend/src/main/resources/`
- Frontend production: `easyeat/frontend/src/`
- Frontend tests: `easyeat/frontend/tests/`

---

## Phase 1: Setup — Upstream Readiness Gate

**Purpose**: `plan.md` §Implementation phases Phase 0. Each task is a single verifiable assertion with a recorded outcome.

**✅ GATE PASSES as of 2026-10-28.** Measured on this branch at `e934713`, fast-forwarded to `develop/sprint-2` and 0 behind / 0 ahead. Specs 005 (PR #160) and 006 (PR #170) are both integrated: `V7` applies, `com.easyeat.dish` exists, and `FoodCostEngine` / `FoodCostAuditService` are present. `R-004-1` and `R-006-1` were answered in writing on issue #146 and verified against the merged code; `R-005-4` is withdrawn in favour of a conditional `UPDATE`; `R-005-3` is settled by a recorded amendment to D1. **One task in this phase remains to be executed** — `T005`, adding `PUBLISHED` to the enum under that amendment. The rule stands: no task from T009 onward starts until every task in this phase passes.

- [x] T001 Verify spec 005's migration `V7__chef_dish_management.sql` is present in `easyeat/backend/src/main/resources/db/migration/` and applies cleanly, and record the outcome against R-005-0 — **VERIFIED 2026-10-28, R-005-0 satisfied**

  Presence: the file is in `db/migration/`, landed on the integration branch via PR #160.

  "Applies cleanly" was proved by **execution, not inspection**: the seven versioned migrations were applied in order against a throwaway `postgres:16` container with `psql -v ON_ERROR_STOP=1`:

  ```
  OK   V1__create_users.sql
  OK   V2__create_password_reset_tokens.sql
  OK   V3__extend_users_staff.sql
  OK   V4__create_ingredients_and_stock_audit.sql
  OK   V5__extend_users_profile_and_dietary.sql
  OK   V6__create_ingredient_batches_and_refactor_lifecycle.sql
  OK   V7__chef_dish_management.sql
  ```

  This is a stronger result than the task asked for: it confirms `V7` applies on top of the exact `V1`–`V6` sequence this feature's `V8` will extend, not merely that it parses.

  **Correction, same day.** This record originally said no Maven was available and offered that as the reason for applying the migrations by hand instead of running spec 005's `V7MigrationAndSeedDevTest`. **That was wrong**: Maven 3.9.9 is on disk at `~/.m2/wrapper/dists/`, merely absent from `PATH`, and it runs the project once it is allowed to fetch the missing surefire plugin. The result above stands on its own evidence and is arguably the better check — it exercises the exact `V1`–`V7` sequence rather than one test's fixture — but the stated reason was false and is withdrawn.
- [x] T002 [P] **SATISFIED 2026-10-28.** Verify the upstream dish domain exists: `DishStatus`, `DishCategory` and the auxiliary entities in `com.easyeat.dish.domain`, and `DishRepository`. **Note the correction**: there is no `com.easyeat.dish.domain.Dish` and none is coming — spec 005 added its five columns to the pre-existing `com.easyeat.catalog.domain.Dish`, which is the single `@Entity` over `dishes`. Assert that entity carries `status`, `category`, `allergens_confirmed`, `created_by` and `submitted_at` in `easyeat/backend/src/main/java/com/easyeat/dish/`, and record the outcome against R-005-0

  **VERIFIED 2026-10-28 on the working branch.** `com.easyeat.dish.domain` holds `DishStatus`, `DishCategory`, `DishCategoryConverter`, `DishPhoto`, `DishRecipeIngredient` and `DishRecipeIngredientId` — and **no `Dish`**, confirming the correction above. `grep -rln '@Table(name = "dishes")'` returns exactly one file, `catalog/domain/Dish.java`, so there is a single entity over the table. That entity carries all five upstream columns:

  ```
  Dish.java:26  @Column(name = "category", length = 50)
  Dish.java:36  @Column(name = "status", nullable = false, length = 50)
  Dish.java:39  @Column(name = "allergens_confirmed", nullable = false)
  Dish.java:42  @Column(name = "created_by")
  Dish.java:45  @Column(name = "submitted_at")
  ```

  `DishRepository` is at `catalog/repository/DishRepository.java`, beside the entity rather than in `dish/`, which is where this feature's inbox queries will go. `dish/repository/` holds `DishPhotoRepository` and `DishRecipeIngredientRepository`.
- [x] T003 [P] Verify the `dishes` table carries `status`, `category`, `allergens_confirmed`, `created_by` and `submitted_at`, and that the index `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)` exists, by inspecting `easyeat/backend/src/main/resources/db/migration/V7__chef_dish_management.sql`; record against R-005-0 and R-005-1 — **VERIFIED 2026-10-28, R-005-0 and R-005-1 satisfied**

  Verified against the **live schema** produced by T001's run, not against the migration text, so the result reflects what the database actually holds:

  ```
  allergens_confirmed | boolean                  | NOT NULL
  category            | character varying        | NULL
  created_by          | uuid                     | NULL
  status              | character varying        | NOT NULL
  submitted_at        | timestamp with time zone | NULL
  ```

  The FIFO index exists exactly as FR-003 needs it, column order included:

  ```sql
  CREATE INDEX idx_dishes_review_inbox_pending
      ON public.dishes USING btree (tenant_id, status, submitted_at)
  ```

  **Two findings beyond what the task asked.** First, `chk_dishes_status` was read back from `pg_constraint` and admits `'DRAFT'`, `'PENDING_PRICE'` and `'PUBLISHED'`, closing R-005-2 against the running database rather than against the migration source. Second, that was confirmed behaviourally: an `INSERT` of `status = 'PUBLISHED'` **succeeds**, and an `INSERT` of `status = 'ACTIVE'` is **rejected** by the constraint. The second half matters beyond this task — spec 008's `data-model.md` plans to redefine this constraint around `'ACTIVE'`, and the database as it stands refuses that value, so the cross-spec conflict recorded in `research.md §3.7` is live rather than theoretical.

  This is also the precise proof of T005's premise: the database admits a value the application enum cannot yet express.
- [x] T004 [P] Verify the tables `dish_recipe_ingredients` and `dish_photos` exist in `easyeat/backend/src/main/resources/db/migration/V7__chef_dish_management.sql`, and record the outcome against R-005-0 — **VERIFIED 2026-10-28, R-005-0 satisfied**

  Both tables are present in the live schema built by T001:

  ```
  dish_photos
  dish_recipe_ingredients
  ```

  `dish_recipe_ingredients` is the table FR-006 and FR-015 condition 2 read through spec 006's audit service and through this feature's own gate query (T076a). `dish_photos` is read by the detail view.

  **Recorded while here, as the negative half of the gate**: none of the objects this feature *owns* exists yet, which is the state T014's migration assumes. `information_schema` returns `0` columns named `retail_price`, `published_at` or `version` on `dishes`, and `0` tables named `dish_review_history` or `dish_price_history`. If any of them had already existed, T014 would have been writing against a schema it did not expect.
- [x] T005 **DONE 2026-10-28 under the recorded D1 amendment.** Add the `PUBLISHED` constant to `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishStatus.java`, which declares only `DRAFT` and `PENDING_PRICE` while the database has admitted `PUBLISHED` since 005's `V7`. The authority is the amendment recorded in `plan.md` §R-005-3 and Complexity Tracking, which declares that enum **jointly owned** by specs 005 and 007. **Add the constant and nothing else**: do not reorder, rename or remove the existing ones — the enum is persisted by name through `@Enumerated(EnumType.STRING)`, so any of those is a data migration — do not touch any other class in `com.easyeat.dish.domain`, which stays 005-owned under D1 unchanged, and do not create a competing status enum in this feature. Update `DishEnumsTest` to assert the three constants. **R-005-4 is withdrawn and no longer part of this task**: no `@Version` is added anywhere, so T024 is no longer blocked.

  **Executed TDD, both steps proved.** The red and green steps were first run with `javac` and the JUnit 5.12.2 platform assembled by hand from the local repository, and then **re-confirmed with the project's own Maven build**:

  ```
  Tests run: 30, Failures: 0, Errors: 0, Skipped: 0 — com.easyeat.dish.domain.DishEnumsTest
  ```

  **Correction, same day**: this record originally said Maven was unavailable and gave that as the reason for the hand-assembled run. Maven 3.9.9 is on disk at `~/.m2/wrapper/dists/`, simply not on `PATH`. The two runs agree, so the measurement was right and the explanation was not.

  | Step | Result |
  |---|---|
  | Baseline, before any change | **29 of 29 passing** — the old assertion `containsExactly(DRAFT, PENDING_PRICE)` was green, so it actively asserted a two-value enum |
  | RED, test first | **Compilation fails**: four `cannot find symbol: variable PUBLISHED` at `DishEnumsTest` lines 124, 129, 149, 150 |
  | GREEN, after the constant | **30 of 30 passing** |

  Three assertions were added or widened rather than one, because the enum is persisted by name: `values()` and `valueOf`, a new test pinning the constant **names** against the `chk_dishes_status` domain, and Jackson round-tripping. The name test exists because a rename would silently stop matching the database CHECK and nothing else in the suite would catch it.

  **Regression check (Principle VII).** Adding a constant to an enum is only safe if nothing enumerates it exhaustively. Verified across the whole backend: zero `switch` on `DishStatus`, zero `ordinal()`, zero `EnumSet`/`Set.of`/`List.of` over it, and the only `values()` calls are the two in this test. The 84 other references name `DRAFT` or `PENDING_PRICE` explicitly.

  **Noted while verifying**: spec 005's own `V7MigrationAndSeedDevTest:210` already carries a test named *"CHECK constraint chk_dishes_status should allow PUBLISHED status in dishes"*. Spec 005 therefore tested that the database admits the value **and never added the constant that would let the application express it** — which is precisely the gap R-005-3 recorded, now visible in 005's own suite.

  **FINDING — a dependent artifact nothing owns.** The frontend carries its own status domain, and it is now narrower than the backend's:

  ```ts
  // easyeat/frontend/src/types/dish.ts:23
  export type DishStatus = 'DRAFT' | 'PENDING_PRICE';
  export function isDishStatus(val: unknown): val is DishStatus {
    return val === 'DRAFT' || val === 'PENDING_PRICE';   // false for 'PUBLISHED'
  }
  ```

  And `components/dish/DishStatusBadge.tsx:34` falls back to `'DRAFT'` for any status it does not recognise, so a published dish would render as *"Borrador"* rather than failing visibly. **No task in this breakdown covers either file** — T044 creates a *new* `types/dishReview.ts` and does not touch the existing union.

  **This is latent, not live, and that is what fixes the ordering**: nothing can set `PUBLISHED` until T076 exists, so no user can see the mislabel today. It becomes a real defect the moment US3 lands. Recorded as **T079a** in US3 rather than fixed here, because Principle V forbids changes no task backs and the files belong to spec 005's surface.
- [x] T006 **SATISFIED 2026-10-28 — outcome recorded.** Spec 006's owner moved the status gate to the controller (issue #146) and spec 006 is integrated via PR #170. Verify and record: `easyeat/backend/src/main/java/com/easyeat/dish/service/FoodCostAuditService.java` holds **zero** references to `DishStatus` or `PENDING_PRICE` and both `auditDishCost(Dish)` and `auditDishCost(UUID)` compute for any lifecycle state; the `PENDING_PRICE` restriction lives in `AdminDishCostAuditController` and raises `DishLockedException`. **The degraded path is withdrawn, not activated** — this feature injects `FoodCostAuditService` directly. Record two consequences found while verifying, both of which shape later tasks: `auditDishCost` raises `EmptyRecipeException` rather than returning a zero cost (T042a), and `RecipeItemAuditResponse` does not carry the ingredient's `IngredientStatus`, which FR-015 condition 2 needs (T076a)

  **VERIFIED 2026-10-28 on the working branch**, after spec 006 landed via PR #170:

  ```
  grep -cE "DishStatus|PENDING_PRICE" dish/service/FoodCostAuditService.java   ->  0
  grep -n  "PENDING_PRICE"            dish/controller/AdminDishCostAuditController.java
      39:  if (dish.getStatus() != DishStatus.PENDING_PRICE) {
      40:      throw new DishLockedException(
  ```

  Zero references in the service and the restriction in the controller is exactly the shape R-006-1 asked for. Both `auditDishCost(Dish)` and `auditDishCost(UUID)` are `@Transactional(readOnly = true)` and compute for any lifecycle state, so this feature can call them for `PUBLISHED` dishes under FR-011 and FR-012 without touching spec 006's code. **The degraded path is withdrawn, not activated**, and the duplicated-assembly debt item it would have created is removed from `plan.md` Complexity Tracking rather than accepted.

  Also confirmed, since this feature's RBAC must agree with it: that controller already requires `hasAnyRole('ADMIN', 'ADMINISTRADOR')`, which matches FR-001's role set including the alias.
- [x] T007 **SATISFIED 2026-10-28 — rule recorded, not re-decided.** The authoritative per-unit acquisition cost was named in writing by spec 006's owner on issue #146 and confirmed in merged code: **`ingredients.unit_purchase_cost`**, the master catalogue figure, never `ingredient_batches.unit_cost` and never `stock_audit_entries.unit_cost`. "Unpriced" means that column is `NULL` or `<= 0`, in which case the line contributes `$0.00` and the computation continues with `has_unpriced_ingredients` set. Scale 4 becomes scale 2 at **each line subtotal** (`HALF_UP`), and the total is the sum of already-rounded subtotals — so the Food Cost arrives at scale 2 and **this feature rounds it nowhere**. T042, T067 and T071 are thereby unblocked.

  **VERIFIED 2026-10-28 in `FoodCostEngine`**, clause by clause rather than as a whole:

  ```java
  16:  public record IngredientCostItem(BigDecimal quantity, BigDecimal unitPurchaseCost) {}
  39:  boolean isUnpriced = (unitPurchaseCost == null || unitPurchaseCost.compareTo(BigDecimal.ZERO) <= 0);
  48:  BigDecimal subtotal = quantity.multiply(costToMultiply).setScale(2, RoundingMode.HALF_UP);
  53:  totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);
  ```

  The engine's only cost input is `unitPurchaseCost`, and `FoodCostAuditService` populates it from `Ingredient.getUnitPurchaseCost()` through `IngredientRepository`. It never reads `ingredient_batches` or `stock_audit_entries`, so the three-referent ambiguity is closed in code and not only in prose.

  **Two consequences this feature must not get wrong.** First, the Food Cost arrives **already at scale 2**, so `dish_price_history.food_cost_snapshot NUMERIC(10,2)` stores it losslessly and this feature must round the Food Cost **nowhere** — a second `setScale` would be a no-op at best and a silent double-rounding if the scale ever widened. Second, rounding each line before summing means the total can differ from the unrounded sum by up to half a cent per line. That is spec 006's deliberate choice for cent-exact reporting, and it leaves FR-008 untouched: FR-008 forbids intermediate rounding inside the **margin** arithmetic, of which this value is an input, not an intermediate
- [x] T008 **DONE 2026-10-28 — `V8`.** Determine this feature's Flyway version by listing `easyeat/backend/src/main/resources/db/migration/` and taking the lowest free sequential integer strictly greater than every migration present; record the chosen number for T014 and add its row to `.specify/memory/migrations.md` in the same commit as T014. **Confirmed 2026-10-28: the directory holds `V1`–`V7`, so the number is `V8`.** Spec 008 has written `V8__create_establishments.sql` on its own feature branch but has merged nothing to the integration branch, and by registry rule 4 a claim in flight reserves nothing — whichever merges first keeps the number. **Do not yield `V9` out of courtesy**: with `outOfOrder` disabled a later `V8` below an applied `V9` is rejected outright, so 008 would have to renumber anyway and a gap would be left for nothing. Re-confirm against the directory at the moment T014 authors the file, because the answer changes if 008 merges first.

  **DETERMINED 2026-10-28: `V8__admin_dish_approval_pricing.sql`.** The directory listing on this branch:

  ```
  V1__create_users.sql                                   V5__extend_users_profile_and_dietary.sql
  V2__create_password_reset_tokens.sql                   V6__create_ingredient_batches_and_refactor_lifecycle.sql
  V3__extend_users_staff.sql                             V7__chef_dish_management.sql
  V4__create_ingredients_and_stock_audit.sql
  ```

  The highest present is `V7`, so the lowest free integer strictly greater than every migration is **8**. Registered in `.specify/memory/migrations.md` as contested with spec 008, whose `V8__create_establishments.sql` exists on its own feature branch only.

  **Recorded for whoever renumbers**: `V8__create_establishments.sql` was read on `origin/008-diner-catalog-multi-tenant` and creates only `establishments`. It does **not** touch `dishes`, `dishes.status` or `chk_dishes_status`, so the two migrations are independent in content and renumbering either is a rename plus a registry row. The `chk_dishes_status` redefinition that `008/data-model.md` plans has not been written — and T003 confirmed behaviourally that the live constraint **rejects** `'ACTIVE'`, the value 008 intends to use, so that conflict is a design question for its owner and not a numbering one.

  **Also noted, not acted on**: spec 005's migration test is named `V7MigrationAndSeedDevTest`, which carries a version in its class name and would be invalidated by a renumbering — exactly what rule 5 of the registry exists to prevent. It is spec 005's file, so Principle V forbids renaming it from here; flagged for its owner

### Single-tier authorization for this phase (Principle X)

Principle X forbids single-tier work and states that **no automatic exception is permitted for setup, foundational or infrastructure tasks**; any such work requires explicit human authorization, and the Compliance clause requires that authorization to be documented.

**Granted 2026-10-28 by the developer, recorded here as the constitution requires.** This phase is backend-only by its nature rather than by omission: seven of its eight tasks assert the state of upstream code and produce no artifact beyond a recorded outcome, and the eighth adds one constant to an enum. There is no user-facing surface to pair it with — a frontend counterpart to an enum constant would be an invented deliverable, which Principle V forbids.

The frontend consequence that *does* exist was found while executing T005 and is deliberately **not** absorbed into this phase: the frontend's own status union is now narrower than the backend's. It is recorded as **T079a** in US3, where it becomes observable and where its vertical slice is real. That is the scope boundary this authorization covers — one tier now, with the paired work written down rather than forgotten.

**Checkpoint**: readiness recorded and **the gate passes**. All eight tasks are closed with their evidence. Nothing in this phase waits on another spec's owner, and no task was closed by choosing a resolution unilaterally: T006 and T007 record decisions their owners made in writing on issue #146, and T005 executed a change authorized by a recorded amendment to D1. **Phase 2 may begin.**

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `plan.md` §Implementation phases 1–2 — the migration, the two append-only audit aggregates, the fields added to the dish entity, the pure pricing calculator and the shared error surface. Shared by all four stories, so no `[US]` label.

**⚠️ CRITICAL**: no user story work may begin until this phase is complete.

### Pricing calculator (pure, Spring-free — independent of R-004-1)

The calculator receives the Food Cost as a parameter, so it does not depend on which column supplies it. Only the services that *obtain* the Food Cost depend on T007.

- [x] T009 Write `RetailPricingCalculatorTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/RetailPricingCalculatorTest.java` covering margin→price and price→margin, HALF_UP at exactly half a cent, negative margin, margin 0 (`PVP = food_cost`), margin at and beyond 100%, margin ≤ −500%, food cost `0.00`, more than two decimals and large amounts — **DONE 2026-10-28**, 35 tests across four nested groups

  Every expected figure is either carried by `spec.md` or verified in `research.md §3.1` with exact decimal arithmetic; none was computed by this implementation and then enshrined. The three cases pinned from §3.1 are `3.33 @ 60% → 8.33` (60.0240%), `1.25 @ 70% → 4.17` (70.0240%) and `0.07 @ 60% → 0.18` (61.1111%).

  **Scales are asserted alongside values**, because FR-008 makes scale 2 for money and scale 4 for margins a requirement rather than a formatting choice — including an assertion that a margin is *not* equal to its scale-2 reading, which is the property FR-008 states explicitly.

  **TDD ordering, stated plainly.** The test was written before the implementation, but both were authored before either was run, so **the red step was not observed at the time**. It was verified afterwards by removing the implementation and compiling: `cannot find symbol: class RetailPricingCalculator`, exit 1. That is a weaker guarantee than a red observed first, and it is recorded as what it is. T011 onward follow red-commit then green-commit.
- [x] T010 Implement `RetailPricingCalculator` in `easyeat/backend/src/main/java/com/easyeat/dish/service/RetailPricingCalculator.java` as a Spring-free `BigDecimal` component handling no request types, scale 2 for money and scale 4 for ratios, `HALF_UP` — **DONE 2026-10-28**, 35 of 35 green

  **How FR-008's "no intermediate rounding" is honoured.** Both directions divide, and division can be non-terminating, so a precision bound is unavoidable: each quotient is taken at `MathContext.DECIMAL128` (34 significant digits) and rounded exactly once, when the value is produced. In `marginFrom` the multiplication by 100 precedes the division, so there is one rounding rather than two.

  **The two blocked directions are exposed as predicates, not only as exceptions.** `marginDerivable` and `marginToPriceAvailable` let the caller disable an input instead of catching, which is what FR-030's interaction contract needs; the exceptions remain as guards against a programming error. They are `IllegalArgumentException` rather than this feature's domain exceptions because the component takes no request types and must stay Spring-free — the service layer translates.
- [x] T011 Write `MarginRoundTripToleranceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/MarginRoundTripToleranceTest.java` against the bound derived in `research.md §3.1`, **not** FR-008's prose: pin `3.33 @ 60% → 8.33` (0.0240 pp), `1.25 @ 70% → 4.17` (0.0240 pp) and `0.07 @ 60% → 0.18` (1.1111 pp), assert INV-1 unconditionally, and assert ±0.01 pp **only** at or above the threshold — **DONE 2026-10-28**, RED observed before T012

  **INV-1 is asserted without restating the implementation.** Comparing the derived price against `food_cost / (1 − margin/100)` computed the same way would be a tautology, so the test computes the exact quotient at **50 significant digits** and asserts the derived price is within half a cent of it, plus scale 2. That is the defining property of correct rounding rather than a re-run of the code under test.

  **INV-2 is asserted in both directions, and they are not symmetric.** At or above the threshold the deviation never exceeds 0.01 pp — swept over seven margins and ~1,500 qualifying prices, asserting the sweep actually reaches that region so a mis-scoped loop cannot pass vacuously. Below the threshold the test asserts only that **violations exist**, not that every value violates: some quotients below the threshold round exactly, so "if and only if" read as a universal would be false. What makes FR-008's condition load-bearing is that the region contains violations at all.

  **One case discriminates between the two candidate formulas.** `research.md §3.1` records that the first-order estimate `0.5·fc/p²` *understates* the error at `fc=0.07` (1.0802 pp estimated against 1.1111 pp observed). The test computes both and asserts the exact bound covers the observed deviation while the first-order estimate does not — so an implementation that used the cheaper formula would fail here rather than pass quietly.
- [x] T012 Extend `RetailPricingCalculator` in `easyeat/backend/src/main/java/com/easyeat/dish/service/RetailPricingCalculator.java` with the worst-case deviation `marginTolerancePp` and the disclosure threshold `toleranceThresholdPrice` that `contracts/openapi.yaml` §MarginSummary and the amended FR-008 require — **DONE 2026-10-28**, 16 of 16 green

  `marginTolerancePp` implements the **exact** bound `0.5·fc / (p·(p − 0.005))`, deliberately not the first-order estimate `0.5·fc/p²`. T011 asserts the difference rather than trusting it: at `fc = 0.07` the estimate gives 1.0802 pp while the real deviation is 1.1111 pp, so the cheap formula under-reports exactly where a client most needs the truth.

  `toleranceThresholdPrice` returns `50·(1 − margin/100)` at scale 2, matching the four values `research.md §3.1` tabulates for the 55–75% band: 22.50, 20.00, 15.00 and 12.50.

### Migration and dev seed

- [x] T013 Write `AdminPricingMigrationTest` in `easyeat/backend/src/test/java/com/easyeat/dish/migration/AdminPricingMigrationTest.java` against Testcontainers PostgreSQL 16, asserting the migration applies on top of every migration already present, that the `retail_price` backfill copies only strictly positive prices and leaves `NULL`/`0.00` unpriced, and that the new constraints and indexes exist. **Keep the name version-free** so an upstream renumbering cannot invalidate it — **DONE 2026-10-28**, RED observed before T014

  **Nothing in the test hardcodes a version**, not just the class name. The migration is located by its Flyway **description** (`admin dish approval pricing`), and the version immediately preceding it is read from `flyway.info()`. A renumbering upstream changes neither.

  **The backfill is genuinely exercised, which required building the schema in two steps.** A backfill can only be observed if rows exist *before* it runs, and the dev seed is a repeatable migration that runs *after* every versioned one — so it cannot supply them. The test migrates to the version preceding this feature's, inserts three dishes carrying the three price shapes that matter (`4500.00`, `0.00`, `NULL`), then applies the rest. Asserting the backfill against rows created afterwards would have proved nothing.

  **No Spring context.** Testcontainers, the Flyway API and plain JDBC only — the subject is the schema, so loading the application would add startup cost and a second thing that can fail.

  The RED is informative rather than merely red: it lists what it found — `[1 -> create users, … 7 -> chef dish management]` — so the failure names the gap instead of reporting a missing column.
- [x] T014 Create the feature migration at `easyeat/backend/src/main/resources/db/migration/<version from T008>__admin_dish_approval_pricing.sql` per `data-model.md §3`: `dishes.retail_price`, `dishes.published_at`, the `retail_price` backfill, the `dish_review_history` and `dish_price_history` tables, their indexes, and `idx_dishes_review_inbox_published`. **Do not declare `idx_dishes_review_inbox_pending`** (owned by spec 005's `V7`), **do not drop or replace `chk_dishes_status`** (owned by spec 005, already widened), and **do not add a `version` column** — R-005-4 is withdrawn and FR-025 is enforced by the conditional `UPDATE`s of T067 and T076, so a version column would be dead weight on a table three specs write through. This task's column list previously named one; that was a leftover from the withdrawn design and is corrected — **DONE 2026-10-28** as `V8__admin_dish_approval_pricing.sql`, T013 green at 19 of 19

  Section 3 of the migration ships **empty on purpose**, carrying only a comment: `R-005-2` was satisfied upstream, so `chk_dishes_status` is left exactly as spec 005 wrote it. T013 asserts that by name — exactly one constraint matching `%status%` on `dishes` — so a future edit that replaces it fails the suite rather than passing review.

  The backfill copies `price` only where it is `NOT NULL AND > 0`. `0.00` stays unpriced because `FR-013` requires `PVP > 0` and copying it would turn "free or unset" into "priced at nothing".

  Registry row added in the same commit, as this task requires.
- [x] T015 **DONE 2026-10-28 — D5 withdrawn, see `research.md §3.6`.** Update the dev seed `easyeat/backend/src/main/resources/db/dev/R__seed_dev_dishes_and_dietary.sql` to state `retail_price` and `published_at` **explicitly as `NULL`**, in both the `INSERT` column list and the `ON CONFLICT … DO UPDATE` clause, so the five dev dishes are deterministically unpriced and never approved. **Leave `status`, `is_active`, `allergens_confirmed` and `created_by` exactly as spec 005 wrote them**, and add no synthetic review rows.

  The task previously implemented D5: flip the five to `PUBLISHED`, backfill `retail_price`, set `published_at = NOW()`, `version = 0`, and insert a synthetic `APPROVED` audit row each. That is withdrawn for three reasons. D5's premise was false — no test references the seeded dish identifiers, and the suite is green at 575 with the seed in `PENDING_PRICE`. Carrying it out would have broken `V7MigrationAndSeedDevTest:143-147`, a green test of spec 005 that asserts `status = 'PENDING_PRICE'` and `created_by IS NOT NULL` for every dish in that tenant. And `PENDING_PRICE` is what this feature actually needs: its surface is an inbox of dishes awaiting a decision, and its own `quickstart.md` approves a seeded dish, which requires one to be pending. The `version = 0` it named was itself a leftover of the withdrawn R-005-4.

  **Why the explicit `NULL`s are not a no-op.** On a developer's existing database `V8`'s backfill copies `price` into `retail_price` for rows already present, so the five dishes would become *priced*; on a fresh clone they would be *unpriced*. Stating the columns in the seed makes the state the same either way — and the `DO UPDATE` clause is what returns dev to a known state after someone prices a dish by hand.

  **Verified**: `DevSeedDishesMigrationTest` 3 of 3, `AdminPricingMigrationTest` 19 of 19, and — the one that mattered — **spec 005's `V7MigrationAndSeedDevTest` still 6 of 6**. Had D5 been executed, that suite would have failed on two assertions in the same loop.
- [x] T016 **DONE 2026-10-28.** Extend `DevSeedDishesMigrationTest` in `easyeat/backend/src/test/java/com/easyeat/profile/migration/DevSeedDishesMigrationTest.java` with the new assertions, leaving every existing assertion unchanged: the five dev dishes carry `retail_price IS NULL` and `published_at IS NULL`, hold **no** `dish_review_history` row, and remain in `PENDING_PRICE`. The last one is asserted here as well as in spec 005's own test, so this feature records the state it depends on instead of relying on another spec to keep it.

  **No RED step was possible for this pair, and that is a property of the change rather than a shortcut.** After D5's withdrawal the seed's existing shape already satisfies most of what this feature needs, so the new assertions pass the moment they are written. The one behavioural change T015 makes — the explicit `NULL`s — only alters the outcome on a database that existed *before* `V8`, and a Testcontainers test always starts from an empty one, so no automated test can reach that path. Writing a failing test first would have meant inventing a failure. Recorded rather than dressed up.

### Audit aggregates (append-only)

- [x] T017 [P] Create the `ReviewEventType` enum (`APPROVED`, `REJECTED`) in `easyeat/backend/src/main/java/com/easyeat/dish/domain/ReviewEventType.java` — **DONE 2026-10-28**

  Documented as closed against the database: the column is persisted by name and mirrored by `CHECK (event_type IN ('APPROVED','REJECTED'))`, so adding a constant means editing that constraint in the same change, and renaming or reordering one is a data migration. The two constants deliberately absent are recorded too — no `REPRICED`, because a price change is not a review decision and lives in `dish_price_history` (FR-010), and no `SUBMITTED`, because this log records decisions taken rather than states passed through.
- [x] T018 [P] Write `DishReviewHistoryRepositoryTest` in `easyeat/backend/src/test/java/com/easyeat/dish/repository/DishReviewHistoryRepositoryTest.java` asserting the append-only shape required by FR-034 and FR-035: save and query are exposed, **no** delete and **no** `@Modifying` update exist anywhere on the interface, and the `uq_drh_dish_cycle` uniqueness holds. The absence of those operations is the observable — assert that the interface does not declare them, not merely that calling them fails — **DONE 2026-10-28**, RED observed before T019/T020

  **The sharp edge is inheritance, and the task's wording forces the right test.** `JpaRepository` brings `delete`, `deleteById`, `deleteAll` and `deleteAllInBatch` with it, so a repository that extends it cannot satisfy FR-034 no matter what it declares itself. The suite therefore walks `Class.getMethods()`, which includes inherited methods, rather than `getDeclaredMethods()`, and asserts outright that neither `JpaRepository` nor `CrudRepository` is in the hierarchy. A test written against declared methods only would have passed on a repository that inherits a full delete surface.

  **FR-021's comment rule is tested as unexpressible rather than as validated.** Two static factories, `approved(...)` and `rejected(...)`, make the rule structural: the approval factory has no comment parameter at all, so attaching one to an approval is not a mistake that can be made. The suite asserts the arity of `approved` to pin that, plus the 10 and 1000 boundaries measured after trimming.
- [x] T019 [P] Create the immutable `DishReviewHistory` entity in `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishReviewHistory.java` with no setters and no `@PreUpdate`, per `data-model.md §2` — **DONE 2026-10-28**

  Three mechanisms make the immutability structural rather than conventional, and each covers a gap the others leave: `@Immutable` stops Hibernate emitting an `UPDATE` for the mapping at all; the absence of setters stops application code trying; and every `@Column` carries `updatable = false`, so even a mapping change elsewhere cannot reopen one. No `updated_at` exists, because a row that cannot change has nothing to say about when it last did.

  **Construction is through two static factories, which is what turns FR-021 from a validation into a type property.** `rejected(...)` demands a comment; `approved(...)` has no comment parameter, so attaching feedback to an approval is not a mistake available to a caller. `cycleNumber < 1` is refused in the domain as well as by `chk_drh_cycle_positive`, so the failure arrives as an argument error at the call site rather than as a constraint violation from the driver.
- [x] T020 Create `DishReviewHistoryRepository` in `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishReviewHistoryRepository.java` exposing save and queries only — **DONE 2026-10-28**, T018 green at 15 of 15

  **Extends the bare `Repository` marker, not `JpaRepository`.** That is the whole point: `JpaRepository` and `CrudRepository` both inherit `delete`, `deleteById` and `deleteAll`, so either would hand this interface a deletion surface regardless of what it declared. The cost is stated in the file rather than discovered later — every method, `save` included, has to be declared by hand. That is the intended trade: the interface says exactly what it can do.

  Five methods: `save`, the tenant-scoped timeline ordered ascending for SC-005, `findMaxCycleNumberByDishId` for FR-022's `MAX + 1` (a read, so `uq_drh_dish_cycle` stays the *proof* of the invariant rather than its mechanism), the cycle count for the detail view, and `existsByDishIdAndTenantId` — the predicate behind FR-002's third inbox view.
- [x] T021 [P] Write `DishPriceHistoryRepositoryTest` in `easyeat/backend/src/test/java/com/easyeat/dish/repository/DishPriceHistoryRepositoryTest.java` asserting the same append-only shape required by FR-034 and FR-035 — no delete and no `@Modifying` update declared — plus chronological retrieval by creation timestamp ascending as SC-005 now specifies — **DONE 2026-10-28**, RED observed before T022/T023

  Same inheritance check as T018, for the same reason. Beyond the shape, the suite pins the three invariants the schema also enforces, so each failure arrives as an argument error at the call site rather than as a driver-level constraint violation: a `NULL` previous price is **legitimate** on a first pricing and not missing data (FR-010); a new price of zero or below is refused; and a Food Cost snapshot of `0.00` is accepted while a negative one is not, because `0.00` is what a recipe whose every ingredient is unpriced actually costs.

  It also asserts **scale 2 on all three monetary fields**, which is the downstream half of T007's finding: spec 006 rounds each line subtotal to scale 2, so this feature stores the Food Cost without rounding it a second time.
- [x] T022 [P] Create the immutable `DishPriceHistory` entity in `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishPriceHistory.java` with no setters and no `@PreUpdate`, per `data-model.md §2` — **DONE 2026-10-28**

  Same three immutability mechanisms as T019. **The snapshot is the reason the row exists**: it is what lets the margin of a past decision be reconstructed after the inventory has moved, which FR-012's recomputed margin cannot do by design.

  One judgement recorded in the code rather than left implicit: `atMoneyScale` normalises to scale 2 with `HALF_UP`, but **no rounding occurs in practice** — spec 006 rounds each line subtotal and the calculator produces prices at scale 2, so every value arriving here is already there. A value with more decimals would be an upstream defect, and normalising keeps the stored column honest instead of letting the driver truncate it silently.
- [x] T023 Create `DishPriceHistoryRepository` in `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishPriceHistoryRepository.java` exposing save and queries only — **DONE 2026-10-28**, T021 green at 12 of 12 (27 with T018)

  Four methods: `save`, the ascending timeline for SC-005, `findFirstByDishIdAndTenantIdOrderByCreatedAtDesc` to read the price a repricing replaces, and the reprice count for the detail view.

  **The descending finder reads the audit trail, not the dish**, and the distinction is written into its Javadoc because it is easy to confuse: the authoritative current price is `dishes.retail_price` per FR-012, while this answers the narrower question of what the last recorded change said. Empty is FR-010's first-pricing case, which is why `previous_price` is nullable.

### Entity fields and the immutable catalog mapping

- [x] T024 **DONE 2026-10-28 (R-005-4 withdrawn).** Add `retailPrice` and `publishedAt` — **and nothing else** — to `easyeat/backend/src/main/java/com/easyeat/catalog/domain/Dish.java`, the single JPA entity over `dishes`, because spec 005 consolidated onto it rather than creating `dish.domain.Dish`. Incremental work on an existing class, permitted by D1. **Do not add `@Version`, and do not add `@Immutable`**: FR-025 is satisfied by the conditional `UPDATE`s of T067 and T076, so no version column exists and nothing on spec 003's or spec 005's write paths changes behaviour. Do not alter the fields spec 005 owns.

  Two fields and two getter/setter pairs, nothing else. Verified that **no `@Version` and no `@Immutable` appear anywhere in the file**, and that the write paths of the other specs are untouched: `DishEntityTest` 6/6, `DishRepositoryTest` 3/3, `CatalogControllerTest` 4/4, `CatalogComplianceServiceTest` 5/5 — 18 of 18. That is the whole benefit of withdrawing R-005-4: this change needed no acknowledgement from two other owners because it changes nothing for them.
- [x] T025 **CANCELLED 2026-10-28 — D3 withdrawn, and R-005-4 withdrawn after it.** This task was to mark `catalog/domain/Dish.java` `@Immutable` so that no future write could bypass `@Version`. It is impossible on both counts now: that class is the only entity over `dishes` and spec 005 writes `submitted_at` through it, so annotating it would break spec 005's submit path — and there is no `@Version` left to bypass. The guarantee is replaced by a review-enforced invariant recorded in `plan.md` Complexity Tracking: any future write path added to `catalog/repository/DishRepository` must carry its own state predicate, because the guard is per operation rather than per row. **Do not implement this task and do not substitute a second entity for it**

### Shared error surface

- [x] T026 [P] Create `DishStateConflictException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/DishStateConflictException.java`
- [x] T027 [P] Create `DishAlreadyDecidedException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/DishAlreadyDecidedException.java`
- [x] T028 [P] Create `StaleDishPriceException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/StaleDishPriceException.java`, carrying the retail price that actually prevails so the handler can return `currentRetailPrice`. Raised when the repricing `UPDATE` affects no row
- [x] T029 [P] Create `PublicationGateException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/PublicationGateException.java` carrying the unmet conditions
- [x] T030 [P] Create `AcknowledgementRequiredException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/AcknowledgementRequiredException.java` carrying the acknowledgement kinds
- [x] T031 [P] Create `InvalidRetailPriceException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/InvalidRetailPriceException.java`
- [x] T032 [P] Create `InvalidRejectionCommentException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/InvalidRejectionCommentException.java`
- [x] T033 Write the handler-mapping tests in `easyeat/backend/src/test/java/com/easyeat/dish/controller/GlobalExceptionHandlerDishMappingTest.java` asserting 409 for state conflict, `dish-already-decided` and `dish-stale-price`, 422 `publication-gate-failed` with one entry per unmet condition, 428-style acknowledgement signalling per the contract, and 400 for the two validation exceptions — **DONE 2026-10-28**, RED observed before T034

  **The taxonomy is what is tested, not the status codes.** Three distinct 409s and two distinct 422s exist so the interface can tell the Administrator what actually happened, so each is asserted by its `type` URI and by the extension properties a client needs to recover. Two tests assert the URIs are **mutually distinct**, because a client that cannot tell them apart cannot choose between refreshing the list, refreshing the price and reporting an illegal action.

  **One correction to this task's wording**: it describes the acknowledgement as "428-style" signalling, but `contracts/openapi.yaml` and `data-model.md` both specify **422** `acknowledgement-required`. The contract is the authority, so 422 is what the suite asserts, and the discrepancy is recorded in the test's own Javadoc rather than silently resolved.

  Two tests assert what the advice must **not** gain: no handler for `ObjectOptimisticLockingFailureException`, since no `@Version` exists anywhere in this feature and such a handler would be dead code asserting a mechanism that is not there, and exactly one `@RestControllerAdvice`.
- [x] T034 Extend the single `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` with the seven handlers for this feature's own exceptions, all seven and no more. **Do not add a handler for `ObjectOptimisticLockingFailureException`**: no `@Version` is used anywhere in this feature, so Spring never raises it and a handler for it would be dead code asserting a mechanism that does not exist. **Do not add a competing `@RestControllerAdvice`** — **DONE 2026-10-28**, T033 green at 13 of 13 and spec 005's `GlobalExceptionHandlerDishTest` still 7 of 7

  The advice now carries 32 `@ExceptionHandler` methods in one class, seven of them this feature's. No competing advice was added, and no handler for `ObjectOptimisticLockingFailureException` exists — asserted by T033, not merely intended.

  **Correction recorded**: the test committed with T033 had two type errors of its own (`List<?>` and `Map<?,?>` against typed AssertJ matchers). The RED it reported was genuine — all seven handler methods were missing — but the file would not have compiled even with them present. Fixed here, with the untyped casts moved into two helpers so `ProblemDetail`'s untyped extension map is handled in one place instead of in each assertion.
### Coverage, measured per class rather than in aggregate

The JaCoCo gate added at the head of this phase is scoped to `BUNDLE`, because `com.easyeat.dish.service` is shared with spec 006 and a package rule would subject another spec's future work to a threshold chosen here. The consequence was stated when the gate went in: **the bundle figure cannot prove this feature's own classes are covered**, so each one is measured individually.

It was, and the first measurement found the gate doing exactly what was predicted of it. The bundle read 89.16% while **five of this phase's own classes sat below 80%**: `DishPriceHistory` 66.67%, `DishReviewHistory` 67.50%, `InvalidRetailPriceException` 53.85%, `PublicationGateException` and `AcknowledgementRequiredException` 75.00%.

What was uncovered is the part worth recording: it was precisely the behaviour the commit messages of T029, T030 and T031 claim. T031 says the exception "distinguishes its three faults"; one branch was exercised. T029 and T030 say an empty condition list is refused because it would mean the gate passed; that guard had no test at all. A claim in a commit message that no test exercises is a hope.

Closed by `AdminDishExceptionsTest` and by extending the two audit suites. Final figures, 715 tests green with the gate met:

| Class | LINE | BRANCH |
|---|---|---|
| `RetailPricingCalculator` | 97.56% | 93.75% |
| `DishReviewHistory` | 100% | 80.00% |
| `DishPriceHistory` | 100% | 75.00% |
| The seven exceptions and three enums | 100% | 100% |

Phase 2's classes together: **99.48% line**. The project moved from 89.91% to **90.54%** line and from 68.33% to **69.75%** branch, so this phase raised both rather than leaning on the existing margin.

**Checkpoint**: foundation ready — user story implementation can begin. Nothing remains blocked: T007 is satisfied, so T042, T067 and T071 proceed. All 26 tasks of this phase are closed, T025 as cancelled.

---

## Phase 3: User Story 1 — Administrative Inbox of Dishes Pending Review (Priority: P1) 🎯 MVP

**Goal**: the Administrator discovers submitted dishes in a segmented inbox and opens one to see its culinary file and itemized Food Cost audit.

**Independent Test**: submit several drafts from kitchen accounts, open the Back-Office inbox, assert the three segmented views, FIFO ordering, the pending badge count and 20-per-page pagination, then open a pending dish and assert the composition and cost audit render.

### Tests for User Story 1

- [x] T035 [US1] Write `DishReviewInboxServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishReviewInboxServiceTest.java` covering tab filtering, FIFO ordering by `submitted_at ASC`, resubmission derivation, and a query-count assertion proving the count is **flat in the page size** regardless of page size — **Serves**: FR-002, FR-003, FR-004 — **DONE 2026-10-28**, RED observed

  **Correction to this task's own wording.** It asked for "2 queries per page". That number is not reachable under the contract this feature publishes: the item payload needs the author's name and account status, the past-rejection count and the pending badge, so a page costs the dish content query, its count query, one batched rejection-count query and one batched author query. The *invariant the number was standing in for* is what matters and is what the suite asserts — **the count does not grow with the page size**, measured at sizes 1, 5, 20 and 100, which is precisely the N+1 this requirement exists to prevent. Pinning a literal 2 would have forced an awkward design or failed honestly.

  **Query count is measured as repository invocations, not SQL statements**, because this is a unit test over mocks. That is the right level for the defect: a service that asks the history repository once per dish is an N+1 whatever SQL results. The SQL-level count stays with `DishReviewInboxIntegrationTest` in Phase 7.

  **Found while writing it, and fixed in the same commit**: `contracts/openapi.yaml` still offered the degraded creation-time ordering for the pending tab, which `plan.md` R-005-1 withdrew and explicitly deauthorised once `submitted_at` landed. The suite now asserts the absence of that fallback — no `createdAt` or `updatedAt` order may appear in the requested sort — so the contract and the plan agree and the test enforces the agreement.
- [x] T036 [P] [US1] Write the inbox and detail portions of `AdminDishReviewControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishReviewControllerTest.java`: the full RBAC matrix (ADMINISTRADOR allowed, ADMIN alias allowed, CHEF 403, ENCARGADO 403, COMENSAL 403, anonymous 401), cross-tenant access denied with 403, pagination beyond the last page returning an empty list with accurate metadata, the per-view empty state (FR-028), the out-of-range page result with accurate metadata (FR-036), and that a dish whose author Chef has been deactivated still appears in the inbox with its author name rendered (FR-026) — **Serves**: FR-001, FR-005, FR-026, FR-027, FR-028, SC-003

### Implementation for User Story 1

- [x] T037 [US1] **DONE 2026-10-28. The original named a file that does not exist.** Create `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishReviewInboxRepository.java` with the three inbox queries: tenant-scoped paging with `submitted_at ASC` for the pending tab, `published_at DESC` for the published tab, and an `EXISTS` over `dish_review_history` for the reviewed tab. **Do not add them to `catalog/repository/DishRepository`** — **Serves**: FR-002, FR-003

  The task used to say "add the inbox queries to `com.easyeat.dish.repository.DishRepository` — incremental work on a 005-owned interface". **Three claims in one sentence, and all three were wrong.** There is no `DishRepository` in `com.easyeat.dish.repository`; the only one is `catalog/repository/DishRepository.java`; and it belongs to spec **003**, not 005. `plan.md` carried the same error in its source tree, and `data-model.md §1.2` described that interface as declaring only two methods when it declares seven — a description that was accurate before spec 005 extended it. All three are corrected in this commit.

  **Why a 007-owned interface rather than extending the catalog one.** Two Spring Data interfaces over one entity are ordinary, and D1's concern is two owners of the **entity**, not of a query. More to the point, `data-model.md §1.2` rests the review-enforced read-only invariant on `catalog/repository/DishRepository` gaining no new paths — so that is precisely the file not to widen. The `findByTenantIdAndStatus` already there is left alone rather than reused, because the pending tab needs an ordering it does not impose.

  **Ordering lives in the query, not in a caller-supplied sort.** FR-003's FIFO is a requirement rather than a preference, so a caller cannot hand in a different `Sort` and still be served. Every query is tenant-scoped in its own `WHERE`, so a caller that forgets to filter cannot read across establishments.

  **The batch rejection count went on `DishReviewHistoryRepository`**, this feature's own Phase 2 interface: one grouped query for the whole page. The per-dish `countByDishIdAndTenantId` already there is correct for one dish and an N+1 for twenty, so T035 asserts it is never called while building a page. A dish with no rejections yields **no row** rather than a zero — there is nothing to group — and the service reads a missing entry as zero, which keeps the query over one table instead of outer-joining from the dish side.

  **Signature corrected from what T035 guessed.** The test had assumed `List<Object[]>`; the method returns `List<DishRejectionCount>`, a record. A positional array compiles just as happily with the count and the identifier swapped, which is a defect waiting to happen. The test is updated in this commit, and its fixtures stop reflecting into `Dish` and `User` now that both were confirmed to expose the setters it needs.
- [x] T038 [US1] Implement `DishReviewInboxService` in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishReviewInboxService.java` — three tabs, FIFO, 20-per-page default, pending count badge, and the initial-versus-resubmission indicator in 2 queries per page — **Serves**: FR-002, FR-003, FR-004

  **DONE 2026-10-28**, T035 green at 33 of 33. Four repository calls per page — the dish page, its count, one grouped rejection count, one batched author lookup — and the count does not move with the page size.

  **The test found a design error, which is what it was for.** The service first reported `page` and `size` from the returned `Page`. `Page.empty()` is *unpaged*, so an empty view reported `size = 0` and `totalPages = 0`: a client that asked for page 9 of 20 would have been told it was on page 0 of 0. FR-036 requires the metadata to stay accurate **precisely when the page is empty**, so the response now echoes the **requested** page and size and derives the page count from the total. That is also robust against a repository returning an unpaged result.

  A second failure was the test's own: its pageable captor always verified `findPendingInbox`, so the published-ordering case verified the wrong method. Split into two captors.
- [x] T039 [US1] Create the inbox response records `DishReviewInboxItemResponse` and `DishReviewInboxPageResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-003, FR-004

  Committed **before T038** rather than after it, although the task list numbers them the other way: T038's service cannot compile without these records, so this order keeps every commit in the history buildable. The 1:1 mapping is unaffected.

  **What the item record deliberately omits** is as much the design as what it carries: no Food Cost and no margin. FR-012 recomputes those on consultation, so a list of twenty dishes would otherwise cost twenty cost audits. They belong to the detail view.

  **Correction to T036 in the same commit.** That test asserted an absent author serialises as an *omitted* key. The project sets no `@JsonInclude` and no Jackson inclusion policy, so nulls are serialised as present nulls — the assertion was testing a convention the project does not have. It now asserts a present null, which is both what happens and what the contract's `nullable: true` describes.
- [x] T040 [US1] Write `DishReviewDetailServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishReviewDetailServiceTest.java` covering the embedded spec 006 audit, the publication-gate status projection and the recomputed margin — **Serves**: FR-006, FR-012, FR-015

  **DONE 2026-10-28**, RED observed before T041–T043.

  **Two things the suite is careful not to test.** The audit's arithmetic belongs to spec 006's own suite, so the assertion here is that the **very object** its service returned is embedded — `isSameAs` — which proves the payload is passed through rather than reshaped and means a change in 006's shape needs no change here. And the gate is asserted as *status*, not as enforcement: FR-015 is enforced at the approval transition in US3, and reporting it here is a different job.

  **Condition 2 is asserted to come from a dedicated query, not from the audit payload.** One test sets zero active recipe lines while the audit still returns a non-zero cost, which is the real situation a recipe of only `DISCONTINUED` ingredients produces: the cost projects the recipe as written, the gate asks whether the dish can be produced today, and neither can be derived from the other. That requires a new 007-owned `DishPublicationGateRepository`, created in T042.

  **Two contract changes in this commit, both forced by FR-037.** `DishReviewDetailResponse` gains `recipeAbsent`, and `foodCostAudit` becomes nullable and leaves the required list. Without the flag a null audit is indistinguishable from a failed lookup, and FR-037 asks for the absence to be *presented* rather than merely left blank.
- [ ] T041 [US1] Create the detail response records `DishReviewDetailResponse`, `MarginSummary` and `PublicationGateStatus` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-006, FR-008, FR-012
- [ ] T042 [US1] Implement `DishReviewDetailService` in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishReviewDetailService.java` — identity, labels, the itemized Food Cost audit obtained by injecting spec 006's `FoodCostAuditService` directly (T006: it is status-agnostic, so the same call serves `PENDING_PRICE` and `PUBLISHED`), gate evaluation and the margin recomputed on consultation. The Food Cost source is fixed by T007 — `ingredients.unit_purchase_cost`, already at scale 2 — so do not re-decide it and do not round it again — **Serves**: FR-006, FR-012
- [ ] T042a [US1] In the same service, catch `EmptyRecipeException` from `FoodCostAuditService` and render the recipe-absent state — no itemization, no margin, publication gate reported unmet on condition 2 — while still returning identity, status, author attribution, the assigned price if any and the review history. **Never propagate it as a failed request**: a dish with no recipe lines is a fact about the dish, not a fault in the consultation. Covered by a case in `DishReviewDetailServiceTest` — **Serves**: FR-037
- [ ] T043 [US1] Implement the inbox and detail endpoints in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishReviewController.java` — `GET /admin/dishes/review-inbox` and `GET /admin/dishes/{id}/review-detail` — **Serves**: FR-002, FR-006
- [ ] T044 [P] [US1] Create the shared frontend types in `easyeat/frontend/src/types/dishReview.ts` for inbox, detail, pricing, history and feedback payloads — **Serves**: shared infrastructure for all four stories; no single requirement
- [ ] T045 [P] [US1] Write `easyeat/frontend/tests/api/dishReviewApi.test.ts` covering request shaping and error propagation for the inbox and detail calls — **Serves**: FR-032
- [ ] T046 [US1] Implement `easyeat/frontend/src/api/dishReviewApi.ts` as a typed client over the shared `apiClient` — **Serves**: shared infrastructure for all four stories; no single requirement
- [ ] T047 [US1] Write `easyeat/frontend/tests/hooks/useDishReviewInbox.test.ts` covering tab and page state, counts, and reconciliation after a stale decision — **Serves**: FR-003, FR-032
- [ ] T048 [US1] Implement `easyeat/frontend/src/hooks/useDishReviewInbox.ts` — **Serves**: FR-003, FR-032
- [ ] T049 [US1] Write `easyeat/frontend/tests/components/dishreview/ReviewInbox.test.tsx` covering tab switching, FIFO rendering, pagination, the pending badge, the initial-versus-resubmission marker and a distinct empty state per tab — **Serves**: FR-002, FR-003, FR-004, FR-028, FR-029
- [ ] T050 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/ReviewInboxTabs.tsx` — three tabs plus the pending badge — **Serves**: FR-002, FR-003
- [ ] T051 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/ReviewInboxList.tsx` — FIFO list with distinct empty, loading (skeleton) and error states — **Serves**: FR-028, FR-029
- [ ] T052 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/ReviewInboxRow.tsx` — name, category, author, `submittedAt`, status and the "Inicial" / "Reenvío (ciclo N)" marker — **Serves**: FR-004, FR-027
- [ ] T053 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/InboxPagination.tsx` — 20-per-page controls — **Serves**: FR-003
- [ ] T054 [US1] Write `easyeat/frontend/tests/components/dishreview/DishAuditPanel.test.tsx` covering identity, dietary labels, the itemized cost breakdown and the unpriced-ingredient advisory — **Serves**: FR-006
- [ ] T055 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/FoodCostBreakdownTable.tsx` — line items, subtotals and total per portion — **Serves**: FR-006
- [ ] T056 [P] [US1] Implement `easyeat/frontend/src/components/dishreview/UnpricedIngredientBadge.tsx` — amber advisory — **Serves**: FR-006
- [ ] T057 [US1] Implement `easyeat/frontend/src/components/dishreview/DishAuditPanel.tsx` composing identity, labels and the cost breakdown — **Serves**: FR-006
- [ ] T058 [US1] Write `easyeat/frontend/tests/pages/AdminDishReviewPage.test.tsx` covering the inbox-to-detail transition, the rendering of each backend error, and the information sufficiency SC-007 now requires: the detail view presents the culinary file, the itemized Food Cost audit, the prevailing margin, the state of each publication gate condition and the prior review cycles, with no additional navigation — **Serves**: FR-002, FR-032, SC-007
- [ ] T059 [US1] Implement `easyeat/frontend/src/pages/AdminDishReviewPage.tsx` — `/admin/dishes`, inbox plus detail panel — **Serves**: FR-002, FR-006
- [ ] T060 [US1] Write `easyeat/frontend/tests/router/adminDishReviewRoute.test.tsx` asserting `/admin/dishes` resolves before the `/admin/*` catch-all and is role-guarded — **Serves**: FR-001
- [ ] T061 [US1] Register the route in `easyeat/frontend/src/router/AppRoutes.tsx` **before** the `/admin/*` catch-all, matching how `/admin/staff` and `/admin/inventory` are declared — **Serves**: FR-001
- [ ] T062 [US1] Write `easyeat/frontend/tests/pages/AdminWorkspacePage.test.tsx` asserting the dish-review hub card renders, displays the pending count it receives, and links to `/admin/dishes` — **Serves**: FR-003
- [ ] T063 [US1] Add the hub card linking to the inbox with its pending count in `easyeat/frontend/src/pages/AdminWorkspacePage.tsx` — **Serves**: FR-003

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 — Commercial Margin Setting and Retail Price Assignment (Priority: P1)

**Goal**: the Administrator sets the commercial terms of a dish through a bidirectional margin ↔ PVP calculator, and every price change leaves an immutable audit entry.

**Independent Test**: on a pending or published dish, enter a target margin and assert the computed PVP, enter a PVP and assert the derived margin, save, then assert the price persisted and a price-history row was written with exact decimal precision.

### Tests for User Story 2

- [ ] T064 [US2] Write the pricing portion of `AdminDishDecisionControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishDecisionControllerTest.java`: price validation at 3 decimals, 0 and negative; `expectedRetailPrice` absent from the body → 400; an explicit `expectedRetailPrice: null` accepted on an unpriced dish; a mismatched `expectedRetailPrice` → 409 `dish-stale-price`; the negative-margin confirmation requirement; repricing a `PUBLISHED` dish; and the full RBAC and cross-tenant matrix — **Serves**: FR-009, FR-013, FR-014, SC-003
- [ ] T065 [P] [US2] Write the price-save portion of `DishGovernanceServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishGovernanceServiceTest.java`: the audit row contents, that saving is permitted in both `PENDING_PRICE` and `PUBLISHED`, and that a `PUBLISHED` dish keeps its status, recipe and visibility — **Serves**: FR-009, FR-010, FR-011, SC-008

### Implementation for User Story 2

- [ ] T066 [US2] Create the pricing records `SetRetailPriceRequest` and `DishPricingResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-007, FR-008, FR-013
- [ ] T067 [US2] **Depends on T007 (R-004-1).** Implement the price-save transaction in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishGovernanceService.java` — validate `PVP > 0` with at most two decimals, persist the price, write the immutable `dish_price_history` row capturing actor, previous price, new price and the prevailing Food Cost snapshot, and enforce concurrency with a single conditional `UPDATE` carrying `AND retail_price IS NOT DISTINCT FROM :expectedRetailPrice`, accepted only when it affects exactly one row — `IS NOT DISTINCT FROM` rather than `=` so the first assignment can compare against `NULL`; zero rows raises `StaleDishPriceException`. An absent `expectedRetailPrice` is a 400; an explicit `null` is a valid assertion that the dish was unpriced — **Serves**: FR-009, FR-010, FR-011, FR-025, SC-008
- [ ] T068 [US2] Implement `PUT /admin/dishes/{id}/retail-price` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishDecisionController.java` — **Serves**: FR-009, FR-013
- [ ] T069 [US2] Write `easyeat/frontend/tests/hooks/useRetailPriceCalculator.test.ts` covering bidirectional derivation, formatting, disabled states and the boundaries at and beyond 100%, at −500%, at margin 0 and at Food Cost `0.00` — **Serves**: FR-007, FR-008, FR-030, SC-002
- [ ] T070 [US2] Implement `easyeat/frontend/src/hooks/useRetailPriceCalculator.ts` as a pure, unit-testable derivation with its formatting rules — **Serves**: FR-007, FR-030
- [ ] T071 [US2] **Unblocked (T007 satisfied).** Write `easyeat/frontend/tests/components/dishreview/MarginPriceCalculator.test.tsx` covering reactive bidirectional updates, the negative-margin advisory requiring confirmation before save, the disabled margin→price derivation when the Food Cost is `0.00`, and the disclosure of the achievable deviation below the precision threshold — **Serves**: FR-007, FR-014, FR-030, SC-002
- [ ] T072 [US2] Implement `easyeat/frontend/src/components/dishreview/MarginPriceCalculator.tsx` — bidirectional margin ↔ PVP bound to the current Food Cost, surfacing `marginTolerancePp` from the server rather than asserting ±0.01 pp — **Serves**: FR-007, FR-008, FR-030

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 — Active Catalog Publication and Approval Gate (Priority: P1)

**Goal**: the Administrator approves a complete dish and it becomes eligible for diner visibility, with the publication gate blocking every incomplete case.

**Independent Test**: take a dish in `PENDING_PRICE` with price, recipe and allergens satisfied, approve it, assert the status becomes `PUBLISHED` and an approval audit row exists; then attempt approval without a price, with an empty recipe, with unconfirmed allergens and from `DRAFT`, asserting each is blocked with its own actionable reason.

### Tests for User Story 3

- [ ] T073 [US3] Write the publication-gate portion of `DishGovernanceServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishGovernanceServiceTest.java`: every combination of the three gate conditions, the acknowledgement requirement for unpriced ingredients and negative margin, the approval audit row contents, and idempotent approval of an already-`PUBLISHED` dish — **Serves**: FR-015, FR-017, FR-018
- [ ] T074 [P] [US3] Write the approval portion of `AdminDishDecisionControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishDecisionControllerTest.java`: each gate failure with its own `unmetConditions` entry, approve from `DRAFT` → 409, approve an already-`PUBLISHED` dish → 200 idempotent, the acknowledgement challenge, and the RBAC and cross-tenant matrix — **Serves**: FR-016, FR-018, FR-019, SC-003

### Implementation for User Story 3

- [ ] T075 [US3] Create the approval records `ApproveDishRequest`, `DishApprovalResponse`, `PublicationGateCondition` and `AcknowledgementKind` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-015, FR-017
- [ ] T076 [US3] Implement the approval transition in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishGovernanceService.java` — evaluate the three gate conditions at the instant of the action, require acknowledgement where advisories apply, then set `status = 'PUBLISHED'`, `published_at` and `is_active = TRUE` (per `research.md §3.9`) and write the approval audit row, all in one transaction. The status change MUST be a single conditional `UPDATE` carrying `AND status = 'PENDING_PRICE'`, accepted only when it affects exactly one row; zero rows raises `DishAlreadyDecidedException` carrying the status that now prevails. **Do not read-then-check in Java and then update unconditionally** — that is the race FR-025 exists to close — **Serves**: FR-016, FR-025, FR-033, SC-001
- [ ] T076a [US3] Add the query that evaluates FR-015 condition 2 to `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishRecipeIngredientRepository.java` or this feature's own repository — counting the dish's recipe lines whose ingredient has `status = 'ACTIVE'`. **It cannot be derived from the Food Cost audit payload**: `RecipeItemAuditResponse` does not carry `IngredientStatus`, and `FoodCostAuditService` deliberately includes discontinued lines in the total, because the cost projects the recipe as written while the gate asks whether the dish can be produced today. Assert both halves in `DishGovernanceServiceTest`: a recipe of only `DISCONTINUED` ingredients fails the gate while still reporting a non-zero Food Cost — **Serves**: FR-015
- [ ] T077 [US3] Implement `POST /admin/dishes/{id}/approval` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishDecisionController.java` — **Serves**: FR-016, FR-018, FR-019
- [ ] T078 [US3] Write `easyeat/frontend/tests/components/dishreview/PublicationConfirmModal.test.tsx` covering the advisory content, the required second confirmation, and that cancelling leaves the dish untouched — **Serves**: FR-017, FR-031
- [ ] T079 [US3] Implement `easyeat/frontend/src/components/dishreview/PublicationConfirmModal.tsx` — unpriced-ingredient and negative-margin advisories with an explicit second confirmation (FR-017) — **Serves**: FR-017, FR-031

- [ ] T079a [US3] Widen the frontend status domain to match the backend, in `easyeat/frontend/src/types/dish.ts` and `easyeat/frontend/src/components/dish/DishStatusBadge.tsx`. The union is `'DRAFT' | 'PENDING_PRICE'` and `isDishStatus` returns `false` for `'PUBLISHED'`, while the badge falls back to `'DRAFT'` for any unrecognised status — so once T076 can set `PUBLISHED`, a published dish renders as *"Borrador"* instead of failing visibly. Add the third member, admit it in the guard, and give the badge its own styling. **Found during T005, not during planning**: adding the constant to the backend enum made the two domains disagree, and no other task owns either file (T044 creates a new `types/dishReview.ts` and leaves the existing union untouched). These are files on spec 005's surface, so the change is narrow by intent — the third member and its badge entry, nothing else — **Serves**: FR-016, FR-033

**Checkpoint**: all three P1 stories are independently functional.

---

## Phase 6: User Story 4 — Rejection with Traceable Feedback to Chef (Priority: P2)

**Goal**: the Administrator returns a dish to the kitchen with a mandatory comment, the cycle is recorded immutably, and the Chef reads the feedback without ever seeing a financial figure.

**Independent Test**: reject a dish in `PENDING_PRICE` with a valid comment, assert the status returns to `DRAFT` and it leaves the pending inbox, assert the Chef can read the feedback on the draft, and assert no acquisition cost, Food Cost or margin appears in that payload.

### Tests for User Story 4

- [ ] T080 [US4] Write the rejection portion of `DishGovernanceServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishGovernanceServiceTest.java`: `cycle_number` computation across repeated reject→resubmit rounds, the review audit row contents, and that the assigned retail price is preserved and no price-history row is written by the rejection (FR-020 as amended 2026-10-28) — **Serves**: FR-020, FR-022, SC-004
- [ ] T081 [P] [US4] Write the rejection portion of `AdminDishDecisionControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishDecisionControllerTest.java`: comment validation at 9, 10, 1000 and 1001 characters and whitespace-only; rejecting a `PUBLISHED` dish → 409; and that markup in the comment is stored verbatim rather than stripped — **Serves**: FR-021, FR-024
- [ ] T082 [P] [US4] Write `ChefDishReviewFeedbackControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/ChefDishReviewFeedbackControllerTest.java` asserting the Chef of the establishment can read the feedback, cross-tenant access is denied, and the payload contains **no** cost, margin or price key — **Serves**: FR-023, SC-003

### Implementation for User Story 4

- [ ] T083 [US4] Create the rejection and history records `RejectDishRequest`, `DishRejectionResponse`, `DishReviewHistoryEntryResponse`, `DishPriceHistoryEntryResponse` and `DishAuditHistoryResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-021, FR-022, SC-005
- [ ] T084 [US4] Implement the rejection transition in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishGovernanceService.java` — validate the comment at 10–1000 characters excluding whitespace-only, store it verbatim, transition `PENDING_PRICE → DRAFT` atomically, preserve the retail price, increment `cycle_number` and write the immutable review audit row — **Serves**: FR-020, FR-021, FR-022
- [ ] T085 [US4] Implement `POST /admin/dishes/{id}/rejection` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishDecisionController.java` — **Serves**: FR-020, FR-021, FR-024
- [ ] T086 [US4] Implement `GET /admin/dishes/{id}/review-history` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishReviewController.java` returning review cycles and price changes in chronological order — **Serves**: FR-022, SC-005
- [ ] T087 [US4] Create the Chef-facing records `ChefReviewFeedbackEntry` and `ChefReviewFeedbackResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, carrying only text, timestamp and reviewer name — **Serves**: FR-023
- [ ] T088 [US4] Implement `ChefReviewFeedbackService` in `easyeat/backend/src/main/java/com/easyeat/dish/service/ChefReviewFeedbackService.java` as a financially blind projection that cannot reach a cost, margin or price field — **Serves**: FR-023
- [ ] T089 [US4] Implement `GET /dishes/{id}/review-feedback` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/ChefDishReviewFeedbackController.java` — **Serves**: FR-023
- [ ] T090 [US4] Write `easyeat/frontend/tests/components/dishreview/RejectionDialog.test.tsx` covering the live 10–1000 character counter, submission blocked below the minimum and on whitespace-only, and that markup is rendered as literal text — **Serves**: FR-021, FR-031
- [ ] T091 [US4] Implement `easyeat/frontend/src/components/dishreview/RejectionDialog.tsx` — mandatory comment with the live counter — **Serves**: FR-021
- [ ] T092 [US4] Write `easyeat/frontend/tests/components/chef/DishRejectionFeedbackBanner.test.tsx` asserting text, timestamp and reviewer render, and that no financial figure appears — **Serves**: FR-023
- [ ] T093 [US4] Implement `easyeat/frontend/src/components/chef/DishRejectionFeedbackBanner.tsx` — **Serves**: FR-023
- [ ] T094 [US4] Write `easyeat/frontend/tests/pages/KitchenWorkspacePage.test.tsx` asserting a draft returned by rejection renders the feedback banner, and that no acquisition cost, Food Cost or margin figure appears anywhere on the page — **Serves**: FR-023
- [ ] T095 [US4] Render the feedback banner on a returned draft in `easyeat/frontend/src/pages/KitchenWorkspacePage.tsx` — **Serves**: FR-020, FR-023

**Checkpoint**: all four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: the diner-visibility slice (D2/D4) and the cross-story suites that no single story owns.

- [ ] T096 Write `DishVisibilityPredicateIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/dish/integration/DishVisibilityPredicateIntegrationTest.java` asserting D4's predicate returns only approved dishes and that the diner payload carries the retail price — **Serves**: FR-033, SC-001
- [ ] T097 Apply the visibility predicate `is_active = true AND status = 'PUBLISHED'` in `easyeat/backend/src/main/java/com/easyeat/catalog/repository/DishRepository.java` — **Serves**: FR-033, SC-001
- [ ] T098 Read `retail_price` in `easyeat/backend/src/main/java/com/easyeat/catalog/service/CatalogComplianceService.java` per D2, preserving the `price` JSON field name so the wire contract for specs 003 and 008 stays intact — **Serves**: FR-033
- [ ] T099 [P] Write `DishReviewInboxIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/dish/integration/DishReviewInboxIntegrationTest.java` covering FIFO ordering, pagination beyond the last page with accurate metadata, and tenant isolation
- [ ] T100 [P] Write `DishApprovalPricingIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/dish/integration/DishApprovalPricingIntegrationTest.java` covering audit rows for approval, rejection and repricing, `cycle_number` across repeated rounds, repricing a published dish leaving status, recipe and visibility untouched, the margin recomputed after an acquisition cost changes, and that pricing, approval and rejection all succeed on a dish whose author Chef has been deactivated (FR-026)
- [ ] T101 Write `DishDecisionConcurrencyTest` in `easyeat/backend/src/test/java/com/easyeat/dish/concurrency/DishDecisionConcurrencyTest.java` following `inventory/concurrency/StockAdjustmentConcurrencyTest` — two genuinely competing decisions from two threads, exactly one commit, a 409 for the loser and exactly one audit row, exercising both the sequential and the overlapping-transaction orderings
- [ ] T102 [P] Write `AdminDishApprovalOpenApiContractTest` in `easyeat/backend/src/test/java/com/easyeat/dish/contract/AdminDishApprovalOpenApiContractTest.java` following `auth/contract/OpenApiContractTest`, asserting every one of the seven operations matches `contracts/openapi.yaml`
- [ ] T103 Execute the three scenarios of `specs/007-admin-dish-approval-pricing/quickstart.md` end to end and record the outcome, including the frontend validation of section 6
- [ ] T104 Measure and record, in `specs/007-admin-dish-approval-pricing/quickstart.md`, the 95th-percentile elapsed time for the two surfaces SC-006 bounds: inbox request to list rendered, and detail request to itemized Food Cost rendered, each with 20 pending dishes on a 20 Mbps connection at 50 ms round-trip latency. SC-006 now names its start point, end point, load, network conditions and percentile, so record the measured values against those exact conditions and state the verdict — do not substitute a subjective impression of speed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (readiness)** blocks everything. T005, T006 and T007 are decisions owned by other specs
- **Phase 2 (foundational)** blocks all user stories. T009–T012 may proceed while T005–T007 are open, because the calculator is pure; T013–T034 need T001–T004 and T008
- **Phases 3–6 (stories)** depend on Phase 2 and are then mutually independent, with one caveat below
- **Phase 7 (polish)** depends on the stories whose behaviour it exercises

### User Story Dependencies

- **US1** — depends only on Phase 2. The MVP
- **US2** — depends on Phase 2. Creates `AdminDishDecisionController` and the price-save path of `DishGovernanceService`, which US3 and US4 then extend
- **US3** — depends on Phase 2; extends `DishGovernanceService` and `AdminDishDecisionController`, so it is sequenced **after US2** at file level even though its behaviour is independent
- **US4** — depends on Phase 2; also extends both, so it follows US2. It is otherwise independent of US1 and US3

```
Phase 1 (readiness) ──► Phase 2 (foundational) ──┬──► US1 ──┐
                                                 │           │
                                                 └──► US2 ──┼──► US3 ──┐
                                                             │          ├──► Phase 7
                                                             └──► US4 ──┘
```

### Blocked on an upstream decision — **none remain as of 2026-10-28**

All four upstream decisions are closed. The dependencies they created survive as ordinary task ordering, not as waits on another owner:

- **T007 (R-004-1)** — satisfied. The authoritative cost is `ingredients.unit_purchase_cost`, named on issue #146 and confirmed in `FoodCostEngine`. **T042**, **T067** and **T071** proceed
- **T006 (R-006-1)** — satisfied. The gate moved to the controller, so **T042** injects `FoodCostAuditService` directly and the degraded path is withdrawn. Two consequences of reading the merged code became **T042a** and **T076a**
- **R-005-4** — withdrawn, so **T024** is unblocked and adds no `@Version`. The conditional `UPDATE` replaces it in **T067** and **T076**
- **T005 (R-005-3)** — settled by a recorded amendment to D1 and now this feature's own work. It still **orders** the work: **T076**, **T015** and **T016** depend on it, because the dev seed writes `status = 'PUBLISHED'` and until the enum admits that constant every read of those five rows through the dish entity fails on the enum mapping. Seeding `PUBLISHED` before the enum accepts it would break spec 003's catalog tests rather than this feature's. T005 is in Phase 1, so this ordering is satisfied by construction

### Parallel Opportunities

- Phase 1: T002, T003 and T004 run together
- Phase 2: the seven exception classes T026–T032 all run together; T017–T022 pair up across the two aggregates
- Phase 3: the four inbox components T050–T053 run together once T049 is red; T055 and T056 run together
- Across stories: once Phase 2 closes, US1 can run in parallel with US2, and US3 with US4 once US2's controller exists
- Phase 7: T099, T100 and T102 run together

### Parallel Example: User Story 1

```bash
# With T049 written and failing, the four inbox components are disjoint files:
#   T050 components/dishreview/ReviewInboxTabs.tsx
#   T051 components/dishreview/ReviewInboxList.tsx
#   T052 components/dishreview/ReviewInboxRow.tsx
#   T053 components/dishreview/InboxPagination.tsx
# And once T054 is failing, two more are disjoint:
#   T055 components/dishreview/FoodCostBreakdownTable.tsx
#   T056 components/dishreview/UnpricedIngredientBadge.tsx
```

### Parallel Example: User Story 2

```bash
# The two test tasks that open this story touch different files, so they go together:
#   T064 dish/controller/AdminDishDecisionControllerTest.java   (pricing portion)
#   T065 dish/service/DishGovernanceServiceTest.java            (price-save portion)
# The backend chain is sequential, because each step feeds the next:
#   T066 (DTO records) -> T067 (price-save transaction) -> T068 (PUT endpoint)
# The frontend pairs may run alongside that chain once T066 fixes the payload shape,
# and they are independent of each other (a hook and a component, different files):
#   T069 + T070  useRetailPriceCalculator test and hook
#   T071 + T072  MarginPriceCalculator test and component
```

### Parallel Example: User Story 3

```bash
# Same shape: the two opening test tasks are disjoint files:
#   T073 dish/service/DishGovernanceServiceTest.java            (gate portion)
#   T074 dish/controller/AdminDishDecisionControllerTest.java   (approval portion)
# The implementation chain T075 -> T076 -> T077 is sequential: the DTO records feed
# the transition, which feeds the endpoint. T078 and T079 are frontend and may start
# as soon as T075 fixes the payload shape.
```

### Parallel Example: User Story 4

```bash
# Three opening test tasks, three different files:
#   T080 dish/service/DishGovernanceServiceTest.java                 (rejection portion)
#   T081 dish/controller/AdminDishDecisionControllerTest.java        (rejection portion)
#   T082 dish/controller/ChefDishReviewFeedbackControllerTest.java   (blind projection)
# Later in the story two frontend pairs are mutually independent, since the
# Administrator dialog and the Chef banner share no file:
#   T090 + T091  RejectionDialog test and component
#   T092 + T093  DishRejectionFeedbackBanner test and component
```

### Parallel Example: Phase 7

```bash
# Three cross-story suites over disjoint files:
#   T099 dish/integration/DishReviewInboxIntegrationTest.java
#   T100 dish/integration/DishApprovalPricingIntegrationTest.java
#   T102 dish/contract/AdminDishApprovalOpenApiContractTest.java
```

---

## Implementation Strategy

**MVP** — Phase 1 + Phase 2 + Phase 3 (US1). Delivers the inbox and the cost audit: the Administrator can discover and inspect submitted dishes. 64 tasks.

**Increment 2** — US2 (pricing). Adds commercial terms and the price audit trail.

**Increment 3** — US3 (publication gate). Closes the P1 set; dishes reach diners.

**Increment 4** — US4 (rejection) plus Phase 7. Completes the feedback loop and the cross-story suites.

---

## Notes

- **Task count**: 106 total — Phase 1: 8, Phase 2: 26, US1: 30, US2: 9, US3: 8, US4: 16, Phase 7: 9. T025 is cancelled and stays numbered so later identifiers do not shift
- **Two deliberate deviations from the one-file-per-task rule**, both stated rather than hidden: DTO records are grouped per endpoint payload (T039, T041, T066, T075, T083, T087), because each record is a few lines and the group is generated from one contract operation; and `DishGovernanceService` and the two controllers are each created in the earliest story that needs them and extended by later stories, because three stories share them and splitting them per story would create competing classes
- **No task is blocked on a decision this feature cannot make.** T006 and T007 became recordings of decisions their owners made in writing (issue #146), T005 became this feature's own work under a recorded amendment to D1, and R-005-4 was withdrawn by replacing the mechanism rather than by obtaining the acknowledgement. Principle VIII was satisfied in each case by changing the written agreement before the code — not by deciding quietly and documenting afterwards
- **No task hardcodes a migration version.** T008 determines it and T014 consumes it; `AdminPricingMigrationTest` carries no version in its name. As of 2026-10-28 the number is **`V8`**, re-confirmed against the migration directory at the moment T014 authors the file, and recorded in `.specify/memory/migrations.md` in the same commit
- **Objects this feature must not touch**: `chk_dishes_status` and `idx_dishes_review_inbox_pending` (owned by spec 005's `V7`), any new object inside `com.easyeat.dish.domain` beyond the fields D1 permits, and catalog unpublishing or discontinuation (a declared non-goal)
- `checklists/approval-pricing.md` holds 59 requirements-quality items, **all 59 marked with their provenance as of 2026-10-28**, so `/speckit-implement` reads a satisfied gate. A marked box records requirements quality, not implementation progress
- **Task count is now 106**: T042a and T076a were added on 2026-10-28 from consequences found while verifying spec 006's merged code, and T025 remains cancelled. Both carry the `Serves` annotation, so the FR→task mapping stays complete; FR-037 is new in `spec.md` and is served by T042a
