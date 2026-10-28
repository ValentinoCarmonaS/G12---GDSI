---
description: "Task list for feature 006 - Automated Food Cost Engine and Recipe Audit Breakdown (ADM-5.1.2)"
---

# Tasks: 006-recipe-food-cost

**Input**: Design documents from `/specs/006-recipe-food-cost/`
**Target Story**: ADM-5.1.2 (Recipe Audit, Ingredients, and Base Food Cost for Financial Administrator)
**Prerequisites**: `spec.md`, `plan.md`, `contracts/openapi.yaml`, `manual-test-plan.md`
**Tests**: TDD required (Unit, Integration, Component, and User Journey tests)

---

## Phase 1: Setup & Upstream Prerequisites (Satisfied by Spec 005)

- [x] T001 Upstream schema Flyway migration `V7__chef_dish_management.sql` providing `dishes`, `dish_recipe_ingredients`, and `dish_dietary_restrictions`.
- [x] T002 Upstream domain aggregate `com.easyeat.catalog.domain.Dish`, `DishStatus`, and `DishRecipeIngredient`.

---

## Phase 2: Core Domain & Calculation Engine (Backend)

- [x] T003 [P] [US1] Implement pure domain component `FoodCostEngine.java` applying exact `BigDecimal` arithmetic, scale 2 `RoundingMode.HALF_UP`, and unpriced ingredient detection (FR-006, FR-007).
- [x] T004 [P] [US1] Unit test `FoodCostEngineTest.java` covering arithmetic precision, zero fractional-cent drift, and boundary cases (SC-001).
- [x] T005 [P] [US1] Create DTOs `FoodCostAuditResponse`, `RecipeItemAuditResponse`, and exception `EmptyRecipeException` mapped to HTTP 400 (FR-008, FR-009).
- [x] T006 [P] [US1] Refactor `FoodCostAuditService.java` to decouple the `PENDING_PRICE` state gate (`R-006-1`), retrieve authoritative costs from `ingredients.unit_purchase_cost` (`R-004-1`), and aggregate dietary tags.
- [x] T007 [P] [US1] Update `FoodCostAuditServiceTest.java` verifying status-agnostic calculation, unpriced warnings, empty recipe error, and dietary restriction mapping.

---

## Phase 3: Controller & HTTP Boundary (Backend)

- [x] T008 [US1] Refactor `AdminDishCostAuditController.java` to enforce `PENDING_PRICE` status gate at HTTP controller layer (`R-006-1`, FR-003) and `@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")` (FR-002).
- [x] T009 [P] [US1] Update `AdminDishCostAuditControllerTest.java` verifying RBAC (401, 403), controller state gate (409 on DRAFT, 200 on PENDING_PRICE), and 400 on empty recipe (SC-003, SC-004).
- [x] T010 [P] [US1] Implement PostgreSQL integration test `DishCostAuditIT.java` in `src/test/java/com/easyeat/dish/integration/` validating database persistence, live inventory price updates, and multi-tenant isolation (SC-005, SC-006).

---

## Phase 4: Admin Back-Office Visual Audit (Frontend)

- [x] T011 [P] [US1] Add `getFoodCostAudit` client method in `dishApi.ts`.
- [x] T012 [P] [US1] Implement `CostAuditPanel.tsx` component displaying itemized cost table, unpriced warning alert, and dietary labels (FR-010).
- [x] T013 [P] [US1] Component test `CostAuditPanel.test.tsx` verifying currency rendering and warning alerts.
- [x] T014 [P] [US1] Implement `DishCostAuditPage.tsx` and register route `/admin/dishes/:id/cost-audit` in `AppRoutes.tsx`.
- [x] T015 [US1] Integrate "Auditar Costo" link in `AdminWorkspacePage.tsx` under pending dish review notifications, connecting the navigation flow (SC-007).
- [x] T016 [P] [US1] Implement user journey integration test `us2_dish_cost_audit.test.tsx` verifying end-to-end admin workflow from workspace to audit panel.

---

## Phase 5: Polish & Alignment

- [x] T017 [P] Synchronize OpenAPI contract `specs/006-recipe-food-cost/contracts/openapi.yaml` with ADM-5.1.2 endpoint.
- [x] T018 [P] Update `manual-test-plan.md` with step-by-step verification runbook for ADM-5.1.2.
- [x] T019 [P] Document decisions `R-006-1` and `R-004-1` on GitHub Issue #146, reconcile PR #155, and close absorbed scope issues.

---

## Dependencies & Execution Order

- **Phase 1 (Prerequisites)**: Satisfied.
- **Phase 2 & 3 (Backend Refactoring & IT Test)**: T006, T007, T008, T009, T010 (Completed).
- **Phase 4 (Frontend Connection & User Journey Test)**: T015, T016 (Completed).
- **Phase 5 (Polish & Documentation)**: T017, T018, T019 (Completed).
