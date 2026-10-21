# Implementation Plan: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Branch**: `003-diner-profile-dietary` | **Date**: 2026-10-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-diner-profile-dietary/spec.md` and product backlog (COM-1.4.1, COM-1.4.3, COM-4.2.1).

**Note**: All design decisions grilled, tested, and resolved via `/grill-me` interactive design session prior to artifact creation.

---

## Summary

Implement diner profile editing, dietary preference and allergen management, and their automatic application to establishment menu catalogs for the EasyEat multi-tenant platform (Sprint 1).

Key capabilities delivered:
- **Diner Personal Data Management (COM-1.4.1)**: First name, last name, phone number editing with strict format validation, and an asynchronous 24-hour verification token workflow for email changes with session revocation (`token_version`).
- **Dietary Restrictions & Allergens Configuration (COM-1.4.3)**: Standardized platform taxonomy (Lifestyles: Celiac, Vegan, Vegetarian; Allergens: Dairy, Peanuts, Tree Nuts, Eggs, Fish, Shellfish, Soy, Wheat) with server-side persistence and live cart re-evaluation.
- **Automated Catalog Filtering & Browsing (COM-4.2.1)**: Automated default injection of dietary profile filters using strict conjunction (AND logic), pre-computed compliance in the backend (`is_compliant`, `conflicting_restrictions`), an ephemeral session master toggle (ON/OFF) with 0ms perceived latency, empty state handling, and mandatory risk consent at checkout.

---

## Technical Context

**Language/Version**: Java 21 (LTS) & TypeScript 5.x / React 18+

**Primary Dependencies**:
- Backend:
  - `spring-boot-starter-web` — REST controllers, Jackson JSON, embedded Tomcat
  - `spring-boot-starter-security` — Spring Security 6.3.x stateless filter chain
  - `spring-boot-starter-data-jpa` — Hibernate ORM, repository abstractions
  - `spring-boot-starter-validation` — Jakarta Bean Validation (`@Valid`, regex patterns)
  - `spring-boot-starter-mail` — Transactional email dispatch for verification links
  - `io.jsonwebtoken:jjwt-api:0.12.6` — Stateless JWT extraction and validation
  - `org.flywaydb:flyway-core` + `flyway-database-postgresql` — Schema migration management
- Frontend:
  - React 18+ with TypeScript
  - Modular hooks and Context API for ephemeral state management

**Storage**: PostgreSQL 16+ (relational storage); Flyway migration `V3__extend_users_profile_and_dietary.sql`

**Testing**:
- JUnit 5, Mockito, AssertJ (unit tests)
- Spring Security Test (`MockMvc`) for controller contract testing
- Testcontainers (PostgreSQL 16) for repository and Flyway migration verification
- React Testing Library + Jest for frontend component state and toggle tests

**Target Platform**: Linux / Docker container (production); local environment (development)

**Project Type**: Multi-tenant SaaS web application (Spring Boot backend + React frontend)

**Performance Goals**:
- Profile retrieval / update p95 ≤ 150ms
- Catalog retrieval with compliance enrichment p95 ≤ 200ms
- Frontend catalog master toggle flip: 0ms perceived latency (compliance evaluated server-side upfront)

**Constraints**:
- Strict dual validation: client-side checks backed by server-side validation and database CHECK constraints
- No optimistic local-only state on dietary changes (explicit error notification on failure)
- Session-scoped master toggle: resets to ON upon page reload (F5) or restaurant change
- Zero token compromise: verification tokens hashed with SHA-256 before persistence

**Scale/Scope**: Sprint 1 MVP — Diner profile, dietary taxonomy, catalog filtering, cart risk evaluation. Out of scope: demographic data (avatar, age, gender), manual price/category filters, keyword search.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Pre-Design Status | Post-Design Status | Evidence |
|---|---|---|---|
| I. Spec as source of truth | ✅ PASS | ✅ PASS | Plan, data model, and OpenAPI contracts derive directly from `spec.md` (FR-001–FR-027). |
| II. Spec before implementation | ✅ PASS | ✅ PASS | Workflow: `spec.md` approved → `/grill-me` design → `plan.md` + design artifacts. No application code written yet. |
| III. Layered verification | ✅ PASS | ✅ PASS | Unit tests (conjunction logic, validation) + Testcontainers integration tests (atomic email update, FK constraints) + MockMvc contract tests. |
| IV. Simple, cohesive design | ✅ PASS | ✅ PASS | Feature-based packaging (`profile`, `catalog`, `cart`). In-memory compliance engine avoids complex SQL stored procedures while maximizing testability. |
| V. Bounded scope | ✅ PASS | ✅ PASS | Excludes avatar upload, demographic data, category sliders, keyword search, payment gateways as stipulated in `spec.md`. |
| VI. Controlled technical debt | ✅ PASS | ✅ PASS | Email resend rate-limiting handled with persisted timestamp in `users` table instead of premature Redis cluster setup. |
| VII. Traceability | ✅ PASS | ✅ PASS | Complete Traceability Matrix in `data-model.md` mapping every FR-xxx requirement to schema columns, DTOs, and endpoints. |
| VIII. Evidence over assumption | ✅ PASS | ✅ PASS | All 5 key technical decisions resolved interactively via `/grill-me` (compliance engine, cooldown persistence, dish schema, toggle state, principal injection). |
| IX. Engineering over convenience | ✅ PASS | ✅ PASS | Real PostgreSQL Testcontainers chosen over H2; SHA-256 token hashing used; explicit risk consent enforced at checkout. |
| X. Technology-agnostic governance | ✅ PASS | ✅ PASS | Follows project's technology stack without introducing unapproved libraries or architectural deviations. |

**GATE RESULT: ALL PASS — Phase 0 and Phase 1 complete.**

---

## Project Structure

### Documentation (this feature)

```text
specs/003-diner-profile-dietary/
├── plan.md              ← This file (Phase 0+1 output)
├── research.md          ← Phase 0 output — all /grill-me technical decisions (D-001..D-008)
├── data-model.md        ← Phase 1 output — entities, Flyway V3 migration, traceability
├── quickstart.md        ← Phase 1 output — runnable validation scenarios & curl guides
├── contracts/
│   └── openapi.yaml     ← Phase 1 output — OpenAPI 3.0.3 contract specification
└── checklists/
    └── requirements.md  ← Quality verification checklist
```

### Source Code Layout

```text
backend/
├── src/main/resources/
│   ├── db/migration/
│   │   └── V3__extend_users_profile_and_dietary.sql
│   └── application.yml
├── src/main/java/com/easyeat/
│   ├── profile/
│   │   ├── domain/
│   │   │   ├── DietaryRestriction.java
│   │   │   ├── DietaryCategory.java
│   │   │   └── UserDietaryRestrictionId.java
│   │   ├── dto/
│   │   │   ├── PersonalDataRequest.java
│   │   │   ├── ProfileResponse.java
│   │   │   ├── EmailChangeRequestDto.java
│   │   │   ├── EmailChangeInitiatedResponse.java
│   │   │   ├── EmailConfirmationRequest.java
│   │   │   ├── EmailConfirmedResponse.java
│   │   │   ├── DietaryRestrictionItemDto.java
│   │   │   └── DietaryPreferencesRequest.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java (extended)
│   │   │   └── DietaryRestrictionRepository.java
│   │   ├── service/
│   │   │   ├── ProfileService.java
│   │   │   └── EmailChangeService.java
│   │   └── controller/
│   │       ├── ProfileController.java
│   │       └── DietaryRestrictionController.java
│   └── catalog/
│       ├── domain/
│       │   ├── Dish.java
│       │   └── DishDietaryRestrictionId.java
│       ├── dto/
│       │   ├── EnrichedDishResponse.java
│       │   ├── CartComplianceRequest.java
│       │   ├── CartComplianceResponse.java
│       │   └── CartItemEvaluation.java
│       ├── repository/
│       │   └── DishRepository.java
│       ├── service/
│       │   └── CatalogComplianceService.java
│       └── controller/
│           ├── CatalogController.java
│           └── CartComplianceController.java
└── src/test/java/com/easyeat/
    ├── profile/
    │   ├── service/ (ProfileServiceTest.java, EmailChangeServiceTest.java)
    │   ├── repository/ (UserRepositoryProfileTest.java, Testcontainers)
    │   └── controller/ (ProfileControllerTest.java, MockMvc)
    └── catalog/
        ├── service/ (CatalogComplianceServiceTest.java)
        └── controller/ (CatalogControllerTest.java, CartComplianceControllerTest.java)

frontend/
├── src/
│   ├── features/
│   │   ├── profile/
│   │   │   ├── components/
│   │   │   │   ├── PersonalDataForm.tsx
│   │   │   │   ├── PendingEmailBanner.tsx
│   │   │   │   └── DietaryRestrictionsSelector.tsx
│   │   │   └── hooks/
│   │   │       ├── useProfile.ts
│   │   │       └── useDietaryRestrictions.ts
│   │   ├── catalog/
│   │   │   ├── components/
│   │   │   │   ├── CatalogMasterToggle.tsx
│   │   │   │   ├── DishCard.tsx
│   │   │   │   ├── AllergenWarningBadge.tsx
│   │   │   │   └── EmptyCatalogDietaryState.tsx
│   │   │   ├── context/
│   │   │   │   └── CatalogFilterContext.tsx
│   │   │   └── hooks/
│   │   │       └── useCatalog.ts
│   │   └── cart/
│   │       └── components/
│   │           ├── CartItemWithBadge.tsx
│   │           └── CheckoutDietaryRiskModal.tsx
```

**Structure Decision**: Web application layout separating `backend` (Spring Boot 3.3.4, Java 21) and `frontend` (React 18+, TypeScript). Adheres to feature-sliced packaging (`profile`, `catalog`, `cart`) for high cohesion and low coupling.

---

## Complexity Tracking

No constitution violations detected. Design utilizes standard Spring Data JPA, stateless Spring Security filters, and in-memory evaluation without premature infrastructure complexity.
