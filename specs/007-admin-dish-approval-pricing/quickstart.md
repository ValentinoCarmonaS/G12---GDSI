# Quickstart Validation Guide: Administrative Back-Office for Dish Audit, Retail Pricing and Approval

**Feature**: `007-admin-dish-approval-pricing`
**Date**: 2026-10-26
**Audience**: Developers, QA and technical evaluators

Runnable validation of the feature end to end. Schemas are **not** duplicated here — see
[contracts/openapi.yaml](./contracts/openapi.yaml) for request/response shapes, error bodies and the
409 taxonomy, and [data-model.md](./data-model.md) for the DDL, the state machine and the margin
invariants.

---

## 0. Upstream readiness gate — run this before anything else

This feature is **not implementable** until spec 005's dish domain and spec 006's cost engine are on
`develop/sprint-2` (see [plan.md § Upstream Requirements](./plan.md#upstream-requirements)). Verify,
from the repository root:

```bash
ls easyeat/backend/src/main/resources/db/migration/            # confirmed 2026-10-28: V1..V7 present (V7 from 005), so this feature takes V8
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

---

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

---

## 2. Automated verification

```bash
# Backend: unit, MockMvc slice and Testcontainers integration tests
cd easyeat/backend && mvn test

# Only this feature's suites
mvn test -Dtest='RetailPricingCalculatorTest,MarginRoundTripToleranceTest,AdminDishReviewControllerTest,AdminDishPricingControllerTest,AdminDishDecisionControllerTest,ChefReviewFeedbackControllerTest,DishApprovalConcurrencyTest,DishApprovalPricingIntegrationTest,DishReviewInboxIntegrationTest,AdminPricingMigrationTest,DevSeedDishesMigrationTest'

# Frontend
cd easyeat/frontend && npm test
```

Expected: all green, including `MarginRoundTripToleranceTest`, which asserts the **derived** bound of
[data-model.md §5.3](./data-model.md) — not FR-008's unconditional ±0.01 pp — and pins
`3.33 @ 60% → 8.33` (0.0240 pp), `1.25 @ 70% → 4.17` (0.0240 pp) and `0.07 @ 60% → 0.18` (1.1111 pp)
as expected behaviour.

---

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

---

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

---

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

---

## 6. Frontend validation

With both servers running, log in as `admin@easyeat.com` and open `/admin/dishes`
(reachable from the Administration panel hub):

- the three tabs render with the pending badge, FIFO order, 20-per-page controls and distinct empty
  states;
- the audit panel shows the itemized Food Cost, the amber unpriced-ingredient badge and the
  bidirectional margin ↔ PVP calculator; editing one field drives the other, and reformatting to two
  decimals happens on blur, never mid-keystroke;
- with Food Cost `0.00` the margin input is disabled and the direct-price path is required;
- margins at `≥ 100%` and `≤ −500%` show a boundary advisory and block derivation;
- a PVP below the tolerance threshold shows the achievable precision instead of claiming ±0.01 pp;
- saving a negative margin and publishing with advisories each require a second confirmation;
- the rejection dialog enforces 10–1000 characters with a live counter and a disabled submit;
- `403`, each flavour of `409` and `400` surface distinct messages, and a stale decision refreshes
  the list instead of leaving a phantom row.
