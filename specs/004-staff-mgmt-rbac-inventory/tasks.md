    # Implementation Tasks: Staff Management, RBAC, and Initial Ingredient Inventory

**Feature**: Staff Management, RBAC, and Initial Ingredient Inventory  
**Branch**: `004-staff-mgmt-rbac-inventory`  
**Spec**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/spec.md) | **Plan**: [plan.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/plan.md)  
**Data Model**: [data-model.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/data-model.md) | **Contracts**: [openapi.yaml](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/contracts/openapi.yaml)  

---

## Phase 1: Setup (Shared Infrastructure & Environment)

**Purpose**: Establish package structure and configuration defaults for iteration 004 within the existing Spring Boot backend.

- [x] T001 Initialize package structure for staff and inventory modules under `easyeat/backend/src/main/java/com/easyeat/`
- [x] T002 [P] Configure global method security by adding `@EnableMethodSecurity` in `easyeat/backend/src/main/java/com/easyeat/config/MethodSecurityConfig.java`
- [x] T003 [P] Add domain exception classes (`OrphanedAdminException` in `staff/exception`, `NegativeStockException` in `inventory/exception`, `TenantViolationException` in `common/exception`)
- [x] T004 Update `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` to map custom exceptions to RFC 9457 `ProblemDetail` responses with HTTP status codes 400, 403, 404, 409, and 422

---

## Phase 2: Foundational (Data Migrations & Multi-Tenant Security)

**Purpose**: Core database schema evolution and multi-tenant security filters that block all user stories.

**⚠️ CRITICAL**: No user story implementation can proceed until this phase is complete and verified.

- [x] T005 [P] Create Flyway migration script `easyeat/backend/src/main/resources/db/migration/V3__extend_users_staff.sql` extending `users` with `full_name`, `must_change_password`, and updated role/status CHECK constraints
- [x] T006 [P] Create Flyway migration script `easyeat/backend/src/main/resources/db/migration/V4__create_ingredients_and_stock_audit.sql` defining `ingredients` and append-only `stock_audit_entries` tables, indexes, constraints, and immutability rules
- [x] T007 Implement `TenantContextHolder` backed by `ThreadLocal<UUID>` in `easyeat/backend/src/main/java/com/easyeat/common/context/TenantContextHolder.java`
- [x] T008 Implement `TenantContextFilter` extracting and validating `tenant_id` from JWT claims into `TenantContextHolder` in `easyeat/backend/src/main/java/com/easyeat/common/filter/TenantContextFilter.java`
- [x] T009 Implement structured security audit logger emitting JSON audit events for access denials in `easyeat/backend/src/main/java/com/easyeat/auth/security/SecurityAuditLogger.java`
- [x] T010 Register `TenantContextFilter` into the Spring Security filter chain after `JwtAuthenticationFilter` in `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java`

**Checkpoint**: Foundational database schemas and tenant isolation filters verified.

---

## Phase 3: User Story 1 & 2 - Staff Registration, Profile Management & RBAC Enforcement (Priority: P1) 🎯 MVP

**Goal**: Enable Administrators to register restaurant staff members with secure, one-time temporary credentials, view/edit rosters, prevent orphaned administrative accounts, and enforce role-based access boundaries across the platform.

**Independent Test**: Register staff members as `ADMIN`, `CHEF`, and `COUNTER_CLERK`, verify one-time plaintext credential display with clipboard copy support, verify role reassignments and orphaned admin blocking, and confirm that non-admin roles receive 403 Forbidden on restricted endpoints.

### Tests for User Story 1 & 2 (TDD - Write First) ⚠️

- [x] T011 [P] [US1] Unit test `StaffServiceTest` in `easyeat/backend/src/test/java/com/easyeat/staff/service/StaffServiceTest.java` covering staff registration, temporary credential generation, case-insensitive email normalization, and orphaned admin prevention
- [x] T012 [P] [US1] Web layer contract test `StaffControllerTest` in `easyeat/backend/src/test/java/com/easyeat/staff/controller/StaffControllerTest.java` verifying `POST /api/v1/admin/staff`, `GET /api/v1/admin/staff`, and `PUT /api/v1/admin/staff/{id}`
- [x] T013 [P] [US2] Security integration test `TenantIsolationIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/security/TenantIsolationIntegrationTest.java` verifying that `CHEF`, `COUNTER_CLERK`, and `DINER` roles are rejected with 403 Forbidden on admin endpoints, cross-tenant data requests return 404/403, and security audit log events are recorded

### Implementation for User Story 1 & 2

- [x] T014 [P] [US1] Create Staff DTOs (`StaffRole`, `StaffAccountStatus`, `StaffRegistrationRequest`, `StaffRegistrationResponse`, `StaffUpdateRequest`, `StaffMemberResponse`) in `easyeat/backend/src/main/java/com/easyeat/staff/dto/`
- [x] T015 [P] [US1] Update `User` entity in `easyeat/backend/src/main/java/com/easyeat/auth/domain/User.java` mapping `full_name`, `must_change_password`, and updated role/status enums
- [x] T016 [US1] Implement `StaffUserRepository` with tenant-scoped query methods and active admin count queries in `easyeat/backend/src/main/java/com/easyeat/staff/repository/StaffUserRepository.java`
- [x] T017 [US1] Implement `StaffService` in `easyeat/backend/src/main/java/com/easyeat/staff/service/StaffService.java` handling user registration with `SecureRandom` 12-char credential generation, BCrypt hashing (cost 12), lowercase normalization, and orphaned admin verification on updates
- [x] T018 [US1] Implement `StaffController` exposing `/api/v1/admin/staff` endpoints protected with `@PreAuthorize("hasRole('ADMIN')")` in `easyeat/backend/src/main/java/com/easyeat/staff/controller/StaffController.java`
- [x] T019 [US1] Implement `FirstLoginPasswordChangeRequest` DTO and endpoint `POST /api/v1/auth/first-login-password-change` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/FirstLoginPasswordController.java` to transition account from `PENDING_FIRST_LOGIN` to `ACTIVE`
- [x] T020 [US2] Configure authorization rules in `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java` ensuring `/api/v1/admin/**` requires `ROLE_ADMIN` and unauthenticated/unauthorized requests emit structured security audit entries

**Checkpoint**: User Stories 1 and 2 fully functional. Staff can be registered, roles enforced, and temporary credentials changed on first login.

---

## Phase 4: User Story 3 - Ingredient Master Catalog CRUD & Lifecycle Management (Priority: P2)

**Goal**: Allow Administrators to create, view, edit, discontinue, reactivate, and permanently archive raw kitchen ingredients, enforcing unique naming, fixed categories, and unit-of-measure modification restrictions.

**Independent Test**: Create ingredients with valid attributes, verify rejection of duplicate names within the same tenant, test editing properties, verify that unit-of-measure changes are blocked when stock is non-zero, verify transition to `DISCONTINUED` when stock > 0, explicit reactivation back to `ACTIVE`, and archival strictly when stock is zero.

### Tests for User Story 3 (TDD - Write First) ⚠️

- [x] T021 [P] [US3] Unit test `IngredientServiceTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/service/IngredientServiceTest.java` validating ingredient creation, unique tenant name checks, unit of measure modification locks, and state transitions (`Active` ↔ `Discontinued` → `Archived` only at zero stock)
- [x] T022 [P] [US3] Web layer contract test `AdminIngredientControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/AdminIngredientControllerTest.java` validating `POST`, `GET`, `PUT`, and `PATCH /status` endpoints on `/api/v1/admin/ingredients/**`

### Implementation for User Story 3

- [x] T023 [P] [US3] Create Ingredient DTOs (`IngredientCategory`, `UnitOfMeasure`, `IngredientStatus`, `CreateIngredientRequest`, `UpdateIngredientRequest`, `AdminIngredientDetailResponse`, `IngredientStatusUpdateRequest`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/`
- [x] T024 [P] [US3] Create `Ingredient` JPA entity in `easyeat/backend/src/main/java/com/easyeat/inventory/entity/Ingredient.java` mapping `tenant_id`, `name`, `category`, `unit_of_measure`, `unit_purchase_cost` (`BigDecimal`), `status`, and `current_stock` (`BigDecimal`)
- [x] T025 [US3] Create `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` with tenant-scoped lookups, name existence checks, and status filtering
- [x] T026 [US3] Implement `IngredientService` in `easyeat/backend/src/main/java/com/easyeat/inventory/service/IngredientService.java` with CRUD business logic, lifecycle transition rules, and unit-of-measure modification validation
- [x] T027 [US3] Implement `AdminIngredientController` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java` exposing `/api/v1/admin/ingredients/**` guarded with `@PreAuthorize("hasRole('ADMIN')")`

**Checkpoint**: User Story 3 fully functional. Master ingredients catalog can be managed throughout its lifecycle.

---

## Phase 5: User Story 4 - Initial Stock Load & Manual Inventory Adjustments with Audit Trail (Priority: P2)

**Goal**: Enable Administrators to adjust physical stock in Absolute and Relative modes, enforce the non-negative stock invariant (`resulting_stock >= 0`), reject zero deltas, serialize concurrent adjustments via pessimistic locking, and maintain an immutable append-only audit trail.

**Independent Test**: Execute Absolute and Relative adjustments, verify calculated deltas and new balances, confirm rejection of negative balances and zero deltas, inspect `stock_audit_entries` for immutability, and run multithreaded concurrent adjustments verifying zero lost updates and no race conditions.

### Tests for User Story 4 (TDD - Write First) ⚠️

- [x] T028 [P] [US4] Unit test `StockAdjustmentServiceTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/service/StockAdjustmentServiceTest.java` verifying Absolute mode, Relative mode, non-negative invariant rejection, zero-delta rejection, mandatory justification, and audit record generation
- [x] T029 [P] [US4] Multithreaded integration test `StockAdjustmentConcurrencyTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/concurrency/StockAdjustmentConcurrencyTest.java` running against Testcontainers PostgreSQL, verifying that simultaneous adjustments are serialized with pessimistic locking and never result in negative stock
- [x] T030 [P] [US4] Web layer contract test `StockAdjustmentControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/StockAdjustmentControllerTest.java` verifying `POST /api/v1/admin/ingredients/{id}/stock-adjustments` and `GET /api/v1/admin/ingredients/{id}/stock-audit`

### Implementation for User Story 4

- [x] T031 [P] [US4] Create Stock Adjustment DTOs (`StockAdjustmentMode`, `StockAdjustmentRequest`, `StockAdjustmentResponse`, `StockAuditEntryResponse`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/`
- [x] T032 [P] [US4] Create `StockAuditEntry` JPA entity in `easyeat/backend/src/main/java/com/easyeat/inventory/entity/StockAuditEntry.java` mapping `tenant_id`, `ingredient_id`, `user_id`, `adjustment_mode`, `previous_stock`, `new_stock`, `net_delta`, and `justification`
- [x] T033 [US4] Add pessimistic locking query `findByIdAndTenantIdForUpdate` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java`
- [x] T034 [US4] Create `StockAuditEntryRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/StockAuditEntryRepository.java` providing append-only `save()` and chronological retrieval methods
- [x] T035 [US4] Implement `StockAdjustmentService` in `easyeat/backend/src/main/java/com/easyeat/inventory/service/StockAdjustmentService.java` handling `@Transactional` stock calculation, non-negative validation, discrete vs continuous unit scale validation, and immutable audit persistence
- [x] T036 [US4] Add adjustment endpoints `POST /api/v1/admin/ingredients/{id}/stock-adjustments` and `GET /api/v1/admin/ingredients/{id}/stock-audit` to `AdminIngredientController` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java`

**Checkpoint**: User Story 4 fully functional. Stock adjustments are atomic, audit trail is immutable, and concurrency is safely locked.

---

## Phase 6: User Story 5 - Kitchen Ingredient Catalog Exploration (Priority: P3)

**Goal**: Provide Chefs with a paginated, sorted kitchen catalog of active ingredients displaying stock availability (`IN_STOCK` / `OUT_OF_STOCK`), while strictly hiding unit purchase costs and audit logs.

**Independent Test**: Authenticate as Chef, request paginated catalog, verify default 20 items per page sorted alphabetically A-Z, confirm that active ingredients with zero stock display `OUT_OF_STOCK`, confirm discontinued ingredients are excluded, test category and text filters, and verify purchase costs are completely omitted from the payload.

### Tests for User Story 5 (TDD - Write First) ⚠️

- [x] T037 [P] [US5] Web and service contract test `ChefCatalogControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/ChefCatalogControllerTest.java` verifying 20-item pagination, alphabetical A-Z sorting, category filtering, name search, zero-stock inclusion, discontinued item exclusion, and financial cost suppression

### Implementation for User Story 5

- [x] T038 [P] [US5] Create Chef Catalog DTOs (`StockAvailabilityStatus`, `ChefIngredientItemResponse`, `ChefIngredientPageResponse`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/` without purchase cost or audit attributes
- [x] T039 [US5] Add Chef catalog query method to `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` filtering by `tenant_id`, `status = 'ACTIVE'`, optional category, and case-insensitive name match with `Pageable`
- [x] T040 [US5] Implement `ChefCatalogController` exposing `GET /api/v1/chef/ingredients` guarded with `@PreAuthorize("hasRole('CHEF')")` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/ChefCatalogController.java`

**Checkpoint**: User Story 5 fully functional. Kitchen staff can browse available ingredients safely.

---

## Phase 7: Polish & End-to-End Validation (Backend)

**Purpose**: Validate end-to-end user journeys against quickstart scenarios, ensure build hygiene, and verify zero cross-tenant leakage.

- [x] T041 Implement end-to-end integration test `StaffAndInventoryQuickstartIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/integration/StaffAndInventoryQuickstartIntegrationTest.java` executing Scenarios 1 through 8 from `quickstart.md`
- [x] T042 [P] Verify code quality, formatting, and zero unhandled exceptions via Maven build: `./mvnw clean verify`
- [x] T043 [P] Validate OpenAPI contract compliance against running controllers in `easyeat/backend/src/test/java/com/easyeat/contract/OpenApiContractValidationTest.java`

---

## Phase 8: Frontend Foundational Types, API Clients & Route Perimeter (#105)

**Purpose**: Define TypeScript domain contracts and Axios API client modules connecting frontend interfaces to the existing backend services.

- [x] T044 [P] [FR-001, FR-010, FR-011] Define Staff and Inventory TypeScript types and DTO interfaces in `easyeat/frontend/src/types/staff.ts` and `easyeat/frontend/src/types/inventory.ts`
- [x] T045 [P] [FR-001, FR-004, FR-005, FR-028] Implement `staffApi.ts` client in `easyeat/frontend/src/api/staffApi.ts` for listing, creating, and updating staff members
- [x] T046 [P] [FR-010, FR-012, FR-014, FR-015, FR-019, FR-021, FR-031, FR-032] Implement `inventoryApi.ts` client in `easyeat/frontend/src/api/inventoryApi.ts` for ingredient CRUD, lifecycle status updates, stock adjustments, audit history, and chef catalog queries
- [x] T047 [P] [FR-003, FR-030] Extend `authApi.ts` in `easyeat/frontend/src/api/authApi.ts` with `changeFirstLoginPassword` method
- [x] T048 [P] [FR-001, FR-010] Write unit tests for API clients in `easyeat/frontend/tests/api/staffApi.test.ts` and `easyeat/frontend/tests/api/inventoryApi.test.ts`

---

## Phase 9: Mandatory First-Login Password Change Flow (FR-003, FR-030) (#106)

**Goal**: Force newly registered staff members logging in for the first time to change their temporary credential before accessing operational workspaces.

- [x] T049 [P] [FR-003, FR-030] Inject `must_change_password` claim in backend `JwtService.java` and `AuthenticatedUserResponse.java` and adapt `AuthContext.tsx` to decode and expose it in user state
- [x] T050 [P] [FR-030] Write failing router and guard tests in `easyeat/frontend/tests/router/FirstLoginRouting.test.tsx` verifying redirection to `/first-login-password-change` when `mustChangePassword = true` and blocking operational routes
- [x] T051 [FR-030] Update `RoleRouteGuard.tsx` and `postAuthRouting.ts` to intercept `mustChangePassword` and enforce routing to `/first-login-password-change`
- [x] T052 [P] [FR-030] Write failing component tests for `FirstLoginPasswordPage` in `easyeat/frontend/tests/pages/FirstLoginPasswordPage.test.tsx`
- [x] T053 [FR-003, FR-030] Implement `FirstLoginPasswordPage.tsx` in `easyeat/frontend/src/pages/FirstLoginPasswordPage.tsx` with validation, complexity checks, and successful submission transitioning account to active

---

## Phase 10: Staff Member Registration & Profile Management UI (FR-001, FR-002, FR-028, FR-029) (#107)

**Goal**: Provide Administrators with a staff management view displaying the establishment's staff roster, creating new staff with temporary credential modals, and editing roles with orphaned admin protection.

- [x] T054 [P] [FR-003, FR-029] Write failing tests for `TemporaryCredentialsModal` in `easyeat/frontend/tests/components/TemporaryCredentialsModal.test.tsx`
- [x] T055 [FR-003, FR-029] Implement `TemporaryCredentialsModal.tsx` in `easyeat/frontend/src/components/staff/TemporaryCredentialsModal.tsx` with single-click copy to clipboard and dismissal warning
- [x] T056 [P] [FR-001, FR-002, FR-005, FR-026] Write failing tests for `StaffFormModal` in `easyeat/frontend/tests/components/StaffFormModal.test.tsx`
- [x] T057 [FR-001, FR-002, FR-005, FR-006, FR-026] Implement `StaffFormModal.tsx` in `easyeat/frontend/src/components/staff/StaffFormModal.tsx` for creating and editing staff with real-time validation
- [x] T058 [P] [FR-004, FR-028] Write failing tests for `StaffRosterTable` and `StaffManagementPage` in `easyeat/frontend/tests/pages/StaffManagementPage.test.tsx`
- [x] T059 [FR-004, FR-028] Implement `StaffRosterTable.tsx` in `easyeat/frontend/src/components/staff/StaffRosterTable.tsx` and `StaffManagementPage.tsx` in `easyeat/frontend/src/pages/StaffManagementPage.tsx` mounted at `/admin/staff`

---

## Phase 11: Master Ingredient Catalog & Lifecycle Management UI (FR-010, FR-011, FR-014, FR-026, FR-031) (#108)

**Goal**: Enable Administrators to manage ingredients in `/admin/inventory`, creating new ingredients, editing attributes (locking UOM if stock > 0), and updating lifecycle states (Active, Discontinued, Archived).

- [x] T060 [P] [FR-010, FR-013, FR-026] Write failing tests for `IngredientFormModal` in `easyeat/frontend/tests/components/IngredientFormModal.test.tsx`
- [x] T061 [FR-010, FR-011, FR-013, FR-026] Implement `IngredientFormModal.tsx` in `easyeat/frontend/src/components/inventory/IngredientFormModal.tsx` with locked unit-of-measure rules when stock > 0
- [x] T062 [P] [FR-012, FR-014, FR-031] Write failing tests for `IngredientCatalogTable` and `InventoryManagementPage` in `easyeat/frontend/tests/pages/InventoryManagementPage.test.tsx`
- [x] T063 [FR-012, FR-031] Implement `IngredientCatalogTable.tsx` in `easyeat/frontend/src/components/inventory/IngredientCatalogTable.tsx` and `InventoryManagementPage.tsx` in `easyeat/frontend/src/pages/InventoryManagementPage.tsx` mounted at `/admin/inventory`
- [x] T064 [FR-014] Implement status transition actions (decommission to DISCONTINUED, reactivate to ACTIVE, archive to ARCHIVED locked if stock > 0)

---

## Phase 12: Manual Stock Adjustments & Immutable Audit Trail UI (FR-015, FR-016, FR-017, FR-018, FR-027, FR-031) (#109)

**Goal**: Allow Administrators to execute Absolute and Relative stock adjustments with live validation and view the chronological, immutable audit trail.

- [x] T065 [P] [FR-015, FR-016, FR-017, FR-018, FR-027] Write failing tests for `StockAdjustmentModal` in `easyeat/frontend/tests/components/StockAdjustmentModal.test.tsx` verifying Absolute/Relative modes, computed deltas, zero-delta block, and negative stock block
- [x] T066 [FR-015, FR-016, FR-017, FR-018, FR-027] Implement `StockAdjustmentModal.tsx` in `easyeat/frontend/src/components/inventory/StockAdjustmentModal.tsx`
- [x] T067 [P] [FR-019] Write failing tests for `StockAuditModal` in `easyeat/frontend/tests/components/StockAuditModal.test.tsx`
- [x] T068 [FR-019] Implement `StockAuditModal.tsx` in `easyeat/frontend/src/components/inventory/StockAuditModal.tsx` displaying chronological append-only audit trail entries

---

## Phase 13: Chef Read-Only Kitchen Ingredient Catalog UI (FR-021, FR-022, FR-023, FR-032) (#110)

**Goal**: Provide Chefs with a paginated, sorted kitchen catalog of active ingredients displaying stock availability (`IN_STOCK` / `OUT_OF_STOCK`), while strictly hiding unit purchase costs and audit logs.

- [x] T069 [P] [FR-021, FR-022, FR-023, FR-024, FR-032] Write failing tests for `ChefIngredientTable` and `ChefIngredientsPage` in `easyeat/frontend/tests/pages/ChefIngredientsPage.test.tsx` asserting pagination (20/page), A-Z sort, category filters, cost suppression, and availability badges
- [x] T070 [FR-021, FR-022, FR-023, FR-024, FR-032] Implement `ChefIngredientTable.tsx` in `easyeat/frontend/src/components/chef/ChefIngredientTable.tsx`
- [x] T071 [FR-021, FR-032] Implement `ChefIngredientsPage.tsx` in `easyeat/frontend/src/pages/ChefIngredientsPage.tsx` mounted at `/cocina/ingredientes` (with alias `/kds/ingredients`)
- [x] T072 [FR-032] Update `WorkspaceLayout.tsx` and `KitchenWorkspacePage.tsx` to provide accessible navigation to `/cocina/ingredientes`

---

## Phase 14: End-to-End Frontend Integration, Quality Gates & Regression Verification (#111)

**Purpose**: Tie together all workspaces, verify complete test suite, and ensure production build hygiene.

- [x] T073 [FR-028, FR-031] Update `AdminWorkspacePage.tsx` cards ("Personal Activo" and "Materias Primas") to link to `/admin/staff` and `/admin/inventory` displaying real counts
- [x] T074 [FR-028, FR-030, FR-031, FR-032] Register all new routes (`/first-login-password-change`, `/admin/staff`, `/admin/inventory`, `/cocina/ingredientes`, `/kds/ingredients`) in `AppRoutes.tsx` with proper guards and test in `easyeat/frontend/tests/router/AppRoutes.test.tsx`
- [x] T075 [P] [FR-028, FR-030, FR-031, FR-032] Verify full frontend test suite, typecheck, and production bundle build: `npx tsc --noEmit && npm test && npm run build`

---

## Phase 15: Inventory Refactor - Batches, Financial Shrinkage & Lifecycle Simplification (Priority: P2) (#125)

**Goal**: Refactor manual stock adjustments to record purchases as distinct cost-bearing batches, impute shrinkage/waste to specific lots with exact financial loss calculation, and simplify ingredient lifecycle to a reversible Active/Discontinued model.

**Independent Test**: Perform an "Agregar stock" operation creating Lot A ($2.000/kg) and Lot B ($1.000/kg), execute a "Restar stock" operation deducting 2 kg from Lot A, assert that Lot A balance decrements to 3 kg and an audit record records $4.000 financial loss, and verify that Discontinue/Reactivate operates reversibly without archive blockers.

### Database & Domain Model Foundation

- [x] T076 [P] [FR-014, FR-015a, FR-015b] Create Flyway migration script `easyeat/backend/src/main/resources/db/migration/V6__create_ingredient_batches_and_refactor_lifecycle.sql` creating `ingredient_batches` table, adding `batch_id`, `unit_cost`, and `financial_loss` to `stock_audit_entries`, and removing `ARCHIVED` from `ingredients.status`.
- [x] T077 [P] [FR-014, FR-015a] Implement `IngredientBatch.java` in `easyeat/backend/src/main/java/com/easyeat/inventory/entity/IngredientBatch.java`, create `IngredientBatchRepository.java` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientBatchRepository.java`, update `StockAuditEntry.java`, and update `IngredientStatus.java` removing `ARCHIVED`.

### Backend Implementation & Tests (TDD)

- [x] T078 [P] [FR-014, FR-015a, FR-015b] Write unit tests in `easyeat/backend/src/test/java/com/easyeat/inventory/service/StockAdjustmentServiceTest.java` and `IngredientServiceTest.java` covering batch creation on addition, lot deduction and financial loss on subtraction, rejection on insufficient batch balance, and reversible Active/Discontinued transitions.
- [x] T079 [FR-014, FR-015a, FR-015b, FR-015c] Refactor `StockAdjustmentService.java` in `easyeat/backend/src/main/java/com/easyeat/inventory/service/StockAdjustmentService.java` to implement batch creation on `ADD` and lot deduction with financial loss calculation on `SUBTRACT`, and refactor `IngredientService.java` removing archive restrictions.
- [ ] T080 [FR-015b] Add endpoint `GET /api/v1/admin/ingredients/{id}/batches` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/StockAdjustmentController.java` returning active batches with remaining balance, and update web contract tests in `StockAdjustmentControllerTest.java`.

### Frontend Implementation & Tests (TDD)

- [ ] T081 [P] [FR-014, FR-015a, FR-015b] Update TypeScript interfaces in `easyeat/frontend/src/types/inventory.ts` (`IngredientBatch`, `StockAdjustmentAction = 'ADD' | 'SUBTRACT'`, `IngredientStatus = 'ACTIVE' | 'DISCONTINUED'`) and update client methods in `easyeat/frontend/src/api/inventoryApi.ts` adding `getBatches(id)`.
- [ ] T082 [P] [FR-014, FR-015a, FR-015b] Update component tests in `easyeat/frontend/tests/components/StockAdjustmentModal.test.tsx` and `easyeat/frontend/tests/components/IngredientCatalogTable.test.tsx` asserting "Agregar stock" with purchase cost, "Restar stock" with batch selection and financial loss, and "Desactivar" / "Reactivar" buttons without "Archivar".
- [ ] T083 [FR-014, FR-015a, FR-015b] Refactor `StockAdjustmentModal.tsx` in `easyeat/frontend/src/components/inventory/StockAdjustmentModal.tsx` providing "Agregar stock" and "Restar stock" tabs with batch selector and computed loss, and refactor `IngredientCatalogTable.tsx` removing archive/desarchive actions in favor of single toggle "Desactivar" / "Reactivar".

### Full Verification & Regression Gate

- [ ] T084 [P] [FR-014, FR-015a, FR-015b] Execute full backend regression test suite (`mvn test`) and frontend test suite (`npm test && npm run build`) verifying zero regressions across staff, inventory, chef catalog, and dietary features.


---

## Dependencies & Execution Order

### Phase Dependencies

```
[Backend Slices]
Phase 1: Setup ──► Phase 2: Foundational (DB/Tenant) ──► Phase 3, 4, 5, 6 ──► Phase 7: Backend Polish (Complete [x])
                                                                                  │
                                                                                  ▼
[Frontend Slices]                                                          Phase 8: Frontend Types & API Clients
                                                                                  │
                                    ┌─────────────────────────────────────────────┼─────────────────────────────────────────────┐
                                    ▼                                             ▼                                             ▼
                     Phase 9: First-Login Password Change       Phase 10: Staff Management UI         Phase 11: Ingredient Catalog UI
                                                                                                                        │
                                                                                                                        ▼
                                                                                                              Phase 12: Stock Adjustments & Audit
                                                                                                                        │
                                                                                                                        ▼
                                                                                                              Phase 13: Chef Catalog UI
                                                                                                                        │
                                                                                  ┌─────────────────────────────────────┘
                                                                                  ▼
                                                                     Phase 14: E2E Integration & Quality Gates
                                                                                  │
                                                                                  ▼
                                                                     Phase 15: Inventory Refactor (T076–T084)
```

- **Backend (Phases 1-7)**: Complete and verified with unit, slice, and integration tests.
- **Frontend Types & Clients (Phase 8)**: Prerequisite for all frontend phases.
- **First-Login Flow (Phase 9)**: Unblocks authenticated staff accounts.
- **Staff Management (Phase 10)**: Provides admin controls to create staff accounts.
- **Ingredient Catalog & Stock (Phases 11 & 12)**: Provides master data and adjustments for inventory.
- **Chef Catalog (Phase 13)**: Depends on active ingredient data model from Phase 11.
- **Integration & Polish (Phase 14)**: Connects all views into `AppRoutes` and `WorkspaceLayout`, verifying full test suite and build.

---

## Parallel Execution Opportunities

### Phase 2 (Foundational)
- `T005` (Flyway V3) and `T006` (Flyway V4) can be written in parallel.
- `T007` (`TenantContextHolder`) and `T009` (`SecurityAuditLogger`) can be implemented in parallel.

### Phase 3 (US1 & US2)
- Tests `T011`, `T012`, and `T013` can be written in parallel by different developers.
- DTOs `T014` and Entity update `T015` can run in parallel.

### Phase 4 (US3)
- Tests `T021` and `T022` can be written in parallel.
- DTOs `T023` and Entity `T024` can be created in parallel.

### Phase 5 (US4)
- Tests `T028`, `T029`, and `T030` can be written in parallel.
- DTOs `T031` and Entity `T032` can be implemented in parallel.

---

## Implementation Strategy & MVP Rollout

### Milestone 1: MVP Delivery (Phases 1, 2, and 3)
1. Complete Setup and Foundational database migrations and security context.
2. Implement User Stories 1 and 2 (Staff Registration, Profile Management, and RBAC).
3. **Validate**: Administrator can provision staff, temporary credentials work once, and non-admin roles are rejected with 403 Forbidden.

### Milestone 2: Inventory Foundation (Phase 4)
1. Implement User Story 3 (Ingredient Master Catalog and Lifecycle).
2. **Validate**: Administrator can create ingredients, categories and units are enforced, and discontinue/reactivate/archive state machine operates correctly.

### Milestone 3: Stock Management & Kitchen Catalog (Phases 5 and 6)
1. Implement User Story 4 (Stock Adjustments, Pessimistic Locking, and Audit Trail).
2. Implement User Story 5 (Chef Kitchen Catalog with Cost Redaction).
3. **Validate**: Concurrency test passes with zero race conditions, and Chef can browse active inventory without seeing purchase costs.

### Milestone 4: Verification & Sign-off (Phase 7)
1. Execute full `quickstart.md` scenarios through `StaffAndInventoryQuickstartIntegrationTest`.
2. Confirm 100% requirements traceability and clean Maven build.
