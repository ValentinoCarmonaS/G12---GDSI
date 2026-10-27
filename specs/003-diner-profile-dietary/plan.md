# Implementation Plan: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Branch**: `003-diner-profile-dietary` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-diner-profile-dietary/spec.md`, product backlog (COM-1.4.1, COM-1.4.3, COM-4.2.1), and architectural alignment with baseline iterations 001 (`central-auth-recovery`), 002 (`staff-auth-redirect-guards`), and 004 (`staff-mgmt-rbac-inventory`).

**Note**: All design decisions, integration points, and trade-offs grilled, verified against the codebase, and approved via `/grill-me` interactive design sessions (Decisions D-001 through D-012).

---

## Summary

Implement diner profile editing, dietary preference and allergen management, and their automatic application to establishment menu catalogs for the EasyEat multi-tenant platform (Sprint 1).

Key capabilities delivered:
- **Diner Personal Data Management (COM-1.4.1)**: First name, last name, phone number editing with strict alphabetic and phone format validations. Dedicated `first_name` and `last_name` columns in Flyway `V5` with automatic synchronization of `full_name = first_name.trim() + " " + last_name.trim()` on `users` to maintain complete backward compatibility with Specs 001, 002, and 004 (`AuthContext`, `WorkspaceLayout`, `/api/v1/auth/me`). Asynchronous 24-hour verification token workflow for email changes with enforced 60-second cooldown rate-limiting (HTTP 429 ProblemDetail and UI timer) and atomic session revocation via `token_version`.
- **Dietary Restrictions & Allergens Configuration (COM-1.4.3)**: Standardized platform taxonomy (Lifestyles: Celiac, Vegan, Vegetarian; Allergens: Dairy, Peanuts, Tree Nuts, Eggs, Fish, Shellfish, Soy, Wheat) with atomic server-side persistence and live cart re-evaluation without optimistic local state.
- **Automated Catalog Filtering & Browsing (COM-4.2.1)**: Default injection of dietary profile filters using strict conjunction (AND logic), pre-computed compliance in backend (`is_compliant`, `conflicting_restrictions`), ephemeral session master toggle (ON/OFF) with 0ms perceived latency, empty state handling, unauthenticated public guest browsing on `/catalogo/*` with registration call-to-action (FR-026), and single-point preventive risk confirmation on "Agregar al carrito" via `AddToCartRiskModal.tsx` (permanent warning badges in cart drawer and checkout summary without redundant blocking modals at checkout per FR-021, FR-022, SC-005).

---

## Baseline Integration & Alignment

| Baseline Iteration | Integration Points & Shared Contracts | Resolution in Feature 003 |
|---|---|---|
| **001 (`central-auth-recovery`)** | • `users` table baseline schema (`V1__create_users.sql`).<br>• JWT issuance with claims (`user_id`, `role`, `tenant_id`, `token_version`).<br>• Password recovery and BCrypt hashing conventions. | Inherits `users` table; extends it via Flyway migration `V5`. Uses `token_version` to invalidate sessions upon email change. |
| **002 (`staff-auth-redirect-guards`)** | • Single active session enforcement in `JwtAuthenticationFilter` via `token_version`.<br>• `GET /api/v1/auth/me` session check.<br>• Frontend routing (`AppRoutes.tsx`, `RoleRouteGuard.tsx`, `WorkspaceLayout.tsx`).<br>• Canonical Spanish URLs (`/catalogo`, `/mostrador`, `/cocina`, `/admin`). | • Increments `token_version` on email confirmation to trigger 002's filter rejection (HTTP 401).<br>• Adapts `SecurityConfig` and `AppRoutes.tsx` to permit unauthenticated guest browsing on `/catalogo/*` (FR-026).<br>• Follows canonical Spanish URLs: `/perfil` and `/verificar-email` with redirects from `/profile` and `/verify-email`.<br>• Protects `/perfil` with `RoleRouteGuard` exclusively for `COMENSAL`. |
| **004 (`staff-mgmt-rbac-inventory`)** | • Merged into `main` (PR #113).<br>• Added `V3__extend_users_staff.sql` (`full_name`, `must_change_password`, expanded role/status CHECKs).<br>• Added `V4__create_ingredients_and_stock_audit.sql`.<br>• Subnavigation links in `WorkspaceLayout.tsx` for `CHEF` and `ADMINISTRADOR`.<br>• Bounded-context repository pattern (`StaffUserRepository`).<br>• Multi-tenant scoping via `TenantContextFilter` & `TenantContextHolder`. | • Migration sequence strictly numbered `V5__extend_users_profile_and_dietary.sql`.<br>• Synchronizes `full_name = first_name.trim() + " " + last_name.trim()` upon personal data updates.<br>• Creates `ProfileUserRepository` in `com.easyeat.profile.repository` following the 004 repository isolation pattern.<br>• Extends `WorkspaceLayout.tsx` top navigation bar with "Catálogo" (`/catalogo`) and "Mi perfil" (`/perfil`) links for `COMENSAL`.<br>• Respects `tenant_id IS NULL` for diners while scoping catalog queries to establishment path `{tenantId}`. |

---

## Technical Context

**Language/Version**: Java 21 (LTS) & TypeScript 5.x / React 18+

**Primary Dependencies**:
- Backend (`easyeat/backend`):
  - `spring-boot-starter-web` (Spring Boot 3.3.4) — REST controllers, Jackson JSON, embedded Tomcat
  - `spring-boot-starter-security` — Spring Security stateless filter chain, `@AuthenticationPrincipal User user`
  - `spring-boot-starter-data-jpa` — Hibernate ORM, repository abstractions
  - `spring-boot-starter-validation` — Jakarta Bean Validation (`@Valid`, regex patterns)
  - `spring-boot-starter-mail` — Transactional email dispatch for verification links
  - `io.jsonwebtoken:jjwt-api:0.12.6` — Stateless JWT extraction and validation
  - `org.flywaydb:flyway-core` + `flyway-database-postgresql` — Schema migration management
- Frontend (`easyeat/frontend`):
  - React 18+ with TypeScript (Vite bundler)
  - React Router DOM 6.x
  - Axios HTTP client with interceptors
  - Context API for ephemeral toggle and cart state management
  - Tailwind CSS for responsive UI

**Storage**: PostgreSQL 16+ (relational storage); Flyway migration `V5__extend_users_profile_and_dietary.sql`

**Testing Strategy**:
- JUnit 5, Mockito, AssertJ (unit tests for services, validators, and conjunction engine)
- Spring Security Test (`MockMvc`, `@WithMockUser`) for controller contract testing
- Testcontainers (PostgreSQL 16) for repository and Flyway migration verification
- React Testing Library + Jest for frontend components, toggle behavior, and route guards

**Target Platform**: Linux / Docker container (production); local environment (development)

**Project Type**: Multi-tenant web application within `easyeat/` monorepo (`easyeat/backend/` and `easyeat/frontend/`)

**Performance Goals**:
- Profile retrieval / update p95 ≤ 150ms
- Catalog retrieval with compliance enrichment p95 ≤ 200ms
- Frontend catalog master toggle flip: 0ms perceived latency (compliance evaluated server-side upfront)

**Constraints**:
- Dual validation: client-side checks backed by server-side Jakarta Bean Validation and database CHECK constraints
- No optimistic local-only state on dietary changes (explicit error notification on failure)
- Session-scoped master toggle: resets to ON upon page reload (F5) or restaurant change
- Zero raw token exposure: verification tokens hashed with SHA-256 before persistence

**Scale/Scope**: Sprint 1 MVP — Diner profile, dietary taxonomy, catalog filtering, cart risk evaluation. Out of scope: demographic data (avatar, age, gender), recipe N:M ingredient links, manual price/category sliders, keyword search.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Pre-Design Status | Post-Design Status | Evidence |
|---|---|---|---|
| I. Spec as source of truth | ✅ PASS | ✅ PASS | Plan, data model, and OpenAPI contracts derive directly from `spec.md` (FR-001–FR-027). |
| II. Spec before implementation | ✅ PASS | ✅ PASS | Full SDD workflow: `spec.md` → `/grill-me` design alignment → `research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, and `plan.md`. No implementation code written before plan approval. |
| III. Layered verification | ✅ PASS | ✅ PASS | Unit tests (conjunction logic, validation) + Testcontainers integration tests (Flyway V5, atomic email update, FK constraints) + MockMvc contract tests. |
| IV. Simple, cohesive design | ✅ PASS | ✅ PASS | Feature-based backend packaging (`profile`, `catalog`) and layered frontend structure (`components/`, `pages/`, `context/`). In-memory compliance engine avoids complex SQL stored procedures while maximizing testability. |
| V. Bounded scope | ✅ PASS | ✅ PASS | Excludes avatar upload, demographic data, recipe ingredient links, payment gateways as stipulated in `spec.md`. |
| VI. Controlled technical debt | ✅ PASS | ✅ PASS | Email resend rate-limiting handled with persisted timestamp in `users` table instead of premature Redis cluster setup. Migration versioning ordered sequentially (V5). |
| VII. Traceability | ✅ PASS | ✅ PASS | Complete Traceability Matrix in `data-model.md` mapping every FR-xxx requirement to schema columns, DTOs, and endpoints. |
| VIII. Evidence over assumption | ✅ PASS | ✅ PASS | All key technical decisions resolved interactively via `/grill-me` and verified against active repository code from 001, 002, and 004. |
| IX. Engineering over convenience | ✅ PASS | ✅ PASS | Real PostgreSQL Testcontainers chosen; SHA-256 token hashing; single-point risk confirmation on add-to-cart (FR-021, FR-022); session revocation via `token_version`. |
| X. Full-Stack End-to-End Integrity | ✅ PASS | ✅ PASS | Feature delivers an integrated vertical slice covering both Spring Boot REST endpoints and React client interfaces, routing, and guards. |
| XI. Upstream Synchronization Awareness | ✅ PASS | ✅ PASS | Proactive `git fetch origin main` executed; 45 commits of drift detected (including PR #113) and merged cleanly before planning finalization. |

**GATE RESULT: ALL PASS**

---

## Project Structure

### Documentation (`specs/003-diner-profile-dietary/`)

```text
specs/003-diner-profile-dietary/
├── plan.md              ← This file (Implementation Plan)
├── research.md          ← Phase 0 output — all /grill-me technical decisions (D-001..D-012)
├── data-model.md        ← Phase 1 output — entities, Flyway V5 migration, traceability
├── quickstart.md        ← Phase 1 output — runnable validation scenarios & curl guides
├── contracts/
│   └── openapi.yaml     ← Phase 1 output — OpenAPI 3.0.3 contract specification
└── checklists/
    └── quality.md       ← Quality verification checklist
```

### Source Code Layout (`easyeat/`)

```text
easyeat/
├── backend/
│   ├── src/main/resources/
│   │   ├── db/migration/
│   │   │   └── V5__extend_users_profile_and_dietary.sql
│   │   └── application.yml
│   ├── src/main/java/com/easyeat/
│   │   ├── config/
│   │   │   └── SecurityConfig.java (updated: permitAll for dishes, taxonomy, email confirm)
│   │   ├── profile/
│   │   │   ├── domain/
│   │   │   │   ├── DietaryRestriction.java
│   │   │   │   ├── DietaryCategory.java
│   │   │   │   └── UserDietaryRestrictionId.java
│   │   │   ├── dto/
│   │   │   │   ├── PersonalDataRequest.java
│   │   │   │   ├── ProfileResponse.java
│   │   │   │   ├── EmailChangeRequestDto.java
│   │   │   │   ├── EmailChangeInitiatedResponse.java
│   │   │   │   ├── EmailConfirmationRequest.java
│   │   │   │   ├── EmailConfirmedResponse.java
│   │   │   │   ├── DietaryRestrictionItemDto.java
│   │   │   │   └── DietaryPreferencesRequest.java
│   │   │   ├── repository/
│   │   │   │   ├── ProfileUserRepository.java (extends JpaRepository<User, UUID>)
│   │   │   │   └── DietaryRestrictionRepository.java
│   │   │   ├── service/
│   │   │   │   ├── ProfileService.java
│   │   │   │   └── EmailChangeService.java
│   │   │   └── controller/
│   │   │       ├── ProfileController.java
│   │   │       └── DietaryRestrictionController.java
│   │   └── catalog/
│   │       ├── domain/
│   │       │   ├── Dish.java
│   │       │   └── DishDietaryRestrictionId.java
│   │       ├── dto/
│   │       │   ├── EnrichedDishResponse.java
│   │       │   ├── CartComplianceRequest.java
│   │       │   ├── CartComplianceResponse.java
│   │       │   └── CartItemEvaluation.java
│   │       ├── repository/
│   │       │   └── DishRepository.java
│   │       ├── service/
│   │       │   └── CatalogComplianceService.java
│   │       └── controller/
│   │           ├── CatalogController.java
│   │           └── CartComplianceController.java
│   └── src/test/java/com/easyeat/
│       ├── profile/
│       │   ├── service/ (ProfileServiceTest.java, EmailChangeServiceTest.java)
│       │   ├── repository/ (ProfileUserRepositoryTest.java, Testcontainers)
│       │   └── controller/ (ProfileControllerTest.java, MockMvc)
│       └── catalog/
│           ├── service/ (CatalogComplianceServiceTest.java)
│           └── controller/ (CatalogControllerTest.java, CartComplianceControllerTest.java)
│
└── frontend/
    └── src/
        ├── api/
        │   ├── profileApi.ts
        │   └── catalogApi.ts
        ├── components/
        │   ├── common/
        │   │   └── WorkspaceLayout.tsx (updated: "Catálogo" and "Mi perfil" links for COMENSAL)
        │   ├── profile/
        │   │   ├── PersonalDataForm.tsx
        │   │   ├── PendingEmailBanner.tsx
        │   │   └── DietaryRestrictionsSelector.tsx
        │   ├── catalog/
        │   │   ├── CatalogMasterToggle.tsx
        │   │   ├── DishCard.tsx
        │   │   ├── AllergenWarningBadge.tsx
        │   │   └── EmptyCatalogDietaryState.tsx
        │   └── cart/
        │       ├── CartDrawer.tsx
        │       ├── CartItemWithBadge.tsx
        │       └── AddToCartRiskModal.tsx
        ├── context/
        │   ├── CatalogFilterContext.tsx
        │   └── CartContext.tsx
        ├── hooks/
        │   ├── useProfile.ts
        │   ├── useCatalog.ts
        │   └── useCart.ts
        ├── pages/
        │   ├── ProfilePage.tsx (mounted at /perfil)
        │   ├── VerifyEmailPage.tsx (mounted at /verificar-email)
        │   └── CatalogPage.tsx (mounted at /catalogo; guest CTA banner and toggle)
        ├── router/
        │   └── AppRoutes.tsx (updated: public /catalogo/*, role-guarded /perfil, /verificar-email, redirects)
        └── types/
            ├── profile.ts
            ├── catalog.ts
            └── cart.ts
```

---

## Complexity Tracking

No constitution violations detected. Design utilizes standard Spring Data JPA, stateless Spring Security filters with `@AuthenticationPrincipal User user`, isolated repository interfaces (`ProfileUserRepository`), sequential Flyway migration numbering (`V5`), and in-memory compliance evaluation without premature distributed caching or complex database stored procedures.
