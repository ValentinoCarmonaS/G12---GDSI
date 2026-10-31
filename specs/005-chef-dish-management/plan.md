# Implementation Plan: Chef Dish Management — Technical File Creation and Submission

**Branch**: `005-chef-dish-management` | **Date**: 25/10/2026 | **Spec**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/005-chef-dish-management/spec.md)

**Input**: Feature specification from `/specs/005-chef-dish-management/spec.md` (User stories CHF-2.2.1, CHF-2.2.2, CHF-2.2.3, CHF-2.2.4).

---

## Summary

This technical plan details the full-stack end-to-end implementation (vertical slice) of the culinary technical file creation, recipe composition (Bill of Materials), allergen labeling, and submission workflow for restaurant Chefs.

### Technical approach
- **Persistence and relational model**: Flyway migration `V7__chef_dish_management.sql` evolving the existing `dishes` table (making `price` optional, adding closed category, `DRAFT`/`PENDING_PRICE` status, confirmed allergen flag, creator reference, and whitespace-trimmed case-insensitive name uniqueness per tenant). Relational 3NF storage for recipe lines (`dish_recipe_ingredients` without unit redundancy) and PostgreSQL binary table `dish_photos` (`BYTEA`) for photos up to 5 MB. Administrative alerts in `admin_notifications`.
- **API and contracts**: Versioned RESTful interface under `/api/v1` (`/api/v1/dishes`, `/api/v1/admin/notifications`) matching OpenAPI 3.0 contracts in `contracts/dishes.yaml` and `contracts/admin-notifications.yaml`, standardized error handling via `ProblemDetail` (RFC 7807), and tenant isolation via `TenantContextFilter`.
- **Security and immutability**: Strict backend transactional gate evaluating all 4 completeness conditions before transitioning to `PENDING_PRICE`. Absolute locking of any mutation or deletion on dishes in `PENDING_PRICE` status (returning `409 Conflict`).
- **Frontend**: Kitchen interface (`/cocina/platos`) with paginated list (20 items/page, sorted by `updated_at DESC`, status filters), guided sequential 4-step wizard/Stepper (Identity, Recipe/BOM, Allergens, Review/Submit), real-time validation, and read-only view locking while under review.

### Seed data audit and notification (CRITICAL REQUIREMENT)
- **Audit finding**: In `R__seed_dev_dishes_and_dietary.sql`, 5 sample dish records exist for the synthetic administration tenant (`a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`):
  1. `Ensalada Mediterránea`
  2. `Pad Thai con Maní`
  3. `Hamburguesa Clásica con Queso`
  4. `Bowl Vegano Proteico`
  5. `Salmón Grillado con Espárragos`
- **Conflict diagnosis**: These dishes have mandatory `price` and lack `category`, `status`, `allergens_confirmed`, and `created_by`. Leaving `price NOT NULL` in `dishes` breaks technical file creation by the Chef. In turn, test `DevSeedDishesMigrationTest` requires the presence of these 5 dishes.
- **Agreed resolution**: Modify `dishes` to make `price` nullable in migration V7, and adapt `R__seed_dev_dishes_and_dietary.sql` by assigning valid categories (`'Ensaladas'` and `'Platos principales'`), `status = 'PENDING_PRICE'`, `allergens_confirmed = true`, and `created_by = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'`. This preserves the validity of preceding diner catalog tests without destructively modifying data.

---

## Technical Context

**Language/Version**: Java 21 (LTS) for Backend; TypeScript 5.x / React 19 for Frontend.

**Primary Dependencies**:
- *Backend*: Spring Boot 3.3.x, Spring Data JPA (Hibernate), Spring Security 6.x (Stateless JWT), Flyway Migration, Testcontainers 1.19+ (PostgreSQL 16), Jackson, AssertJ, Mockito.
- *Frontend*: React 19, React Router v6, Lucide React (icons), Tailwind CSS v3, Axios / Fetch API with interceptors, Vitest, Testing Library.

**Storage**: PostgreSQL 16 (relational, 3NF or higher, ACID transactions, multi-tenant isolation via `tenant_id` column). Multimedia photo storage in `dish_photos` table (`BYTEA`).

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Testcontainers for persistence integration and OpenAPI contracts in Backend; Vitest and React Testing Library in Frontend.

**Target Platform**: Linux / Containerized execution (Docker), responsive web architecture targeting modern browsers on kitchen display terminals (kitchen touchscreens / KDS and desktop).

**Project Type**: Monorepo full-stack web application with decoupled modules: `easyeat/backend` and `easyeat/frontend`.

**Performance Goals**:
- Draft creation and API response < 100 ms.
- 5 MB image upload and processing < 500 ms.
- Paginated dish list query (20 items) < 50 ms via indexes on `(tenant_id, status, updated_at DESC)`.

**Constraints**:
- Strict tenant data isolation on every query and mutation.
- Case-insensitive, whitespace-trimmed dish name uniqueness constraint per establishment.
- Closed status lifecycle for Chef: `DRAFT` and `PENDING_PRICE` only.
- Relational persistence in PostgreSQL without external cloud services or asynchronous message brokers.

**Scale/Scope**: Core culinary authoring module for kitchen personnel across restaurant establishments in EasyEat, covering 4 user stories (CHF-2.2.1 through CHF-2.2.4).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | Status | Rationale and Evidence |
|---|---|---|
| **I. The Specification Is the Source of Truth** | PASS | Plan and contracts derive strictly from functional requirements (FR-001 through FR-024) and acceptance criteria in `specs/005-chef-dish-management/spec.md`. |
| **II. Specification Before Implementation** | PASS | All planning, models, and OpenAPI contract artifacts precede any user story implementation code. |
| **III. Layered Verification Proportional to Risk** | PASS | 4-tier testing strategy defined: domain unit tests, PostgreSQL Testcontainers integration, MockMvc OpenAPI contract adherence, and Vitest UI component tests. |
| **IV. Simple, Cohesive, and Justified Design** | PASS | Strict 3NF relational model without unit redundancy in recipes, dedicated photo table without complex cloud dependencies, and database alerts without WebSockets. |
| **V. Bounded Scope** | PASS | Strictly excludes pricing, admin approval workflows, nutritional calculations, POS stock deductions, and diner menu catalog rendering. |
| **VI. Controlled Technical Debt** | PASS | Technical debt of the 5 seed dishes from V5 is resolved by adapting the seed without temporary patches or breaking spec 003 tests. |
| **VII. Traceability, Consistency, and Documentation** | PASS | Bidirectional links maintained between `spec.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/`, and `plan.md`. |
| **VIII. Evidence Over Assumption and Human Escalation** | PASS | Each design branch was inspected in the codebase and critical decisions were resolved interactively via `ask_question`. |
| **IX. Engineering Over Convenience** | PASS | State machine, unit validation (discrete vs continuous), and backend immutability modeled with rigorous tests rather than shortcuts. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | PASS | Plan covers database (Flyway V7), backend business logic (Spring Boot), OpenAPI contracts, and frontend user experience (React / Stepper) in a coordinated slice. |
| **XI. Continuous Upstream Synchronization Awareness** | PASS | `git fetch origin main` executed, confirming 0/0 drift against remote integration branch before commencing design. |

---

## Project Structure

### Documentation (this feature)

```text
specs/005-chef-dish-management/
├── spec.md              # Functional specification (approved source of truth)
├── checklists/          # Requirements and business-and-safety checklists
├── plan.md              # Technical implementation plan (this file)
├── research.md          # Technical decisions and research findings
├── data-model.md        # Database schema, entities, and migration DDL
├── quickstart.md        # Runnable verification and validation scenarios
├── contracts/           # OpenAPI 3.0 interface specifications
│   ├── dishes.yaml              # Chef dish lifecycle and BOM endpoints
│   └── admin-notifications.yaml # Internal administrator alert endpoints
└── tasks.md             # Work breakdown tasks (created in Phase 2)
```

### Source Code Layout

```text
easyeat/
├── backend/
│   ├── src/main/java/com/easyeat/
│   │   ├── catalog/
│   │   │   ├── domain/
│   │   │   │   └── Dish.java                        # Existing Dish entity (evolved with status, category, etc.)
│   │   │   └── repository/
│   │   │       └── DishRepository.java              # Existing repository (extended with tenant isolation and uniqueness queries)
│   │   ├── dish/
│   │   │   ├── domain/
│   │   │   │   ├── DishCategory.java                # Enum: Entradas, Platos principales, etc.
│   │   │   │   ├── DishStatus.java                  # Enum: DRAFT, PENDING_PRICE
│   │   │   │   ├── DishPhoto.java                   # Entity: binary image storage (BYTEA)
│   │   │   │   ├── DishRecipeIngredient.java        # Compound entity (dish_id, ingredient_id, quantity)
│   │   │   │   └── DishRecipeIngredientId.java      # Composite PK
│   │   │   ├── repository/
│   │   │   │   ├── DishPhotoRepository.java         # Photo binary queries
│   │   │   │   └── DishRecipeIngredientRepository.java # BOM queries
│   │   │   ├── service/
│   │   │   │   ├── DishService.java                 # Draft lifecycle, validation, gate evaluation
│   │   │   │   └── DishPhotoService.java            # Upload, MIME validation, binary retrieval
│   │   │   ├── controller/
│   │   │   │   ├── DishController.java              # REST controller /api/v1/dishes
│   │   │   │   └── DishPhotoController.java         # Multipart upload & byte streaming
│   │   │   ├── dto/                                 # Requests and responses matching OpenAPI
│   │   │   └── exception/                           # Domain exceptions (DishLockedException, etc.)
│   │   ├── notification/
│   │   │   ├── domain/
│   │   │   │   ├── AdminNotification.java           # Relational internal alert entity
│   │   │   │   └── NotificationType.java            # DISH_SUBMITTED_FOR_REVIEW
│   │   │   ├── repository/
│   │   │   │   └── AdminNotificationRepository.java # Tenant notifications query & unread filter
│   │   │   ├── service/
│   │   │   │   └── AdminNotificationService.java    # Emission and read-status toggle
│   │   │   └── controller/
│   │   │       └── AdminNotificationController.java # REST controller /api/v1/admin/notifications
│   │   └── config/
│   │       └── SecurityConfig.java                  # Authorization mappings for CHEF & ADMIN roles
│   ├── src/main/resources/
│   │   └── db/
│   │       ├── migration/
│   │       │   └── V7__chef_dish_management.sql     # Flyway migration DDL
│   │       └── dev/
│   │           └── R__seed_dev_dishes_and_dietary.sql # Adapted dev seed data
│   └── src/test/java/com/easyeat/
│       ├── dish/                                    # Unit, integration, and contract tests
│       └── notification/                            # Notification unit and integration tests
│
└── frontend/
    └── src/
        ├── api/
        │   ├── dishApi.ts                           # API client for dishes and photo endpoints
        │   └── adminNotificationApi.ts              # API client for admin notification endpoints
        ├── types/
        │   ├── dish.ts                              # TypeScript interfaces for dishes, BOM, allergens
        │   └── notification.ts                      # TypeScript interfaces for alerts
        ├── components/
        │   ├── dish/
        │   │   ├── DishStepper.tsx                  # 4-step wizard container
        │   │   ├── BasicIdentityStep.tsx            # Step 1: name, category, description, photo upload
        │   │   ├── RecipeBOMStep.tsx                # Step 2: ingredient selector modal, quantities, BOM table
        │   │   ├── AllergenStep.tsx                 # Step 3: lifestyles, allergens, confirm button
        │   │   ├── ReviewSubmitStep.tsx             # Step 4: checklist, preview, submit action
        │   │   ├── IngredientSelectorModal.tsx      # Active ingredients browser (20/page, A-Z)
        │   │   └── DishStatusBadge.tsx              # Draft / Pending Price badges
        │   └── notification/
        │       └── AdminNotificationBell.tsx        # Notification badge and dropdown for Admin panel
        ├── pages/
        │   ├── ChefDishesPage.tsx                   # List of drafts with filters and pagination
        │   ├── ChefDishEditorPage.tsx               # Stepper page (/cocina/platos/nuevo, /cocina/platos/:id)
        │   ├── KitchenWorkspacePage.tsx             # Updated kitchen dashboard linking to dishes
        │   └── AdminWorkspacePage.tsx               # Updated admin panel surfacing alerts
        └── router/
            └── AppRoutes.tsx                        # Route definitions under /cocina/platos
```

---

## Complexity Tracking

| Component / Pattern | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| **Table `dish_photos` with `BYTEA` in PostgreSQL** | Allows persisting and serving images in test and dev environments without requiring cloud buckets (AWS S3) or non-existent external env vars. | Local filesystem storage: rejected because it does not persist cleanly across ephemeral container restarts and complicates multi-instance setups. |
| **3NF relationship without redundant `unit_of_measure` in recipe** | Keeps database normalized, eliminating update anomalies and ensuring unit always matches master inventory from spec 004. | Denormalizing unit in intermediate table: rejected because it introduces transitive functional dependency and potential inconsistency. |
| **Submission gate with pessimistic row locking in backend** | Prevents race conditions if two Chefs attempt to submit or edit the same dish concurrently, ensuring only 100% complete dishes transition to `PENDING_PRICE`. | Frontend-only validation: rejected because it violates backend security and integrity requirements. |
| **Guided 4-step Stepper in frontend** | Guides Chef methodically, ensuring recipe and allergens are not missed before reaching review. | Long monolithic form: rejected because it increases cognitive load and error rate in kitchen environments. |

---

## Detailed Implementation Phases

### Phase 1: Persistence and database migration
1. **Creation of `V7__chef_dish_management.sql`**:
   - `ALTER TABLE dishes`: Make `price` optional, shorten `name` to 100 characters, add `category` with `CHECK` constraint, `status` with `CHECK ('DRAFT', 'PENDING_PRICE')`, `allergens_confirmed BOOLEAN DEFAULT FALSE`, `created_by UUID REFERENCES users(id)`.
   - `CREATE UNIQUE INDEX uq_dishes_tenant_lower_trimmed_name ON dishes (tenant_id, LOWER(TRIM(name)));`
   - Creation of `dish_photos` table with `BYTEA` column and MIME type / size constraints (<= 5 MB).
   - Creation of `dish_recipe_ingredients` table with composite PK `(dish_id, ingredient_id)`, FK to `dishes` and `ingredients`, and strictly positive quantity.
   - Creation of `admin_notifications` table with index on `(tenant_id, is_read, created_at DESC)`.
2. **Update of `R__seed_dev_dishes_and_dietary.sql`**:
   - Update 5 existing dishes with valid categories, `status = 'PENDING_PRICE'`, `allergens_confirmed = true`, and `created_by = admin synthetic user`.
3. **Regression validation**: Execute `FlywayMigrationV5Test`, `V7MigrationAndSeedDevTest`, and `DevSeedDishesMigrationTest` to verify that preceding migrations and seed continue running without failure.

### Phase 2: Backend — Domain model, repositories, and services
1. **JPA Entities**:
   - Update `Dish.java` in `com.easyeat.catalog.domain` with new fields (`category`, `status`, `allergensConfirmed`, `createdBy`). Completeness conditions are evaluated by `DishService`.
   - Create `DishPhoto.java`, `DishRecipeIngredient.java` with `DishRecipeIngredientId.java`, and `AdminNotification.java`.
2. **Spring Data Repositories**:
   - `DishRepository`: Query methods by tenant and status with pagination (`Pageable`), existence check by trimmed case-insensitive name (`existsByTenantIdAndNameLowerTrimmed`).
   - `DishPhotoRepository`: Binary queries and deletion by `dishId`.
   - `DishRecipeIngredientRepository`: Recipe ingredient listing with projection of master ingredient.
   - `AdminNotificationRepository`: Notification query by tenant and read status.
3. **Business Services**:
   - `DishService`:
     - Draft basic data creation and update with name uniqueness check.
     - Recipe ingredient upsert and removal validating positive quantities, discrete/continuous units, and `ACTIVE` status in `ingredients`.
     - Allergen updating and confirmation.
     - Submission gate evaluation (`submitForReview`): atomic validation of all 4 conditions, transition to `PENDING_PRICE`, and notification dispatch.
     - Immutability guard: systematic verification that `status == DRAFT` on every mutation operation.
   - `DishPhotoService`: MIME type validation (JPEG, PNG, WebP), 5 MB limit, persistence in `dish_photos`, and byte streaming.
   - `AdminNotificationService`: Alert recording and mark-as-read handling.

### Phase 3: Backend — REST controllers and security
1. **Controllers**:
   - `DishController` (`/api/v1/dishes`): CRUD endpoints, recipe management, allergen management, and review submission.
   - `DishPhotoController` (`/api/v1/dishes/{id}/photo`): Upload (`multipart/form-data`), binary query, and deletion.
   - `AdminNotificationController` (`/api/v1/admin/notifications`): Listing with `unreadOnly` filter and read update.
2. **Security and Authorization (`SecurityConfig`)**:
   - Protect `/api/v1/dishes/**` requiring `CHEF` role (and optionally `ADMINISTRADOR` for reading).
   - Protect `/api/v1/admin/notifications/**` requiring `ADMINISTRADOR` role.
   - Ensure tenant propagation via `TenantContextFilter`.

### Phase 4: Frontend — API client and state management
1. **API Modules and TypeScript Types**:
   - `dish.ts` and `notification.ts`: Interface definitions matching OpenAPI contracts.
   - `dishApi.ts`: Functions for dish CRUD, photo upload (FormData), recipe, allergens, and submission.
   - `adminNotificationApi.ts`: Functions for querying and marking alerts.
2. **Technical Editor Components (Stepper)**:
   - `DishStepper.tsx`: 4-step wizard container (1. Identity, 2. Recipe, 3. Allergens, 4. Submit).
   - `BasicIdentityStep.tsx`: Name, closed category, description form, and photo component with dropzone and preview.
   - `RecipeBOMStep.tsx`: Modal active supply browser (`IngredientSelectorModal`), unit-dependent input mask, and recipe table.
   - `AllergenStep.tsx`: Allergens and lifestyles grid with explicit confirmation button.
   - `ReviewSubmitStep.tsx`: Technical file summary, pending checklist, and "Send for Review" button.
3. **Main Views**:
   - `ChefDishesPage.tsx`: Chef dish catalog with table, status badges, last updated sorting, pagination, and filter.
   - Link and entry point from `KitchenWorkspacePage.tsx`.
   - Notifications in `AdminWorkspacePage.tsx` with unread count badge.

### Phase 5: Automated testing and verification
1. **Unit tests**:
   - Whitespace trimming and unique name validation.
   - Discrete vs continuous quantity validation.
   - Exhaustive completeness gate evaluation.
   - Edit blocking on dishes in `PENDING_PRICE`.
2. **Integration tests (Testcontainers)**:
   - Full persistence flow of dish, recipe lines, and allergen labels in real PostgreSQL.
   - Verification of alert insertion in `admin_notifications`.
   - MockMvc OpenAPI contract adherence tests for `/api/v1/dishes` and `/api/v1/admin/notifications`.
3. **Frontend tests (Vitest)**:
   - Live form validation and character limits (100 and 500).
   - Stepper flow and dynamic enablement of "Send for Review" button.
   - Read-only view rendering for dishes in `PENDING_PRICE`.
