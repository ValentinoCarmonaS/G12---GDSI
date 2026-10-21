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

- [ ] T011 [P] [US1] Unit test `StaffServiceTest` in `easyeat/backend/src/test/java/com/easyeat/staff/service/StaffServiceTest.java` covering staff registration, temporary credential generation, case-insensitive email normalization, and orphaned admin prevention
- [ ] T012 [P] [US1] Web layer contract test `StaffControllerTest` in `easyeat/backend/src/test/java/com/easyeat/staff/controller/StaffControllerTest.java` verifying `POST /api/v1/admin/staff`, `GET /api/v1/admin/staff`, and `PUT /api/v1/admin/staff/{id}`
- [ ] T013 [P] [US2] Security integration test `TenantIsolationIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/security/TenantIsolationIntegrationTest.java` verifying that `CHEF`, `COUNTER_CLERK`, and `DINER` roles are rejected with 403 Forbidden on admin endpoints, cross-tenant data requests return 404/403, and security audit log events are recorded

### Implementation for User Story 1 & 2

- [ ] T014 [P] [US1] Create Staff DTOs (`StaffRole`, `StaffAccountStatus`, `StaffRegistrationRequest`, `StaffRegistrationResponse`, `StaffUpdateRequest`, `StaffMemberResponse`) in `easyeat/backend/src/main/java/com/easyeat/staff/dto/`
- [ ] T015 [P] [US1] Update `User` entity in `easyeat/backend/src/main/java/com/easyeat/auth/entity/User.java` mapping `full_name`, `must_change_password`, and updated role/status enums
- [ ] T016 [US1] Implement `StaffUserRepository` with tenant-scoped query methods and active admin count queries in `easyeat/backend/src/main/java/com/easyeat/staff/repository/StaffUserRepository.java`
- [ ] T017 [US1] Implement `StaffService` in `easyeat/backend/src/main/java/com/easyeat/staff/service/StaffService.java` handling user registration with `SecureRandom` 12-char credential generation, BCrypt hashing (cost 12), lowercase normalization, and orphaned admin verification on updates
- [ ] T018 [US1] Implement `StaffController` exposing `/api/v1/admin/staff` endpoints protected with `@PreAuthorize("hasRole('ADMIN')")` in `easyeat/backend/src/main/java/com/easyeat/staff/controller/StaffController.java`
- [ ] T019 [US1] Implement `FirstLoginPasswordChangeRequest` DTO and endpoint `POST /api/v1/auth/first-login-password-change` in `easyeat/backend/src/main/java/com/easyeat/auth/controller/FirstLoginPasswordController.java` to transition account from `PENDING_FIRST_LOGIN` to `ACTIVE`
- [ ] T020 [US2] Configure authorization rules in `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java` ensuring `/api/v1/admin/**` requires `ROLE_ADMIN` and unauthenticated/unauthorized requests emit structured security audit entries

**Checkpoint**: User Stories 1 and 2 fully functional. Staff can be registered, roles enforced, and temporary credentials changed on first login.

---

## Phase 4: User Story 3 - Ingredient Master Catalog CRUD & Lifecycle Management (Priority: P2)

**Goal**: Allow Administrators to create, view, edit, discontinue, reactivate, and permanently archive raw kitchen ingredients, enforcing unique naming, fixed categories, and unit-of-measure modification restrictions.

**Independent Test**: Create ingredients with valid attributes, verify rejection of duplicate names within the same tenant, test editing properties, verify that unit-of-measure changes are blocked when stock is non-zero, verify transition to `DISCONTINUED` when stock > 0, explicit reactivation back to `ACTIVE`, and archival strictly when stock is zero.

### Tests for User Story 3 (TDD - Write First) ⚠️

- [ ] T021 [P] [US3] Unit test `IngredientServiceTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/service/IngredientServiceTest.java` validating ingredient creation, unique tenant name checks, unit of measure modification locks, and state transitions (`Active` ↔ `Discontinued` → `Archived` only at zero stock)
- [ ] T022 [P] [US3] Web layer contract test `AdminIngredientControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/AdminIngredientControllerTest.java` validating `POST`, `GET`, `PUT`, and `PATCH /status` endpoints on `/api/v1/admin/ingredients/**`

### Implementation for User Story 3

- [ ] T023 [P] [US3] Create Ingredient DTOs (`IngredientCategory`, `UnitOfMeasure`, `IngredientStatus`, `CreateIngredientRequest`, `UpdateIngredientRequest`, `AdminIngredientDetailResponse`, `IngredientStatusUpdateRequest`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/`
- [ ] T024 [P] [US3] Create `Ingredient` JPA entity in `easyeat/backend/src/main/java/com/easyeat/inventory/entity/Ingredient.java` mapping `tenant_id`, `name`, `category`, `unit_of_measure`, `unit_purchase_cost` (`BigDecimal`), `status`, and `current_stock` (`BigDecimal`)
- [ ] T025 [US3] Create `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` with tenant-scoped lookups, name existence checks, and status filtering
- [ ] T026 [US3] Implement `IngredientService` in `easyeat/backend/src/main/java/com/easyeat/inventory/service/IngredientService.java` with CRUD business logic, lifecycle transition rules, and unit-of-measure modification validation
- [ ] T027 [US3] Implement `AdminIngredientController` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java` exposing `/api/v1/admin/ingredients/**` guarded with `@PreAuthorize("hasRole('ADMIN')")`

**Checkpoint**: User Story 3 fully functional. Master ingredients catalog can be managed throughout its lifecycle.

---

## Phase 5: User Story 4 - Initial Stock Load & Manual Inventory Adjustments with Audit Trail (Priority: P2)

**Goal**: Enable Administrators to adjust physical stock in Absolute and Relative modes, enforce the non-negative stock invariant (`resulting_stock >= 0`), reject zero deltas, serialize concurrent adjustments via pessimistic locking, and maintain an immutable append-only audit trail.

**Independent Test**: Execute Absolute and Relative adjustments, verify calculated deltas and new balances, confirm rejection of negative balances and zero deltas, inspect `stock_audit_entries` for immutability, and run multithreaded concurrent adjustments verifying zero lost updates and no race conditions.

### Tests for User Story 4 (TDD - Write First) ⚠️

- [ ] T028 [P] [US4] Unit test `StockAdjustmentServiceTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/service/StockAdjustmentServiceTest.java` verifying Absolute mode, Relative mode, non-negative invariant rejection, zero-delta rejection, mandatory justification, and audit record generation
- [ ] T029 [P] [US4] Multithreaded integration test `StockAdjustmentConcurrencyTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/concurrency/StockAdjustmentConcurrencyTest.java` running against Testcontainers PostgreSQL, verifying that simultaneous adjustments are serialized with pessimistic locking and never result in negative stock
- [ ] T030 [P] [US4] Web layer contract test `StockAdjustmentControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/StockAdjustmentControllerTest.java` verifying `POST /api/v1/admin/ingredients/{id}/stock-adjustments` and `GET /api/v1/admin/ingredients/{id}/stock-audit`

### Implementation for User Story 4

- [ ] T031 [P] [US4] Create Stock Adjustment DTOs (`StockAdjustmentMode`, `StockAdjustmentRequest`, `StockAdjustmentResponse`, `StockAuditEntryResponse`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/`
- [ ] T032 [P] [US4] Create `StockAuditEntry` JPA entity in `easyeat/backend/src/main/java/com/easyeat/inventory/entity/StockAuditEntry.java` mapping `tenant_id`, `ingredient_id`, `user_id`, `adjustment_mode`, `previous_stock`, `new_stock`, `net_delta`, and `justification`
- [ ] T033 [US4] Add pessimistic locking query `findByIdAndTenantIdForUpdate` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` to `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java`
- [ ] T034 [US4] Create `StockAuditEntryRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/StockAuditEntryRepository.java` providing append-only `save()` and chronological retrieval methods
- [ ] T035 [US4] Implement `StockAdjustmentService` in `easyeat/backend/src/main/java/com/easyeat/inventory/service/StockAdjustmentService.java` handling `@Transactional` stock calculation, non-negative validation, discrete vs continuous unit scale validation, and immutable audit persistence
- [ ] T036 [US4] Add adjustment endpoints `POST /api/v1/admin/ingredients/{id}/stock-adjustments` and `GET /api/v1/admin/ingredients/{id}/stock-audit` to `AdminIngredientController` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/AdminIngredientController.java`

**Checkpoint**: User Story 4 fully functional. Stock adjustments are atomic, audit trail is immutable, and concurrency is safely locked.

---

## Phase 6: User Story 5 - Kitchen Ingredient Catalog Exploration (Priority: P3)

**Goal**: Provide Chefs with a paginated, sorted kitchen catalog of active ingredients displaying stock availability (`IN_STOCK` / `OUT_OF_STOCK`), while strictly hiding unit purchase costs and audit logs.

**Independent Test**: Authenticate as Chef, request paginated catalog, verify default 20 items per page sorted alphabetically A-Z, confirm that active ingredients with zero stock display `OUT_OF_STOCK`, confirm discontinued ingredients are excluded, test category and text filters, and verify purchase costs are completely omitted from the payload.

### Tests for User Story 5 (TDD - Write First) ⚠️

- [ ] T037 [P] [US5] Web and service contract test `ChefCatalogControllerTest` in `easyeat/backend/src/test/java/com/easyeat/inventory/controller/ChefCatalogControllerTest.java` verifying 20-item pagination, alphabetical A-Z sorting, category filtering, name search, zero-stock inclusion, discontinued item exclusion, and financial cost suppression

### Implementation for User Story 5

- [ ] T038 [P] [US5] Create Chef Catalog DTOs (`StockAvailabilityStatus`, `ChefIngredientItemResponse`, `ChefIngredientPageResponse`) in `easyeat/backend/src/main/java/com/easyeat/inventory/dto/` without purchase cost or audit attributes
- [ ] T039 [US5] Add Chef catalog query method to `IngredientRepository` in `easyeat/backend/src/main/java/com/easyeat/inventory/repository/IngredientRepository.java` filtering by `tenant_id`, `status = 'ACTIVE'`, optional category, and case-insensitive name match with `Pageable`
- [ ] T040 [US5] Implement `ChefCatalogController` exposing `GET /api/v1/chef/ingredients` guarded with `@PreAuthorize("hasRole('CHEF')")` in `easyeat/backend/src/main/java/com/easyeat/inventory/controller/ChefCatalogController.java`

**Checkpoint**: User Story 5 fully functional. Kitchen staff can browse available ingredients safely.

---

## Phase 7: Polish & End-to-End Validation

**Purpose**: Validate end-to-end user journeys against quickstart scenarios, ensure build hygiene, and verify zero cross-tenant leakage.

- [ ] T041 Implement end-to-end integration test `StaffAndInventoryQuickstartIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/integration/StaffAndInventoryQuickstartIntegrationTest.java` executing Scenarios 1 through 8 from `quickstart.md`
- [ ] T042 [P] Verify code quality, formatting, and zero unhandled exceptions via Maven build: `./mvnw clean verify`
- [ ] T043 [P] Validate OpenAPI contract compliance against running controllers in `easyeat/backend/src/test/java/com/easyeat/contract/OpenApiContractValidationTest.java`

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup
    │
    ▼
Phase 2: Foundational (Flyway V3/V4, TenantContext, SecurityAudit) ──► BLOCKS all stories
    │
    ├────────────────────────────────────────┬────────────────────────────────────────┐
    ▼                                        ▼                                        ▼
Phase 3: US1 & US2 (Staff & RBAC) [P1]   Phase 4: US3 (Ingredient Catalog) [P2]   Phase 5: US4 (Stock & Concurrency) [P2]
    │                                        │                                        │
    └────────────────────────────────────────┴──────────────────┬─────────────────────┘
                                                                ▼
                                                     Phase 6: US5 (Chef Catalog) [P3]
                                                                │
                                                                ▼
                                                     Phase 7: Polish & E2E Validation
```

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1; must be fully verified before any User Story starts.
- **User Story 1 & 2 (Phase 3)**: Depends on Phase 2. Delivers the core MVP.
- **User Story 3 (Phase 4)**: Depends on Phase 2. Can execute in parallel with Phase 3 on the data layer.
- **User Story 4 (Phase 5)**: Depends on Phase 4 (`Ingredient` entity and repository must exist).
- **User Story 5 (Phase 6)**: Depends on Phase 4 and Phase 5 (`Ingredient` catalog and stock calculations must exist).
- **Polish (Phase 7)**: Depends on all User Stories being complete.

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
