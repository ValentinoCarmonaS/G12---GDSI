# Quickstart Validation Guide: Administrative Back-Office for Dish Audit, Retail Pricing and Approval

**Feature**: `007-admin-dish-approval-pricing`
**Date**: 2026-10-26
**Audience**: Developers, QA and technical evaluators

Runnable validation of the feature end to end. Schemas are **not** duplicated here — see
[contracts/openapi.yaml](./contracts/openapi.yaml) for request/response shapes, error bodies and the
409 taxonomy, and [data-model.md](./data-model.md) for the DDL, the state machine and the margin
invariants.


## 0. Upstream readiness gate — run this before anything else

This feature is **not implementable** until spec 005's dish domain and spec 006's cost engine are on
`develop/sprint-2` (see [plan.md § Upstream Requirements](./plan.md#upstream-requirements)). Verify,
from the repository root:

```bash
ls easyeat/backend/src/main/resources/db/migration/            # confirmed 2026-10-28: V1..V7 present (V7 from 005), so this feature took V8, then V9 (correction 2026-10-31: spec 008 merged its own V8__create_establishments.sql into develop/sprint-2 first, so this feature's migration was renumbered to V9__admin_dish_approval_pricing.sql on integration — see .specify/memory/migrations.md)
ls easyeat/backend/src/main/java/com/easyeat/dish/domain/      # expect Dish.java, DishStatus.java
grep -rn "submitted_at" easyeat/backend/src/main/resources/db/migration/   # R-005-1
grep -rn "idx_dishes_review_inbox_pending" easyeat/backend/src/main/resources/db/migration/  # R-005-1 index, owned by 005
grep -rn "PUBLISHED" easyeat/backend/src/main/java/com/easyeat/dish/domain/DishStatus.java   # R-005-3
grep -rln "class FoodCostEngine" easyeat/backend/src/main/java/com/easyeat/dish/
```

**Every miss is a stop condition.** `submitted_at` and the `PUBLISHED` status check were delivered by
spec 005 on 2026-10-27 (issue #137, PR #138), so the degraded FIFO fallback that used to apply here is
withdrawn — if the column is missing, the branch simply lacks 005's work and you stop. `DishStatus`
without `PUBLISHED` is also a stop condition: it is R-005-3, pending a decision on who owns that enum.


## 1. Prerequisites

```bash
# 1. PostgreSQL 16
cd easyeat/backend && docker compose up -d

# 2. Backend with the dev profile (seeds users and the five dev dishes).
#    NOTE: this repository ships no Maven wrapper; a local Maven install is required.
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend
cd easyeat/frontend && npm install && npm run dev
```

Dev credentials from `db/dev/R__seed_dev_users.sql`: administrator `admin@easyeat.com` /
`SecurePass123`, tenant `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`.

```bash
export API=http://localhost:8080/api/v1
export ADMIN_TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@easyeat.com","password":"SecurePass123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
```

The dev seed has **no CHEF in the administrator's tenant** (the seeded chef belongs to another
establishment), so create one first and use its token as `$CHEF_TOKEN`:

```bash
curl -s -X POST $API/admin/staff -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Chef QA","email":"chef.qa@easyeat.com","role":"CHEF"}'
```


## 2. Automated verification

```bash
# Backend: unit, MockMvc slice and Testcontainers integration tests
cd easyeat/backend && mvn test

# Only this feature's suites
mvn test -Dtest='RetailPricingCalculatorTest,MarginRoundTripToleranceTest,AdminDishReviewControllerTest,AdminDishDecisionControllerTest,DishGovernanceServiceTest,AdminDishApprovalOpenApiContractTest,AdminPricingMigrationTest,DevSeedDishesMigrationTest'

# Frontend
cd easyeat/frontend && npm test
```

Expected: all green, including `MarginRoundTripToleranceTest`, which asserts the **derived** bound of
[data-model.md §5.3](./data-model.md) — not FR-008's unconditional ±0.01 pp — and pins
`3.33 @ 60% → 8.33` (0.0240 pp), `1.25 @ 70% → 4.17` (0.0240 pp) and `0.07 @ 60% → 0.18` (1.1111 pp)
as expected behaviour.


## 3. Scenario A — submit, audit, price, approve, verify publication (US1, US2, US3)

1. **Chef submits.** Create a draft, add ≥ 1 active recipe line, confirm allergens, then
   `POST $API/dishes/{id}/submit-review` with `$CHEF_TOKEN`. Expect `200` and `status: PENDING_PRICE`.
2. **The dish appears in the inbox, FIFO.**
   `GET $API/admin/dishes/review-inbox?tab=PENDING&page=0&size=20`.
   Expect `200`; the dish is listed, `pendingCount` ≥ 1, `isResubmission: false`,
   `reviewCycleCount: 0`, items ordered by `submittedAt` ascending.
3. **Audit detail.** `GET $API/admin/dishes/{id}/review-detail`.
   Expect the itemized `foodCostAudit` from spec 006, `margin.marginDerivable`,
   `margin.hasUnpricedIngredients`, and `publicationGate.unmetConditions` containing
   `RETAIL_PRICE_MISSING`. Note the returned `retailPrice` — it is `null` on a dish never priced.
4. **Approve too early fails.** `POST $API/admin/dishes/{id}/approval` with `{}`.
   Expect `422` `publication-gate-failed` with `unmetConditions: ["RETAIL_PRICE_MISSING"]`; the dish
   stays `PENDING_PRICE`.
5. **Set the price.** `PUT $API/admin/dishes/{id}/retail-price` with
   `{"retailPrice": 11250.00, "expectedRetailPrice": null}` — `null` because the dish was unpriced
   when it was read, which is the positive assertion the compare-and-set expects.
   Expect `200`, `margin.marginPct` recomputed at scale 4, a `priceHistoryEntryId`, and
   `status` still `PENDING_PRICE` (FR-009).
6. **Omitting the expected price is rejected.** Repeat step 5 with no `expectedRetailPrice` key at
   all: expect `400` with `errors[0].field = "expectedRetailPrice"` — never a silent overwrite. Note
   that sending `"expectedRetailPrice": null` is *not* omission and must succeed on an unpriced dish.
7. **Approve.** `POST $API/admin/dishes/{id}/approval` with the acknowledgements the detail response
   asked for. Expect `200`, `status: PUBLISHED`, `publishedAt` set, `alreadyPublished: false`, a
   `reviewHistoryEntryId`, and the dish gone from the `PENDING` tab and present in `PUBLISHED`.
8. **Diner visibility (SC-001, D4).** `GET $API/establishments/{tenantId}/dishes` as a diner: the
   dish is returned, its `price` field carries the **`retail_price`** value (D2), and dishes still in
   `PENDING_PRICE` or `DRAFT` are absent.
9. **Idempotent approval (FR-018).** Approve again: expect `200`, `alreadyPublished: true`, the
   **original** `publishedAt`, `reviewHistoryEntryId: null`, and no new audit row.


## 4. Scenario B — reject, and verify the Chef sees feedback but no costs (US4, FR-023)

1. **Reject with an invalid comment.** `POST $API/admin/dishes/{id}/rejection` with a 9-character
   comment, then with `"   "`. Expect `400` both times, `errors[0].field = "comment"`, and the dish
   still `PENDING_PRICE`.
2. **Reject validly.** Send a 25-character comment. Expect `200`, `status: DRAFT`, `cycleNumber: 1`,
   and the dish absent from the `PENDING` tab.
3. **Chef reads the feedback.** `GET $API/dishes/{id}/review-feedback` with `$CHEF_TOKEN`.
   Expect `200` with `entries[0]` carrying only `cycleNumber`, `reviewerName`, `comment`,
   `createdAt`. **Assert the absence of financial data structurally**:
   ```bash
   curl -s $API/dishes/$DISH_ID/review-feedback -H "Authorization: Bearer $CHEF_TOKEN" \
     | grep -Eiv 'cost|margin|price' > /dev/null && echo "OK: no financial field present"
   ```
4. **Chef cannot reach the admin surfaces.** `GET $API/admin/dishes/review-inbox` with
   `$CHEF_TOKEN`: expect `403`. Same for `ENCARGADO` and `COMENSAL` tokens; anonymous gets `401`.
5. **Resubmission is visible.** Chef resubmits; reopen the `PENDING` tab. Expect
   `isResubmission: true` and `reviewCycleCount: 1`, and `GET .../review-history` returning cycle 1
   `REJECTED` in chronological order.


## 5. Scenario C — reprice a published dish and verify the audit trail (US2, FR-011, SC-008)

1. Take the dish published in Scenario A and note its `retailPrice`.
2. `PUT $API/admin/dishes/{id}/retail-price` with a new price and that value as `expectedRetailPrice`.
   Expect `200`; `status` is still `PUBLISHED`, `publishedAt` unchanged, recipe untouched, and the
   dish still visible to diners at the new price.
3. **Stale decision (FR-025).** Reuse the now-stale `expectedRetailPrice`: expect `409`
   `dish-stale-price` with `currentRetailPrice` present so the UI can reconcile.
4. **Negative margin needs confirmation (FR-014).** Submit a price below the Food Cost without
   `acknowledgeNegativeMargin`: expect `422` `acknowledgement-required` with
   `["NEGATIVE_MARGIN"]`. Resend with the flag: expect `200`.
5. **Price precision (FR-013).** Submit `0`, `-5.00` and `10.123`: expect `400` each time with the
   error bound to `retailPrice`.
6. **Audit trail.** `GET $API/admin/dishes/{id}/review-history`: `priceChanges` lists every change in
   chronological order with `previousPrice`, `newPrice`, `foodCostSnapshot` and the acting
   administrator; `reviewEvents` lists the approval. Nothing is ever updated or deleted.
7. **Live margin (FR-012).** Change an ingredient's `unitPurchaseCost` via
   `PUT $API/admin/ingredients/{ingredientId}`, then re-read `review-detail`: `margin.marginPct`
   changed while `retailPrice` did not.


## 6. Scenario D — Administrator cancels a dish under review (C-02, FR-038..FR-042)

1. As a Chef, submit a complete dish and capture its id as `$DISH_ID`. Confirm it appears as
   `PENDING_PRICE` in the Administrator's pending inbox.
2. Verify role ownership: `POST $API/admin/dishes/$DISH_ID/cancel` with `$CHEF_TOKEN` returns `403`.
   A Chef's legacy `DELETE $API/dishes/$DISH_ID` is only for `DRAFT`; against this submitted dish it
   returns `409` and leaves it pending. Spec 005's separate deletion of a Chef-owned draft remains
   unchanged.
3. As the Administrator, open `/admin/dishes`, open the pending dish and select "Borrar". Cancel
   the confirmation dialog: the route remains `/admin/dishes`, the dish remains pending, and no
   cancellation history event is written.
4. Reopen the confirmation and confirm it. Expect `204`; the UI remains on `/admin/dishes`, clears
   the terminal detail and refreshes the inbox. The dish no longer appears in the `PENDING` tab.
5. Read the audit trail. Expect exactly one `CANCELLED` event with the Administrator, tenant,
   timestamp and next cycle number; a free-text comment is not required. The Chef list shows the
   dish as read-only "Cancelado" with no edit/delete actions. Diner catalog queries exclude it.
6. Attempt cancellation again, or race it against another terminal decision. Expect `409`; no
   second `CANCELLED` event is recorded. An unknown event type remains rejected by the database's
   closed `chk_drh_event_type` domain.

## 7. Frontend validation

With both servers running, log in as `admin@easyeat.com` and open `/admin/dishes`
(reachable from the Administration panel hub):

- the three tabs render with the pending badge, FIFO order, 20-per-page controls and distinct empty
   states;
- the audit panel shows itemized Food Cost, unpriced-ingredient warnings, the calculator and gate
   state; editing either calculator input derives the other without reformatting the active field;
- a Food Cost of `0.00` disables margin-to-price derivation and requires direct price entry;
- margins at or above 100% show a boundary advisory; negative margins remain valid but require the
   FR-014 confirmation before saving;
- a price below the round-trip precision threshold shows its achievable deviation instead of
   claiming the ±0.01 pp bound;
- approval acknowledgements and rejection require their explicit confirmation/dialog flows;
- cancellation of a pending dish opens a confirmation dialog. Abandoning it sends no request;
   confirming keeps the Administrator at `/admin/dishes`, clears the terminal detail and refreshes
   the inbox;
- the Chef dish list shows cancelled dishes with the "Cancelado" badge and no edit or delete action;
- `403`, each flavour of `409` and `400` surface actionable messages, and stale decisions refresh
   the list instead of leaving a phantom row.

**Correction, found 2026-10-31 during manual UI testing after this feature was fully merged to
`main` (recorded in `HANDOFF-post-merge-ui-gap.md`).** When T103 executed this section, every
bullet above was true of `MarginPriceCalculator`, `PublicationConfirmModal` and `RejectionDialog`
**in isolation**, which is as far as that task's own note (`tasks.md` T103) honestly scoped its
check — "referenced against the existing component/page test suites... rather than a fabricated
manual-browser narrative". What neither that check nor the suite caught: none of the three
components was imported by `AdminDishReviewPage` or `DishAuditPanel`. `/admin/dishes` rendered the
publication gate's status and nothing to act on it — no price field, no "Aprobar y Publicar", no
"Rechazar / Devolver a cocina" — because no task in Phases 3-7 wired them in, and no page-level
test asserted they were reachable. Closed in Phase 8 (`tasks.md` T105-T110): `dishReviewApi`
gained its three missing write methods, a new `DishDecisionActions` component composes the three
UI pieces against them, and it is now rendered on `/admin/dishes` beside `DishAuditPanel`. Every
bullet above is now additionally backed by a page-level test (`AdminDishReviewPage.test.tsx`), not
only a component-level one — see T109. Full frontend suite verified green after the wiring: 587 of
587 across 91 files, `tsc --noEmit` clean.


## 8. Execution record (T103/T104, 2026-10-30)

### Scenarios A, B and C — executed end to end against a live instance

Ran against a real backend (`mvn spring-boot:run -Dspring-boot.run.profiles=dev`) and a real
PostgreSQL 16 container, with real `curl` requests driving every step — not inferred from the test
suites, though the suites already cover the same ground (`DishApprovalPricingIntegrationTest`,
`DishVisibilityPredicateIntegrationTest`, `DishReviewInboxIntegrationTest`, every controller test).

- **Scenario A** (§3): all 9 steps reproduced exactly, including step 8's diner-visibility check —
   confirmed with a real `GET /establishments/{tenantId}/dishes` call that only the `PUBLISHED` dish
   is visible and its `price` field carries the `retail_price` value (`1500.0`), exercised against a
   running server, not just the test suite.
- **Scenario B** (§4): all 5 steps reproduced exactly. Step 4's "same for `ENCARGADO` and `COMENSAL`
   tokens" was not separately driven by `curl` — the `CHEF`-vs-anonymous pair already demonstrates
   the role gate, and the other roles are covered by `ChefDishReviewFeedbackControllerTest`'s RBAC
   matrix.
- **Scenario C** (§5): all 7 steps reproduced exactly, including the live-margin check (step 7):
   after raising the one recipe ingredient's `unitPurchaseCost` from 1200 to 2000, `review-detail`
   reported `foodCost` moving from 600 to 1000 and `marginPct` from −20% to −100% while
   `retailPrice` stayed at 500 — the margin is recomputed live, never read from a stored value.

Every response body, status code and error type matched what this document already specified; no
corrections were needed to §3, §4 or §5. The one correction this execution did produce is §7's
`≤ −500%` line, recorded above with T103.

### SC-006 (T104) — what could and could not be verified here

No `tc`/`netem` and no browser-based network emulation (Chrome DevTools Protocol or equivalent) is
available in this execution environment — checked before falling back to the plan below, per the
constitution's Principle VIII, which requires stating this rather than fabricating a true end-to-end
capture.

**What was measured**: 20 pending dishes were seeded (one already carrying a recipe and a priced
history from Scenario A/C). Both SC-006 endpoints were called 20 times each against the live local
server, with no added network delay (loopback), and the 95th percentile of `curl`'s own
`time_total` was taken:

| Surface | Server p95 (loopback) | Response payload |
|---|---|---|
| `GET /admin/dishes/review-inbox?tab=PENDING&size=20` | 0.0828 s | 6184 bytes |
| `GET /admin/dishes/{id}/review-detail` | 0.0227 s | 1156 bytes |

**What was calculated, not measured**: the 20 Mbps / 50 ms RTT network component SC-006 names, from
the stated conditions rather than a real constrained link — transfer time as payload size ÷ 2.5 MB/s
(20 Mbps), plus 2 × 50 ms for the connection/request round trip:

| Surface | Transfer time | RTT overhead | Calculated network component |
|---|---|---|---|
| Review inbox | 6184 / 2.5 MB/s ≈ 0.0025 s | 0.100 s | ≈ 0.103 s |
| Review detail | 1156 / 2.5 MB/s ≈ 0.0005 s | 0.100 s | ≈ 0.100 s |

**Combined estimate (measured server p95 + calculated network component), labeled as such, not as a
captured end-to-end measurement**:

- Review inbox: 0.0828 s + 0.103 s ≈ **0.19 s**
- Review detail: 0.0227 s + 0.100 s ≈ **0.12 s**

**Verdict**: both estimates fall far inside SC-006's 2-second bound — roughly a 10× margin — so the
criterion is very likely met. It is reported as "likely met, by calculated estimate" rather than
"verified", because this combined figure has three honest gaps Principle VIII requires naming: (1)
it does not model TCP slow start, congestion control or any packet loss a real constrained link
would add beyond flat RTT overhead; (2) it excludes client-side render time — the interval between
the response arriving and the list or the Food Cost panel actually painting, which this
curl-only setup cannot observe since no browser tool is available in this session; and (3) the
local Docker/JVM environment this was measured in is not production hardware. A real measurement
with browser-based network throttling, if that tool becomes available in a future session, would be
strictly better evidence than this estimate and should replace it rather than being added alongside it.
