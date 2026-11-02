---
description: "Task list for Feature 011: Detalle de Plato, Información Nutricional, Precios y Filtros Manuales de Catálogo"
---

# Tasks: 011-dish-detail-manual-filters

**Input**: Design documents from `/specs/011-dish-detail-manual-filters/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/behavioral-contracts.md`, `contracts/openapi.yaml`, `quickstart.md`).  
**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `research.md`, `data-model.md`, `contracts/`.  
**GitHub Issues**: [#222](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/222), [#223](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/223), [#224](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/224), [#225](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/225), [#226](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/226), [#227](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/227), [#228](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/228)  
**Tests**: Strict TDD methodology required. For every capability, RED test tasks are written first and must fail before the implementation tasks turn them GREEN. Target coverage is $\ge 80\%$.  
**Organization**: Tasks are grouped by phase and user story to enable independent implementation, testing, and MVP delivery.

---

## Phase 1: Setup (Shared Infrastructure) - [Issue #222](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/222)

**Purpose**: Verification of the local execution environment, baseline test suites, and test fixtures.

- [X] T001 Verify backend and frontend test environments (`mvn test-compile` in `easyeat/backend` and `npm run test -- --run` in `easyeat/frontend`)
- [X] T002 [P] Setup test fixtures and MSW mocks for catalog dish detail, allergens, and availability in `easyeat/frontend/src/test/mocks/dinerCatalogHandlers.ts`

---

## Phase 2: Foundational (Blocking Prerequisites) - [Issue #223](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/223)

**Purpose**: Core utilities, domain models, and API contracts that MUST be complete before ANY user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Unit tests (RED) for regional currency formatting (`$ 12.500,00`) and exact integer cent arithmetic in `easyeat/frontend/src/test/utils/currencyUtils.test.ts`
- [X] T004 [P] Implementation (GREEN) of regional currency formatting and cents arithmetic in `easyeat/frontend/src/utils/currencyUtils.ts`
- [X] T005 [P] Unit & Integration tests (RED) for neutral allergens mapping and catalog item DTO in `easyeat/backend/src/test/java/com/easyeat/catalog/service/DinerCatalogServiceTest.java`
- [X] T006 [P] Implementation (GREEN) extending `DishCatalogItemDto` with `allergens: List<String>` in `easyeat/backend/src/main/java/com/easyeat/catalog/dto/DishCatalogItemDto.java` and neutral mapping in `easyeat/backend/src/main/java/com/easyeat/catalog/service/DinerCatalogService.java`
- [X] T007 [P] Extend frontend TypeScript types with neutral allergens array in `easyeat/frontend/src/types/dinerCatalog.ts`
- [X] T008 [P] Contract tests (RED) for catalog detail and availability endpoints with neutral allergens in `easyeat/backend/src/test/java/com/easyeat/catalog/controller/DinerCatalogControllerTest.java`
- [X] T009 Verify MockMvc contract tests pass (GREEN) for catalog controller in `easyeat/backend/src/test/java/com/easyeat/catalog/controller/DinerCatalogControllerTest.java`

**Checkpoint**: Foundation ready — domain models, currency utilities, and neutral catalog contracts are verified. User story implementation can now begin.

---

## Phase 3: User Story 1 - Visualizar multimedia y nombre (Priority: P1) 🎯 MVP - [Issue #224](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/224)

**Goal (COM-5.1.1)**: Display overlaid dish detail panel with high-resolution photo, commercial name, and detailed description without leaving catalog. Preserve exact vertical scroll position (`window.scrollY`) on close (button or backdrop click). Support graceful degradation on slow network (immediate text + skeleton), omit container when no photo exists without empty placeholders, accommodate long descriptions with internal vertical scroll, and reactively reflect stock exhaustion or administrative cancellation in $\le 3\text{ s}$ without closing the modal.

**Independent Test**: Open modal from catalog, verify scroll restoration on close, verify absence of photo omits container, verify slow network shows text immediately with animated skeleton, and verify simulated stock variation updates UI to "Plato no disponible" in $\le 3\text{ s}$ without closing modal (AC-1.1 to AC-1.6, SC-001 to SC-005, SC-010).

### Tests for User Story 1 (TDD RED) ⚠️
> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

- [X] T010 [P] [US1] Component tests (RED) for modal opening and scroll coordinate capture/restoration on close (button and backdrop click) in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`
- [X] T011 [P] [US1] Component tests (RED) for conditional photo omission without broken placeholders, progressive image loading with skeleton, and long text internal scroll in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`
- [X] T012 [P] [US1] Integration tests (RED) for real-time availability polling invalidation ($\le 3\text{ s}$) in open modal without forced close and restock rehabilitation in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`

### Implementation for User Story 1 (GREEN)

- [X] T013 [US1] Implement scroll coordinate capture (`window.scrollY`) and instant restoration on modal close (button and backdrop) in `easyeat/frontend/src/components/catalog/DishDetailModal.tsx`
- [X] T014 [US1] Implement conditional photo container omission (completely omit container when `!photoUrl`, removing placeholders) in `easyeat/frontend/src/components/catalog/DishDetailModal.tsx`
- [X] T015 [US1] Implement progressive image loading with animated skeleton and load error handling in `easyeat/frontend/src/components/catalog/DishDetailModal.tsx`
- [X] T016 [US1] Implement internal vertical scroll container (`max-h-[85vh]`, `overflow-y-auto break-words`) for long descriptions in `easyeat/frontend/src/components/catalog/DishDetailModal.tsx`
- [X] T017 [US1] Integrate `useRealtimeAvailability` in `DishDetailModal.tsx` to handle reactive invalidation ($\le 3\text{ s}$) to "Plato no disponible" and restock rehabilitation without closing the modal
- [X] T018 [US1] Verify all component and integration tests pass (GREEN) for User Story 1 in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`

**Checkpoint**: User Story 1 (MVP) is fully functional, reactive, and independently testable.

---

## Phase 4: User Story 2 - Ver precio final y subtotal provisorio (Priority: P1) - [Issue #225](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/225)

**Goal (COM-5.1.3)**: Prominently display retail price with taxes included in regional format (`$ 12.500,00`) next to action buttons. Reflect administrative price changes upon next reload or reopen without requiring re-login. Provide quantity selector $N \ge 1$ with dynamic subtotal recalculation without artificial cap on estimation; enforce strict inventory validation against remaining kitchen stock (deducting quantities already in cart) when clicking "Agregar al Carrito". Ensure out-of-stock dishes display referential price with disabled actions.

**Independent Test**: Open modal, verify regional format on price, increment quantity selector $N=1,2,5$ and verify dynamic subtotal calculation, attempt adding $N > \text{remainingStock}$ and verify rejection with explicit notification, and verify out-of-stock dish displays price with disabled purchase controls (AC-3.1 to AC-3.5, SC-007 to SC-009, SC-011).

### Tests for User Story 2 (TDD RED) ⚠️
> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

- [X] T019 [P] [US2] Component tests (RED) for prominent final price with taxes, regional formatting, and out-of-stock referential price with disabled actions in `easyeat/frontend/src/test/catalog/DishCard.test.tsx` and `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`
- [X] T020 [P] [US2] Component tests (RED) for quantity selector without artificial cap, dynamic subtotal calculation, and estimation reset on close in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`
- [X] T021 [P] [US2] Unit tests (RED) for `CartContext.addItem` inventory validation against remaining stock (deducting existing cart quantity) in `easyeat/frontend/src/test/cart/CartContext.test.tsx`

### Implementation for User Story 2 (GREEN)

- [X] T022 [US2] Refactor quantity selector in `DishDetailModal.tsx` to remove artificial cap on increment and implement dynamic subtotal calculation using `currencyUtils.ts`
- [X] T023 [US2] Implement inventory validation on "Agregar al carrito" in `DishDetailModal.tsx` displaying insufficient stock warning when $N + q_{\text{en\_carrito}} > \text{stockDisponible}$
- [X] T024 [US2] Extend `CartContext.tsx` to validate available stock against accumulated cart quantities before appending items to cart state
- [X] T025 [US2] Update `DishCard.tsx` to ensure out-of-stock dishes display referential price and disable action buttons
- [X] T026 [US2] Verify all component, unit, and integration tests pass (GREEN) for User Story 2 in `DishCard.test.tsx`, `DishDetailModal.test.tsx`, and `CartContext.test.tsx`

**Checkpoint**: User Stories 1 AND 2 are fully functional and independently testable.

---

## Phase 5: User Story 3 - Ver ingredientes y alérgenos (Priority: P2) - [Issue #226](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/226)

**Goal (COM-5.1.2)**: Display complete ingredient breakdown and neutral, uniform allergen identifier pills for all diners without personalized dietary warnings or red alert badges (per Clarification #4). Dynamically reflect kitchen recipe modifications upon next load. Display explicit notice `"Lista de componentes no disponible"` when dishes have no registered ingredients.

**Independent Test**: Open dish with allergens and verify neutral pills without profile-based alert badges, open dish without recipe and verify explicit notice `"Lista de componentes no disponible"`, and verify kitchen recipe updates are reflected on subsequent load (AC-2.1 to AC-2.3, SC-006).

### Tests for User Story 3 (TDD RED) ⚠️
> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

- [X] T027 [P] [US3] Unit & Integration tests (RED) in backend for dynamic recipe reflection and empty ingredients handling in `easyeat/backend/src/test/java/com/easyeat/catalog/service/DinerCatalogServiceTest.java`
- [ ] T028 [P] [US3] Component tests (RED) for neutral allergen pills rendering and explicit `"Lista de componentes no disponible"` notice in `easyeat/frontend/src/test/catalog/DishDetailModal.test.tsx`

### Implementation for User Story 3 (GREEN)

- [ ] T029 [P] [US3] Create `AllergenNeutralPill.tsx` component for objective, uniform allergen rendering in `easyeat/frontend/src/components/catalog/AllergenNeutralPill.tsx`
- [ ] T030 [US3] Replace `AllergenWarningBadge` in `DishDetailModal.tsx` with `AllergenNeutralPill`, removing all personalized profile warnings per Clarification #4
- [ ] T031 [US3] Implement explicit notice `"Lista de componentes no disponible"` for dishes without ingredients in `DishDetailModal.tsx`
- [ ] T032 [US3] Implement neutral informative notice for dishes with ingredients but no declared allergens in `DishDetailModal.tsx`
- [ ] T033 [US3] Verify all unit and component tests pass (GREEN) for User Story 3 in `DinerCatalogServiceTest.java` and `DishDetailModal.test.tsx`

**Checkpoint**: User Stories 1, 2, AND 3 are fully functional and independently testable.

---

## Phase 6: User Story 4 - Búsqueda por filtros manuales (Priority: P2) - [Issue #227](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/227)

**Goal (COM-4.2.2)**: Provide manual filter sidebar with multiple category selection (OR semantics) and closed price range interval ($[P_{\min}, P_{\max}]$) with validation ($P_{\min} > P_{\max}$ blocks application). Compose with existing fast text search (COM-4.2.3) via conjunctive AND intersection. Provide empty results state with unified "Limpiar filtros" action that resets categories, price range, and search text simultaneously. Persist filters in `sessionStorage` partitioned by `tenantId` (surviving navigation to profile/cart, resetting upon restaurant switch). Ensure out-of-stock dishes matching filters remain visible in dimmed, disabled state.

**Independent Test**: Select multiple categories and verify OR union, apply price range and verify closed interval boundaries, enter search text and verify AND intersection across all dimensions, test $P_{\min} > P_{\max}$ and verify validation block, test unified clear button, and verify session persistence in `sessionStorage` per tenant (AC-4.1 to AC-4.6, SC-012 to SC-014).

### Tests for User Story 4 (TDD RED) ⚠️
> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

- [ ] T034 [P] [US4] Component tests (RED) for `ManualFiltersSidebar` category OR selection, price range closed interval, and $P_{\min} > P_{\max}$ validation block in `easyeat/frontend/src/test/catalog/ManualFiltersSidebar.test.tsx`
- [ ] T035 [P] [US4] Integration tests (RED) for conjunctive AND filtering, out-of-stock visibility under filters, empty results state notice, and unified clear in `easyeat/frontend/src/test/catalog/CatalogPage.test.tsx`
- [ ] T036 [P] [US4] Unit tests (RED) for `CatalogFilterContext` session persistence in `sessionStorage` per `tenantId` in `easyeat/frontend/src/test/context/CatalogFilterContext.test.tsx`

### Implementation for User Story 4 (GREEN)

- [ ] T037 [US4] Implement `ManualFiltersSidebar.tsx` with canonical category checkboxes, min/max price inputs, and inline validation for $P_{\min} > P_{\max}$ in `easyeat/frontend/src/components/catalog/ManualFiltersSidebar.tsx`
- [ ] T038 [US4] Implement `EmptyFilterResultsNotice.tsx` component with informative empty state message and unified "Limpiar filtros" button in `easyeat/frontend/src/components/catalog/EmptyFilterResultsNotice.tsx`
- [ ] T039 [US4] Extend `CatalogFilterContext.tsx` to manage `selectedCategories`, `minPrice`, `maxPrice`, `searchQuery`, unified reset, and `sessionStorage` persistence under `easyeat_catalog_filters_{tenantId}`
- [ ] T040 [US4] Integrate manual filters sidebar and unified predicate logic into `CatalogPage.tsx` composing categories OR, price range AND, text search AND, and unified clear
- [ ] T041 [US4] Ensure out-of-stock dishes remain visible and disabled under active manual filters in `CatalogPage.tsx`
- [ ] T042 [US4] Verify all tests pass (GREEN) for User Story 4 in `ManualFiltersSidebar.test.tsx`, `CatalogPage.test.tsx`, and `CatalogFilterContext.test.tsx`

**Checkpoint**: All User Stories (1, 2, 3, 4) are fully functional, integrated, and independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns - [Issue #228](https://github.com/ValentinoCarmonaS/G12---GDSI/issues/228)

**Purpose**: End-to-end scenario validation, full suite coverage verification ($\ge 80\%$), code quality audit, and documentation review.

- [ ] T043 [P] Execute end-to-end quickstart validation scenarios (1 to 7) against `specs/011-dish-detail-manual-filters/quickstart.md`
- [ ] T044 [P] Run full backend test suite (`mvn clean test`) and verify $\ge 80\%$ line and branch coverage in `easyeat/backend`
- [ ] T045 [P] Run full frontend test suite (`npm run test -- --run --coverage`) and verify $\ge 80\%$ line and branch coverage in `easyeat/frontend`
- [ ] T046 Code cleanup, formatting, and technical debt audit across all modified backend and frontend files

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — executes immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories**.
- **User Stories (Phases 3 to 6)**: All depend on Foundational phase completion.
  - Phase 3 (US1 - COM-5.1.1): P1 priority (MVP candidate).
  - Phase 4 (US2 - COM-5.1.3): P1 priority (integrates pricing with US1 modal).
  - Phase 5 (US3 - COM-5.1.2): P2 priority (integrates neutral allergens with US1 modal).
  - Phase 6 (US4 - COM-4.2.2): P2 priority (independent catalog filtering sidebar & page integration).
- **Polish (Phase 7)**: Depends on completion of all user story phases.

### Within Each User Story (Strict TDD)

1. Tests MUST be written FIRST and verified to FAIL (RED).
2. Domain/Component implementation is written to turn tests GREEN.
3. Verification passes before marking the checkpoint complete.

---

## Parallel Opportunities

```bash
# Parallel execution in Phase 2 (Foundational):
Task T003: "Unit tests (RED) in currencyUtils.test.ts"
Task T005: "Unit & Integration tests (RED) in DinerCatalogServiceTest.java"
Task T007: "Extend frontend types in dinerCatalog.ts"

# Parallel execution in Phase 3 (User Story 1 Tests):
Task T010: "Component tests (RED) for scroll restoration in DishDetailModal.test.tsx"
Task T011: "Component tests (RED) for photo omission & skeleton in DishDetailModal.test.tsx"
Task T012: "Integration tests (RED) for real-time invalidation in DishDetailModal.test.tsx"

# Parallel execution in Phase 4 (User Story 2 Tests):
Task T019: "Component tests (RED) in DishCard.test.tsx and DishDetailModal.test.tsx"
Task T020: "Component tests (RED) for selector & subtotal in DishDetailModal.test.tsx"
Task T021: "Unit tests (RED) in CartContext.test.tsx"

# Parallel execution in Phase 6 (User Story 4 Tests):
Task T034: "Component tests (RED) in ManualFiltersSidebar.test.tsx"
Task T035: "Integration tests (RED) in CatalogPage.test.tsx"
Task T036: "Unit tests (RED) in CatalogFilterContext.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (blocking prerequisites).
3. Complete Phase 3: User Story 1 (COM-5.1.1).
4. **STOP and VALIDATE**: Test User Story 1 independently with Scenarios 1, 2, and 3 from `quickstart.md`.

### Incremental Delivery

1. Setup + Foundational $\rightarrow$ Core ready.
2. User Story 1 $\rightarrow$ Overlaid modal with scroll preservation, image degradation, and real-time invalidation (MVP!).
3. User Story 2 $\rightarrow$ Prominent pricing, selector without cap, dynamic subtotal, and cart inventory validation.
4. User Story 3 $\rightarrow$ Neutral allergen badges and explicit missing recipe notice.
5. User Story 4 $\rightarrow$ Manual filters sidebar (categories OR, price range AND, text search AND, unified clear, session persistence).
6. Polish $\rightarrow$ Full suite run, coverage check $\ge 80\%$, quickstart end-to-end verification.
