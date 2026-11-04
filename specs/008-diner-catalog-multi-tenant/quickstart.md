# Quickstart Validation Guide & Test Suite Specification: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Feature Branch**: `008-diner-catalog-multi-tenant`  
**Date**: 28/10/2026  
**Status**: Draft (Audited & Test-Hardened for Stock and Tenant Isolation)  
**Spec Reference**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/008-diner-catalog-multi-tenant/spec.md)  
**Contract Reference**: [openapi.yaml](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/008-diner-catalog-multi-tenant/contracts/openapi.yaml)  
**Data Model Reference**: [data-model.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/008-diner-catalog-multi-tenant/data-model.md)

---

## 1. Prerequisites and Setup

1. **Docker Environment**: PostgreSQL 16 container running and accessible, or active Testcontainers runtime.
2. **Database Migrations**: Flyway executed up to `V8__create_establishments.sql`.
3. **Authentication**: Active JWT token with `COMENSAL` (`ROLE_COMENSAL`) role obtained via `/api/v1/auth/login`.
4. **Backend**: Spring Boot 3.3.4 running on port 8080 (`./mvnw spring-boot:run` or automated test suite).
5. **Frontend**: Vite development server running on port 5173 (`npm run dev` in `easyeat/frontend`).

---

## 2. Test Suite Architecture: Layered Verification Proportional to Risk (Constitution Principle III)

To prevent build slowdowns while maximizing verification rigor, tests are structured in a three-tier pyramid:

```text
               ▲
              / \     Tier 3: Frontend Component Tests (Vitest)
             /   \    Static rendering: Available vs Agotado, badge, modal (< 200 ms)
            /─────\
           /       \   Tier 2: Multi-Tenant Integration Tests (PostgreSQL Testcontainers)
          /         \  Real SQL WHERE tenant_id, homonymous entities, singleton container (~3-5 s)
         /───────────\
        /             \ Tier 1: Stock Boundary & Recipe Unit Tests (Pure Java / In-Memory)
       /               \ Decimal arithmetic, exact limits, negative stock, N:M matrix (< 50 ms)
      ───────────────────
```

---

## 3. Tier 1: Stock Calculation & Boundary Unit Test Suite (`DishAvailabilityServiceTest`)

Class: `com.easyeat.catalog.service.DishAvailabilityServiceTest`  
Framework: JUnit 5, Mockito, AssertJ. Pure in-memory execution (< 50 ms total runtime).

### Test Cases & Edge Cases

| Test Method | Category / Edge Case | Input Condition | Expected Behavior |
|---|---|---|---|
| `shouldMarkAvailableWhenCurrentStockEqualsRecipeQuantityExactBoundary()` | Exact boundary | Recipe needs `0.250 kg`, stock is `0.250 kg` | `isAvailable = true` |
| `shouldMarkOutOfStockWhenCurrentStockHasFractionalShortfall()` | Sub-minimal shortfall | Recipe needs `0.250 kg`, stock is `0.249 kg` | `isAvailable = false` (Agotado), no floating-point rounding errors |
| `shouldMarkOutOfStockWhenAnySingleIngredientHasZeroStock()` | Partial ingredient depletion | Recipe requires 3 ingredients: A (stock 5), B (stock 0), C (stock 10) | `isAvailable = false` |
| `shouldHandleNegativeStockGracefullyWithoutException()` | Data anomaly guard | Recipe needs `1.0 unit`, inventory registers `-1.0 units` | `isAvailable = false`, no runtime exception thrown |
| `shouldHandleEmptyRecipeAsOutOfStockWithout500Error()` | Empty recipe guard | Dish in `PUBLISHED` status has zero lines in recipe composition | `isAvailable = false`, handled gracefully without 500 error |
| `shouldHandleDecommissionedIngredientAsOutOfStockWithout500Error()` | Inactive inventory item | Recipe references an ingredient marked `is_active = false` | `isAvailable = false`, handled safely |
| `shouldDiscriminateAvailabilityForSharedIngredientWithAsymmetricDemands()` | Shared N:M matrix | Ingredient "Carne" has stock `1.0 kg`. Dish A requires `2.0 kg`; Dish B requires `0.5 kg` | Dish A: `isAvailable = false`; Dish B: `isAvailable = true` |
| `shouldInvalidateAllDependentDishesSimultaneouslyWhenSharedIngredientReachesZero()` | Shared depletion | Dishes A, B, and C depend on "Queso". "Queso" stock drops from `10` to `0` | Dishes A, B, and C all evaluate to `isAvailable = false` simultaneously |
| `shouldReenableAllDependentDishesWhenDepletedIngredientIsReplenished()` | Replenishment | Dishes A and B were out of stock. Kitchen replenishes stock above recipe thresholds | Dishes A and B both evaluate to `isAvailable = true` |
| `shouldNeverExposeNumericStockAmountsInDinerCatalogItemPayload()` | Information security | Catalog DTO mapping for diner endpoint | DTO exposes strictly boolean `isAvailable`; zero internal stock numbers or portion quantities |

---

## 4. Tier 2: Multi-Tenant Isolation Integration Test Suite (`MultiTenantCatalogIntegrationTest`)

Class: `com.easyeat.catalog.controller.MultiTenantCatalogIntegrationTest`  
Framework: Spring Boot `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `MockMvc`, PostgreSQL Testcontainers (Singleton Container Pattern).

### Test Cases & Edge Cases

| Test Method | Verification Target | Setup / Action | Assertion & Guarantee |
|---|---|---|---|
| `shouldIsolateDishesAndStockBetweenTenantsWithHomonymousEntities()` | Strict tenant isolation | Create Tenant A and Tenant B. Both have a dish named `"Hamburguesa Clásica"` and ingredient `"Carne"`. Set Tenant A `"Carne"` stock = `0`; set Tenant B `"Carne"` stock = `50`. Query both catalogs. | Tenant A returns `"Hamburguesa Clásica"` as `isAvailable: false`. Tenant B returns `"Hamburguesa Clásica"` as `isAvailable: true`. Zero cross-tenant stock interference. |
| `shouldNeverLeakDishesAcrossTenantsInCatalogQuery()` | Catalog query scoping | Tenant A has 5 dishes; Tenant B has 3 dishes. Query `GET /api/v1/establishments/{tenantA}/catalog`. | Exactly 5 dishes returned. 0 dishes from Tenant B returned. 100% scoped to Tenant A. |
| `shouldReturn404ProblemDetailWhenTenantIdDoesNotExist()` | Non-existent tenant | Send `GET /api/v1/establishments/{randomUUID}/catalog` with valid JWT. | HTTP 404 Not Found, RFC 7807 `ProblemDetail` with `type: "about:blank"`, zero data leaked. |
| `shouldReturn403ProblemDetailWhenTenantIsPausedOrDisabled()` | Paused/disabled tenant | Set Tenant status to `PAUSED`. Send request to catalog endpoint. | HTTP 403 Forbidden with temporary inactivity message ("This establishment is temporarily inactive and is not accepting orders"). |
| `shouldRejectUnauthenticatedRequestsWith401Unauthorized()` | Authentication security | Send catalog or search requests without `Authorization` bearer token. | HTTP 401 Unauthorized, zero catalog or establishment data exposed. |

---

## 5. Tier 3: Frontend Component Test Suite (Vitest / React Testing Library)

Directory: `easyeat/frontend/src/test/catalog/`  
Framework: Vitest, React Testing Library, jsdom. Focused on static component rendering contracts.

### Test Cases & Edge Cases

| Test File | Test Case | Render State | Assertions |
|---|---|---|---|
| `DishCard.test.tsx` | `rendersAvailableDishInFullColor()` | Dish with `isAvailable: true` | Card rendered in full color, no `"Agotado"` badge, card action button enabled |
| `DishCard.test.tsx` | `rendersOutOfStockDishInGreyscaleWithBadge()` | Dish with `isAvailable: false` | Card element has greyscale styling (`grayscale opacity-75`), `"Agotado"` badge visible, card action button disabled |
| `DishCard.test.tsx` | `opensDetailModalWithOutOfStockNotice()` | Click card of dish with `isAvailable: false` | Detail modal opens, displays photo/placeholder, description, price, ingredients, and `"Agotado"` status notice |
| `CatalogPage.test.tsx` | `hidesCategoriesWithZeroPublishedDishes()` | Category where all dishes are in `DRAFT` | Category heading and its item in sticky category navigation bar are not rendered in the DOM |
| `CatalogPage.test.tsx` | `showsCategoryEvenWhenAllPublishedDishesAreOutOfStock()` | Category where all dishes are `isAvailable: false` | Category heading remains visible in DOM, dishes rendered in greyscale with `"Agotado"` badges |
| `CatalogPage.test.tsx` | `rendersReadOnlyModeOutsideOperatingHours()` | Establishment with `isOpen: false` | Informational opening schedule banner displayed, dishes shown in full color, selection/order actions disabled |
| `CatalogSearch.test.tsx` | `filtersDishesInRealTimeCaseAndDiacriticInsensitive()` | Typing `"jamon"` in catalog search | Displays `"Jamón Serrano"`, hides non-matching categories, clearing input restores full catalog |

---

## 6. End-to-End Manual Validation Scenarios

### Scenario 1: Establishment Discovery & Autocomplete (FR-001, FR-002, FR-003, SC-001)
- **Action**: In the search bar on `/descubrimiento`, enter 2 characters (e.g., `"ca"`).
- **Expected Outcome**: No autocomplete results displayed; input waits for 3rd character.
- **Action**: Enter 3rd character without diacritics (e.g., `"caf"`).
- **Expected Outcome**: Autocomplete dropdown appears within 300 ms displaying `"Café Central"`. Case and diacritics are ignored. Only active establishments are shown.

### Scenario 2: Active Establishment Selection & Catalog Load (FR-004, FR-007, FR-008, SC-001, SC-002)
- **Action**: Select `"Café Central"` from the autocomplete dropdown.
- **Expected Outcome**:
  - Multi-tenant context is set to the selected establishment ID.
  - Browser navigates to `/catalogo`.
  - Menu loads categorized under Entradas, Platos principales, Postres, Bebidas, Ensaladas.
  - Dishes display retail price (PVP from spec 007), dietary restriction badges (spec 003), and photos served with placeholder fallback.
  - Sticky category navigation bar allows smooth scrolling between sections.
  - 100% of data is scoped strictly to selected establishment (zero cross-tenant leakage).

### Scenario 3: Paused / Disabled Establishment Handling (FR-005, SC-008)
- **Action**: Attempt to view a catalog for an establishment marked as `PAUSED` or `DISABLED`.
- **Expected Outcome**: Backend returns HTTP 403 ProblemDetail; frontend displays a full-page blocking notice ("This establishment is temporarily inactive and is not accepting orders") with zero catalog/dish data leaked, and provides an action to return to `/descubrimiento`.

### Scenario 4: Dynamic Stock-Driven Dish Availability (FR-013, FR-014, SC-003)
- **Initial State**: Dish `"Hamburguesa Clásica"` requires 1 unit of `"Pan de papa"` (Stock = 5) and 1 unit of `"Carne molida"` (Stock = 2).
- **Expected Outcome**: Dish renders in full color with active card controls.
- **Action**: In kitchen inventory, adjust `"Carne molida"` stock to 0.
- **Expected Outcome**: Within `<= 3 seconds` (without manual browser refresh), the conditional polling endpoint detects the change and returns updated availability. The dish card transitions to greyed-out with a visible `"Agotado"` badge.
- **Action**: In kitchen inventory, replenish `"Carne molida"` stock to 10.
- **Expected Outcome**: Within `<= 3 seconds`, the dish card returns to full color and the badge disappears.

### Scenario 5: Simultaneous Invalidation for Shared Critical Ingredients (FR-017, SC-004)
- **Initial State**: Dish A (`"Milanesa Napolitana"`) and Dish B (`"Pizza Mozzarella"`) both depend on `"Queso Mozzarella"` (Stock = 1).
- **Action**: Kitchen updates `"Queso Mozzarella"` stock to 0.
- **Expected Outcome**: Within `<= 3 seconds`, both Dish A and Dish B simultaneously transition to greyed-out with `"Agotado"` badges across their respective categories.

### Scenario 6: Non-Published Dish and Empty Category Hiding (FR-009, FR-015, SC-005, SC-006)
- **Initial State**: Category `"Bebidas"` has two dishes: one in `DRAFT` status and one in `PUBLISHED` status.
- **Expected Outcome**: Only the `PUBLISHED` dish is rendered; the `DRAFT` dish is completely absent.
- **Action**: The remaining dish in `"Bebidas"` transitions to `DRAFT` (or has not been approved yet).
- **Expected Outcome**: The entire `"Bebidas"` category heading and its entry in the sticky navigation bar disappear from the catalog view.

### Scenario 7: In-Catalog Real-Time Search (FR-020, FR-021, FR-022, FR-024, SC-007)
- **Action**: In the catalog search bar, type `"jamon"` (without accent).
- **Expected Outcome**: Dishes whose title or recipe ingredients contain `"Jamón"` (with accent) appear; non-matching categories are hidden.
- **Action**: Clear search bar.
- **Expected Outcome**: Complete catalog with all categories immediately reappears without page reload.

### Scenario 8: Operating Hours & Read-Only Mode (FR-012, SC-010)
- **Initial State**: Current server time in restaurant's timezone is outside `[opening_time, closing_time)`.
- **Expected Outcome**:
  - Catalog renders in read-only mode in full color with an informational banner displaying the next opening schedule (e.g., `"Abre hoy a las 11:00"`).
  - All dish cards are disabled for ordering; clicking opens the informational detail modal showing the outside-hours notice without labelling dishes as out of stock.

### Scenario 9: Mandatory Authentication & Zero Data Exposure (FR-027, SC-011)
- **Action**: Send `GET /api/v1/establishments/search?q=cafe` and `GET /api/v1/establishments/{id}/catalog` without `Authorization` header.
- **Expected Outcome**: Backend returns `HTTP 401 Unauthorized` with RFC 7807 ProblemDetail. Zero establishment, dish, or stock data is returned.
- **Action**: Direct unauthenticated browser navigation to `/descubrimiento` or `/catalogo`.
- **Expected Outcome**: Frontend `RoleRouteGuard` intercepts navigation and redirects client to `/login`.

### Scenario 10: Complete Establishment Listing (Fix C-01, FR-030, FR-031, FR-032)
- **Action**: Login as an authenticated `ROLE_COMENSAL` diner and open `/descubrimiento`.
- **Expected Outcome**: The discovery screen loads the complete list of active establishments via `GET /api/v1/establishments`; paused/disabled establishments do not appear; selecting one loads its live catalog (same behavior as COM-1.5.1 CA2); if no active establishments exist, an informative empty state is displayed.

---

## 7. Automated Execution Commands

```bash
# Tier 1: Unit tests for stock evaluation, decimal boundaries, and recipes (Pure in-memory, < 50 ms)
cd easyeat/backend
./mvnw test -Dtest="DishAvailabilityServiceTest"

# Tier 2: Multi-tenant isolation integration tests with PostgreSQL Testcontainers
./mvnw test -Dtest="MultiTenantCatalogIntegrationTest"

# Tier 3: Frontend component and page tests with Vitest (Static rendering)
cd ../frontend
npm test -- src/test/catalog
```
