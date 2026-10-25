# Implementation Tasks: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering  
**Branch**: `003-diner-profile-dietary`  
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)  
**Data Model**: [data-model.md](./data-model.md) | **Contracts**: [openapi.yaml](./contracts/openapi.yaml) | **Quality Checklist**: [quality.md](./checklists/quality.md)  

---

## Phase 1: Setup (Shared Infrastructure & Environment)

**Purpose**: Establish package structure, exception hierarchies, and configuration stubs for profile and catalog modules within the existing Spring Boot backend.

- [X] T001 Initialize package structure for profile and catalog modules under `easyeat/backend/src/main/java/com/easyeat/profile/` and `easyeat/backend/src/main/java/com/easyeat/catalog/`
- [X] T002 [P] Configure `EmailSender` interface and dev/test logging stubs (`LoggingEmailSender` under `@Profile("dev | test")` printing raw verification token URLs to console logs at `INFO`) in `easyeat/backend/src/main/resources/application.yml` and `application-test.yml`
- [X] T003 [P] Create domain exceptions (`EmailVerificationException`, `CooldownRateLimitException`, `DietaryTaxonomyException`, `DishNotFoundException`) in `easyeat/backend/src/main/java/com/easyeat/profile/exception/` and `easyeat/backend/src/main/java/com/easyeat/catalog/exception/`
- [X] T004 Update `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` mapping profile/catalog exceptions to RFC 9457 `ProblemDetail` with appropriate status codes (400, 404, 409, 422, 429)

---

## Phase 2: Foundational (Database Migrations & Security Baseline)

**Purpose**: Database schema evolution and security configuration updates that block all user stories.

**⚠️ CRITICAL**: No user story implementation can proceed until this phase is complete and verified.

- [X] T005 [P] Create Flyway migration `easyeat/backend/src/main/resources/db/migration/V5__extend_users_profile_and_dietary.sql` defining user profile columns (`first_name`, `last_name`, `phone`, `pending_email`, `email_verification_token_hash`, `email_token_expires_at`, `email_verification_last_sent_at`), dietary taxonomy, join tables, dishes, and catalog constraints
- [X] T006 [P] Create repeatable dev seed migration `easyeat/backend/src/main/resources/db/dev/R__seed_dev_dishes_and_dietary.sql` populating sample establishment dishes with allergens and lifestyle tags for tenant `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` (Admin establishment seed) for local testing
- [X] T007 Update `easyeat/backend/src/main/java/com/easyeat/config/SecurityConfig.java` configuring `permitAll()` for `/api/v1/dietary-restrictions`, `/api/v1/profile/email-change/confirm`, and public catalog browsing `/api/v1/establishments/{tenantId}/dishes`
- [X] T008 [P] Update `User` entity in `easyeat/backend/src/main/java/com/easyeat/auth/domain/User.java` adding persistent fields for `first_name`, `last_name`, `phone`, `pending_email`, `email_verification_token_hash`, `email_token_expires_at`, and `email_verification_last_sent_at`
- [X] T009 Implement `ProfileUserRepository` in `easyeat/backend/src/main/java/com/easyeat/profile/repository/ProfileUserRepository.java` extending `JpaRepository<User, UUID>` with queries for token hash lookup and case-insensitive pending email uniqueness

**Checkpoint**: Foundational database schemas, dev seed scripts, and security endpoints verified.

---

## Phase 3: User Story 1 - Diner Personal Data Management (Priority: P1) 🎯 MVP

**Goal**: Allow authenticated diners to view and update personal contact data (`first_name`, `last_name`, `phone`) with automatic synchronization of `full_name`, and execute asynchronous email change verification with 60s cooldown (HTTP 429), single-use SHA-256 tokens, and active session revocation via `token_version`.

> **Architectural Note (FR-011 / SC-004)**: Historical orders immutability is guaranteed by architectural design through entity decoupling (`User` modifications do not alter past order snapshots; the future `orders` table in Sprint 2/3 will store a denormalized customer snapshot rather than a mutable join). Direct regression test execution is deferred to the Checkout/Orders sprint.

**Independent Test**: Navigate to `/perfil`, edit names and phone, verify validations, request email change, verify 60s cooldown on resend, confirm token from verification link at `/verificar-email`, and verify that previous JWT sessions are invalidated (HTTP 401).

### Tests for User Story 1 (TDD - Write First) ⚠️

- [X] T010 [P] [US1] Unit test `ProfileServiceTest` in `easyeat/backend/src/test/java/com/easyeat/profile/service/ProfileServiceTest.java` verifying personal data update, alphabetic name validations, phone format checks, and automatic `full_name` synchronization
- [X] T011 [P] [US1] Unit test `EmailChangeServiceTest` in `easyeat/backend/src/test/java/com/easyeat/profile/service/EmailChangeServiceTest.java` verifying token generation, 60s cooldown enforcement (HTTP 429 ProblemDetail), previous token revocation on re-request, and session revocation via `token_version`
- [X] T012 [P] [US1] Web contract test `ProfileControllerTest` in `easyeat/backend/src/test/java/com/easyeat/profile/controller/ProfileControllerTest.java` verifying `GET /api/v1/profile/me`, `PUT /api/v1/profile/personal-data`, and email change endpoints
- [X] T013 [P] [US1] Integration test `EmailChangeIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/profile/integration/EmailChangeIntegrationTest.java` using Testcontainers to verify end-to-end token consumption, DB constraints, cooldown, and `token_version` session revocation
- [X] T014 [P] [US1] Frontend component test `PersonalDataForm.test.tsx` in `easyeat/frontend/tests/components/PersonalDataForm.test.tsx` verifying input validation, submission, and cancel reset
- [X] T015 [P] [US1] Frontend component test `PendingEmailBanner.test.tsx` in `easyeat/frontend/tests/components/PendingEmailBanner.test.tsx` verifying 60s resend cooldown timer and cancel request triggers

### Implementation for User Story 1

- [X] T016 [P] [US1] Create Profile DTOs (`PersonalDataRequest`, `ProfileResponse`, `EmailChangeRequestDto`, `EmailChangeInitiatedResponse`, `EmailConfirmationRequest`, `EmailConfirmedResponse`) in `easyeat/backend/src/main/java/com/easyeat/profile/dto/`
- [X] T017 [US1] Implement `EmailChangeService` in `easyeat/backend/src/main/java/com/easyeat/profile/service/EmailChangeService.java` handling token generation, SHA-256 hashing, delegating dispatch to `EmailSender` interface (logged to console in dev/test per DIP), 60s cooldown rate-limiting, and `token_version` increment
- [X] T018 [US1] Implement `ProfileService` in `easyeat/backend/src/main/java/com/easyeat/profile/service/ProfileService.java` managing diner contact updates, synchronizing `full_name = first_name.trim() + " " + last_name.trim()`, and retrieving profile state
- [X] T019 [US1] Implement `ProfileController` in `easyeat/backend/src/main/java/com/easyeat/profile/controller/ProfileController.java` exposing `/api/v1/profile/me`, `/api/v1/profile/personal-data`, and `/api/v1/profile/email-change/**`
- [X] T020 [P] [US1] Create frontend API client `profileApi.ts` in `easyeat/frontend/src/api/profileApi.ts` and TypeScript interfaces in `easyeat/frontend/src/types/profile.ts`
- [X] T021 [P] [US1] Implement custom hook `useProfile.ts` in `easyeat/frontend/src/hooks/useProfile.ts` for managing profile data fetching, mutation state, and email verification status
- [X] T022 [P] [US1] Implement `PersonalDataForm.tsx` in `easyeat/frontend/src/components/profile/PersonalDataForm.tsx` with alphabetic regex checks, optional phone syntax validation, and discard changes action
- [X] T023 [P] [US1] Implement `PendingEmailBanner.tsx` in `easyeat/frontend/src/components/profile/PendingEmailBanner.tsx` displaying pending status badge, 60s reactive countdown timer, and cancel action
- [X] T024 [US1] Implement `VerifyEmailPage.tsx` in `easyeat/frontend/src/pages/VerifyEmailPage.tsx` mounted at `/verificar-email` to handle email verification token URL callbacks and display success/expired messaging
- [X] T025 [US1] Implement `ProfilePage.tsx` in `easyeat/frontend/src/pages/ProfilePage.tsx` mounted at `/perfil` assembling personal data form and email banner
- [X] T026 [US1] Update `WorkspaceLayout.tsx` in `easyeat/frontend/src/components/common/WorkspaceLayout.tsx` adding "Catálogo" (`/catalogo`) and "Mi perfil" (`/perfil`) navigation links for `COMENSAL` users, and ensuring that when `user` is null, "Cerrar sesión" is hidden and "Iniciar sesión" (`/login`) / "Registrarse" (`/register`) links are rendered for unauthenticated guests (FR-026)
- [X] T027 [US1] Register `/perfil` (role-guarded for `COMENSAL`), `/verificar-email`, and redirects (`/profile` → `/perfil`, `/verify-email` → `/verificar-email`) in `easyeat/frontend/src/router/AppRoutes.tsx`

**Checkpoint**: User Story 1 (MVP) fully functional and testable independently.

---

## Phase 4: User Story 2 - Dietary Restrictions and Allergens Configuration (Priority: P1)

**Goal**: Enable diners to configure their dietary lifestyle preferences and food allergies from a standardized platform taxonomy with atomic server persistence, live cart compliance re-evaluation, and advisory warnings for active kitchen orders.

**Independent Test**: Open the dietary preferences section in `/perfil`, select lifestyles and allergens, verify immediate persistence, and verify explicit error alerts on failure (0% optimistic state). Note: Live cart re-evaluation is tested as an end-to-end integration scenario in Phase 5 (US3).

### Tests for User Story 2 (TDD - Write First) ⚠️

- [X] T028 [P] [US2] Unit test `DietaryRestrictionServiceTest` in `easyeat/backend/src/test/java/com/easyeat/profile/service/DietaryRestrictionServiceTest.java` verifying taxonomy retrieval and atomic preferences replacement
- [X] T029 [P] [US2] Web contract test `DietaryRestrictionControllerTest` in `easyeat/backend/src/test/java/com/easyeat/profile/controller/DietaryRestrictionControllerTest.java` verifying `GET /api/v1/dietary-restrictions` and `PUT /api/v1/profile/dietary-restrictions`
- [X] T030 [P] [US2] Integration test `DietaryPreferencesIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/profile/integration/DietaryPreferencesIntegrationTest.java` verifying cascade persistence and rollback on invalid taxonomy code
- [X] T031 [P] [US2] Frontend component test `DietaryRestrictionsSelector.test.tsx` in `easyeat/frontend/tests/components/DietaryRestrictionsSelector.test.tsx` verifying grouping into Lifestyles and Allergens, multi-selection, and error alert on save failure (no optimistic state)

### Implementation for User Story 2

- [X] T032 [P] [US2] Create Dietary domain entities (`DietaryRestriction`, `DietaryCategory`, `UserDietaryRestrictionId`) in `easyeat/backend/src/main/java/com/easyeat/profile/domain/`
- [ ] T033 [P] [US2] Create Dietary DTOs (`DietaryRestrictionItemDto`, `DietaryPreferencesRequest`) in `easyeat/backend/src/main/java/com/easyeat/profile/dto/`
- [ ] T034 [US2] Implement `DietaryRestrictionRepository` in `easyeat/backend/src/main/java/com/easyeat/profile/repository/DietaryRestrictionRepository.java`
- [ ] T035 [US2] Implement `DietaryRestrictionService` in `easyeat/backend/src/main/java/com/easyeat/profile/service/DietaryRestrictionService.java` managing taxonomy querying and atomic preferences updates
- [ ] T036 [US2] Implement `DietaryRestrictionController` in `easyeat/backend/src/main/java/com/easyeat/profile/controller/DietaryRestrictionController.java` exposing `GET /api/v1/dietary-restrictions` and `PUT /api/v1/profile/dietary-restrictions`
- [ ] T037 [P] [US2] Implement `DietaryRestrictionsSelector.tsx` in `easyeat/frontend/src/components/profile/DietaryRestrictionsSelector.tsx` rendering Lifestyles and Allergens sections with advisory notification for in-flight kitchen orders
- [ ] T038 [US2] Integrate `DietaryRestrictionsSelector.tsx` into `ProfilePage.tsx` in `easyeat/frontend/src/pages/ProfilePage.tsx`, triggering proactive cart re-evaluation via `useCart().reEvaluateCompliance()` on successful save if cart has active items (FR-015)

**Checkpoint**: User Stories 1 and 2 functional. Diner can manage contact data and dietary taxonomy preferences.

---

## Phase 5: User Story 3 - Automated Catalog Filtering & Food Safety Browsing (Priority: P1)

**Goal**: Automatically inject diner profile dietary restrictions as default exclusion filters in establishment menus using strict conjunction (AND logic), pre-compute compliance server-side, provide an ephemeral master toggle with inline warning badges, allow unauthenticated guest browsing (FR-026), and enforce single-point preventive risk confirmation upon adding conflicting dishes to the cart via `AddToCartRiskModal.tsx` (FR-021, FR-022, SC-005).

**Independent Test**: Log in as a diner with configured restrictions, access an establishment catalog at `/catalogo`, verify non-compliant dishes are excluded by default, flip the master toggle OFF to inspect full menu with warning badges, verify 0ms latency, click "Agregar al carrito" on a conflicting dish to verify the preventive modal intercept, confirm risk assumption, and verify permanent visible warning badges in the cart drawer and checkout summary without redundant blocking modals at checkout.

### Tests for User Story 3 (TDD - Write First) ⚠️

- [ ] T039 [P] [US3] Unit test `CatalogComplianceServiceTest` in `easyeat/backend/src/test/java/com/easyeat/catalog/service/CatalogComplianceServiceTest.java` verifying strict conjunction AND logic, lifestyle matching, allergen exclusions, and guest fallback
- [ ] T040 [P] [US3] Web contract test `CatalogControllerTest` in `easyeat/backend/src/test/java/com/easyeat/catalog/controller/CatalogControllerTest.java` verifying `GET /api/v1/establishments/{tenantId}/dishes` for authenticated diner and guest
- [ ] T041 [P] [US3] Web contract test `CartComplianceControllerTest` in `easyeat/backend/src/test/java/com/easyeat/catalog/controller/CartComplianceControllerTest.java` verifying `POST /api/v1/cart/evaluate-compliance`
- [ ] T042 [P] [US3] Integration test `CatalogComplianceIntegrationTest` in `easyeat/backend/src/test/java/com/easyeat/catalog/integration/CatalogComplianceIntegrationTest.java` verifying repository queries and tenant isolation
- [ ] T043 [P] [US3] Frontend component test `CatalogMasterToggle.test.tsx` in `easyeat/frontend/tests/components/CatalogMasterToggle.test.tsx` verifying toggle visibility (hidden for 0 restrictions), default ON state, and flip action
- [ ] T044 [P] [US3] Frontend component test `AllergenWarningBadge.test.tsx` in `easyeat/frontend/tests/components/AllergenWarningBadge.test.tsx` verifying rendering of specific conflicting restriction names
- [ ] T045 [P] [US3] Frontend component test `AddToCartRiskModal.test.tsx` in `easyeat/frontend/tests/components/AddToCartRiskModal.test.tsx` verifying preventive modal interception on conflicting dishes, explicit risk confirmation, and cancel blocking cart insertion per FR-021
- [ ] T046 [P] [US3] Frontend router integration test `AppRoutesProfileCatalog.test.tsx` in `easyeat/frontend/tests/router/AppRoutesProfileCatalog.test.tsx` verifying public access to `/catalogo/*`, role guard protection on `/perfil`, and redirect aliases

### Implementation for User Story 3

- [ ] T047 [P] [US3] Create Catalog domain entities (`Dish`, `DishDietaryRestrictionId`) in `easyeat/backend/src/main/java/com/easyeat/catalog/domain/`
- [ ] T048 [P] [US3] Create Catalog & Cart DTOs (`EnrichedDishResponse`, `CartComplianceRequest`, `CartComplianceResponse`, `CartItemEvaluation`) in `easyeat/backend/src/main/java/com/easyeat/catalog/dto/`
- [ ] T049 [US3] Implement `DishRepository` in `easyeat/backend/src/main/java/com/easyeat/catalog/repository/DishRepository.java` fetching dishes and tags scoped by `tenant_id`
- [ ] T050 [US3] Implement `CatalogComplianceService` in `easyeat/backend/src/main/java/com/easyeat/catalog/service/CatalogComplianceService.java` evaluating strict intersection conjunction and enriching dishes with `isCompliant` and `conflictingRestrictions`
- [ ] T051 [US3] Implement `CatalogController` in `easyeat/backend/src/main/java/com/easyeat/catalog/controller/CatalogController.java` exposing `GET /api/v1/establishments/{tenantId}/dishes`
- [ ] T052 [US3] Implement `CartComplianceController` in `easyeat/backend/src/main/java/com/easyeat/catalog/controller/CartComplianceController.java` exposing `POST /api/v1/cart/evaluate-compliance`
- [ ] T053 [P] [US3] Create frontend API client `catalogApi.ts` in `easyeat/frontend/src/api/catalogApi.ts`, custom hook `useCatalog.ts` resolving `tenantId` from URL params (`/catalogo/:tenantId` or `?tenantId=...`) or `sessionStorage('activeTenantId')` with fallback to default dev seed tenant (`a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`), and types in `catalog.ts` and `cart.ts`
- [ ] T054 [P] [US3] Implement `CatalogFilterContext.tsx` in `easyeat/frontend/src/context/CatalogFilterContext.tsx` managing ephemeral master toggle state with automatic reset on reload or establishment change
- [ ] T055 [P] [US3] Implement `CartContext.tsx` in `easyeat/frontend/src/context/CartContext.tsx` managing cart dishes, exposing proactive `reEvaluateCompliance()` method, live compliance evaluation via `POST /api/v1/cart/evaluate-compliance`, and risk confirmation state
- [ ] T056 [P] [US3] Implement `CatalogMasterToggle.tsx` in `easyeat/frontend/src/components/catalog/CatalogMasterToggle.tsx`
- [ ] T057 [P] [US3] Implement `DishCard.tsx` and `AllergenWarningBadge.tsx` in `easyeat/frontend/src/components/catalog/`
- [ ] T058 [P] [US3] Implement `EmptyCatalogDietaryState.tsx` in `easyeat/frontend/src/components/catalog/EmptyCatalogDietaryState.tsx` with one-click action to view full menu
- [ ] T059 [P] [US3] Implement `CartDrawer.tsx` and `CartItemWithBadge.tsx` in `easyeat/frontend/src/components/cart/` displaying permanent visible warning badges without redundant blocking modal at checkout (FR-022)
- [ ] T060 [P] [US3] Implement `AddToCartRiskModal.tsx` in `easyeat/frontend/src/components/cart/AddToCartRiskModal.tsx` intercepting cart additions on conflicting dishes (FR-021)
- [ ] T061 [US3] Update `CatalogPage.tsx` in `easyeat/frontend/src/pages/CatalogPage.tsx` mounted at `/catalogo` (supporting optional route param `:tenantId` and fallback resolution), integrating master toggle, empty state, dish grid with warning badges, cart drawer, and guest banner inviting login/register (FR-026)
- [ ] T062 [US3] Update `AppRoutes.tsx` in `easyeat/frontend/src/router/AppRoutes.tsx` allowing unauthenticated guest access to `/catalogo/*` and canonical alias `/catalog/*`

**Checkpoint**: All user stories fully implemented and integrated.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end verification, contract compliance, type safety, and test regression validation across all modules.

- [ ] T063 [P] Execute quickstart validation scenarios from `specs/003-diner-profile-dietary/quickstart.md`
- [ ] T064 [P] Verify OpenAPI 3.0 contract adherence against `specs/003-diner-profile-dietary/contracts/openapi.yaml` via MockMvc test
- [ ] T065 Run full backend test suite (`mvn clean test` under `easyeat/backend`) verifying zero regressions across modules 001, 002, 004, and 003
- [ ] T066 Run frontend type and lint checks (`npm run lint` / `npx tsc --noEmit` under `easyeat/frontend`) and test suite (`npm run test`)

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1: Setup ──────────────┐
                             ▼
Phase 2: Foundational (V5, Security)
                             │
                             ├──────────────────────────────┐
                             ▼                              ▼
Phase 3: US1 - Personal Data (MVP)             Phase 4: US2 - Dietary Taxonomy
                             │                              │
                             └──────────────┬───────────────┘
                                            ▼
                               Phase 5: US3 - Catalog & Cart
                                            │
                                            ▼
                               Phase 6: Polish & Regressions
```

- **Setup (Phase 1)**: Can start immediately.
- **Foundational (Phase 2)**: Depends on Setup; BLOCKS all user story phases.
- **User Story 1 (Phase 3 - MVP)**: Depends on Foundational completion. Completely independent of US2 and US3.
- **User Story 2 (Phase 4)**: Depends on Foundational completion. Can run in parallel with US1 or sequentially.
- **User Story 3 (Phase 5)**: Depends on US2 (consumes dietary preferences and taxonomy) and Foundational.
- **Polish (Phase 6)**: Runs after all user stories are complete.

### Parallel Opportunities

- **Phase 1**: T002, T003 can execute in parallel.
- **Phase 2**: T005, T006, T008 can execute in parallel.
- **Phase 3**: Tests T010, T011, T012, T013, T014, T015 can run in parallel before implementation. DTOs (T016), API client (T020), and hooks (T021) can run in parallel.
- **Phase 4**: Tests T028, T029, T030, T031 can run in parallel. Entity definitions (T032) and DTOs (T033) can run in parallel.
- **Phase 5**: Tests T039, T040, T041, T042, T043, T044, T045, T046 can run in parallel. Entities (T047), DTOs (T048), contexts (T054, T055), and components (T056, T057, T058, T059, T060) can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T009)
3. Complete Phase 3: User Story 1 (T010–T027)
4. **STOP and VALIDATE**: Verify personal data editing and email verification token cycle independently.

### Incremental Delivery

1. Foundation + US1 → Deliver standalone diner contact and email management (MVP).
2. Add US2 → Deliver dietary taxonomy selection and persistent food profile.
3. Add US3 → Deliver automated catalog exclusion filtering, master toggle, public guest browsing, and Add-to-Cart risk modal.
4. Execute Polish (Phase 6) → Validate full regression and quickstart guides.
