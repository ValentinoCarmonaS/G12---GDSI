---
description: "Task list for feature 006 - Recipe Many-to-Many Model and Automated Food Cost Engine"
---

# Tasks: 006-recipe-food-cost

**Input**: Design documents from `/specs/006-recipe-food-cost/`

**Prerequisites**: `plan.md` (required), `spec.md` (required for user stories), `data-model.md`, `contracts/`

**Tests**: Tests are required (TDD) for backend validation and frontend components.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Create project structure per implementation plan (e.g., ensure Maven/Gradle modules, src/main/java, src/main/resources, frontend folder).
- [ ] T002 Initialize Spring Boot application with required dependencies (Spring Web, Spring Data JPA, Lombok, Validation, Security, Flyway).
- [ ] T003 [P] Configure linting and code formatting tools (Spotless, EditorConfig).
- [ ] T004 [P] Setup Git hooks for pre‑commit linting.

---

## Phase 2: Foundational (Blocking Prerequisites)

- [ ] T005 Setup database migration framework (Flyway) and create baseline migration for existing tables (dishes, ingredients, dish_recipe_ingredients).
- [ ] T006 [P] Implement authentication/authorization framework (Spring Security) with ADMIN and CHEF roles.
- [ ] T007 [P] Configure API routing and global exception handling.
- [ ] T008 Create base entity classes (`Dish`, `Ingredient`, `DishRecipeIngredient`) with JPA mappings in `src/main/java/com/easyeat/domain/`.
- [ ] T009 Configure multi‑tenant filter/interceptor to enforce tenant isolation.
- [ ] T010 Setup application.yml with datasource, flyway, and security settings.

---

## Phase 3: User Story 1 – Technical Recipe Composition & Persistence (Priority: P1) [US1]

**Goal**: Allow Chef to create/edit recipe ingredients via Many‑to‑Many association while dish is in DRAFT.

**Independent Test**: Verify that a draft dish can be saved with a valid list of ingredients; invalid data (zero qty, duplicates, discontinued, cross‑tenant) must be rejected.

### Tests (TDD) – Backend

- [ ] T011 [P] [US1] Write contract test for `POST /api/v1/dishes/{id}/recipe` validating request schema and response codes in `src/test/java/com/easyeat/api/DishRecipeControllerContractTest.java`.
- [ ] T012 [P] [US1] Write integration test for successful recipe persistence in `src/test/java/com/easyeat/integration/DishRecipePersistenceIT.java`.
- [ ] T013 [P] [US1] Write unit tests for `DishRecipeService.validateRecipe` covering quantity > 0, duplicate detection, discontinued ingredient, tenant mismatch.

### Implementation

- [ ] T014 [P] [US1] Create DTO classes `RecipeIngredientDto` and `RecipeRequestDto` in `src/main/java/com/easyeat/api/dto/`.
- [ ] T015 [US1] Implement `DishRecipeController` endpoint `POST /api/v1/dishes/{id}/recipe` handling validation and delegating to service.
- [ ] T016 [P] [US1] Implement `DishRecipeService` with method `saveRecipe(dishId, List<RecipeIngredientDto>)` performing atomic full‑replace using a transaction.
- [ ] T017 [P] [US1] Add JPA repository `DishRecipeIngredientRepository` with custom bulk delete/insert operations.
- [ ] T018 [P] [US1] Add validation annotations and custom validator for positive quantity and max 4 decimal places.
- [ ] T019 [P] [US1] Add exception handling for `DuplicateIngredientException`, `DiscontinuedIngredientException`, `TenantMismatchException`.
- [ ] T020 [P] [US1] Create unit test for service layer covering all validation branches.

### Frontend (if applicable)

- [ ] T021 [P] [US1] Create React component `RecipeEditor` in `frontend/src/components/RecipeEditor.jsx` with form fields for ingredient selection and quantity.
- [ ] T022 [P] [US1] Write Jest tests for `RecipeEditor` validating form validation rules (positive qty, duplicate prevention).

---

## Phase 4: User Story 2 – Recipe Audit & Food Cost Calculation (Priority: P1) [US2]

**Goal**: Administrator can request cost audit for a dish in PENDING_PRICE; system returns itemized cost with exact arithmetic and flags unpriced ingredients.

**Independent Test**: Verify audit endpoint returns correct total, rounding to 2 decimals, includes `has_unpriced_ingredients` flag when needed, and enforces RBAC and status checks.

### Tests (TDD) – Backend

- [ ] T023 [P] [US2] Write contract test for `GET /api/v1/dishes/{id}/cost-audit` checking response schema and RBAC in `src/test/java/com/easyeat/api/DishCostAuditControllerContractTest.java`.
- [ ] T024 [P] [US2] Write integration test for cost calculation correctness with sample ingredients in `src/test/java/com/easyeat/integration/DishCostAuditIT.java`.
- [ ] T025 [P] [US2] Write unit test for `FoodCostCalculator.calculate` covering exact `BigDecimal` arithmetic, rounding, and unpriced flag.

### Implementation

- [ ] T026 [P] [US2] Create DTO `CostAuditResponseDto` in `src/main/java/com/easyeat/api/dto/`.
- [ ] T027 [US2] Implement `DishCostAuditController` endpoint `GET /api/v1/dishes/{id}/cost-audit` with ADMIN role restriction.
- [ ] T028 [P] [US2] Implement `FoodCostCalculator` service using `BigDecimal` and `RoundingMode.HALF_UP` (2 decimal places).
- [ ] T029 [P] [US2] Add method to fetch ingredient costs and compute line subtotals, total, and unpriced flag.
- [ ] T030 [P] [US2] Extend `DishRepository` to verify dish status is `PENDING_PRICE` before audit.
- [ ] T031 [P] [US2] Add audit response mapping including dietary restriction codes.
- [ ] T032 [P] [US2] Write exception handling for unauthorized access and invalid dish state.

### Frontend (if applicable)

- [ ] T033 [P] [US2] Add admin view component `CostAuditPanel` in `frontend/src/pages/CostAudit.jsx` displaying itemized cost table.
- [ ] T034 [P] [US2] Write Cypress end‑to‑end test verifying ADMIN can view audit and non‑ADMIN is blocked.

---

## Phase 5: Polish & Cross‑Cutting Concerns

- [ ] T035 [P] Update API documentation (OpenAPI) to include new endpoints and request/response schemas.
- [ ] T036 [P] Add performance benchmark test ensuring audit endpoint returns < 100 ms for 50‑ingredient recipes.
- [ ] T037 [P] Run static analysis (SpotBugs, Sonar) and address any new issues.
- [ ] T038 [P] Write integration tests for multi‑tenant isolation on both endpoints.
- [ ] T039 [P] Update README with feature overview and how to run TDD tests.

---

## Dependencies & Execution Order

- **Setup (Phase 1)** has no dependencies.
- **Foundational (Phase 2)** depends on Phase 1.
- **User Stories (Phase 3 & 4)** depend on Phase 2.
- Within each user story, **tests (T011‑T013, T023‑T025)** must be written and fail before implementation tasks (T014‑T020, T026‑T032).
- **Polish (Phase 5)** depends on completion of all prior phases.

---

*All tasks follow the required checklist format and are ordered to enable independent development, testing, and delivery.*
