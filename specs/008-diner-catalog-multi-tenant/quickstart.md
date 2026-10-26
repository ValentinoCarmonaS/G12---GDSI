# Quickstart Validation Guide: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Feature Branch**: `008-diner-catalog-multi-tenant`  
**Date**: 26/10/2026  
**Status**: Completed  
**Spec Reference**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/008-diner-catalog-multi-tenant/spec.md)  
**Contract Reference**: [openapi.yaml](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/008-diner-catalog-multi-tenant/contracts/openapi.yaml)  
**Data Model Reference**: [data-model.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/008-diner-catalog-multi-tenant/data-model.md)

---

## 1. Prerequisites and Setup

1. **Docker Environment**: PostgreSQL 16 container running and accessible, or active testcontainers runtime.
2. **Database Migrations**: Flyway executed up to `V7__create_establishments_and_dish_catalog.sql`.
3. **Authentication**: Active JWT token with `COMENSAL` role obtained via `/api/v1/auth/login`.
4. **Backend**: Spring Boot 3.3.4 running on port 8080 (`./mvnw spring-boot:run` or tests).
5. **Frontend**: Vite development server running on port 5173 (`npm run dev` in `easyeat/frontend`).

---

## 2. End-to-End Validation Scenarios

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

### Scenario 6: Decommissioned Dish and Empty Category Hiding (FR-009, FR-015, SC-005, SC-006)
- **Initial State**: Category `"Bebidas"` has two dishes: one with `status = 'DECOMMISSIONED'` and one with `status = 'ACTIVE'`.
- **Expected Outcome**: Only the active dish is rendered; the decommissioned dish is completely absent.
- **Action**: Mark the remaining active dish as `DECOMMISSIONED`.
- **Expected Outcome**: The entire `"Bebidas"` category heading and its entry in the sticky navigation bar disappear from the catalog view.

### Scenario 7: In-Catalog Real-Time Search (FR-020, FR-021, FR-022, FR-024, SC-007)
- **Action**: In the catalog search bar, type `"jamon"` (without accent).
- **Expected Outcome**: Dishes whose title or recipe ingredients contain `"Jamón"` (with accent) appear; non-matching categories are hidden.
- **Action**: Clear search bar.
- **Expected Outcome**: Complete catalog with all categories immediately reappears without page reload.

### Scenario 8: Operating Hours & Read-Only Mode (FR-012, SC-010)
- **Initial State**: Current server time in restaurant's timezone is outside `[opening_time, closing_time)`.
- **Expected Outcome**:
  - Catalog renders in read-only mode with an informational banner displaying the next opening schedule (e.g., `"Abre hoy a las 11:00"`).
  - All dish cards are disabled for ordering; clicking opens the informational detail modal showing the outside-hours notice.

### Scenario 9: Mandatory Authentication & Zero Data Exposure (FR-027, SC-011)
- **Action**: Send `GET /api/v1/establishments/search?q=cafe` and `GET /api/v1/establishments/{id}/catalog` without `Authorization` header.
- **Expected Outcome**: Backend returns `HTTP 401 Unauthorized` with RFC 7807 ProblemDetail. Zero establishment, dish, or stock data is returned.
- **Action**: Direct unauthenticated browser navigation to `/descubrimiento` or `/catalogo`.
- **Expected Outcome**: Frontend `RoleRouteGuard` intercepts navigation and redirects client to `/login`.

---

## 3. Automated Verification Commands

```bash
# Backend unit, integration, and contract tests
cd easyeat/backend
./mvnw clean test -Dtest="*Establishment*,*Catalog*,*DishAvailability*"

# Frontend test suite
cd ../frontend
npm test -- src/test/catalog
```
