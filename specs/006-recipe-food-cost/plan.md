# Technical Implementation Plan: Recipe Many-to-Many Model and Automated Food Cost Engine

**Branch**: `006-recipe-food-cost` | **Date**: 25/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-recipe-food-cost/spec.md`, backlog activity (ADM-5.1.2), clarification agreements from 2026-10-25, and architecture established in `specs/005-chef-dish-management/`.

---

## Executive Summary

This feature delivers the backend domain engine and administrative REST endpoint for the automated raw material cost calculation (`ADM-5.1.2`) for the EasyEat multi-tenant platform.

Following architectural reconciliation with feature 005 (`specs/005-chef-dish-management/`):
- The recipe persistence model (`dish_recipe_ingredients`), composite entity `DishRecipeIngredient`, composite identifier `DishRecipeIngredientId`, repository `DishRecipeIngredientRepository`, and recipe upsert endpoint `PUT /api/v1/dishes/{id}/recipe` (`CHF-2.2.2`) are established and owned by feature 005 via Flyway migration `V6__chef_dish_management.sql`.
- Feature 006 focuses strictly on financial cost computation and auditing: a pure domain calculation component (`FoodCostEngine`) applying direct fixed-point multiplication (`BigDecimal`), and a secure audit service and controller (`FoodCostAuditService`, `AdminDishCostAuditController`) exposing `GET /api/v1/dishes/{id}/cost-audit` restricted to `ADMIN` role and gated strictly to dishes in `PENDING_PRICE` status.

The implementation adheres to the project baseline: **Java 21**, **Spring Boot 3.3.4**, **PostgreSQL 16**, exact arithmetic via `java.math.BigDecimal` (`RoundingMode.HALF_UP`), RFC 9457 `ProblemDetail` errors, and layered testing with **JUnit 5**, **Mockito**, **MockMvc**, and **Testcontainers**.

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
- **No new Flyway migration in this feature.** Relational tables `dishes`, `dish_recipe_ingredients`, and `dish_dietary_restrictions` are provisioned by `V6__chef_dish_management.sql` (feature 005).

**Numeric Precision**:
- `java.math.BigDecimal` with `RoundingMode.HALF_UP`.
- Scale 4 for quantities and inventory unit costs (`NUMERIC(12, 4)`).
- Scale 2 for line subtotals and total Food Cost output.
- Direct formula: $\text{lineSubtotal} = \text{quantity} \times \text{unit\_purchase\_cost}$. No unit conversion required (3NF inventory alignment).

**Testing Strategy**:
- JUnit 5 and AssertJ for pure domain unit tests (`FoodCostEngineTest`).
- Spring Security Test (`MockMvc`, `@WithMockUser`) for RBAC and controller slice verification (`AdminDishCostAuditControllerTest`).
- Testcontainers running PostgreSQL 16 for database integration tests (`DishCostAuditIntegrationTest`).

**Target Platform**: Linux containers / Docker (production); local JVM execution for development.  
**Project Type**: Modular backend REST API (`easyeat/backend`).

**Performance Goals**:
- Cost audit query with itemized breakdown and dietary tag aggregation: p95 latency $\le 100$ ms.

**Domain & Security Constraints**:
- Multi-tenant isolation: dish and all associated ingredients must match the authenticated user's `tenant_id`.
- RBAC: `GET /api/v1/dishes/{id}/cost-audit` restricted strictly to `ADMIN` role (HTTP 403 Forbidden for `CHEF` and unauthorized users).
- State-machine gate: cost audit rejected with `409 Conflict` if `dish.status != 'PENDING_PRICE'` (drafts cannot be audited).
- Zero fractional-cent drift: monetary sums computed via fixed-point `BigDecimal` with `RoundingMode.HALF_UP`.

---

## Constitution Check

*GATE: Pre-design and post-design compliance evaluation against `.specify/memory/constitution.md`.*

| Principle | Pre-Design Status | Post-Design Status | Evidence and Justification |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | ✅ PASS | ✅ PASS | Design derives strictly from FR-001 through FR-013 in `spec.md`. Reconciled with spec 005 schema. |
| **II. Specification Before Implementation** | ✅ PASS | ✅ PASS | Specification, data model, contracts, and research completed prior to code execution. |
| **III. Layered Verification Proportional to Risk** | ✅ PASS | ✅ PASS | Multi-tier test strategy: domain unit tests for BigDecimal calculations, MockMvc slice tests for RBAC/state gate, and Testcontainers integration tests. |
| **IV. Simple, Cohesive, and Justified Design** | ✅ PASS | ✅ PASS | Eliminates duplicate tables and controllers. Reuses spec 005 entities and concentrates new logic in cohesive cost domain components under `com.easyeat.dish`. |
| **V. Bounded Scope** | ✅ PASS | ✅ PASS | Scope strictly limited to ADM-5.1.2 (Food Cost auditing). PVP retail pricing (ADM-5.2.1) and catalog publication (ADM-5.2.2) remain deferred. |
| **VI. Controlled Technical Debt** | ✅ PASS | ✅ PASS | Design trade-offs documented in `research.md`. No redundant schemas or dead abstraction layers. |
| **VII. Traceability, Consistency, and Documentation** | ✅ PASS | ✅ PASS | Full traceability across requirements, data model, and API contracts. Path parameters aligned to `{id}` matching spec 005 conventions. |
| **VIII. Evidence Over Assumption and Human Escalation** | ✅ PASS | ✅ PASS | Clarifications resolved through interactive review sessions (grill-me) and cross-spec comparative analysis. |
| **IX. Engineering Over Convenience** | ✅ PASS | ✅ PASS | Exact arithmetic, robust multi-tenant boundaries, and explicit state machine enforcement at the service layer. |
| **X. Full-Stack End-to-End Integrity** | ✅ PASS | ✅ PASS | Delivers the scheduled backend cost audit capability for Sprint 2 with OpenAPI contracts prepared for frontend consumption. |
| **XI. Continuous Upstream Synchronization Awareness** | ✅ PASS | ✅ PASS | Branch aligned with `develop/sprint-2`, incorporating all baseline migrations (V1 through V6). |

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

Feature 006 integrates into the existing `com.easyeat.dish` package without creating redundant packages:

```text
easyeat/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/easyeat/
    │   │   └── dish/
    │   │       ├── domain/
    │   │       │   ├── Dish.java                           # REUSED from spec 005
    │   │       │   ├── DishStatus.java                     # REUSED from spec 005 (DRAFT, PENDING_PRICE)
    │   │       │   ├── DishRecipeIngredient.java           # REUSED from spec 005
    │   │       │   └── DishRecipeIngredientId.java         # REUSED from spec 005 (Composite PK)
    │   │       ├── repository/
    │   │       │   ├── DishRepository.java                 # REUSED from spec 005
    │   │       │   └── DishRecipeIngredientRepository.java # REUSED from spec 005
    │   │       ├── service/
    │   │       │   ├── FoodCostEngine.java                 # NEW: Stateless BigDecimal cost calculator
    │   │       │   └── FoodCostAuditService.java           # NEW: Orchestrates retrieval, validation, and audit assembly
    │   │       ├── controller/
    │   │       │   └── AdminDishCostAuditController.java   # NEW: GET /dishes/{id}/cost-audit (ADMIN only)
    │   │       ├── dto/
    │   │       │   ├── RecipeUpdateRequest.java            # REUSED from spec 005
    │   │       │   ├── FoodCostAuditResponse.java          # NEW: Complete cost audit response
    │   │       │   └── RecipeItemAuditResponse.java        # NEW: Breakdown line item response
    │   │       └── exception/
    │   │           ├── DishLockedException.java            # REUSED from spec 005 (maps to 409)
    │   │           └── EmptyRecipeException.java           # NEW: Maps to 400 when recipe has no ingredients
    │   └── resources/
    │       └── db/migration/
    │           └── (V6__chef_dish_management.sql from spec 005 already provisions required tables)
    └── test/
        └── java/com/easyeat/dish/
            ├── service/
            │   └── FoodCostEngineTest.java                 # Unit tests: arithmetic precision, zero cost handling
            ├── controller/
            │   └── AdminDishCostAuditControllerTest.java   # MockMvc slice tests: RBAC, 409 gate, DTO validation
            └── integration/
                └── DishCostAuditIntegrationTest.java       # Testcontainers: full database flow & price change reflection
```

---

## API Surface

Detailed specification in [`contracts/openapi.yaml`](./contracts/openapi.yaml). Path parameter naming is standardized to `{id}` matching spec 005 conventions:

| Method | Path | Owner Spec | Role | State Gate | Description |
|---|---|---|---|---|---|
| `PUT` | `/api/v1/dishes/{id}/recipe` | Spec 005 (`CHF-2.2.2`) | `CHEF`, `ADMIN` | `DRAFT` only | Replaced atomically via `DishController` |
| `GET` | `/api/v1/dishes/{id}/cost-audit` | Spec 006 (`ADM-5.1.2`) | `ADMIN` only | `PENDING_PRICE` only | Itemized Food Cost and dietary audit breakdown |

**`GET /api/v1/dishes/{id}/cost-audit`** — Response body (`200 OK`):
```json
{
  "dishId": "a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01",
  "dishName": "Milanesa a la Napolitana",
  "totalFoodCost": 4.73,
  "hasUnpricedIngredients": false,
  "warningNotes": [],
  "requiredDietaryRestrictions": ["GLUTEN_FREE", "LACTOSE_FREE"],
  "items": [
    {
      "ingredientId": "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02",
      "ingredientName": "Peceto",
      "recipeQuantity": 0.2500,
      "unitOfMeasure": "KG",
      "unitPurchaseCost": 18.90,
      "lineSubtotal": 4.73,
      "unpricedWarning": false
    }
  ]
}
```

---

## Implementation Phases

### Phase 0 — Research & Alignment *(Completed)*
- [x] Inspect spec 005 schema (`V6__chef_dish_management.sql`), entities, and contracts.
- [x] Confirm reuse of `dish_recipe_ingredients`, `DishRecipeIngredient`, and `DishRecipeIngredientId`.
- [x] Resolve path parameter standard `{id}` across contracts.
- [x] Record architectural decisions in `research.md`.

### Phase 1 — Specification & Interface Design *(Completed)*
- [x] Specification `spec.md` updated and verified.
- [x] Data model `data-model.md` aligned with 3NF direct calculation formula.
- [x] OpenAPI contract `contracts/openapi.yaml` harmonized with `{id}` parameter.
- [x] Runnable verification scenarios in `quickstart.md`.
- [x] Technical implementation plan `plan.md` reconciled (this document).

### Phase 2 — Application Services *(Pending)*
1. **`FoodCostEngine.java`**: Pure component computing line subtotals and total cost using `BigDecimal` (`HALF_UP`, scale 2). Handles unpriced ingredients (`unitPurchaseCost == 0` or null) by raising `hasUnpricedIngredients = true` and logging warnings.
2. **`EmptyRecipeException.java`**: Custom domain exception mapped to HTTP 400 in `GlobalExceptionHandler`.
3. **DTOs (`FoodCostAuditResponse`, `RecipeItemAuditResponse`)**: Immutable record or Lombok representations conforming to OpenAPI schema.
4. **`FoodCostAuditService.java`**:
   - Enforce tenant isolation (`dish.tenantId == user.tenantId`).
   - Validate state machine gate: throw `DishLockedException` (HTTP 409) if `dish.status != PENDING_PRICE`.
   - Query recipe items via `DishRecipeIngredientRepository` joined with `Ingredient`.
   - Validate non-empty recipe (throw `EmptyRecipeException` if empty).
   - Execute calculation via `FoodCostEngine`.
   - Collect declared dietary restrictions from `dish_dietary_restrictions`.
   - Return structured audit response.
5. **`AdminDishCostAuditController.java`**:
   - Expose `GET /api/v1/dishes/{id}/cost-audit`.
   - Enforce `@PreAuthorize("hasRole('ADMIN')")`.

### Phase 3 — Tests *(Pending)*
1. **`FoodCostEngineTest`**: Unit testing decimal rounding, zero fractional-cent drift, and unpriced ingredient flags.
2. **`AdminDishCostAuditControllerTest`**: MockMvc controller slice testing RBAC authorization (ADMIN allow, CHEF 403, unauthenticated 401), state gate (DRAFT 409, PENDING_PRICE 200), and empty recipe (400).
3. **`DishCostAuditIntegrationTest`**: End-to-end Testcontainers integration validating dynamic recalculation upon inventory price updates and tenant boundary enforcement.

---

## Complexity Tracking

*No constitutional violations identified. Reusing spec 005 persistence models and controller infrastructure avoids redundant tables, duplicated endpoints, and unnecessary translation layers.*
