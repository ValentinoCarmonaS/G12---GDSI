# Technical Implementation Plan: Recipe Many-to-Many Model and Automated Food Cost Engine

**Branch**: `006-recipe-food-cost` | **Date**: 25/10/2026 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-recipe-food-cost/spec.md`, backlog activity (ADM-5.1.2, CHF-2.2.2 Backend), and clarification agreements from 2026-10-25.

---

## Executive Summary

This feature delivers the backend domain capabilities and relational persistence for technical dish recipes (`CHF-2.2.2 Backend`) and the automated raw material cost engine (`ADM-5.1.2`) for the EasyEat multi-tenant platform.

The technical design introduces a Many-to-Many relational join table (`recipe_items`) linking dishes and master ingredients, an atomic full-replacement persistence service (idempotent `PUT`), a pure domain metric unit conversion and food cost computation engine (`FoodCostEngine`), and a financial cost audit endpoint exposing itemized raw material breakdowns, exact monetary totals, and aggregated required dietary/allergen tags exclusively to the restaurant Administrator (`ADMIN`).

The implementation follows the conventions established in iterations 001 through 004: **Java 21**, **Spring Boot 3.3.4**, **PostgreSQL 16** with **Flyway** migrations (`V6__create_recipe_items.sql`), exact arithmetic via `java.math.BigDecimal`, RFC 9457 `ProblemDetail` errors, and layered testing with **JUnit 5**, **Mockito**, **MockMvc**, and **Testcontainers**.

---

## Technical Context

**Language / Version**: Java 21 (LTS)  
**Primary Dependencies**:
- Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`)
- Flyway Core + Flyway PostgreSQL Extension 10.x
- PostgreSQL JDBC Driver 42.7.x
- Lombok

**Storage & Persistence**:
- PostgreSQL 16 with native `UUID` (`gen_random_uuid()`), `TIMESTAMPTZ`, and `NUMERIC(12, 4)`.
- Relational join table `recipe_items` with unique constraint `UNIQUE (dish_id, ingredient_id)` and foreign key cascades.
- Migration managed via Flyway (`V6__create_recipe_items.sql`).

**Testing Strategy**:
- JUnit 5 and AssertJ for pure domain unit tests (`FoodCostEngineTest`, `UnitConversionServiceTest`).
- Spring Security Test (`MockMvc`, `@WithMockUser`) for role access control and controller slice validation.
- Testcontainers running PostgreSQL 16 for database integration, Flyway migration verification, and transactional full-replace integrity tests.

**Target Platform**: Linux containers / Docker (production); local JVM execution for development.  
**Project Type**: Web service / REST API (modular backend within monorepo).

**Performance Goals**:
- Recipe save/update transaction: p95 latency ≤ 150ms.
- Cost audit query with metric unit conversion and label aggregation: p95 latency ≤ 100ms.

**Domain & Security Constraints**:
- Multi-tenant isolation: dishes and recipe ingredients must share identical `tenant_id`.
- Role-Based Access Control: `PUT /recipe` allowed for `CHEF` and `ADMIN`; `GET /cost-audit` exclusively restricted to `ADMIN` (HTTP 403 for non-admin).
- State-machine locking: in-place recipe modification rejected with `409 Conflict` if `dish.isActive == true`.
- Zero floating-point drift: monetary totals computed with fixed-point `BigDecimal` and `HALF_UP` rounding to 2 decimal places.

---

## Constitution Check

*GATE: Pre-design and post-design compliance evaluation against `.specify/memory/constitution.md`.*

| Principle | Pre-Design Status | Post-Design Status | Evidence and Justification |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | ✅ PASS | ✅ PASS | Design derives strictly from FR-001 through FR-012 in `spec.md`. No unsolicited features added. |
| **II. Specification Before Implementation** | ✅ PASS | ✅ PASS | Completed `spec.md`, clarifications, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md` prior to code implementation. |
| **III. Layered Verification Proportional to Risk** | ✅ PASS | ✅ PASS | Layered test strategy covering unit conversion math, zero cent drift, RBAC security filters, and Testcontainers database integration. |
| **IV. Simple, Cohesive, and Justified Design** | ✅ PASS | ✅ PASS | Normalized `recipe_items` table and cohesive `com.easyeat.recipe` package integrating with existing `com.easyeat.catalog` and `com.easyeat.inventory`. |
| **V. Bounded Scope** | ✅ PASS | ✅ PASS | Bounded to Sprint 2 backend deliverables. No image upload (CHF-2.2.1), PVP pricing (ADM-5.2.1), or catalog publication (ADM-5.2.2) included. |
| **VI. Controlled Technical Debt** | ✅ PASS | ✅ PASS | All architectural decisions and rejected alternatives recorded in `research.md`. |
| **VII. Traceability, Consistency, and Documentation** | ✅ PASS | ✅ PASS | Complete traceability matrix in `data-model.md` mapping requirements directly to SQL schemas and API endpoints. |
| **VIII. Evidence Over Assumption and Human Escalation** | ✅ PASS | ✅ PASS | Conducted interactive clarification session (2026-10-25) resolving RBAC, unpriced items, UOM compatibility, and state locking. |
| **IX. Engineering Over Convenience** | ✅ PASS | ✅ PASS | Exact decimal precision with `BigDecimal`, full-replacement transactional atomicity, and strict dimensional type checking. |
| **X. Full-Stack End-to-End Integrity** | ✅ PASS | ✅ PASS | Satisfies backend scope for the scheduled Sprint 2 activity, preparing OpenAPI contracts for frontend integration. |
| **XI. Continuous Upstream Synchronization Awareness** | ✅ PASS | ✅ PASS | Branch branched directly from updated `develop/sprint-2`, incorporating all previous sprint migrations (V1 through V5). |

**GATE RESULT: ALL PASS**

---

## Project Structure

### Documentation (`specs/006-recipe-food-cost/`)

```text
specs/006-recipe-food-cost/
├── plan.md              # Technical implementation plan
├── research.md          # Technical research and decision log (Phase 0)
├── data-model.md        # Entities, SQL schema, conversion rules, and traceability (Phase 1)
├── quickstart.md        # Runnable verification guide and test commands (Phase 1)
├── contracts/
│   └── openapi.yaml     # OpenAPI 3.0 interface specification (Phase 1)
└── checklists/
    └── requirements.md  # Requirements quality checklist
```

### Source Code (`easyeat/backend/`)

```text
easyeat/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/easyeat/
    │   │   ├── recipe/
    │   │   │   ├── domain/
    │   │   │   │   └── RecipeItem.java
    │   │   │   ├── repository/
    │   │   │   │   └── RecipeItemRepository.java
    │   │   │   ├── service/
    │   │   │   │   ├── RecipeService.java
    │   │   │   │   ├── FoodCostEngine.java
    │   │   │   │   ├── FoodCostAuditService.java
    │   │   │   │   └── UnitConversionService.java
    │   │   │   ├── controller/
    │   │   │   │   ├── DishRecipeController.java
    │   │   │   │   └── AdminDishCostAuditController.java
    │   │   │   ├── dto/
    │   │   │   │   ├── SetRecipeRequest.java
    │   │   │   │   ├── RecipeItemRequest.java
    │   │   │   │   ├── RecipeDetailResponse.java
    │   │   │   │   ├── RecipeItemDetailResponse.java
    │   │   │   │   ├── FoodCostAuditResponse.java
    │   │   │   │   └── RecipeItemAuditResponse.java
    │   │   │   └── exception/
    │   │   │       ├── ActiveDishModificationException.java
    │   │   │       ├── IncompatibleUnitException.java
    │   │   │       ├── DuplicateRecipeIngredientException.java
    │   │   │       └── EmptyRecipeException.java
    │   │   └── common/exception/GlobalExceptionHandler.java
    │   └── resources/
    │       └── db/migration/
    │           └── V6__create_recipe_items.sql
    └── test/
        └── java/com/easyeat/recipe/
            ├── service/
            │   ├── FoodCostEngineTest.java
            │   ├── UnitConversionServiceTest.java
            │   └── RecipeServiceTest.java
            ├── controller/
            │   ├── DishRecipeControllerTest.java
            │   └── AdminDishCostAuditControllerTest.java
            └── integration/
                └── RecipePersistenceIntegrationTest.java
```

---

## Complexity Tracking

*No constitutional violations identified. Design reuses existing database, authentication filters, and exception handling without introducing unnecessary abstractions.*
