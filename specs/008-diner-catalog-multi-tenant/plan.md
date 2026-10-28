# Implementation Plan: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Branch**: `008-diner-catalog-multi-tenant` | **Date**: 28/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/008-diner-catalog-multi-tenant/spec.md`

---

## Summary

Deliver the complete full-stack diner experience for discovering restaurant establishments and exploring real-time stock-aware gastronomic catalogs. Authenticated diners (`ROLE_COMENSAL`) search establishments with a central diacritic-insensitive autocomplete bar (`>= 3` characters). Selecting an active establishment sets the multi-tenant context and loads its live categorized menu (Entradas, Platos principales, Postres, Bebidas, Ensaladas).

Dishes display high-resolution photos (with automatic placeholder fallback), names, descriptions, prices, and recipe ingredients in an informational detail modal. Dish availability is computed dynamically from kitchen inventory (`CurrentStock >= RecipeQuantity`). When stock changes, dependent dishes update in real time within a maximum SLA of 3 seconds using lightweight conditional polling with HTTP ETags (`304 Not Modified`), smoothly toggling greyscale styling and `"Agotado"` badges without page reloads or visual flicker. Browsing outside operating hours presents the menu in read-only mode with an opening schedule banner. Unauthenticated access attempts are strictly rejected with HTTP 401 Unauthorized and redirected to login, exposing zero restaurant or catalog data.

Shopping cart creation, checkout, payment processing, and direct QR/deep-link access are out of scope for Sprint 2. However, switching active establishments when items exist in the diner's in-memory cart triggers a confirmation dialog that empties the cart upon confirmation (COM-1.5.1 CA 5), integrating cleanly with the existing frontend `CartContext` from spec 003.

---

## Technical Context

**Language/Version**: Java 21, TypeScript 5.x / React 18  
**Primary Dependencies**:
- Backend: Spring Boot 3.3.4 (Spring Web, Spring Data JPA, Spring Security, Validation), Flyway Core & PostgreSQL, JJWT (`0.12.5`), Lombok, Jackson Problem (`problem-spring-web`), Testcontainers (`1.20.1`).
- Frontend: React 18, React Router v6, Tailwind CSS, Lucide icons, Axios / native Fetch API.  
**Storage**: PostgreSQL 16 (relational tables: `establishments`, `dishes`, `dish_recipe_ingredients`, `ingredients`).  
**Testing**:
- Backend: JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers (PostgreSQL integration tests).
- Frontend: Vitest, React Testing Library, jsdom.  
**Target Platform**: Linux / Docker containers for backend services; modern evergreen web browsers (desktop and mobile) for frontend SPA.  
**Project Type**: Full-stack Web Application (Spring Boot REST backend + React SPA frontend).  
**Performance Goals**:
- Real-time stock update SLA: `<= 3 seconds` latency from stock modification to visual catalog reflection.
- Establishment autocomplete search: `< 300 ms` response time with debounced requests.
- Bandwidth optimization: HTTP 304 Not Modified on conditional polling when kitchen stock is unchanged.  
**Constraints**:
- Mandatory diner authentication: `ROLE_COMENSAL` required for all discovery and catalog endpoints. Unauthenticated requests return HTTP 401 with RFC 7807 `ProblemDetail` and zero data leakage.
- Strict multi-tenancy: 100% of queries, categories, dishes, and stock evaluations scoped strictly to selected establishment `tenant_id`.
- Binary stock availability: available vs out-of-stock badge. Raw kitchen stock quantities and numeric portion counts must NOT be exposed to diners.
- Operating hours awareness: dynamic `isOpen` evaluation in establishment's timezone (`America/Argentina/Buenos_Aires`).  
**Scale/Scope**: Multi-tenant gastronomic SaaS platform, hundreds of active restaurants, menus with 50+ dishes across 5 categories, 100+ concurrent diners per establishment.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence / Justification |
|---|---|---|
| **I. The Specification Is the Source of Truth** | **PASS** | Implementation design faithfully reflects all 29 Functional Requirements (FR-001 to FR-029) and 11 Success Criteria (SC-001 to SC-011) of `spec.md`. General cart management, checkout, and deep-linking are strictly excluded. |
| **II. Specification Before Implementation** | **PASS** | Specification completed and approved; technical planning artifacts (`research.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, `plan.md`) generated prior to any code implementation or task execution. |
| **III. Layered Verification Proportional to Risk** | **PASS** | Three-tier testing strategy in `quickstart.md`: (1) Tier 1 pure in-memory unit tests (`DishAvailabilityServiceTest`) covering decimal boundaries (`BigDecimal`), exact limits, fractional shortfalls, negative stock anomalies, empty recipes, and shared N:M ingredients (< 50 ms); (2) Tier 2 integration tests with singleton PostgreSQL Testcontainers (`MultiTenantCatalogIntegrationTest`) validating strict tenant isolation with homonymous entities and security error codes (401, 403, 404); (3) Tier 3 static frontend component tests in Vitest (`DishCard.test.tsx`, `CatalogPage.test.tsx`) for available vs out-of-stock rendering without timers. |
| **IV. Simple, Cohesive, and Justified Design** | **PASS** | Applied lightweight conditional polling with standard HTTP ETags (`304 Not Modified`) rather than complex stateful WebSockets. Third normal form (3NF) relational schema for recipe lines. No unnecessary abstractions. |
| **V. Bounded Scope** | **PASS** | Confined strictly to establishment discovery, live categorized menu browsing, and real-time stock-based availability. Shopping cart and checkout are completely omitted in compliance with user agreements. |
| **VI. Controlled Technical Debt** | **PASS** | Clean Flyway migration `V8__create_establishments.sql` (sequentially following `V7__chef_dish_management.sql`), creating exclusively `establishments` and seed data without touching culinary tables owned by 005/007. |
| **VII. Traceability & Documentation** | **PASS** | Explicit bidirectional traceability between requirements, data model entities, OpenAPI endpoints, and quickstart scenarios. |
| **VIII. Evidence Over Assumption & Escalation** | **PASS** | All key architectural questions (status machine alignment, PVP retail price, dish photo consumption, dietary compliance, migration scope) interviewed and agreed in grill-me session. |
| **IX. Engineering Over Convenience** | **PASS** | Strict RFC 7807 ProblemDetail for security errors, robust diacritic normalization, timezone-aware server-side operating hours evaluation. |
| **X. Full-Stack End-to-End Integrity (Vertical Slices)** | **PASS** | Delivers complete vertical slice: database DDL (`V8`), JPA entities and repositories, REST controllers and security configuration, and React frontend pages (`/descubrimiento`, `/catalogo`), components, hooks, and route guards. |
| **XI. Continuous Upstream Synchronization Awareness** | **PASS** | Synchronized with `develop/sprint-2`, fully coordinated with specs 003, 004, 005, 006, and 007. |

---

## Project Structure

### Documentation (this feature)

```text
specs/008-diner-catalog-multi-tenant/
├── spec.md              # Functional specification (aligned with specs 005, 006 & 007)
├── checklists/
│   └── requirements.md  # Specification quality checklist
├── plan.md              # This technical implementation plan
├── research.md          # Phase 0: technical research and architectural decisions
├── data-model.md        # Phase 1: relational schema, entities, and business rules
├── contracts/
│   └── openapi.yaml     # Phase 1: OpenAPI 3.0 contract specification
├── quickstart.md        # Phase 1: runnable end-to-end validation scenarios
└── tasks.md             # Phase 2: task breakdown (to be created by /speckit-tasks)
```

### Source Code Layout

```text
easyeat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/easyeat/
│   │   │   │   ├── catalog/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   ├── EstablishmentDiscoveryController.java  # GET /api/v1/establishments/search
│   │   │   │   │   │   └── DinerCatalogController.java            # GET /api/v1/establishments/{id}/catalog & availability
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── Establishment.java                     # Entity for restaurant & operating hours (owned by 008)
│   │   │   │   │   │   ├── EstablishmentStatus.java               # Enum: ACTIVE, PAUSED, DISABLED
│   │   │   │   │   │   # Reuses com.easyeat.dish.domain.DishCategory (spec 005)
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── EstablishmentSummaryResponse.java      # Autocomplete result DTO
│   │   │   │   │   │   ├── DinerCatalogResponse.java              # Full categorized menu DTO
│   │   │   │   │   │   ├── DishCatalogItemDto.java                # Dish card item DTO with availability & dietary flags
│   │   │   │   │   │   ├── CategoryGroupDto.java                  # Category section DTO
│   │   │   │   │   │   └── AvailabilityMapResponse.java           # Map of dishId to boolean with ETag
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── EstablishmentRepository.java           # JPA repo with unaccent/ILIKE search
│   │   │   │   │   └── service/
│   │   │   │   │       ├── EstablishmentDiscoveryService.java     # Autocomplete business logic
│   │   │   │   │       ├── DinerCatalogService.java               # Menu assembly, schedule checks & dietary compliance
│   │   │   │   │       └── DishAvailabilityEngine.java            # Dynamic stock evaluation & ETag hashing
│   │   │   │   └── config/
│   │   │   │       └── SecurityConfig.java                        # Updated for /api/v1/establishments/** hasRole('COMENSAL')
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   │           └── V8__create_establishments.sql                  # Flyway DDL (establishments & seeds)
│   │   └── test/java/com/easyeat/catalog/
│   │       ├── controller/
│   │       │   ├── EstablishmentDiscoveryControllerTest.java     # Autocomplete MockMvc tests
│   │       │   └── DinerCatalogControllerTest.java                # Catalog & Availability MockMvc tests
│   │       ├── service/
│   │       │   ├── DishAvailabilityEngineTest.java                # Unit tests for stock formula & edge cases
│   │       │   └── DinerCatalogServiceTest.java                   # Category grouping, status filtering & compliance
│   │       └── integration/
│   │           └── DinerCatalogIntegrationTest.java               # Full vertical slice with Testcontainers
│   └── pom.xml
│
└── frontend/
    ├── src/
    │   ├── api/
    │   │   └── dinerCatalogApi.ts                                 # API client for search, catalog, and availability polling
    │   ├── components/
    │   │   └── catalog/
    │   │       ├── EstablishmentSearchBar.tsx                     # Autocomplete search bar (>= 3 chars, debounce)
    │   │       ├── EstablishmentBlockingNotice.tsx                # Full-page blocking screen for paused restaurants
│       │       ├── EstablishmentSwitchModal.tsx                   # Confirmation modal to clear cart when switching tenant (COM-1.5.1 CA 5)
    │   │       ├── StickyCategoryNav.tsx                          # Smooth-scrolling category navigation bar
    │   │       ├── DishCard.tsx                                   # Dish card (color/greyscale, Agotado badge, image fallback)
    │   │       ├── DishDetailModal.tsx                            # Informational modal (photo, recipe ingredients, state)
    │   │       ├── CatalogSearchFilter.tsx                        # In-catalog search filter by title/ingredients
    │   │       └── OperatingHoursBanner.tsx                       # Banner for closed schedule read-only mode
    │   ├── context/
    │   │   └── TenantContext.tsx                                  # Active establishment state management
    │   ├── hooks/
    │   │   ├── useEstablishmentSearch.ts                          # Hook for autocomplete queries
    │   │   ├── useDinerCatalog.ts                                 # Hook for catalog loading and category navigation
    │   │   └── useRealtimeAvailability.ts                         # Hook for 2.5s conditional polling with ETag and Page Visibility API
    │   ├── pages/
    │   │   ├── EstablishmentDiscoveryPage.tsx                     # Dedicated /descubrimiento route
    │   │   └── CatalogPage.tsx                                    # Refactored for discovery redirect & stock availability
    │   └── router/
    │       ├── AppRoutes.tsx                                      # Protected routes for /descubrimiento and /catalogo
    │       └── postAuthRouting.ts                                 # Default redirect to /descubrimiento for COMENSAL
    └── tests/
        ├── components/
        │   ├── EstablishmentSearchBar.test.tsx
        │   ├── DishCard.test.tsx
        │   └── DishDetailModal.test.tsx
        └── hooks/
            └── useRealtimeAvailability.test.ts
```

**Structure Decision**: Standard full-stack web application structure. Backend adheres to existing Spring Boot package-by-feature conventions under `com.easyeat.catalog`. Frontend follows the established component, hook, and page hierarchy under `src/` with React Router and Tailwind CSS.

---

## Complexity Tracking

> *Constitution Check passes completely with zero violations. No complex patterns or premature abstractions introduced.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | N/A | N/A |
