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
`is_active = true AND status = 'PUBLISHED'`. First-decision-wins is delivered by conditional
`UPDATE`s that restate the state the Administrator acted upon — the source status for approval and
rejection, the previously-read price for repricing — each covering a different race and neither
requiring a version column on an entity three specs write through. The margin is
never persisted: it is recomputed from the live Food Cost on every consultation. All arithmetic is
`BigDecimal`/`HALF_UP`, and the round-trip tolerance of FR-008 is replaced by the bound this plan
derives, because ±0.01 pp is not achievable for low prices.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.6 (frontend)

**Primary Dependencies**: Spring Boot 3.3.x (Web, Data JPA, Security, Validation), Flyway, PostgreSQL 16 driver, Lombok · React 18.3.1 (**not** React 19, which `005/plan.md` claims — `package.json` is authoritative), Vite 5, react-router-dom 6, Tailwind CSS 3, axios 1.7, lucide-react

**Storage**: PostgreSQL 16; Flyway versioned migrations. This feature owns exactly one migration, named for the next free sequential version **at the moment the file is authored**, confirmed against `db/migration/` and never reserved in advance. Queue confirmed 2026-10-28 against the integration branch: `V1`–`V7` present, `V7` from spec 005 → this feature takes **`V8__admin_dish_approval_pricing.sql`**, re-confirmed when the file is authored. Spec 008 holds a competing `V8` on its own branch only; registry rule 4 gives the number to whichever merges first. See [research.md §2(b)](./research.md) and [`.specify/memory/migrations.md`](../../.specify/memory/migrations.md)

**Testing**: JUnit 5 + AssertJ + Mockito, Spring MockMvc slice tests, Testcontainers PostgreSQL 16 · Vitest 2 + @testing-library/react + jsdom

**Target Platform**: Linux server (Spring Boot JAR) + modern browsers (SPA)

**Project Type**: Full-stack web application in a monorepo (`easyeat/backend`, `easyeat/frontend`) — vertical slice per Principle X

**Performance Goals**: p95 of 2 s for the inbox (request to list rendered) and for the audit panel (request to itemized Food Cost rendered), with 20 pending dishes on a 20 Mbps connection at 50 ms round-trip latency (SC-006, restated measurably 2026-10-28); the resubmission indicator resolved in 2 queries per page regardless of page size (no N+1)

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

**As of 2026-10-28.** Every verdict below was assessed against the state of this feature's documents on that date, and each one is revalidated when the document that supports it changes — not once per feature. A reader who finds a row stale should re-measure rather than trust it; the upstream-synchronization row is the standing example of a verdict that expired within 24 hours of being recorded.

Four verdicts were re-assessed on that date and the reason each still holds has changed:

- **I** and **X** held only conditionally until `FR-027` to `FR-033` brought the interface behaviour and the diner-visibility condition into the specification. Before that, 28 frontend tasks implemented behaviour whose only source was this plan — a Principle I inversion. Re-check if interface behaviour is ever added to this plan without a matching requirement.
- **IV** and **VI** rested on decision **D3**, two JPA mappings over `dishes`. D3 is withdrawn: spec 005 consolidated onto one entity, so the structural guard `@Immutable` would have given is now a review-enforced invariant, registered as debt. Re-check if a write path is ever added to `catalog/repository/DishRepository`. **VI** was re-assessed again on 2026-10-28 when two of its three debt items disappeared — see the row itself.
- **VII** rested on an FR-to-task mapping that was inferential; 64 tasks now name the requirement they serve. Re-check if tasks are added without that annotation.
- **VIII** briefly carried four open escalations. As of 2026-10-28 **none is open**: R-004-1 and R-006-1 were answered in writing by spec 006's owner (issue #146) and the answers were verified against merged code, not accepted on assertion; R-005-4 is withdrawn because a conditional `UPDATE` satisfies FR-025 without touching a shared entity; and R-005-3 is settled by a recorded amendment to D1 granting joint ownership of `DishStatus`. The verdict holds for the reason it always did — each decision was escalated, and the one that had to be taken here was taken by changing the written agreement first. It would fail if a later boundary crossing were settled inside a commit instead.

| Principle | Pre-design | Post-design | Evidence |
|---|---|---|---|
| **I. Specification is the source of truth** | PASS | PASS | Every FR traced in [data-model.md §7](./data-model.md). Three places where spec and code disagree were resolved explicitly, not silently: FR-008/SC-002's unachievable tolerance, FR-001's non-existent `COUNTER` role, and validation status 400-vs-422 — each recorded as an amendment in [research.md §3](./research.md) to be carried back into `spec.md` before `/speckit-tasks`. |
| **II. Specification before implementation** | PASS | PASS | `spec.md` approved with 0 `[NEEDS CLARIFICATION]`; this plan plus 4 design artifacts precede any code; `tasks.md` deliberately not generated by this command. |
| **III. Layered verification proportional to risk** | PASS | PASS | Four layers named with concrete classes and paths (§Testing strategy): pure unit tests for the money component, MockMvc slices for RBAC/state gates/validation boundaries, Testcontainers integration for the migration, backfill, FIFO, audit rows and tenant isolation, a dedicated concurrency test for first-decision-wins, and Vitest behaviour tests. Boundary cases (9/10/1000/1001 chars, 3 decimals, margin 0/100/−500, food cost 0.00, half-cent rounding) are explicit tests, not happy paths. |
| **IV. Simple, cohesive, justified design** | PASS | PASS | No new framework, no new advice, no new pattern. Reuses `@PageableDefault` + record page DTO, `TenantContextHolder`, `ProblemDetail`, the `src/api`/`src/types`/`src/components/<domain>` layout. Each abstraction justified: `RetailPricingCalculator` is Spring-free so it can be exhausted by pure unit tests; the two audit aggregates are `@Immutable` with append-only repositories, which is structural enforcement rather than decoration; a DB trigger for audit immutability was considered and rejected as over-engineering ([data-model.md §3.3](./data-model.md)). The concurrency guard is a conditional `UPDATE` in this feature's own repository rather than a version column on a shared entity — the simpler mechanism and the narrower blast radius (R-005-4). |
| **V. Bounded scope** | PASS | PASS | Out of scope and not designed: submission notification (005/CHF-2.2.4), unpublish/pause (Sprint 3), promotions and plato del día, taxes, multi-currency, payments, orders, and diner catalog rendering beyond D4's predicate and D2's `retail_price` read. The two touches outside this feature's own files (catalog read path, dev seed) are each mandated by D2/D4/D5 and named as MODIFIED in the source tree. |
| **VI. Controlled technical debt** | PASS | PASS | **Re-assessed 2026-10-28**: of the debt items this row once listed, two are gone rather than merely tolerated. The degraded FIFO fallback closed when R-005-1 was satisfied in schema and in code; duplicated audit-DTO assembly closed when R-006-1 moved the gate to the controller. What remains in Complexity Tracking is the deprecated `dishes.price` column (removal owned by the Sprint 3 catalog-lifecycle feature), the loss of D3's structural guard, and the 400-instead-of-422 validation status. None blocks delivery; each is written down with an owner rather than discovered later. |
| **VII. Traceability, consistency, documentation** | PASS | PASS | FR → artifact matrix in data-model.md; every cross-artifact contradiction found is recorded instead of resolved unilaterally: specs 005 (seed intent, React 19, recipe entity target, status domain), 006 (state gate), 008 (`ACTIVE` vs `PUBLISHED`, `price` vs `retail_price`). Amendments to `spec.md` are named precisely. |
| **VIII. Evidence over assumption, human escalation** | PASS | PASS | Every baseline claim verified by command with the result recorded ([research.md §1](./research.md)); the 005/006 absence verified across all branches; the margin bound derived symbolically **and** checked with exact decimal arithmetic over 2.6M cases rather than asserted from the spec's two convenient examples. The one apparently escalation-worthy conflict (008's `ACTIVE` vs this feature's `PUBLISHED`) proved decidable from 008's own deferral clause, so it is recorded, not escalated. |
| **IX. Engineering over convenience** | PASS | PASS | The convenient paths were all declined: restating ±0.01 pp as a guarantee, reusing `price` as the PVP, resting first-decision-wins on a lock acquired before the gate is evaluated, and letting FR-023 rest on "the UI does not render it" instead of structural omission in the response type. The concurrency mechanism was re-derived rather than inherited: `@Version` on the shared entity was the planned answer until it became clear it would change failure behaviour for two other specs to guard three operations, which a conditional `UPDATE` guards exactly (R-005-4). |
| **X. Full-stack vertical slice** | PASS | PASS | Backend contracts and the Administrator Back-Office UI are specified together, plus the Chef-side feedback banner; `spec.md`'s first clarification records the full-stack scope decision. No single-tier delivery. |
| **XI. Upstream synchronization awareness** | PASS (with alert) | PASS (with alert) | **As of 2026-10-27** (the 2026-10-26 measurement below is superseded; any later reader must re-measure before trusting this row). `git fetch origin` executed 2026-10-27: `HEAD c3536e0`; the sprint's integration branch is `origin/develop/sprint-2`, against which this branch is **33 commits behind and 0 ahead** — every local commit is already integrated. `origin/develop/sprint-2` is itself 0 behind and 22 ahead of `origin/main`. A staleness alert was raised; no `pull`/`merge`/`rebase` was run (the decision is the developer's). **Impact assessed, and this time the baseline did move**: `V6__create_ingredient_batches_and_refactor_lifecycle.sql` (spec 004, PR #126) is now on both branches and consumed the version this plan had attributed to spec 005, which forced the renumbering recorded in [research.md §2(b)](./research.md). `Ingredient.unitPurchaseCost` was verified unchanged (`nullable = false`, scale 4, `HALF_UP`), so the Food Cost premises hold. **Re-measured 2026-10-28**, and the baseline moved twice in one day. `origin/develop/sprint-2` absorbed spec 005 (PR #160) and then spec 006 (PR #170, merged 17:20Z); this branch was fast-forwarded onto it and is **0 behind and 0 ahead** at `e934713`. Impact: the three upstream requirements that were open at the previous measurement are now satisfied in code rather than in promise, and spec 008 consumed `V8` on its own feature branch (PRs #168, #169), which is why this feature claims `V8` on the authority of the registry's merge-order rule rather than deferring to that claim. The intermediate state recorded earlier the same day — PR #155 closed without merging, 006 absent from the integration branch — was accurate when measured and expired within the hour; it is the second illustration of why this row carries a date. Prior measurements, kept for the record: 2026-10-26, `HEAD d89cf47`, `origin/main c29728e`, 12 behind / 17 ahead. |

**Gate result**: PASS pre-design and post-design. No principle is violated. **As of 2026-10-28 one
deliberate boundary exception stands**: the amendment to D1 that gives specs 005 and 007 joint
ownership of `com.easyeat.dish.domain.DishStatus` so this feature can add the `PUBLISHED` constant
(R-005-3), recorded in Complexity Tracking with its limits. The two exceptions previously recorded
here are withdrawn — replacing 005's `chk_dishes_status` became unnecessary when R-005-2 was
satisfied in 005's own `V7`, and `@Version` on the shared `Dish` entity is replaced by a conditional
`UPDATE` confined to this feature's repository (R-005-4).

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

### R-005-4 — Agreement to place `@Version` on the shared `Dish` entity — **WITHDRAWN 2026-10-28**

This requirement asked the owners of specs 003 and 005 to accept `@Version` on
`com.easyeat.catalog.domain.Dish`, because FR-025 needs optimistic concurrency control and spec 005
consolidated onto that shared entity rather than creating a back-office-only one. It is withdrawn
because its premise — that a version attribute is the only available mechanism — is false.

**What replaces it.** Each of FR-025's three write operations is issued as a single conditional
`UPDATE` that restates the state the Administrator acted upon, and the operation is accepted only if
it affected exactly one row:

- **approval** and **rejection** restate the source status, `AND status = 'PENDING_PRICE'`;
- **pricing** restates the retail price the Administrator read,
  `AND retail_price IS NOT DISTINCT FROM :expectedRetailPrice`, the compare-and-set form that also
  covers the first assignment, where the stored value is `NULL`.

Zero affected rows means another Administrator committed first — exactly the condition FR-025
describes — and is surfaced as HTTP 409 Conflict.

**Why this is preferable, not merely cheaper.** It confines the guard to the three operations FR-025
names instead of changing failure behaviour on every write path that touches `dishes`: spec 003's
catalog writes and spec 005's submit transition keep the semantics their own tests assert. It adds
no column, so this feature's migration alters the shared table only with its own columns. And it
crosses no D1 boundary — the guard lives in this feature's repository methods, not in an entity
another spec owns.

**Consequence for FR-025's wording.** The requirement as drafted named "an entity version attribute"
as the mechanism, which is now inaccurate. The amendment is recorded in the Clarifications session of
2026-10-28 in [spec.md](./spec.md); the guarantee itself is unchanged — first commit wins, the rest
receive 409.

**Consequence for the contract.** The pricing request must carry the previously-read retail price, so
the pricing operation's request schema in [contracts/openapi.yaml](./contracts/openapi.yaml) gains
that field. Approval and rejection need no new field: their expected state is the status the inbox
already filtered on.

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
**Residual dependency — CLOSED 2026-10-28.** The write landed with 005's US4 (PR #157, merged to
`develop/sprint-2` via PR #160): `DishService` assigns `setSubmittedAt(Instant.now())` on the
`DRAFT → PENDING_PRICE` transition. FR-003's ordering is now verifiable end to end, and this
requirement is satisfied in full rather than only at the schema level.

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
**Degraded behaviour**: none is acceptable. Two admissible paths existed, and the choice was a human
decision: 005 adds the constant, or `DishStatus` is declared jointly owned and this feature extends it.

**Resolved 2026-10-28 — amendment to D1, recorded.** `com.easyeat.dish.domain.DishStatus` is declared
**jointly owned** by specs 005 and 007, and this feature adds the `PUBLISHED` constant. The amendment
is deliberately narrow and its limits are part of it:

- it covers **that one enum and no other object** in `com.easyeat.dish.domain`. Every other class in
  the package stays 005-owned and D1 applies to them unchanged;
- it authorises **adding** the constant that the database already admits, not reordering, renaming or
  removing the existing ones, because `DishStatus` is persisted by name through
  `@Enumerated(EnumType.STRING)`;
- it is a one-way addition: nothing in 005 needs to change, and 005's existing exhaustive handling of
  `DRAFT` and `PENDING_PRICE` keeps compiling.

The basis for the amendment rather than the wait: spec 005 asked this feature to make the change, the
database has admitted `PUBLISHED` since PR #138, and the escalation filed on 2026-10-28 (issue #133)
drew no reply. Recording the shared ownership is what keeps this from being the silent boundary
crossing D1 exists to prevent — the file changes either way; what matters is that the agreement
changed first and in writing.

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
**Degraded behaviour**: none was safe. Assuming a source would have made this feature report margins
that contradict the inventory's own valuation of the stock actually consumed, and the contradiction
would have surfaced as a financial discrepancy rather than as a visible error.

**RESOLVED 2026-10-28** by spec 006's owner in writing (issue #146), and verified in the merged code
rather than taken on the strength of the reply:

| Question asked | Decision | Verified in |
|---|---|---|
| Which of the three is authoritative | `ingredients.unit_purchase_cost` — the standard recipe-costing figure. `ingredient_batches.unit_cost` values the physical stock consumed under FIFO; the Food Cost projects the recipe's cost structure. The two answer different questions and both stay correct | `FoodCostAuditService` reads `Ingredient.getUnitPurchaseCost()` and never touches `ingredient_batches` |
| What happens with no remaining batch | The question is moot under this rule: the computation is deterministic and independent of whether any batch has remaining quantity | — |
| What "unpriced" means | `unit_purchase_cost IS NULL` **or** `<= 0`. The line contributes `$0.00`, is added to `warning_notes`, and sets `has_unpriced_ingredients` | `FoodCostEngine:39` |
| Where scale 4 becomes scale 2 | At each **line subtotal**: `quantity × unit_purchase_cost` rounded `HALF_UP` to scale 2, and the total is the sum of the already-rounded subtotals | `FoodCostEngine:48` and `:53` |

**Consequence for this feature, beyond unblocking it.** The Food Cost arrives already at scale 2, so
`dish_price_history.food_cost_snapshot NUMERIC(10,2)` stores it without loss and **this feature
rounds the Food Cost at no point**. FR-012's "current acquisition cost" now has exactly one referent,
and FR-008's prohibition on intermediate rounding is unaffected: it governs the margin arithmetic,
whose input this value is.

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

**RESOLVED 2026-10-28** by spec 006's owner (issue #146): the gate moved to the controller, and
`FoodCostAuditService` is now status-agnostic. Verified on `develop/sprint-2` after PR #170:

- `FoodCostAuditService` contains **zero** references to `DishStatus` or `PENDING_PRICE`; both
  `auditDishCost(Dish)` and `auditDishCost(UUID)` compute for any lifecycle state;
- the `PENDING_PRICE` restriction lives in `AdminDishCostAuditController:39`, which raises
  `DishLockedException`;
- that controller already requires `hasAnyRole('ADMIN','ADMINISTRADOR')`, matching FR-001.

**The degraded path above is therefore withdrawn**, and with it the duplicated-assembly debt item in
Complexity Tracking. This feature injects `FoodCostAuditService` directly for `PENDING_PRICE` and
`PUBLISHED` dishes alike, satisfying FR-011 and FR-012 with no duplication of any kind.

**One behaviour to handle, discovered while verifying.** `auditDishCost` raises `EmptyRecipeException`
when a dish has no recipe lines, rather than returning a zero cost. FR-012 recomputes the margin on
**every** consultation, so this feature's read paths must handle that exception rather than propagate
it; the dish is unpublishable anyway under FR-015 condition 2. Recorded as FR-037.

**One value the audit response does not carry.** `RecipeItemAuditResponse` exposes the ingredient's
id, name, quantity, unit, unit cost, line subtotal and unpriced flag — but **not** its
`IngredientStatus`, and `FoodCostAuditService` does not filter discontinued lines out of the total.
FR-015 condition 2 requires at least one recipe line whose ingredient is `ACTIVE`, so that condition
cannot be derived from the audit payload and this feature must evaluate it with its own query against
`IngredientRepository`. This is not a defect in 006 — the two artifacts answer different questions —
but it is a dependency the task breakdown has to state.

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
│   ├── DishStatus.java                         # MODIFIED (jointly owned, D1 amendment 2026-10-28): + PUBLISHED (R-005-3)
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
└── exception/                                  # NEW: DishStateConflict, DishAlreadyDecided, StaleDishPrice,
                                                #      PublicationGate, AcknowledgementRequired,
                                                #      InvalidRetailPrice, InvalidRejectionComment
auth/controller/GlobalExceptionHandler.java     # MODIFIED: + 7 handlers, all for this feature's own exceptions
                                                #   (no ObjectOptimisticLockingFailureException — no @Version is used)
catalog/domain/Dish.java                        # MODIFIED: the single entity over `dishes`. + retailPrice, + publishedAt.
                                                #   NO @Version and NOT @Immutable — D3 withdrawn and R-005-4 withdrawn;
                                                #   spec 005 writes through it, the guard is a conditional UPDATE
catalog/repository/DishRepository.java          # MODIFIED: visibility predicate is_active = true AND status = 'PUBLISHED'
catalog/service/CatalogComplianceService.java   # MODIFIED: reads retail_price (D2); JSON field name `price` preserved
catalog/dto/EnrichedDishResponse.java           # REUSED unchanged - wire contract intact for specs 003/008
common/context/TenantContextHolder.java         # REUSED
config/SecurityConfig.java                      # REUSED - /api/v1/admin/** already role-gated; no change needed
```

```text
src/main/resources/
├── db/migration/V8__admin_dish_approval_pricing.sql   # NEW: the single migration this feature owns (version confirmed at authoring time)
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
| `PUT /api/v1/admin/dishes/{id}/retail-price` | FR-009..FR-011, FR-013, FR-014, FR-025 | ADMINISTRADOR | `PENDING_PRICE` or `PUBLISHED` | `retailPrice`, **`expectedRetailPrice` (required, nullable)**, `acknowledgeNegativeMargin` | `DishPricingResponse` | 400 price or missing expected price, 401, 403, 404, 409 (invalid-state, stale-price), 422 acknowledgement |
| `POST /api/v1/admin/dishes/{id}/approval` | FR-015..FR-019, FR-025 | ADMINISTRADOR | `PENDING_PRICE` → `PUBLISHED`; `PUBLISHED` → 200 no-op; `DRAFT` → 409 | 2 acknowledgement flags | `DishApprovalResponse` (`alreadyPublished`) | 401, 403, 404, 409 (invalid-state, already-decided), 422 (publication-gate-failed, acknowledgement) |
| `POST /api/v1/admin/dishes/{id}/rejection` | FR-020..FR-022, FR-024, FR-025 | ADMINISTRADOR | `PENDING_PRICE` only | `comment` 10..1000 | `DishRejectionResponse` (`cycleNumber`) | 400 comment, 401, 403, 404, 409 (invalid-state, already-decided) |
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

A read-modify-write guarded by a check in Java does **not** deliver first-decision-wins, and neither
would a `@Version` column by itself. When two administrators open the panel minutes apart, each
request re-reads the row inside its own transaction, so two *sequential* transactions both load
current data and both commit happily; and a version check only helps while the two transactions
genuinely overlap. The mechanism chosen instead is uniform: **every one of FR-025's three operations
is a single conditional `UPDATE` that restates the state the Administrator acted upon, and succeeds
only if it affected exactly one row.** Zero rows is the conflict signal; the handler then re-reads the
row to report what the current state actually is.

- **Approve and reject** — both are legal only from `PENDING_PRICE`, so the predicate is
  `AND status = 'PENDING_PRICE'`. This covers the sequential and the overlapping race with the same
  statement, which is why no version column is needed for either. In the overlapping case PostgreSQL's
  default `READ COMMITTED` does the work: the second `UPDATE` blocks on the row lock, and when the
  first commits it re-evaluates its predicate against the updated row, finds `status` no longer
  matches, and affects zero rows. A late rejection of a now-published dish and a late approval of a
  now-`DRAFT` dish both get **409 `dish-already-decided`** with `currentStatus` and `decidedAt`, so
  the UI can say *"otro administrador ya decidió este plato"*. Approving an already-published dish
  remains the one benign case: 200 idempotent, no audit row, no state change (FR-018).
- **Reprice** — the status predicate **cannot** help, because a price save is legal from the dish's
  current state no matter how stale the panel is. Two concurrent saves would otherwise produce a
  classic lost update. The predicate is therefore compare-and-set on the value the Administrator
  read: `AND retail_price IS NOT DISTINCT FROM :expectedRetailPrice`. `IS NOT DISTINCT FROM` rather
  than `=` because the first assignment compares against `NULL`, where `=` is never true.
  `expectedRetailPrice` is **required and nullable** in the request body: a mismatch yields **409
  `dish-stale-price`** with `currentRetailPrice`, and omitting the field entirely yields **400** with
  the error bound to it — never a silent overwrite. Sending `null` is not omission; it is the positive
  assertion "the dish was unpriced when I read it".

Two alternatives were rejected. `@Version` on `catalog.domain.Dish` was the original design and is
withdrawn under R-005-4: it would have changed failure behaviour on spec 003's and spec 005's write
paths in order to guard three operations belonging to this feature. An `If-Match`/`ETag` header was
also rejected: the codebase has no ETag infrastructure, and a body field is visible in the OpenAPI
schema and consistent with existing request DTOs.

**What this mechanism does not cover, stated plainly.** The guard is per-operation, not per-row. A
write path added later to `dishes` outside these three operations inherits no protection from it,
whereas a version column would have covered it automatically. That exposure is registered in
Complexity Tracking alongside the loss of D3's structural guard, and the two share one mitigation:
any new write path through `catalog/repository/DishRepository` must carry its own predicate.

**409 taxonomy** (so the frontend can tell the three apart), all emitted by the extended
`GlobalExceptionHandler` as `ProblemDetail`:
`https://easyeat.com/errors/dish-already-decided` (someone else already decided — reconcile the
list), `…/dish-invalid-state` (wrong state for this action — e.g. approve from `DRAFT`, reject a
`PUBLISHED` dish), `…/dish-stale-price` (the price changed since it was read — refresh and retry). Publication-gate
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
offending field; 409 `dish-stale-price` → *"otro administrador cambió este plato"* plus an
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
3 decimals, 0 and negative; `expectedRetailPrice` absent from the body → 400; and an assertion that the Chef feedback
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
R-005-3, R-004-1 and R-006-1 are satisfied, and an explicit decision on each open item otherwise.
Exit criteria: spec 005's branch is integrated into `develop/sprint-2` so that `V7` applies and
`com.easyeat.catalog.domain.Dish` carries the five upstream columns, `DishStatus` and
`dish_recipe_ingredients` are on the working branch; `DishStatus` admits `PUBLISHED` under an agreed
owner (R-005-3); the cost engine is reachable for any status and its authoritative cost source is
named in writing (R-006-1, R-004-1); and the free migration version is confirmed against
`db/migration/` and against the project registry at
[`.specify/memory/migrations.md`](../../.specify/memory/migrations.md) rather than assumed.

**Status 2026-10-28 — the gate passes.** Measured on this branch after it was fast-forwarded to
`develop/sprint-2` (`e934713`): `V7` present with the five columns and
`idx_dishes_review_inbox_pending`; `dish_recipe_ingredients` and `dish_photos` present;
`FoodCostAuditService` status-agnostic with the gate in `AdminDishCostAuditController:39`; the cost
source named in issue #146 and confirmed in `FoodCostEngine`; the free version is **`V8`**, the
migration directory holding `V1`–`V7`. The one remaining item is this feature's own to execute under
the recorded D1 amendment: adding `PUBLISHED` to `DishStatus`.

**Phase 1 — Persistence.** Deliverables: this feature's migration, the two audit entities and their
append-only repositories, the three columns added to the single `catalog.domain.Dish` entity — and no
version column, since the concurrency guard is the conditional `UPDATE` of R-005-4 — plus the dev
seed update. Exit criteria: `AdminPricingMigrationTest` and the updated
`DevSeedDishesMigrationTest` pass; spec 003's catalog tests still pass.

**Phase 2 — Pricing domain.** Deliverables: `RetailPricingCalculator` and the margin/threshold
invariants. Exit criteria: `RetailPricingCalculatorTest` and `MarginRoundTripToleranceTest` pass with
the derived bound, including the three verified counter-examples.

**Phase 3 — Services and transitions.** Deliverables: inbox, detail, governance and Chef-feedback
services; the publication gate; the audit writes; the conditional-`UPDATE` guards. Exit
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
| Extending 005's `com.easyeat.dish.domain.DishStatus` with `PUBLISHED` from this feature — a D1 exception, **decided 2026-10-28** by a recorded amendment granting specs 005 and 007 joint ownership of that one enum (R-005-3) | The database has admitted `PUBLISHED` since PR #138 but the enum does not, so the approval transition has no value to assign; spec 005 asked this feature to make the change and the escalation on issue #133 drew no reply | Waiting for 005 to add the constant remained the cheaper path and was preferred until it stopped arriving; vendoring a second status enum here would create two sources of truth for the dish lifecycle. **Limits of the amendment**: it covers that enum alone, authorises addition only — not reordering, renaming or removal, since the enum is persisted by name — and requires no change in 005. **Owner of the risk**: any future constant added to `DishStatus` by either spec must be reflected in `chk_dishes_status`, which this feature's migration does not touch |
| ~~`@Version` added to the **shared** `catalog.domain.Dish`~~ — **retired 2026-10-28, not accepted as debt.** FR-025 is satisfied instead by a conditional `UPDATE` in this feature's own repository, guarded on the source status for approval and rejection and on the previously-read price for repricing (**R-005-4**, withdrawn) | The debt as written would have changed failure behaviour on spec 003's and spec 005's write paths in order to guard three operations belonging to this feature | The conditional `UPDATE` is both narrower and simpler, so there is nothing left to accept: it adds no column, needs no agreement from two other owners, and crosses no D1 boundary. **What it does not cover**: a write path added later to `dishes` outside these three operations gets no guard from it — that is the same exposure recorded in the row below |
| Loss of the structural guard D3 provided: the diner read path stays read-only by **review** rather than by `@Immutable` | There is one entity and it must remain writable, so the annotation that made a bypass impossible cannot be applied | Re-introducing a second mapping would duplicate the aggregate spec 005 just consolidated, and would reopen exactly the two-owner confusion D1 exists to prevent. **Owner of the risk**: any future change that adds a write path through `catalog/repository/DishRepository` must carry the version predicate; today that repository declares only `findByTenantIdAndIsActiveTrue` and `findByIdIn` |
| Deprecated `dishes.price` kept alongside `retail_price` | Dropping it would break spec 003's tests and the dev seed inside this feature's scope | Reusing `price` as the PVP cannot express "not yet priced" (`NOT NULL`) and contradicts the spec's Key Entities. **Owner of removal**: the Sprint 3 catalog-lifecycle feature (CHF-2.2.5) |
| ~~Duplicated audit-DTO assembly if R-006-1 is refused~~ — **closed 2026-10-28, never incurred.** Spec 006 moved its status gate into `AdminDishCostAuditController`, so `FoodCostAuditService` is injectable for `PUBLISHED` dishes and nothing is duplicated | FR-011/FR-012 need the Food Cost for published dishes, which 006's previously gated service could not provide | Duplicating the *calculation* was forbidden (006 owns it) and calling 006's HTTP endpoint from the server would have been in-process HTTP; the question became moot once the gate moved |
| Validation status 400 instead of the codebase's bean-validation 422 for price and comment | FR-013 and FR-021 state 400 explicitly, and the specification is the source of truth (Principle I) | Amending the spec to 422 would be cheaper and is recorded as the runner-up in [research.md §3.4](./research.md); a second `@RestControllerAdvice` to rewrite the status is not acceptable |
