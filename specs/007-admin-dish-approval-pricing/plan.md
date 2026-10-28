# Implementation Plan: Administrative Back-Office for Dish Audit, Retail Pricing (PVP) and Catalog Approval

**Branch**: `007-admin-dish-approval-pricing` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-admin-dish-approval-pricing/spec.md`

**Companion artifacts**: [research.md](./research.md) · [data-model.md](./data-model.md) · [contracts/openapi.yaml](./contracts/openapi.yaml) · [quickstart.md](./quickstart.md)

## Summary

Deliver the Administrator's catalog-governance Back-Office as a full vertical slice: a segmented
review inbox (pending FIFO, published, history) with a pending badge and 20-per-page navigation; a
per-dish audit panel rendering spec 006's itemized Food Cost; a bidirectional margin ↔ PVP
calculator; persistence of the retail price with an immutable price audit trail; an approval action
gated on price, recipe and allergens that publishes the dish to the diner catalog; and rejection with
a mandatory 10–1000 character comment that returns the dish to the Chef with traceable, financially
blind feedback.

Technical approach: extend — never duplicate — what already exists. The PVP becomes a new
`dishes.retail_price` owned by this feature; `dishes.price` is deprecated and the diner catalog
migrates to `retail_price` in the same change that adopts the visibility predicate
`is_active = true AND status = 'PUBLISHED'`. First-decision-wins is delivered by a status-machine
guard **plus** the project's first `@Version` column, each covering a different race. The margin is
never persisted: it is recomputed from the live Food Cost on every consultation. All arithmetic is
`BigDecimal`/`HALF_UP`, and the round-trip tolerance of FR-008 is replaced by the bound this plan
derives, because ±0.01 pp is not achievable for low prices.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.6 (frontend)

**Primary Dependencies**: Spring Boot 3.3.x (Web, Data JPA, Security, Validation), Flyway, PostgreSQL 16 driver, Lombok · React 18.3.1 (**not** React 19, which `005/plan.md` claims — `package.json` is authoritative), Vite 5, react-router-dom 6, Tailwind CSS 3, axios 1.7, lucide-react

**Storage**: PostgreSQL 16; Flyway versioned migrations. This feature owns exactly one migration, named for the next free sequential version **at the moment the file is authored**, confirmed against `db/migration/` and never reserved in advance. Current queue: `V6` inventory batches (merged to `main`), `V7` spec 005 (merged into `005-chef-dish-management`), `V8` claimed by spec 008 → this feature takes **`V9__admin_dish_approval_pricing.sql`** unless the queue has moved again. See [research.md §2(b)](./research.md)

**Testing**: JUnit 5 + AssertJ + Mockito, Spring MockMvc slice tests, Testcontainers PostgreSQL 16 · Vitest 2 + @testing-library/react + jsdom

**Target Platform**: Linux server (Spring Boot JAR) + modern browsers (SPA)

**Project Type**: Full-stack web application in a monorepo (`easyeat/backend`, `easyeat/frontend`) — vertical slice per Principle X

**Performance Goals**: inbox and audit panel perceived within 2 s on standard broadband (SC-006); the resubmission indicator resolved in 2 queries per page regardless of page size (no N+1)

**Constraints**: exact decimal arithmetic for every monetary and margin value (scale 2 money, scale 4 quantities/unit costs, `HALF_UP`); no financial datum reachable through any Chef-facing payload (FR-023); strict tenant isolation on every query; one `@RestControllerAdvice` in the backend — extend `com.easyeat.auth.controller.GlobalExceptionHandler`, never add a competing advice

**Scale/Scope**: 33 functional requirements (26 original plus FR-027 to FR-033, added 2026-10-28 to bring the interface behaviour and the diner-visibility condition under the specification's authority), 4 user stories, 7 HTTP endpoints, 2 new tables, 3 new columns, 1 migration, ~10 new frontend components across 1 new admin route plus 1 Chef-side banner

**Sequencing — stated plainly**: spec 005 has delivered `V7__chef_dish_management.sql` and the dish
domain on `origin/005-chef-dish-management` (issue #137, PR #138, merged 2026-10-27), but that branch
is **not yet integrated into `develop/sprint-2`** and no pull request is open to move it; spec 006 has
produced **no code**. This feature is therefore **not implementable** until 005's branch and 006's cost
engine land on `develop/sprint-2`. That is a sequencing dependency, not a Constitution Check failure
and not an unresolved clarification: design proceeds now, implementation waits. Every object needed
from upstream is a numbered requirement below.

## Constitution Check

*GATE: evaluated before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | Pre-design | Post-design | Evidence |
|---|---|---|---|
| **I. Specification is the source of truth** | PASS | PASS | Every FR traced in [data-model.md §7](./data-model.md). Three places where spec and code disagree were resolved explicitly, not silently: FR-008/SC-002's unachievable tolerance, FR-001's non-existent `COUNTER` role, and validation status 400-vs-422 — each recorded as an amendment in [research.md §3](./research.md) to be carried back into `spec.md` before `/speckit-tasks`. |
| **II. Specification before implementation** | PASS | PASS | `spec.md` approved with 0 `[NEEDS CLARIFICATION]`; this plan plus 4 design artifacts precede any code; `tasks.md` deliberately not generated by this command. |
| **III. Layered verification proportional to risk** | PASS | PASS | Four layers named with concrete classes and paths (§Testing strategy): pure unit tests for the money component, MockMvc slices for RBAC/state gates/validation boundaries, Testcontainers integration for the migration, backfill, FIFO, audit rows and tenant isolation, a dedicated concurrency test for first-decision-wins, and Vitest behaviour tests. Boundary cases (9/10/1000/1001 chars, 3 decimals, margin 0/100/−500, food cost 0.00, half-cent rounding) are explicit tests, not happy paths. |
| **IV. Simple, cohesive, justified design** | PASS | PASS | No new framework, no new advice, no new pattern. Reuses `@PageableDefault` + record page DTO, `TenantContextHolder`, `ProblemDetail`, the `src/api`/`src/types`/`src/components/<domain>` layout. Each abstraction justified: `RetailPricingCalculator` is Spring-free so it can be exhausted by pure unit tests; `@Immutable` on the catalog entity is structural enforcement, not decoration; a DB trigger for audit immutability was considered and rejected as over-engineering ([data-model.md §3.3](./data-model.md)). |
| **V. Bounded scope** | PASS | PASS | Out of scope and not designed: submission notification (005/CHF-2.2.4), unpublish/pause (Sprint 3), promotions and plato del día, taxes, multi-currency, payments, orders, and diner catalog rendering beyond D4's predicate and D2's `retail_price` read. The two touches outside this feature's own files (catalog read path, dev seed) are each mandated by D2/D4/D5 and named as MODIFIED in the source tree. |
| **VI. Controlled technical debt** | PASS | PASS | Three debt items registered with owners in Complexity Tracking: the deprecated `dishes.price` column (removal owned by the Sprint 3 catalog-lifecycle feature), the degraded FIFO fallback if R-005-1 never arrives, and duplicated audit-DTO assembly if R-006-1 is refused. None blocks delivery; each is written down rather than discovered later. |
| **VII. Traceability, consistency, documentation** | PASS | PASS | FR → artifact matrix in data-model.md; every cross-artifact contradiction found is recorded instead of resolved unilaterally: specs 005 (seed intent, React 19, recipe entity target, status domain), 006 (state gate), 008 (`ACTIVE` vs `PUBLISHED`, `price` vs `retail_price`). Amendments to `spec.md` are named precisely. |
| **VIII. Evidence over assumption, human escalation** | PASS | PASS | Every baseline claim verified by command with the result recorded ([research.md §1](./research.md)); the 005/006 absence verified across all branches; the margin bound derived symbolically **and** checked with exact decimal arithmetic over 2.6M cases rather than asserted from the spec's two convenient examples. The one apparently escalation-worthy conflict (008's `ACTIVE` vs this feature's `PUBLISHED`) proved decidable from 008's own deferral clause, so it is recorded, not escalated. |
| **IX. Engineering over convenience** | PASS | PASS | The convenient paths were all declined: restating ±0.01 pp as a guarantee, reusing `price` as the PVP, trusting `@Version` alone for first-decision-wins, and letting FR-023 rest on "the UI does not render it" instead of structural omission in the response type. |
| **X. Full-stack vertical slice** | PASS | PASS | Backend contracts and the Administrator Back-Office UI are specified together, plus the Chef-side feedback banner; `spec.md`'s first clarification records the full-stack scope decision. No single-tier delivery. |
| **XI. Upstream synchronization awareness** | PASS (with alert) | PASS (with alert) | **As of 2026-10-27** (the 2026-10-26 measurement below is superseded; any later reader must re-measure before trusting this row). `git fetch origin` executed 2026-10-27: `HEAD c3536e0`; the sprint's integration branch is `origin/develop/sprint-2`, against which this branch is **33 commits behind and 0 ahead** — every local commit is already integrated. `origin/develop/sprint-2` is itself 0 behind and 22 ahead of `origin/main`. A staleness alert was raised; no `pull`/`merge`/`rebase` was run (the decision is the developer's). **Impact assessed, and this time the baseline did move**: `V6__create_ingredient_batches_and_refactor_lifecycle.sql` (spec 004, PR #126) is now on both branches and consumed the version this plan had attributed to spec 005, which forced the renumbering recorded in [research.md §2(b)](./research.md). `Ingredient.unitPurchaseCost` was verified unchanged (`nullable = false`, scale 4, `HALF_UP`), so the Food Cost premises hold. Prior measurement, kept for the record: 2026-10-26, `HEAD d89cf47`, `origin/main c29728e`, 12 behind / 17 ahead. |

**Gate result**: PASS pre-design and post-design. No principle is violated; the single deliberate
boundary exception (replacing 005's `chk_dishes_status` if R-005-2 is refused) is recorded in
Complexity Tracking and requires human approval before implementation.

## Upstream Requirements

Per D1 this feature owns none of specs 005/006's objects. Requirements are numbered R-005-0 upward in dependency order; each states the object,
its owner, why this feature cannot proceed without it, and the degraded behaviour if it never
arrives. Each is mirrored as a decision entry in [research.md](./research.md).

### R-005-0 — The dish domain and migration `V7` (blanket precondition) — **SATISFIED 2026-10-28**

**Object**: `V7__chef_dish_management.sql` (`dishes.status`, `category`, `allergens_confirmed`,
`created_by`, `submitted_at`, `dish_recipe_ingredients`, `dish_photos`), the dish status and category
enums in `com.easyeat.dish.domain`, the `Dish` entity extended with those columns, `DishRepository`,
and the `DRAFT → PENDING_PRICE` submit transition.
**Owner**: spec 005. **Why blocking**: this feature's entire surface reads `status`, `category`,
`allergens_confirmed` and `created_by`, and its whole purpose is to decide dishes that arrive in
`PENDING_PRICE`. **Degraded behaviour if never delivered**: none is possible — the feature cannot be
implemented at all. Creating these objects here is forbidden by D1 and would produce a second owner
of `dishes`.
**Status (2026-10-28)**: **satisfied on `develop/sprint-2`** via PR #160. `V7` is present with all
five columns and the `idx_dishes_review_inbox_pending` index; `dish_recipe_ingredients` and
`dish_photos` exist; `com.easyeat.dish.domain` holds `DishStatus`, `DishCategory`, `DishPhoto`,
`DishRecipeIngredient` and their converters; and `DishService` assigns `submitted_at` on the submit
transition, which also closes the residual verification dependency of R-005-1.

**Correction to this requirement's object.** It previously named `com.easyeat.dish.domain.Dish` as a
deliverable. **No such class exists and none is coming.** Spec 005 added the five columns to the
pre-existing `com.easyeat.catalog.domain.Dish` instead of introducing a second entity. There is one
entity over `dishes`, in `catalog.domain`, written by `DishService`, `DishPhotoService`,
`CatalogComplianceService` and `DishRepository`. This invalidates decision **D3** and reshapes
**R-006-2**; both are revised below, and **R-005-4** records the consequence this feature cannot
decide alone.

### R-005-4 — Agreement to place `@Version` on the shared `Dish` entity

**Object**: `@Version` on `com.easyeat.catalog.domain.Dish`, the single JPA entity over `dishes`.
**Owner**: shared. The entity belongs to spec 003 and spec 005 writes through it; this feature needs
optimistic locking on it for FR-025.
**Why it needs agreement**: adding `@Version` to a shared entity changes failure behaviour for
**every** writer, not only for this feature. A concurrent write that previously overwrote silently
will now raise a conflict in spec 003's catalog path and in spec 005's submit path. That is the
correct behaviour and it is strictly safer than the two-entity split D3 proposed — under D3 only the
back-office path was guarded — but it is a behavioural change to code this feature does not own, so
it is a D1 boundary crossing and needs the same explicit acknowledgement as R-005-3.
**Degraded behaviour**: none acceptable. Without `@Version` on that entity, FR-025 cannot be
satisfied at all, and the alternative — a second entity — is the one spec 005 has already foreclosed.
**What to ask**: confirmation from the owners of specs 003 and 005 that a version conflict is an
acceptable outcome on their write paths, plus agreement on how each surfaces it.

### R-005-1 — `dishes.submitted_at` — **RESOLVED IN SCHEMA**

**Object**: `submitted_at TIMESTAMPTZ NULL`, written to `NOW()` by 005's
`POST /api/v1/dishes/{id}/submit-review` on **every** `DRAFT → PENDING_PRICE` transition, including
resubmissions. Read-only here.
**Owner**: spec 005 (it owns the transition that produces the instant). **Why blocking**: FR-003
requires FIFO by submission timestamp, and `dish_review_history` cannot substitute because a
first-time submission leaves no row in it.
**Status (2026-10-27)**: satisfied at the schema level. 005's `V7` creates `submitted_at` and the
covering index `idx_dishes_review_inbox_pending (tenant_id, status, submitted_at)` (issue #137,
PR #138). FR-003 therefore orders by `submitted_at ASC` against that index, and **the degraded
`created_at ASC` / `updated_at ASC` orderings previously recorded here are withdrawn** — no fallback
is needed and none is authorised.
**Residual dependency**: the write itself belongs to 005's US4 (issue #133, open). Until it lands the
column is `NULL` on every row, so FR-003's ordering cannot be verified end to end even though the
schema supports it.

### R-005-2 — `PUBLISHED` admitted by the status domain — **RESOLVED IN SCHEMA**

**Object**: 005's `CONSTRAINT chk_dishes_status CHECK (status IN ('DRAFT','PENDING_PRICE'))` widened
to include `'PUBLISHED'`.
**Owner**: spec 005. **Why blocking**: the approval transition cannot persist `PUBLISHED` against
that constraint, and Flyway forbids editing an applied migration.
**Status (2026-10-27)**: satisfied. 005's `V7` declares
`CHECK (status IN ('DRAFT','PENDING_PRICE','PUBLISHED'))` (issue #137, PR #138). The deliberate D1
exception that would have had this feature drop and replace a 005-owned constraint is **withdrawn**
and removed from Complexity Tracking. The column's type, nullability and default are untouched.

### R-005-3 — `PUBLISHED` admitted by the `DishStatus` enum

**Object**: the constant `PUBLISHED` added to `com.easyeat.dish.domain.DishStatus`, which currently
declares only `DRAFT` and `PENDING_PRICE`.
**Owner**: spec 005 owns that package per R-005-0, and D1 forbids this feature from creating or
owning objects inside it. **Why blocking**: the database now accepts `PUBLISHED` but the application
enum cannot represent it, so the approval transition has no value to assign.
**Degraded behaviour**: none is acceptable. Two admissible paths, and the choice is a human decision:
005 adds the constant, or `DishStatus` is declared jointly owned and this feature extends it. Spec 005
has asked this feature to extend it; D1 as written does not permit that, so the decision **must be
recorded as an agreed amendment to D1 before implementation** (Principle VIII) rather than settled
inside a commit.

### R-004-1 — A single authoritative source for the per-unit acquisition cost

**Object**: a stated rule naming which value is *the* per-unit acquisition cost that the Food Cost
consumes. Spec 004's inventory refactor (`V6__create_ingredient_batches_and_refactor_lifecycle.sql`,
PR #126) left the cost recorded in three places: `ingredients.unit_purchase_cost NUMERIC(12,4) NOT NULL`
(V4, untouched), `ingredient_batches.unit_cost NUMERIC(12,4) NOT NULL` (per purchase batch), and
`stock_audit_entries.unit_cost` alongside `financial_loss`. The index
`idx_ingredient_batches_active (tenant_id, ingredient_id, remaining_quantity, created_at ASC)` shows
batches are consumed FIFO, so batches of the same ingredient may legitimately hold different costs.
**Owner**: spec 004 owns the three columns; spec 006 owns the Food Cost computation that must choose
among them. The decision is theirs jointly, not this feature's.
**Why blocking**: FR-012 requires the margin to be recomputed "using the current Food Cost derived
from up-to-date inventory acquisition costs", and with three candidate sources that phrase has no
single referent — the master ingredient's cost, the oldest remaining batch under FIFO, or the
weighted average of batches with remaining quantity are three different numbers and three different
margins. The same ambiguity changes the meaning of `has_unpriced_ingredients`, which is detected
today as a zero cost on the master ingredient: an ingredient may now carry a zero master cost while
its batches hold real costs, or the reverse.
**Secondary effect on this feature's own schema**: `dish_price_history.food_cost_snapshot` is
`NUMERIC(10,2)` while every cost column above is scale 4, so the chosen rule must also state where
the reduction to scale 2 happens.
**Degraded behaviour**: none is safe. Assuming the master ingredient's cost would make this feature
report margins that contradict the inventory's own valuation of the stock actually consumed, and the
contradiction would surface as a financial discrepancy rather than as a visible error. This requires
a recorded decision before the pricing domain is implemented (Principle VIII).

### R-006-1 — Food Cost computation reachable for published dishes

**Object**: 006's status gate moved out of `FoodCostAuditService` into
`AdminDishCostAuditController`, leaving the service callable for any status.
**Owner**: spec 006. **Why blocking**: FR-011/FR-012 recompute the prevailing margin on consultation
and repricing applies to `PUBLISHED` dishes, but 006 gates its service and endpoint to
`PENDING_PRICE` only.
**Degraded behaviour**: this feature consumes the pure `FoodCostEngine` plus
`DishRecipeIngredientRepository` and assembles the audit payload itself. Cost: duplicated **assembly**
of the audit DTO (one debt item). The cost **calculation** is never duplicated under either path —
006 owns it.

### R-006-2 — Recipe association targets the writable dish entity

**WITHDRAWN 2026-10-28.** This requirement asked spec 006 to point `DishRecipeIngredient`'s
`@ManyToOne` at `com.easyeat.dish.domain.Dish` rather than at the catalog entity, because D3 was
going to make the catalog mapping `@Immutable`. Two things removed the basis for it:

- D3 is withdrawn (see R-005-0 and `data-model.md §1.2`): there is one entity and it is the catalog
  one, so the association was never pointed at the wrong class.
- The class as merged carries no `@ManyToOne` to `Dish` at all. `DishRecipeIngredient` uses an
  `@EmbeddedId` (`DishRecipeIngredientId`), so there is no association to redirect.

No action is required from spec 006 on this point, and nothing is degraded by closing it.

## Project Structure

### Documentation (this feature)

```text
specs/007-admin-dish-approval-pricing/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, amendments, cross-spec conflicts
├── data-model.md        # Phase 1: entities, migration DDL, state machine, FR traceability
├── quickstart.md        # Phase 1: runnable validation guide
├── contracts/
│   └── openapi.yaml     # Phase 1: the 7-endpoint HTTP surface
├── checklists/          # Pre-existing quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks) - NOT created by this command
```

### Backend (`easyeat/backend/src/main/java/com/easyeat/`)

```text
dish/                                           # package created by spec 005 (enums and auxiliary
                                                #   entities only — the Dish entity lives in catalog/)
├── domain/
│   ├── DishStatus.java                         # MODIFIED (005-owned enum): + PUBLISHED (R-005-3, pending)
│   ├── DishReviewHistory.java                  # NEW: immutable review event, no setters, no @PreUpdate
│   ├── DishPriceHistory.java                   # NEW: immutable price change
│   └── ReviewEventType.java                    # NEW: APPROVED, REJECTED
├── repository/
│   ├── DishRepository.java                     # MODIFIED (005-owned): inbox queries by (tenant, status) + orderings
│   ├── DishReviewHistoryRepository.java        # NEW: save + queries only; no delete, no @Modifying update
│   └── DishPriceHistoryRepository.java         # NEW: same append-only shape
├── service/
│   ├── RetailPricingCalculator.java            # NEW: pure BigDecimal component, Spring-free, no request types
│   ├── DishReviewInboxService.java             # NEW: tabs, FIFO, pagination, resubmission derivation (2 queries/page)
│   ├── DishReviewDetailService.java            # NEW: detail + embedded 006 audit + gate evaluation + margin
│   ├── DishGovernanceService.java              # NEW: price save, approve, reject - the three transactional writes
│   └── ChefReviewFeedbackService.java          # NEW: financially blind projection for the Chef
├── controller/
│   ├── AdminDishReviewController.java          # NEW: GET inbox, review-detail, review-history
│   ├── AdminDishDecisionController.java        # NEW: PUT retail-price, POST approval, POST rejection
│   └── ChefDishReviewFeedbackController.java   # NEW: GET /dishes/{id}/review-feedback
├── dto/                                        # NEW: immutable records mirroring contracts/openapi.yaml
└── exception/                                  # NEW: DishStateConflict, DishAlreadyDecided, StaleDishVersion,
                                                #      PublicationGate, AcknowledgementRequired,
                                                #      InvalidRetailPrice, InvalidRejectionComment
auth/controller/GlobalExceptionHandler.java     # MODIFIED: + 7 handlers incl. ObjectOptimisticLockingFailureException -> 409
catalog/domain/Dish.java                        # MODIFIED: the single entity over `dishes`. + retailPrice, + @Version version,
                                                #   + publishedAt. NOT @Immutable — D3 withdrawn, spec 005 writes through it
catalog/repository/DishRepository.java          # MODIFIED: visibility predicate is_active = true AND status = 'PUBLISHED'
catalog/service/CatalogComplianceService.java   # MODIFIED: reads retail_price (D2); JSON field name `price` preserved
catalog/dto/EnrichedDishResponse.java           # REUSED unchanged - wire contract intact for specs 003/008
common/context/TenantContextHolder.java         # REUSED
config/SecurityConfig.java                      # REUSED - /api/v1/admin/** already role-gated; no change needed
```

```text
src/main/resources/
├── db/migration/V9__admin_dish_approval_pricing.sql   # NEW: the single migration this feature owns (version confirmed at authoring time)
└── db/dev/R__seed_dev_dishes_and_dietary.sql          # MODIFIED (D5): PUBLISHED + retail_price + synthetic approval
```

### Frontend (`easyeat/frontend/src/`)

```text
api/dishReviewApi.ts                            # NEW: typed client over the shared apiClient
types/dishReview.ts                             # NEW: inbox, detail, pricing, history, feedback types
pages/AdminDishReviewPage.tsx                   # NEW: /admin/dishes - inbox + detail panel
components/dishreview/                          # NEW domain folder
├── ReviewInboxTabs.tsx                         #   three tabs + pending badge
├── ReviewInboxList.tsx                         #   FIFO list, empty / loading / error states
├── ReviewInboxRow.tsx                          #   name, category, author, submittedAt, status, initial vs resubmission
├── InboxPagination.tsx                         #   20-per-page controls
├── DishAuditPanel.tsx                          #   identity, labels, itemized Food Cost from spec 006
├── FoodCostBreakdownTable.tsx                  #   line items, subtotals, total per portion
├── UnpricedIngredientBadge.tsx                 #   amber advisory
├── MarginPriceCalculator.tsx                   #   bidirectional margin <-> PVP
├── PublicationConfirmModal.tsx                 #   advisories + second confirmation (FR-017)
└── RejectionDialog.tsx                         #   mandatory comment, live 10..1000 counter
components/chef/DishRejectionFeedbackBanner.tsx # NEW: Chef-side feedback (text, timestamp, reviewer only)
hooks/useDishReviewInbox.ts                     # NEW: tab + page state, counts, reconciliation after a stale decision
hooks/useRetailPriceCalculator.ts               # NEW: pure derivation + formatting rules, unit-testable
router/AppRoutes.tsx                            # MODIFIED: /admin/dishes BEFORE the /admin/* catch-all
pages/AdminWorkspacePage.tsx                    # MODIFIED: hub card linking to the inbox with its pending count
pages/KitchenWorkspacePage.tsx                  # MODIFIED: renders the rejection feedback banner on a returned draft
api/apiClient.ts, components/common/WorkspaceLayout.tsx, router/RoleRouteGuard.tsx   # REUSED
```

**Structure Decision**: the existing monorepo layout is kept verbatim. Backend code lands in the
`com.easyeat.dish` package that specs 005/006 establish, so this feature adds no package of its own
and introduces no second dish domain. Frontend code follows the established vertical-slice layout
(`src/api`, `src/types`, `src/components/<domain>`, `src/pages`, `src/hooks`) with a new
`components/dishreview` folder, and the route is registered **before** the `/admin/*` catch-all,
matching how `/admin/staff` and `/admin/inventory` are declared today.

## API surface

| Method & path | Owning FRs | Roles | State gate | Request | Response | Errors |
|---|---|---|---|---|---|---|
| `GET /api/v1/admin/dishes/review-inbox` | FR-002..FR-005 | ADMINISTRADOR (alias ADMIN) | none; tab filters by status | `tab`, `page`, `size=20` | `DishReviewInboxPageResponse` (+ `pendingCount` on every tab) | 400 bad tab, 401, 403 |
| `GET /api/v1/admin/dishes/{id}/review-detail` | FR-006, FR-007, FR-012, FR-015, FR-017 | ADMINISTRADOR | `PENDING_PRICE` or `PUBLISHED` | — | `DishReviewDetailResponse` (embeds 006's audit, `margin`, `publicationGate`, `version`) | 401, 403, 404, 409 DRAFT |
| `PUT /api/v1/admin/dishes/{id}/retail-price` | FR-009..FR-011, FR-013, FR-014, FR-025 | ADMINISTRADOR | `PENDING_PRICE` or `PUBLISHED` | `retailPrice`, **`expectedVersion` (required)**, `acknowledgeNegativeMargin` | `DishPricingResponse` | 400 price/version, 401, 403, 404, 409 (invalid-state, stale-version), 422 acknowledgement |
| `POST /api/v1/admin/dishes/{id}/approval` | FR-015..FR-019, FR-025 | ADMINISTRADOR | `PENDING_PRICE` → `PUBLISHED`; `PUBLISHED` → 200 no-op; `DRAFT` → 409 | optional `expectedVersion`, 2 acknowledgement flags | `DishApprovalResponse` (`alreadyPublished`) | 401, 403, 404, 409 (invalid-state, already-decided, stale-version), 422 (publication-gate-failed, acknowledgement) |
| `POST /api/v1/admin/dishes/{id}/rejection` | FR-020..FR-022, FR-024, FR-025 | ADMINISTRADOR | `PENDING_PRICE` only | `comment` 10..1000, optional `expectedVersion` | `DishRejectionResponse` (`cycleNumber`) | 400 comment, 401, 403, 404, 409 (invalid-state, already-decided, stale-version) |
| `GET /api/v1/admin/dishes/{id}/review-history` | FR-010, FR-022, SC-005, SC-008 | ADMINISTRADOR | none | — | `DishAuditHistoryResponse` (review events + price changes, chronological) | 401, 403, 404 |
| `GET /api/v1/dishes/{id}/review-feedback` | FR-023, SC-003 | CHEF (own establishment) | none | — | `ChefReviewFeedbackResponse` — **no financial property exists on the type** | 401, 403, 404 |

**Why admin endpoints live under `/api/v1/admin/**`**: `SecurityConfig:78` already enforces
`hasAnyRole("ADMINISTRADOR","ADMIN")` on that prefix at the filter chain, so the controller's
`@PreAuthorize` becomes a second, independent gate. Spec 006 placed its admin-only endpoint at
`/api/v1/dishes/{id}/cost-audit`, where the filter chain applies only `authenticated()` and the method
annotation is the *sole* protection. This feature does not follow that precedent for its own admin
surface and keeps the Chef-facing endpoint on the `/dishes/**` surface that spec 005 owns, where CHEF
authorization is the correct gate.

**Structural financial confidentiality (FR-023, SC-003)**: `ChefReviewFeedbackResponse` and its entry
record declare only `cycleNumber`, `reviewerName`, `comment`, `createdAt`. There is no cost, margin or
price field for a frontend to choose not to render, the service projects straight from
`dish_review_history` without ever loading a price or a Food Cost, and the contract marks both schemas
`additionalProperties: false`. The omission is a property of the type, not a UI decision.

## Concurrency and first-decision-wins (FR-025, US3 scenario 8)

A server-side `@Version` alone does **not** deliver first-decision-wins here. When two
administrators opened the panel minutes apart, each request re-reads the row inside its own
transaction, so two *sequential* read-modify-write transactions both load the current version and
both commit happily. The outcome is produced by two mechanisms, each covering a different race:

- **Approve and reject** — the **status-machine guard** is what delivers it. Both actions are legal
  only from `PENDING_PRICE`, and the guard is evaluated inside the transaction, after the row is
  re-read. Once the first decision commits, the dish is `PUBLISHED` or `DRAFT`, so the second
  administrator's guard fails on current data: a late rejection of a now-published dish gets **409
  `dish-already-decided`** (with `currentStatus` and `decidedAt`, so the UI can say *"otro
  administrador ya decidió este plato"*), and a late approval of a now-`DRAFT` dish gets the same.
  `@Version` still earns its place for the genuinely *overlapping* window, where both transactions
  read `PENDING_PRICE` before either commits: the second `UPDATE` carries `WHERE version = ?`,
  matches zero rows and raises `ObjectOptimisticLockingFailureException` → **409
  `dish-stale-version`**. Approving an already-published dish remains the one benign case: 200
  idempotent, no audit row, no version bump (FR-018).
- **Reprice** — the status guard **cannot** help, because a price save is legal from the dish's
  current state no matter how stale the panel is. Two concurrent saves would otherwise produce a
  classic lost update. Therefore `expectedVersion` is **required** in the request body: a mismatch
  yields **409 `dish-stale-version`** with `currentVersion`, and omitting it yields **400** with the
  error bound to `expectedVersion` — never a silent overwrite. An `If-Match`/`ETag` header was
  considered and rejected: the codebase has no ETag infrastructure, and a body field is visible in
  the OpenAPI schema and consistent with existing request DTOs.

**409 taxonomy** (so the frontend can tell the three apart), all emitted by the extended
`GlobalExceptionHandler` as `ProblemDetail`:
`https://easyeat.com/errors/dish-already-decided` (someone else already decided — reconcile the
list), `…/dish-invalid-state` (wrong state for this action — e.g. approve from `DRAFT`, reject a
`PUBLISHED` dish), `…/dish-stale-version` (stale version — refresh and retry). Publication-gate
failures are **422 `publication-gate-failed`** with `unmetConditions[]`, and missing confirmations
are **422 `acknowledgement-required`** with `requiredAcknowledgements[]`.

## Money and precision

Full treatment in [data-model.md §5](./data-model.md) and [research.md §3.1](./research.md). The
essentials: `BigDecimal`/`HALF_UP`, scale 2 for money and scale 4 for quantities, unit costs and
`margin_pct`; margin on sales as the convention; price → margin exact and margin → price lossy; the
margin never persisted; the server authoritative while the frontend mirrors the formulas for
keystroke feedback. **The ±0.01 pp round-trip tolerance of FR-008/SC-002 is not an unconditional
guarantee.** Rounding the price by up to half a cent perturbs the re-derived margin by
`0.5·food_cost/retail_price²` pp (first-order; the exact bound is
`0.5·food_cost/(p'·(p'−0.005))`), so ±0.01 pp holds only for `retail_price ≥ 50·(1 − margin/100)` —
\$20.00 at a 60% margin, \$15.00 at 70%, \$12.50 at 75%. Verified counter-examples:
`3.33 @ 60% → 8.33 → 60.0240%`, `1.25 @ 70% → 4.17 → 70.0240%`, `0.07 @ 60% → 0.18 → 61.1111%`. The
implementation enforces and tests INV-1 (correctly rounded derivation, unconditional) and INV-2 (the
derived bound, conditional); FR-008 and SC-002 are flagged for amendment.

## Frontend design

**Route and shell**: `/admin/dishes`, wrapped in `<RoleRouteGuard allowedRoles={['ADMINISTRADOR']}>`
inside `<WorkspaceLayout>`, declared before the `/admin/*` catch-all, linked from
`AdminWorkspacePage` with its pending count. Spanish UI copy, matching the existing admin register.

**Segmented inbox**: three tabs with the pending badge fed by `pendingCount`; FIFO list; 20-per-page
controls; per-row name, category, author chef (with a neutral *"cuenta desactivada"* chip when the
account is suspended, and *"Autor no registrado"* when `created_by` is null), submission timestamp,
status, and an "Inicial"/"Reenvío (ciclo N)" marker; distinct empty states per tab; skeleton loading
and a retryable error state.

**Audit and pricing panel**: identity and dietary labels, the itemized Food Cost table rendered from
spec 006's payload, the amber unpriced-ingredient badge, and the bidirectional calculator.

**Calculator interaction contract** (predictability over clever value preservation): the field being
edited is the driver and the other is derived and read-only while typing; the derived field is
formatted to 2 decimals on every recomputation, and the **edited** field is reformatted only on blur,
so a user typing `8.3` is never interrupted mid-keystroke. An empty or partially typed input (`""`,
`"8."`, `"-"`) clears the derived field and shows no error until blur. With Food Cost `0.00` the
margin input is disabled with the explanatory note that the PVP must be entered directly. Margin
`≥ 100.00` or `≤ −500.00` blocks derivation and shows a boundary advisory. When the entered or
derived PVP is below `50·(1 − margin/100)`, the panel discloses the achievable precision — *"a este
precio el margen recalculado puede variar hasta X pp"* using `marginTolerancePp` from the server —
instead of promising ±0.01 pp.

**Confirmations**: a negative margin requires an explicit confirmation before the price is saved
(FR-014); publication opens a modal detailing unpriced-ingredient and negative-margin advisories and
requires a second confirmation (FR-017), sending the acknowledgement flags the server demands.

**Rejection dialog**: mandatory comment, live 10–1000 character counter, whitespace-only treated as
empty, submit disabled while invalid, and the backend's field-bound 400 surfaced on the comment if
validation still fails server-side.

**Chef surface**: `DishRejectionFeedbackBanner` on a returned draft renders only the feedback text,
timestamp and reviewer name — the component receives a type with no financial field, so there is
nothing to leak.

**Error surfacing**: 403 → a non-recoverable permission notice; 400 → the message bound to the
offending field; 409 `dish-stale-version` → *"otro administrador cambió este plato"* plus an
automatic detail refetch; 409 `dish-already-decided` → the row is removed from the pending list and
the list reconciled, so no phantom entry remains; 409 `dish-invalid-state` → the action is disabled
with the reason; 422 `publication-gate-failed` → one actionable line per unmet condition.

## Testing strategy

**Backend — pure unit** (`easyeat/backend/src/test/java/com/easyeat/dish/service/`)
`RetailPricingCalculatorTest` — margin→price and price→margin; HALF_UP at exactly half a cent;
negative margin; margin 0 (`PVP = food_cost`); margin at and beyond 100%; margin ≤ −500%; food cost
`0.00`; more than two decimals; large amounts. The component is Spring-free and handles no request
types, so these are exhaustive and fast.
`MarginRoundTripToleranceTest` — written against the bound this plan derives, **not** FR-008's text.
Pins `3.33 @ 60% → 8.33` (0.0240 pp), `1.25 @ 70% → 4.17` (0.0240 pp), `0.07 @ 60% → 0.18`
(1.1111 pp) as expected behaviour, asserts INV-1 unconditionally, and asserts ±0.01 pp **only** above
the threshold. A suite that asserted ±0.01 pp unconditionally would fail these cases, and one that
omitted them would be hiding the defect.
`DishReviewInboxServiceTest` — tab filtering, FIFO ordering, resubmission derivation, and a query-count
assertion proving 2 queries per page.
`DishGovernanceServiceTest` — publication gate combinations, acknowledgement requirements, audit-row
contents, `cycle_number` computation.
`DishReviewDetailServiceTest` — the embedded 006 audit, the gate-status projection and the margin
recomputed on consultation.

**Backend — repository shape** (`…/dish/repository/`)
`DishReviewHistoryRepositoryTest` and `DishPriceHistoryRepositoryTest` — the append-only contract:
save and queries are exposed, no delete and no `@Modifying` update exist, `uq_drh_dish_cycle` holds,
and price history retrieves in chronological order. Added 2026-10-28: the two aggregates assert their
own immutability rather than relying on the integration suites to notice a violation.

**Backend — error mapping** (`…/dish/controller/`)
`GlobalExceptionHandlerDishMappingTest` — the seven handlers this feature adds, each mapped to the
status and problem type the contract declares. Added 2026-10-28, because the shared advice is the one
object every story's failure path depends on.

**Backend — MockMvc slices** (`…/dish/controller/`)
`AdminDishReviewControllerTest`, `AdminDishDecisionControllerTest`,
`ChefDishReviewFeedbackControllerTest`: the full RBAC matrix (ADMINISTRADOR allowed, ADMIN alias
allowed, CHEF 403, **ENCARGADO** 403, COMENSAL 403, anonymous 401); cross-tenant access; every state
gate (approve from `DRAFT` → 409, reject a `PUBLISHED` dish → 409, approve an already-`PUBLISHED`
dish → 200 idempotent); each publication-gate failure with its own `unmetConditions` entry; rejection
comment validation at 9, 10, 1000 and 1001 characters and whitespace-only; price validation including
3 decimals, 0 and negative; missing `expectedVersion` → 400; and an assertion that the Chef feedback
payload contains no cost, margin or price key.
`…/dish/contract/AdminDishApprovalOpenApiContractTest` — the per-feature contract test, following
`auth/contract/OpenApiContractTest` and `profile/contract/DinerProfileOpenApiContractTest`.

**Backend — Testcontainers PostgreSQL 16** (`…/dish/integration/`, `…/dish/migration/`)
`AdminPricingMigrationTest` — deliberately version-free in its name, so a renumbering upstream does
not invalidate it. Asserts the migration applies cleanly on top of every migration already present
(`V1..V7` as of 2026-10-27, `V8` once spec 008 lands), the
`retail_price` backfill copies only strictly positive prices and leaves `NULL`/`0.00` unpriced, and
the constraints and indexes exist.
`DishApprovalPricingIntegrationTest` — audit rows written for approval, rejection and repricing;
`cycle_number` across repeated reject→resubmit rounds; repricing a published dish leaves status,
recipe and visibility untouched; the margin recomputed after an ingredient's acquisition cost changes.
`DishReviewInboxIntegrationTest` — FIFO ordering, pagination beyond the last page (empty list,
accurate metadata), tenant isolation.
`DishVisibilityPredicateIntegrationTest` — D4's predicate returns only approved dishes, and the diner
payload carries `retail_price`.
`DishDecisionConcurrencyTest` (`…/dish/concurrency/`, following
`inventory/concurrency/StockAdjustmentConcurrencyTest`) — two genuinely competing decisions on one
dish from two threads: exactly one commits, the loser receives a 409, and exactly one audit row
exists. Both the sequential and the overlapping-transaction orderings are exercised, not just the
happy path.
`profile/migration/DevSeedDishesMigrationTest` — **MODIFIED**: existing assertions unchanged, plus the
five seeded rows are `PUBLISHED` with `retail_price = price > 0` and one synthetic `APPROVED` review
row each.

**Frontend — Vitest + React Testing Library** (`easyeat/frontend/tests/`, mirroring `src`)
`tests/hooks/useRetailPriceCalculator.test.ts` (derivation, formatting, disabled states, boundaries),
`tests/hooks/useDishReviewInbox.test.ts` (tab and page state, counts, reconciliation after a stale
decision — FR-032), `tests/pages/AdminWorkspacePage.test.tsx` (the hub card and its pending count) and
`tests/pages/KitchenWorkspacePage.test.tsx` (a returned draft renders the feedback banner and no
financial figure appears) — the last three added 2026-10-28,
`tests/components/dishreview/{ReviewInbox,MarginPriceCalculator,RejectionDialog,PublicationConfirmModal,DishAuditPanel}.test.tsx`,
`tests/components/chef/DishRejectionFeedbackBanner.test.tsx`, `tests/api/dishReviewApi.test.ts`,
`tests/pages/AdminDishReviewPage.test.tsx`, `tests/router/adminDishReviewRoute.test.tsx`. Coverage:
tab switching, FIFO rendering, pagination, per-tab empty states, the bidirectional calculator, the
negative-margin and unpriced-ingredient confirmations, rejection comment validation and submission
blocking, each backend error rendering, and the absence of any financial figure in the Chef feedback
view. Observable behaviour only — no assertions on React internals.

## Implementation phases

*Phases, not tasks: no identifiers, estimates, assignees or per-file checklists.*

**Phase 0 — Upstream readiness gate.** Deliverables: a recorded verification that R-005-0, R-005-1,
R-005-3 and R-006-1 are satisfied, and an explicit decision on each open item otherwise. Exit criteria:
spec 005's branch is integrated into `develop/sprint-2` so that `V7` applies and
`com.easyeat.catalog.domain.Dish` carries the five upstream columns, `DishStatus` and
`dish_recipe_ingredients` are on the working branch;
`DishStatus` admits `PUBLISHED` under an agreed owner (R-005-3); the cost engine is reachable; and the
free migration version is confirmed against `db/migration/` rather than assumed.

**Phase 1 — Persistence.** Deliverables: this feature's migration, the two audit entities and their
append-only repositories, the three columns and `@Version` added to the single `catalog.domain.Dish`
entity (R-005-4),
the dev seed update. Exit criteria: `AdminPricingMigrationTest` and the updated
`DevSeedDishesMigrationTest` pass; spec 003's catalog tests still pass.

**Phase 2 — Pricing domain.** Deliverables: `RetailPricingCalculator` and the margin/threshold
invariants. Exit criteria: `RetailPricingCalculatorTest` and `MarginRoundTripToleranceTest` pass with
the derived bound, including the three verified counter-examples.

**Phase 3 — Services and transitions.** Deliverables: inbox, detail, governance and Chef-feedback
services; the publication gate; the audit writes; the status guards and `@Version` wiring. Exit
criteria: service unit tests pass, including the query-count assertion and `cycle_number` across
cycles.

**Phase 4 — HTTP surface.** Deliverables: the three controllers, the DTO records, the seven
exceptions and their handlers in the single `GlobalExceptionHandler`. Exit criteria: MockMvc slices
and the contract test pass; the full RBAC matrix and the 409 taxonomy are verified.

**Phase 5 — Diner catalog migration (D2/D4).** Deliverables: the visibility predicate and the
`retail_price` read, with `EnrichedDishResponse.price` unchanged on the wire. Exit criteria:
`DishVisibilityPredicateIntegrationTest` passes and spec 003's catalog suites stay green.

**Phase 6 — Frontend vertical slice.** Deliverables: the route, page, components, hooks, API client
and types, plus the Chef feedback banner. Exit criteria: the Vitest suites pass and the
[quickstart.md](./quickstart.md) UI checklist is walked end to end.

**Phase 7 — Integration and concurrency.** Deliverables: the Testcontainers suites and
`DishDecisionConcurrencyTest`. Exit criteria: all three quickstart scenarios reproduce as documented.

## Complexity Tracking

| Violation / debt | Why needed | Simpler alternative rejected because |
|---|---|---|
| Extending 005's `com.easyeat.dish.domain.DishStatus` with `PUBLISHED` from this feature — a D1 exception **pending human decision** (R-005-3) | The database now admits `PUBLISHED` but the enum does not, so the approval transition has no value to assign; spec 005 has asked this feature to make the change | Having 005 add the constant is cheaper and respects D1 literally, and remains the preferred path; vendoring a second status enum into this feature would create two sources of truth for the dish lifecycle |
| `@Version` added to the **shared** `catalog.domain.Dish` instead of to a back-office-only entity, so optimistic-locking failures now reach spec 003's and spec 005's write paths (**R-005-4**, pending acknowledgement) | Spec 005 merged the five new columns onto the existing catalog entity rather than creating a second one, so no back-office-only entity exists to carry `@Version` | The two-entity split of the withdrawn D3 is no longer available: `@Immutable` on the catalog entity would break spec 005's own submit path, which writes `submitted_at` through it |
| Loss of the structural guard D3 provided: the diner read path stays read-only by **review** rather than by `@Immutable` | There is one entity and it must remain writable, so the annotation that made a bypass impossible cannot be applied | Re-introducing a second mapping would duplicate the aggregate spec 005 just consolidated, and would reopen exactly the two-owner confusion D1 exists to prevent. **Owner of the risk**: any future change that adds a write path through `catalog/repository/DishRepository` must carry the version predicate; today that repository declares only `findByTenantIdAndIsActiveTrue` and `findByIdIn` |
| Deprecated `dishes.price` kept alongside `retail_price` | Dropping it would break spec 003's tests and the dev seed inside this feature's scope | Reusing `price` as the PVP cannot express "not yet priced" (`NOT NULL`) and contradicts the spec's Key Entities. **Owner of removal**: the Sprint 3 catalog-lifecycle feature (CHF-2.2.5) |
| Duplicated audit-DTO assembly if R-006-1 is refused | FR-011/FR-012 need the Food Cost for published dishes, which 006's gated service cannot provide | Duplicating the *calculation* is forbidden (006 owns it); calling 006's HTTP endpoint from the server would be in-process HTTP |
| Validation status 400 instead of the codebase's bean-validation 422 for price and comment | FR-013 and FR-021 state 400 explicitly, and the specification is the source of truth (Principle I) | Amending the spec to 422 would be cheaper and is recorded as the runner-up in [research.md §3.4](./research.md); a second `@RestControllerAdvice` to rewrite the status is not acceptable |
