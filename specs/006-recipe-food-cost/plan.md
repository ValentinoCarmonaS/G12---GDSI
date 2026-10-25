# Technical Implementation Plan: Recipe Many-to-Many Model and Automated Food Cost Engine

**Branch**: `006-recipe-food-cost` | **Date**: 25/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-recipe-food-cost/spec.md`, backlog activity (ADM-5.1.2, CHF-2.2.2 Backend), clarification agreements from 2026-10-25, and schema established in `specs/005-chef-dish-management/`.

---

## Executive Summary

This feature delivers the backend domain capabilities for technical dish recipe persistence (`CHF-2.2.2 Backend`) and the automated raw material cost engine (`ADM-5.1.2`) for the EasyEat multi-tenant platform.

The design **reuses directly** the `dish_recipe_ingredients` table and the composite `DishRecipeIngredient` entity introduced by feature 005 (`specs/005-chef-dish-management/`), which already exists via Flyway migration `V6__chef_dish_management.sql`. No new relational table is introduced in this feature. Feature 006 adds the application-layer services and REST endpoints on top of the existing 3NF schema: an atomic full-replacement recipe persistence service (idempotent `PUT`), a pure domain Food Cost computation engine (`FoodCostEngine`) using direct BigDecimal multiplication without unit conversion, and a financial cost audit endpoint (`GET /cost-audit`) gated exclusively to dishes in `PENDING_PRICE` status and to `ADMIN` role.

The implementation follows the conventions established in iterations 001 through 005: **Java 21**, **Spring Boot 3.3.4**, **PostgreSQL 16** with **Flyway** (no new migration in this feature), exact arithmetic via `java.math.BigDecimal`, RFC 9457 `ProblemDetail` errors, and layered testing with **JUnit 5**, **Mockito**, **MockMvc**, and **Testcontainers**.

---

## Technical Context

**Language / Version**: Java 21 (LTS)  
**Primary Dependencies**:
- Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`)
- Flyway Core + Flyway PostgreSQL Extension 10.x
- PostgreSQL JDBC Driver 42.7.x
- Lombok

**Storage & Persistence**:
- PostgreSQL 16 with native `UUID`, `TIMESTAMPTZ`, and `NUMERIC(12, 4)`.
- **No new Flyway migration in this feature.** The `dish_recipe_ingredients` table is already created by `V6__chef_dish_management.sql` from feature 005.
- `dish_dietary_restrictions` table (also from V6) is read-only from this feature's perspective.

**Numeric Precision**:
- `java.math.BigDecimal` with `RoundingMode.HALF_UP`.
- Scale 4 for raw quantities and unit costs (`NUMERIC(12, 4)`).
- Scale 2 for line subtotals and total Food Cost (monetary output).
- Food Cost formula: `lineSubtotal = quantity × unitPurchaseCost`. No cross-unit conversion.

**Testing Strategy**:
- JUnit 5 and AssertJ for pure domain unit tests (`FoodCostEngineTest`).
- Spring Security Test (`MockMvc`, `@WithMockUser`) for RBAC and controller slice validation.
- Testcontainers running PostgreSQL 16 for database integration and transactional full-replace integrity tests (`DishCostAuditIntegrationTest`).

**Target Platform**: Linux containers / Docker (production); local JVM execution for development.  
**Project Type**: Web service / REST API (modular backend within monorepo).

**Performance Goals**:
- Recipe save/update transaction: p95 latency ≤ 150 ms.
- Cost audit query with label aggregation and itemized breakdown: p95 latency ≤ 100 ms.

**Domain & Security Constraints**:
- Multi-tenant isolation: dishes and recipe ingredients must share identical `tenant_id`.
- RBAC: `PUT /dishes/{id}/recipe` allowed for `CHEF` and `ADMIN`; `GET /dishes/{id}/cost-audit` exclusively restricted to `ADMIN` (HTTP 403 for non-admin).
- State-machine gate: recipe modification blocked with `409 Conflict` if `dish.status != 'DRAFT'`. Cost audit blocked with `409 Conflict` if `dish.status != 'PENDING_PRICE'`.
- Zero floating-point drift: all monetary computations use `BigDecimal` with `HALF_UP` to 2 decimal places.

---

## Constitution Check

*GATE: Pre-design and post-design compliance evaluation against `.specify/memory/constitution.md`.*

| Principle | Pre-Design Status | Post-Design Status | Evidence and Justification |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | ✅ PASS | ✅ PASS | Design derives strictly from FR-001 through FR-013 in `spec.md`. No unsolicited features added. All decisions reconciled with spec 005 schema in session 2026-10-25. |
| **II. Specification Before Implementation** | ✅ PASS | ✅ PASS | Completed `spec.md`, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md` prior to code implementation. All artifacts aligned with spec 005. |
| **III. Layered Verification Proportional to Risk** | ✅ PASS | ✅ PASS | Layered test strategy covering exact Food Cost math, zero cent drift, RBAC security filters, and Testcontainers database integration. |
| **IV. Simple, Cohesive, and Justified Design** | ✅ PASS | ✅ PASS | No new table introduced. Services integrated in `com.easyeat.dish` package alongside spec 005 code. Direct multiplication formula (no unit conversion layer). |
| **V. Bounded Scope** | ✅ PASS | ✅ PASS | Bounded to Sprint 2 backend deliverables (CHF-2.2.2 Backend, ADM-5.1.2). No image upload (CHF-2.2.1), PVP pricing (ADM-5.2.1), or catalog publication (ADM-5.2.2) included. |
| **VI. Controlled Technical Debt** | ✅ PASS | ✅ PASS | All architectural decisions and rejected alternatives recorded in `research.md`. D1: no duplicate table. D3: direct multiplication (no cross-unit conversion). |
| **VII. Traceability, Consistency, and Documentation** | ✅ PASS | ✅ PASS | Complete traceability matrix in `data-model.md` mapping FR-001 to FR-013 directly to schema, entities, and API endpoints. All documents aligned. |
| **VIII. Evidence Over Assumption and Human Escalation** | ✅ PASS | ✅ PASS | Two interactive clarification sessions (2026-10-25): initial clarification + spec 005 reconciliation. All decisions in `research.md`. |
| **IX. Engineering Over Convenience** | ✅ PASS | ✅ PASS | Exact decimal precision with `BigDecimal`, full-replacement transactional atomicity, strict RBAC and state-machine gate enforcement. |
| **X. Full-Stack End-to-End Integrity** | ✅ PASS | ✅ PASS | Satisfies complete backend scope for Sprint 2 activity. OpenAPI contracts in `contracts/openapi.yaml` prepared for frontend integration. |
| **XI. Continuous Upstream Synchronization Awareness** | ✅ PASS | ✅ PASS | Branch merged from `develop/sprint-2` (which includes spec 005). All prior migrations V1–V6 are present before this feature's code executes. |

**GATE RESULT: ALL PASS**

---

## Project Structure

### Documentation (`specs/006-recipe-food-cost/`)

```text
specs/006-recipe-food-cost/
├── plan.md              # Technical implementation plan (this file)
├── spec.md              # Feature specification
├── research.md          # Technical research and decision log
├── data-model.md        # Entities, schema, food cost formula, and traceability
├── quickstart.md        # Runnable verification guide and test commands
└── contracts/
    └── openapi.yaml     # OpenAPI 3.0 interface specification
```

### Source Code (`easyeat/backend/`)

Feature 006 integrates entirely within the existing `com.easyeat.dish` package established by spec 005. No new top-level package is created.

```text
easyeat/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/easyeat/
    │   │   └── dish/
    │   │       ├── domain/
    │   │       │   └── DishRecipeIngredient.java       # Reused from spec 005 (composite PK entity)
    │   │       ├── repository/
    │   │       │   └── DishRecipeIngredientRepository.java  # Reused or extended from spec 005
    │   │       ├── service/
    │   │       │   ├── FoodCostEngine.java             # NEW: pure stateless BigDecimal calculator
    │   │       │   └── FoodCostAuditService.java       # NEW: orchestrates query + engine + RBAC checks
    │   │       ├── controller/
    │   │       │   └── AdminDishCostAuditController.java   # NEW: GET /dishes/{id}/cost-audit (ADMIN only)
    │   │       └── dto/
    │   │           ├── RecipeUpdateRequest.java        # Reused from spec 005 (PUT /dishes/{id}/recipe)
    │   │           ├── RecipeIngredientRequest.java    # Reused from spec 005
    │   │           ├── FoodCostAuditResponse.java      # NEW: top-level audit response
    │   │           └── RecipeItemAuditResponse.java    # NEW: per-ingredient line item
    │   └── resources/
    │       └── db/migration/
    │           └── (no new migration; V6__chef_dish_management.sql from spec 005 covers all tables)
    └── test/
        └── java/com/easyeat/dish/
            ├── service/
            │   └── FoodCostEngineTest.java             # Unit tests: math precision, zero-price flag
            ├── controller/
            │   └── AdminDishCostAuditControllerTest.java  # MockMvc: RBAC, state gate, response shape
            └── integration/
                └── DishCostAuditIntegrationTest.java   # Testcontainers: full flow with real DB
```

**Classes removed from previous plan version**:
- ~~`RecipeItem.java`~~ → replaced by `DishRecipeIngredient.java` (spec 005)
- ~~`RecipeItemRepository.java`~~ → replaced by `DishRecipeIngredientRepository.java` (spec 005)
- ~~`UnitConversionService.java`~~ → eliminated (3NF model; formula is direct multiplication)
- ~~`IncompatibleUnitException.java`~~ → eliminated (no cross-unit conversion)
- ~~`ActiveDishModificationException.java`~~ → already defined in spec 005 as `DishLockedException`
- ~~`V6__create_recipe_items.sql`~~ → eliminated; spec 005 migration `V6__chef_dish_management.sql` is the source of truth

---

## API Surface

Defined in full in [`contracts/openapi.yaml`](./contracts/openapi.yaml). Summary:

| Method | Path | Role | State Gate | Description |
|---|---|---|---|---|
| `PUT` | `/api/v1/dishes/{dishId}/recipe` | `CHEF`, `ADMIN` | `DRAFT` only | Full-replacement recipe assignment |
| `GET` | `/api/v1/dishes/{dishId}/cost-audit` | `ADMIN` only | `PENDING_PRICE` only | Itemized Food Cost audit |

**`PUT /api/v1/dishes/{dishId}/recipe`** — Request body:
```json
{
  "ingredients": [
    { "ingredientId": "<uuid>", "quantity": 0.250 },
    { "ingredientId": "<uuid>", "quantity": 2 }
  ]
}
```
- `unitOfMeasure` is **not** a request field; it is resolved via 3NF JOIN from `ingredients.unit_of_measure`.

**`GET /api/v1/dishes/{dishId}/cost-audit`** — Response body (200 OK):
```json
{
  "dishId": "<uuid>",
  "dishName": "Milanesa a la Napolitana",
  "totalFoodCost": "4.73",
  "hasUnpricedIngredients": false,
  "warningNotes": [],
  "dietaryRestrictionCodes": ["GLUTEN_FREE", "LACTOSE_FREE"],
  "items": [
    {
      "ingredientId": "<uuid>",
      "ingredientName": "Peceto",
      "quantity": "0.2500",
      "unitOfMeasure": "KG",
      "unitPurchaseCost": "18.90",
      "lineSubtotal": "4.73"
    }
  ]
}
```

---

## Implementation Phases

### Phase 0 — Research & Schema Alignment *(completed)*

- [x] Read spec 005 schema (`V6__chef_dish_management.sql`, `DishRecipeIngredient`, `DishStatus`).
- [x] Confirm reuse of `dish_recipe_ingredients` (no duplicate table).
- [x] Confirm `PENDING_PRICE` gate for cost audit.
- [x] Confirm 3NF formula (direct multiplication, no unit conversion).
- [x] Confirm dietary tags from `dish_dietary_restrictions` (read-only, no derivation from ingredients).
- [x] Document decisions in `research.md`.

### Phase 1 — Specification & Interface Design *(completed)*

- [x] `spec.md` rewritten and aligned with spec 005.
- [x] `data-model.md` with ERD, JPA entity, formula, and traceability matrix.
- [x] `contracts/openapi.yaml` with `PUT /recipe` and `GET /cost-audit` operations.
- [x] `quickstart.md` with acceptance scenarios (DRAFT → 409, PENDING_PRICE → 200, CHEF → 403).
- [x] `plan.md` rewritten (this file).

### Phase 2 — Application Services *(pending)*

1. **`FoodCostEngine.java`**: Stateless service receiving a list of `(BigDecimal quantity, BigDecimal unitPurchaseCost)` pairs and returning exact BigDecimal totals. Handles zero-cost items by flagging `hasUnpricedIngredients = true`.
2. **`FoodCostAuditService.java`**: Transactional read service. Loads `dish` by `(dishId, tenantId)`, asserts `status == PENDING_PRICE`, loads `dish_recipe_ingredients` with JOIN to `ingredients`, calls `FoodCostEngine`, loads `dish_dietary_restrictions`, and assembles `FoodCostAuditResponse`.
3. **`AdminDishCostAuditController.java`**: REST controller. `@PreAuthorize("hasRole('ADMIN')")`. Maps `GET /api/v1/dishes/{dishId}/cost-audit` to `FoodCostAuditService`. Handles `ProblemDetail` for 409, 403, 404, 400.
4. **`FoodCostAuditResponse.java`** and **`RecipeItemAuditResponse.java`**: Immutable DTOs (record classes or Lombok `@Value`).

### Phase 3 — Tests *(pending)*

1. **`FoodCostEngineTest`** (unit): Exact math, zero-price flagging, multi-item precision with `BigDecimal` assertions.
2. **`AdminDishCostAuditControllerTest`** (MockMvc slice): RBAC (ADMIN pass, CHEF/anon 403), `DRAFT` → 409, `PENDING_PRICE` → 200, empty recipe → 400.
3. **`DishCostAuditIntegrationTest`** (Testcontainers): Full flow against PostgreSQL 16 — insert dish + ingredients + recipe items, transition to `PENDING_PRICE`, GET cost-audit, assert itemized response and math precision.

---

## Complexity Tracking

*No constitutional violations identified. Design reuses the existing schema, authentication filters, and exception handling from spec 005 without introducing unnecessary abstractions. The removal of `UnitConversionService` and the separate `recipe_items` table reduces overall complexity relative to the previous plan version.*
