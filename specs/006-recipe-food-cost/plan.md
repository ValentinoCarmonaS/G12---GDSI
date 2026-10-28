# Technical Implementation Plan: Automated Food Cost Engine and Recipe Audit Breakdown (ADM-5.1.2)

**Branch**: `006-recipe-food-cost` | **Date**: 25/10/2026 | **Last Amended**: 28/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-recipe-food-cost/spec.md`, user story ADM-5.1.2, clarification agreements from 2026-10-25 and 2026-10-28, and Constitution v1.2.0.

---

## Executive Summary

This feature delivers the complete full-stack vertical slice (Principle X) for automated raw material cost calculation and financial recipe audit (`ADM-5.1.2`) for the EasyEat multi-tenant platform.

Following architectural reconciliation and formal scope boundary alignment:
- Recipe persistence (`dish_recipe_ingredients`) and technical recipe composition by Chefs (`CHF-2.2.2`, `PUT /api/v1/dishes/{id}/recipe`) belong exclusively to feature 005 via Flyway migration `V7__chef_dish_management.sql`.
- Feature 006 focuses strictly on financial cost computation, audit breakdown, and administrative visualization:
  - Pure calculation engine (`FoodCostEngine`) applying exact arithmetic with `BigDecimal` (`HALF_UP`, scale 2) without fractional-cent drift.
  - Domain audit service (`FoodCostAuditService`) resolving recipe ingredients, taking the authoritative catalog cost (`ingredients.unit_purchase_cost`), detecting unpriced ingredients, and aggregating declared dietary codes.
  - Secure REST controller (`AdminDishCostAuditController`) exposing `GET /api/v1/dishes/{id}/cost-audit` restricted to `ADMIN` / `ADMINISTRADOR` and enforcing the `PENDING_PRICE` state gate at the HTTP boundary (`R-006-1`).
  - Frontend Back-Office view: dedicated `DishCostAuditPage` hosting `CostAuditPanel`, reachable from the pending review notification tray in `AdminWorkspacePage`.

---

## Technical Context

**Language / Version**: Java 21 (Backend), TypeScript 5.6 / React 18 (Frontend)  
**Primary Dependencies**:
- *Backend*: Spring Boot 3.3.4 (Web, Data JPA, Security, Validation), Flyway, PostgreSQL JDBC, Lombok, AssertJ, Testcontainers (PostgreSQL 16).
- *Frontend*: React 18.3.1, React Router DOM 6.27, Tailwind CSS 3.4, Axios 1.7, Lucide React, Vitest 2.1, React Testing Library.

**Storage & Persistence**:
- PostgreSQL 16 with native `UUID`, `TIMESTAMPTZ`, and `NUMERIC(12, 4)`.
- Reuses tables provisioned by feature 005: `dishes`, `dish_recipe_ingredients`, and `dish_dietary_restrictions`. No new migration required.

**Numeric Precision & Rules**:
- Authoritative unit acquisition cost: `ingredients.unit_purchase_cost` (`R-004-1`).
- Exact `BigDecimal` arithmetic: $\text{lineSubtotal} = \text{quantity} \times \text{unit\_purchase\_cost}$.
- Scale 2 rounding (`RoundingMode.HALF_UP`) on each line subtotal and total Food Cost.
- Unpriced detection: flagged if `unitPurchaseCost == null` or $\le 0$.

**Testing Strategy**:
- Pure domain unit tests: `FoodCostEngineTest` (100% path coverage on arithmetic, zero cost, and null cases).
- Domain service unit tests: `FoodCostAuditServiceTest` (mocked repositories, dietary mapping, empty recipe handling).
- Controller slice tests: `AdminDishCostAuditControllerTest` (MockMvc RBAC, 401 unauthenticated, 403 non-admin, 409 draft gate).
- Persistence & Database integration tests: `DishCostAuditIT` (PostgreSQL / Testcontainers verifying live price updates, empty recipe error, and tenant isolation).
- Frontend component tests: `CostAuditPanel.test.tsx` (table rendering, currency format, unpriced warning banner).
- Frontend user journey integration test: `us2_dish_cost_audit.test.tsx` (navigation from workspace, fetching audit, inspecting itemized costs).

---

## Constitution Check

| Principle | Status | Evidence and Justification |
|---|---|---|
| **I. The Specification Is the Source of Truth** | ✅ PASS | All components strictly mirror requirements FR-001 through FR-010 and acceptance criteria in `spec.md`. |
| **II. Specification Before Implementation** | ✅ PASS | Reconciled `spec.md`, `contracts/openapi.yaml`, and `plan.md` precede missing task implementation. |
| **III. Layered Verification Proportional to Risk** | ✅ PASS | 4-tier testing: domain unit tests, MockMvc security tests, PostgreSQL integration tests (`DishCostAuditIT`), and Vitest UI tests. |
| **IV. Simple, Cohesive, and Justified Design** | ✅ PASS | Pure stateless engine, decoupled service callable by downstream specs, and reuse of spec 005 schema without duplicate entities. |
| **V. Bounded Scope** | ✅ PASS | Scope confined strictly to ADM-5.1.2. CHF-2.2.2 purged. Retail pricing (ADM-5.2.1) and catalog approval deferred to spec 007. |
| **VI. Controlled Technical Debt** | ✅ PASS | Decoupled state gate avoids duplicate DTO assembly in spec 007 (`R-006-1`). Single price source documented (`R-004-1`). |
| **VII. Traceability, Consistency, and Documentation** | ✅ PASS | Tasks directly reference ADM-5.1.2 requirements. OpenAPI contract synchronized. |
| **VIII. Evidence Over Assumption and Human Escalation** | ✅ PASS | Ambiguities escalated and resolved via `grill-me` (scope purge, controller gate, master price source). |
| **IX. Engineering Over Convenience** | ✅ PASS | Real PostgreSQL Testcontainers integration test implemented rather than relying solely on mocks. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | ✅ PASS | Delivers end-to-end vertical slice: backend REST endpoint + frontend page, panel, navigation link, and tests. |
| **XI. Continuous Upstream Synchronization Awareness** | ✅ PASS | Branch fully merged and synchronized with `origin/main` and `origin/develop/sprint-2`. |

---

## Project Structure

### Documentation (`specs/006-recipe-food-cost/`)
```text
specs/006-recipe-food-cost/
├── plan.md              # Technical implementation plan (this file)
├── spec.md              # Reconciled specification (ADM-5.1.2)
├── manual-test-plan.md  # Verification runbook and manual test scenarios
├── contracts/
│   └── openapi.yaml     # OpenAPI 3.0 interface specification (GET /cost-audit)
└── tasks.md             # Reconciled tasks breakdown
```

### Source Code
```text
easyeat/backend/src/
├── main/java/com/easyeat/dish/
│   ├── controller/AdminDishCostAuditController.java   # HTTP GET /cost-audit (RBAC + PENDING_PRICE gate)
│   ├── service/FoodCostAuditService.java             # Domain audit orchestration (status-agnostic)
│   ├── service/FoodCostEngine.java                   # Pure BigDecimal math calculation
│   ├── dto/FoodCostAuditResponse.java                # Output response DTO
│   ├── dto/RecipeItemAuditResponse.java              # Itemized line DTO
│   └── exception/EmptyRecipeException.java           # Maps to 400 Bad Request
└── test/java/com/easyeat/dish/
    ├── controller/AdminDishCostAuditControllerTest.java # MockMvc RBAC & gate slice tests
    ├── service/FoodCostAuditServiceTest.java          # Service logic tests
    ├── service/FoodCostEngineTest.java                # Math precision unit tests
    └── integration/DishCostAuditIT.java               # PostgreSQL Testcontainers IT

easyeat/frontend/src/
├── api/dishApi.ts                                     # getFoodCostAudit client call
├── components/dish/CostAuditPanel.tsx                 # Cost breakdown presentation panel
├── pages/DishCostAuditPage.tsx                        # Admin cost audit page view
├── pages/AdminWorkspacePage.tsx                       # Link to audit from pending reviews
└── router/AppRoutes.tsx                               # Route /admin/dishes/:id/cost-audit
easyeat/frontend/tests/
├── components/dish/CostAuditPanel.test.tsx            # Unit test for presentation
└── integration/us2_dish_cost_audit.test.tsx           # User journey integration test
```

---

## API Surface

**`GET /api/v1/dishes/{id}/cost-audit`**

- **Role**: `ADMIN` / `ADMINISTRADOR` (HTTP 403 for other roles).
- **HTTP State Gate**: `PENDING_PRICE` only (HTTP 409 Conflict if in `DRAFT`).
- **Response**: `200 OK` with `FoodCostAuditResponse`.
