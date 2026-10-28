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

**⚠️ BLOCKING — this gate does not pass on the current branch.** `com.easyeat.dish` does not exist on `develop/sprint-2` and spec 005's migration is not integrated. **No task from T009 onward may start until every task in this phase passes.** T005, T006 and T007 are open upstream requirements awaiting a decision from another spec's owner; they cannot be closed unilaterally (Constitution Principle VIII).

- [ ] T001 Verify spec 005's migration `V7__chef_dish_management.sql` is present in `easyeat/backend/src/main/resources/db/migration/` and applies cleanly, and record the outcome against R-005-0
- [ ] T002 [P] **SATISFIED 2026-10-28.** Verify the upstream dish domain exists: `DishStatus`, `DishCategory` and the auxiliary entities in `com.easyeat.dish.domain`, and `DishRepository`. **Note the correction**: there is no `com.easyeat.dish.domain.Dish` and none is coming — spec 005 added its five columns to the pre-existing `com.easyeat.catalog.domain.Dish`, which is the single `@Entity` over `dishes`. Assert that entity carries `status`, `category`, `allergens_confirmed`, `created_by` and `submitted_at` in `easyeat/backend/src/main/java/com/easyeat/dish/`, and record the outcome against R-005-0
- [ ] T003 [P] Verify the `dishes` table carries `status`, `category`, `allergens_confirmed`, `created_by` and `submitted_at`, and that the index `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)` exists, by inspecting `easyeat/backend/src/main/resources/db/migration/V7__chef_dish_management.sql`; record against R-005-0 and R-005-1
- [ ] T004 [P] Verify the tables `dish_recipe_ingredients` and `dish_photos` exist in `easyeat/backend/src/main/resources/db/migration/V7__chef_dish_management.sql`, and record the outcome against R-005-0
- [ ] T005 **BLOCKED — R-005-3 and R-005-4, two ownership decisions required.** Verify that `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishStatus.java` admits `PUBLISHED`. The database already accepts it; the enum declares only `DRAFT` and `PENDING_PRICE`, and D1 assigns that package to spec 005. Two admissible resolutions, both requiring a recorded decision: spec 005 adds the constant, or `DishStatus` becomes jointly owned by an agreed amendment to D1. **Do not edit the file under either reading until the decision is recorded, and do not create a competing status enum in this feature.** **Second decision (R-005-4, added 2026-10-28)**: `@Version` must go on `com.easyeat.catalog.domain.Dish`, the single entity over `dishes`, because spec 005 consolidated onto it instead of creating `dish.domain.Dish`. That changes failure behaviour for spec 003's and spec 005's write paths as well — a concurrent write that used to overwrite silently will raise a conflict — so it needs the owners of those specs to confirm that outcome is acceptable and to agree how each surfaces it. Blocks **T024**.
- [ ] T006 **BLOCKED — R-006-1, decision required from spec 006.** Verify that spec 006's Food Cost computation is reachable for dishes outside `PENDING_PRICE`, by inspecting `easyeat/backend/src/main/java/com/easyeat/dish/service/FoodCostAuditService.java`. If the status gate has not moved to the controller, record the activation of the documented degraded path (this feature consumes the pure `FoodCostEngine` and assembles the audit payload itself) as an explicit, logged decision rather than a discovery
- [ ] T007 **BLOCKED — R-004-1, decision required jointly from specs 004 and 006.** Verify that a single authoritative per-unit acquisition cost has been named in writing. Spec 004's `easyeat/backend/src/main/resources/db/migration/V6__create_ingredient_batches_and_refactor_lifecycle.sql` left the cost in three columns — `ingredients.unit_purchase_cost`, `ingredient_batches.unit_cost` and `stock_audit_entries.unit_cost` — with batches drawn down FIFO, so FR-012's "current acquisition cost" has three referents and three different margins. The recorded rule MUST also state where the reduction from scale 4 to the scale 2 of `dish_price_history.food_cost_snapshot` happens. **This requirement has no safe degraded path: do not select a source on your own initiative.** T042, T056 and T071 depend on it
- [ ] T008 Determine this feature's Flyway version by listing `easyeat/backend/src/main/resources/db/migration/` and taking the lowest free sequential integer strictly greater than every migration present; record the chosen number for T014. **Do not assume a number from any planning document** — the queue as of 2026-10-28 is `V6` present, `V7` pending integration from spec 005, `V8` claimed by spec 008

**Checkpoint**: readiness recorded. T005, T006 and T007 must be resolved by their owners before Phase 2 proceeds past T012.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `plan.md` §Implementation phases 1–2 — the migration, the two append-only audit aggregates, the fields added to the dish entity, the pure pricing calculator and the shared error surface. Shared by all four stories, so no `[US]` label.

**⚠️ CRITICAL**: no user story work may begin until this phase is complete.

### Pricing calculator (pure, Spring-free — independent of R-004-1)

The calculator receives the Food Cost as a parameter, so it does not depend on which column supplies it. Only the services that *obtain* the Food Cost depend on T007.

- [ ] T009 Write `RetailPricingCalculatorTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/RetailPricingCalculatorTest.java` covering margin→price and price→margin, HALF_UP at exactly half a cent, negative margin, margin 0 (`PVP = food_cost`), margin at and beyond 100%, margin ≤ −500%, food cost `0.00`, more than two decimals and large amounts
- [ ] T010 Implement `RetailPricingCalculator` in `easyeat/backend/src/main/java/com/easyeat/dish/service/RetailPricingCalculator.java` as a Spring-free `BigDecimal` component handling no request types, scale 2 for money and scale 4 for ratios, `HALF_UP`
- [ ] T011 Write `MarginRoundTripToleranceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/MarginRoundTripToleranceTest.java` against the bound derived in `research.md §3.1`, **not** FR-008's prose: pin `3.33 @ 60% → 8.33` (0.0240 pp), `1.25 @ 70% → 4.17` (0.0240 pp) and `0.07 @ 60% → 0.18` (1.1111 pp), assert INV-1 unconditionally, and assert ±0.01 pp **only** at or above the threshold
- [ ] T012 Extend `RetailPricingCalculator` in `easyeat/backend/src/main/java/com/easyeat/dish/service/RetailPricingCalculator.java` with the worst-case deviation `marginTolerancePp` and the disclosure threshold `toleranceThresholdPrice` that `contracts/openapi.yaml` §MarginSummary and the amended FR-008 require

### Migration and dev seed

- [ ] T013 Write `AdminPricingMigrationTest` in `easyeat/backend/src/test/java/com/easyeat/dish/migration/AdminPricingMigrationTest.java` against Testcontainers PostgreSQL 16, asserting the migration applies on top of every migration already present, that the `retail_price` backfill copies only strictly positive prices and leaves `NULL`/`0.00` unpriced, and that the new constraints and indexes exist. **Keep the name version-free** so an upstream renumbering cannot invalidate it
- [ ] T014 Create the feature migration at `easyeat/backend/src/main/resources/db/migration/<version from T008>__admin_dish_approval_pricing.sql` per `data-model.md §3`: `dishes.retail_price`, `dishes.version`, `dishes.published_at`, the `retail_price` backfill, the `dish_review_history` and `dish_price_history` tables, their indexes, and `idx_dishes_review_inbox_published`. **Do not declare `idx_dishes_review_inbox_pending`** (owned by spec 005's `V7`) and **do not drop or replace `chk_dishes_status`** (owned by spec 005, already widened)
- [ ] T015 **Depends on T005 (R-005-3).** Update the dev seed `easyeat/backend/src/main/resources/db/dev/R__seed_dev_dishes_and_dietary.sql` per `research.md §3.6` (D5): the five seeded dishes land `status = 'PUBLISHED'`, `is_active = TRUE`, `allergens_confirmed = TRUE`, `retail_price` backfilled from `price`, `version = 0`, `published_at = NOW()`, plus one synthetic `APPROVED` review row each marked `[SEED SINTÉTICO]`
- [ ] T016 **Depends on T005 (R-005-3).** Extend `DevSeedDishesMigrationTest` in `easyeat/backend/src/test/java/com/easyeat/profile/migration/DevSeedDishesMigrationTest.java` with the new assertions, leaving every existing assertion unchanged

### Audit aggregates (append-only)

- [ ] T017 [P] Create the `ReviewEventType` enum (`APPROVED`, `REJECTED`) in `easyeat/backend/src/main/java/com/easyeat/dish/domain/ReviewEventType.java`
- [ ] T018 [P] Write `DishReviewHistoryRepositoryTest` in `easyeat/backend/src/test/java/com/easyeat/dish/repository/DishReviewHistoryRepositoryTest.java` asserting the append-only shape required by FR-034 and FR-035: save and query are exposed, **no** delete and **no** `@Modifying` update exist anywhere on the interface, and the `uq_drh_dish_cycle` uniqueness holds. The absence of those operations is the observable — assert that the interface does not declare them, not merely that calling them fails
- [ ] T019 [P] Create the immutable `DishReviewHistory` entity in `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishReviewHistory.java` with no setters and no `@PreUpdate`, per `data-model.md §2`
- [ ] T020 Create `DishReviewHistoryRepository` in `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishReviewHistoryRepository.java` exposing save and queries only
- [ ] T021 [P] Write `DishPriceHistoryRepositoryTest` in `easyeat/backend/src/test/java/com/easyeat/dish/repository/DishPriceHistoryRepositoryTest.java` asserting the same append-only shape required by FR-034 and FR-035 — no delete and no `@Modifying` update declared — plus chronological retrieval by creation timestamp ascending as SC-005 now specifies
- [ ] T022 [P] Create the immutable `DishPriceHistory` entity in `easyeat/backend/src/main/java/com/easyeat/dish/domain/DishPriceHistory.java` with no setters and no `@PreUpdate`, per `data-model.md §2`
- [ ] T023 Create `DishPriceHistoryRepository` in `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishPriceHistoryRepository.java` exposing save and queries only

### Entity fields and the immutable catalog mapping

- [ ] T024 **Depends on T005 (R-005-4).** Add `retailPrice`, `publishedAt` and `@Version version` to `easyeat/backend/src/main/java/com/easyeat/catalog/domain/Dish.java` — the single JPA entity over `dishes`, not a back-office-only one, because spec 005 consolidated onto it rather than creating `dish.domain.Dish`. Incremental work on an existing class, permitted by D1 for the two columns; **`@Version` is different** — it changes failure behaviour for spec 003's and spec 005's write paths too, so it requires the acknowledgement R-005-4 records. Do not alter the fields spec 005 owns
- [ ] T025 **CANCELLED 2026-10-28 — D3 withdrawn.** This task was to mark `catalog/domain/Dish.java` `@Immutable` so that no future write could bypass `@Version`. It is now impossible: that class is the only entity over `dishes` and spec 005 writes `submitted_at` through it, so annotating it would break spec 005's submit path. The guarantee is replaced by a review-enforced invariant recorded in `plan.md` Complexity Tracking: any future write path added to `catalog/repository/DishRepository` must carry the version predicate. **Do not implement this task and do not substitute a second entity for it**

### Shared error surface

- [ ] T026 [P] Create `DishStateConflictException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/DishStateConflictException.java`
- [ ] T027 [P] Create `DishAlreadyDecidedException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/DishAlreadyDecidedException.java`
- [ ] T028 [P] Create `StaleDishVersionException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/StaleDishVersionException.java`
- [ ] T029 [P] Create `PublicationGateException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/PublicationGateException.java` carrying the unmet conditions
- [ ] T030 [P] Create `AcknowledgementRequiredException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/AcknowledgementRequiredException.java` carrying the acknowledgement kinds
- [ ] T031 [P] Create `InvalidRetailPriceException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/InvalidRetailPriceException.java`
- [ ] T032 [P] Create `InvalidRejectionCommentException` in `easyeat/backend/src/main/java/com/easyeat/dish/exception/InvalidRejectionCommentException.java`
- [ ] T033 Write the handler-mapping tests in `easyeat/backend/src/test/java/com/easyeat/dish/controller/GlobalExceptionHandlerDishMappingTest.java` asserting 409 for state conflict and stale version, 422 `publication-gate-failed` with one entry per unmet condition, 428-style acknowledgement signalling per the contract, and 400 for the two validation exceptions
- [ ] T034 Extend the single `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` with the seven handlers including `ObjectOptimisticLockingFailureException` → 409. **Do not add a competing `@RestControllerAdvice`**

**Checkpoint**: foundation ready — user story implementation can begin. T042, T056 and T071 remain blocked on T007.

---

## Phase 3: User Story 1 — Administrative Inbox of Dishes Pending Review (Priority: P1) 🎯 MVP

**Goal**: the Administrator discovers submitted dishes in a segmented inbox and opens one to see its culinary file and itemized Food Cost audit.

**Independent Test**: submit several drafts from kitchen accounts, open the Back-Office inbox, assert the three segmented views, FIFO ordering, the pending badge count and 20-per-page pagination, then open a pending dish and assert the composition and cost audit render.

### Tests for User Story 1

- [ ] T035 [US1] Write `DishReviewInboxServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishReviewInboxServiceTest.java` covering tab filtering, FIFO ordering by `submitted_at ASC`, resubmission derivation, and a query-count assertion proving 2 queries per page regardless of page size — **Serves**: FR-002, FR-003, FR-004
- [ ] T036 [P] [US1] Write the inbox and detail portions of `AdminDishReviewControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishReviewControllerTest.java`: the full RBAC matrix (ADMINISTRADOR allowed, ADMIN alias allowed, CHEF 403, ENCARGADO 403, COMENSAL 403, anonymous 401), cross-tenant access denied with 403, pagination beyond the last page returning an empty list with accurate metadata, the per-view empty state (FR-028), the out-of-range page result with accurate metadata (FR-036), and that a dish whose author Chef has been deactivated still appears in the inbox with its author name rendered (FR-026) — **Serves**: FR-001, FR-005, FR-026, FR-027, FR-028, SC-003

### Implementation for User Story 1

- [ ] T037 [US1] Add the inbox queries to `easyeat/backend/src/main/java/com/easyeat/dish/repository/DishRepository.java` — tenant-scoped paging by `(tenant_id, status)` with `submitted_at ASC` for pending and `published_at DESC` for published; incremental work on a 005-owned interface, permitted by D1 — **Serves**: FR-002, FR-003
- [ ] T038 [US1] Implement `DishReviewInboxService` in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishReviewInboxService.java` — three tabs, FIFO, 20-per-page default, pending count badge, and the initial-versus-resubmission indicator in 2 queries per page — **Serves**: FR-002, FR-003, FR-004
- [ ] T039 [US1] Create the inbox response records `DishReviewInboxItemResponse` and `DishReviewInboxPageResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-003, FR-004
- [ ] T040 [US1] Write `DishReviewDetailServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishReviewDetailServiceTest.java` covering the embedded spec 006 audit, the publication-gate status projection and the recomputed margin — **Serves**: FR-006, FR-012, FR-015
- [ ] T041 [US1] Create the detail response records `DishReviewDetailResponse`, `MarginSummary` and `PublicationGateStatus` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-006, FR-008, FR-012
- [ ] T042 [US1] **Depends on T007 (R-004-1).** Implement `DishReviewDetailService` in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishReviewDetailService.java` — identity, labels, the itemized Food Cost audit from spec 006, gate evaluation and the margin recomputed on consultation. The Food Cost source must be the one recorded in T007; do not choose one here — **Serves**: FR-006, FR-012
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

- [ ] T064 [US2] Write the pricing portion of `AdminDishDecisionControllerTest` in `easyeat/backend/src/test/java/com/easyeat/dish/controller/AdminDishDecisionControllerTest.java`: price validation at 3 decimals, 0 and negative; missing `expectedVersion` → 400; the negative-margin confirmation requirement; repricing a `PUBLISHED` dish; and the full RBAC and cross-tenant matrix — **Serves**: FR-009, FR-013, FR-014, SC-003
- [ ] T065 [P] [US2] Write the price-save portion of `DishGovernanceServiceTest` in `easyeat/backend/src/test/java/com/easyeat/dish/service/DishGovernanceServiceTest.java`: the audit row contents, that saving is permitted in both `PENDING_PRICE` and `PUBLISHED`, and that a `PUBLISHED` dish keeps its status, recipe and visibility — **Serves**: FR-009, FR-010, FR-011, SC-008

### Implementation for User Story 2

- [ ] T066 [US2] Create the pricing records `SetRetailPriceRequest` and `DishPricingResponse` in `easyeat/backend/src/main/java/com/easyeat/dish/dto/`, mirroring `contracts/openapi.yaml` — **Serves**: FR-007, FR-008, FR-013
- [ ] T067 [US2] **Depends on T007 (R-004-1).** Implement the price-save transaction in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishGovernanceService.java` — validate `PVP > 0` with at most two decimals, persist the price, write the immutable `dish_price_history` row capturing actor, previous price, new price and the prevailing Food Cost snapshot, and enforce optimistic concurrency via `expectedVersion` — **Serves**: FR-009, FR-010, FR-011, FR-025, SC-008
- [ ] T068 [US2] Implement `PUT /admin/dishes/{id}/retail-price` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishDecisionController.java` — **Serves**: FR-009, FR-013
- [ ] T069 [US2] Write `easyeat/frontend/tests/hooks/useRetailPriceCalculator.test.ts` covering bidirectional derivation, formatting, disabled states and the boundaries at and beyond 100%, at −500%, at margin 0 and at Food Cost `0.00` — **Serves**: FR-007, FR-008, FR-030, SC-002
- [ ] T070 [US2] Implement `easyeat/frontend/src/hooks/useRetailPriceCalculator.ts` as a pure, unit-testable derivation with its formatting rules — **Serves**: FR-007, FR-030
- [ ] T071 [US2] Write `easyeat/frontend/tests/components/dishreview/MarginPriceCalculator.test.tsx` covering reactive bidirectional updates, the negative-margin advisory requiring confirmation before save, the disabled margin→price derivation when the Food Cost is `0.00`, and the disclosure of the achievable deviation below the precision threshold — **Serves**: FR-007, FR-014, FR-030, SC-002
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
- [ ] T076 [US3] Implement the approval transition in `easyeat/backend/src/main/java/com/easyeat/dish/service/DishGovernanceService.java` — evaluate the three gate conditions at the instant of the action, require acknowledgement where advisories apply, then atomically set `status = 'PUBLISHED'`, `published_at` and `is_active = TRUE` (per `research.md §3.9`) and write the approval audit row — **Serves**: FR-016, FR-033, SC-001
- [ ] T077 [US3] Implement `POST /admin/dishes/{id}/approval` in `easyeat/backend/src/main/java/com/easyeat/dish/controller/AdminDishDecisionController.java` — **Serves**: FR-016, FR-018, FR-019
- [ ] T078 [US3] Write `easyeat/frontend/tests/components/dishreview/PublicationConfirmModal.test.tsx` covering the advisory content, the required second confirmation, and that cancelling leaves the dish untouched — **Serves**: FR-017, FR-031
- [ ] T079 [US3] Implement `easyeat/frontend/src/components/dishreview/PublicationConfirmModal.tsx` — unpriced-ingredient and negative-margin advisories with an explicit second confirmation (FR-017) — **Serves**: FR-017, FR-031

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

### Blocked on an upstream decision

- **T007 (R-004-1)** blocks **T042**, **T067** and **T071** — every task that obtains or discloses a Food Cost
- **T005 (R-005-4)** blocks **T024**: `@Version` lands on an entity shared with specs 003 and 005, so the behavioural change needs their acknowledgement
- **T005 (R-005-3)** blocks the approval transition **T076**, because `PUBLISHED` cannot be assigned until the enum admits it. It also blocks **T015** and **T016**: the dev seed writes `status = 'PUBLISHED'` into `dishes`, and until the enum admits that constant every read of those five rows through the dish entity fails on the enum mapping. Seeding `PUBLISHED` before the enum accepts it would break spec 003's catalog tests rather than this feature's
- **T006 (R-006-1)** shapes **T042**: consuming spec 006's audit endpoint or assembling the payload locally

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

**MVP** — Phase 1 + Phase 2 + Phase 3 (US1). Delivers the inbox and the cost audit: the Administrator can discover and inspect submitted dishes. 63 tasks.

**Increment 2** — US2 (pricing). Adds commercial terms and the price audit trail.

**Increment 3** — US3 (publication gate). Closes the P1 set; dishes reach diners.

**Increment 4** — US4 (rejection) plus Phase 7. Completes the feedback loop and the cross-story suites.

---

## Notes

- **Task count**: 104 total — Phase 1: 8, Phase 2: 26, US1: 29, US2: 9, US3: 7, US4: 16, Phase 7: 9
- **Two deliberate deviations from the one-file-per-task rule**, both stated rather than hidden: DTO records are grouped per endpoint payload (T039, T041, T066, T075, T083, T087), because each record is a few lines and the group is generated from one contract operation; and `DishGovernanceService` and the two controllers are each created in the earliest story that needs them and extended by later stories, because three stories share them and splitting them per story would create competing classes
- **Three tasks are blocked on decisions this feature cannot make** (T005, T006, T007). None of them may be closed by choosing a resolution unilaterally — Principle VIII requires the decision to be recorded by its owner
- **No task hardcodes a migration version.** T008 determines it and T014 consumes it; `AdminPricingMigrationTest` carries no version in its name
- **Objects this feature must not touch**: `chk_dishes_status` and `idx_dishes_review_inbox_pending` (owned by spec 005's `V7`), any new object inside `com.easyeat.dish.domain` beyond the fields D1 permits, and catalog unpublishing or discontinuation (a declared non-goal)
- `checklists/approval-pricing.md` holds 59 unchecked requirements-quality items. `/speckit-implement` reads checklist state as a gate and will halt for confirmation while any remain unchecked
