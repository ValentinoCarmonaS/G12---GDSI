# Technical Research & Decision Log: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature**: `006-recipe-food-cost`  
**Date**: 25/10/2026  
**Status**: Approved

This document records the architectural investigations, trade-off evaluations, and design decisions informing the technical planning for the recipe persistence model and the automated raw material cost engine.

---

## Decision 1: Relational Storage Model for Recipe Composition (N:M)

### Context
Dishes and ingredients share a Many-to-Many (N:M) relationship: a dish requires multiple ingredients in specific portion quantities, and an ingredient can be utilized across many dishes. We must store the portion quantity and recipe measurement unit while ensuring strict referential integrity and query efficiency for cost calculations.

### Alternatives Evaluated
1. **Normalized Relational Join Table (`recipe_items`)**: A dedicated relational entity with foreign keys to `dishes(id)` and `ingredients(id)`, carrying `quantity NUMERIC(12, 4)` and `unit_of_measure VARCHAR(20)`. Enforced by a unique constraint `UNIQUE (dish_id, ingredient_id)`.
2. **Denormalized JSONB Column on `dishes`**: Storing an array of `{ingredient_id, quantity, unit}` directly in a JSONB attribute within `dishes`.
3. **Dedicated Separate `recipes` Header + `recipe_items` Detail**: An intermediate `recipes` entity pointing to `dish_id`, allowing multi-version headers.

### Decision
**Option 1: Normalized Relational Join Table (`recipe_items`)**.

### Rationale
- Guarantees database-level foreign key referential integrity (`ON DELETE CASCADE` for dish, `ON DELETE RESTRICT` for ingredients), preventing orphaned or dangling ingredient references.
- Enables high-performance relational joins with the `ingredients` table during cost calculation queries.
- Adheres to clean architecture and aligns with existing Flyway migration patterns (`V4__create_ingredients_and_stock_audit.sql` and `V5__extend_users_profile_and_dietary.sql`).
- Avoids the overhead of an extra `recipes` header entity since each dish currently maintains a single canonical active recipe.

---

## Decision 2: Recipe Persistence Strategy — Idempotent Full-Replacement (PUT)

### Context
When a Chef modifies a recipe, multiple items may change simultaneously (e.g., modifying portions, removing outdated ingredients, adding new seasonings). The operation must be atomic: partial updates are strictly prohibited.

### Alternatives Evaluated
1. **Canonical Full-Replacement (`PUT /api/v1/dishes/{dishId}/recipe`)**: The payload contains the complete desired state of ingredients for the dish. The backend synchronizes the collection in a single transaction (deleting removed items, updating modified quantities, inserting new items).
2. **Granular REST Endpoints (`POST /items`, `DELETE /items/{id}`, `PATCH /items/{id}`)**: Item-by-item modifications.
3. **Compound Delta Payload (`PATCH /recipe`)**: Payload specifying lists of `added`, `removed`, and `modified` items.

### Decision
**Option 1: Canonical Full-Replacement (`PUT /api/v1/dishes/{dishId}/recipe`)**.

### Rationale
- Completely eliminates synchronization drift between the UI and database.
- Guarantees 100% transactional atomicity: if any single ingredient fails validation (e.g., negative quantity, discontinued status, cross-tenant leak), the entire transaction rolls back cleanly.
- Enforces state-machine invariant: if `dish.isActive == true`, the entire recipe update is rejected with `409 Conflict`, requiring explicit deactivation prior to editing.

---

## Decision 3: Precision Arithmetic & Metric Unit Conversion Engine

### Context
Food cost calculations involve multiplying fractional quantities (e.g., 2.5 grams) by unit costs of bulk purchase units (e.g., \$12.50 per kilogram). Binary floating-point arithmetic introduces rounding drift and non-deterministic cent loss, which violates financial accounting invariants.

### Alternatives Evaluated
1. **`java.math.BigDecimal` with Strict Dimensional Typing**: Pure domain engine (`FoodCostEngine` / `UnitConversionService`) operating with fixed precision (`scale = 4` for intermediate unit costs, `scale = 2` for currency totals with `RoundingMode.HALF_UP`).
2. **Double Precision (`double`)**: Standard 64-bit IEEE 754 floating point.
3. **Integer Cents Representation**: Multiplying all figures by 100 or 10,000 to store integer units.

### Decision
**Option 1: `java.math.BigDecimal` with Strict Dimensional Typing**.

### Rationale
- Existing entities (`Ingredient`, `Dish`) already standardize on `BigDecimal` and `NUMERIC(12, 4)` / `NUMERIC(10, 2)` across Spring Boot and PostgreSQL.
- Eliminates floating-point inaccuracies when dividing by conversion factors (e.g., $1\text{ kg} = 1000\text{ g}$).
- Dimensional rules strictly isolated:
  - Mass (`G`, `KG`): 1 KG = 1000 G ($factor = 0.001$).
  - Volume (`ML`, `L`): 1 L = 1000 ML ($factor = 0.001$).
  - Discrete (`U`): 1 U = 1 U ($factor = 1.0$).
  - Incompatible cross-dimensional combinations throw `IncompatibleUnitException` (HTTP 400).

---

## Decision 4: Role-Based Access Control and Financial Confidentiality

### Context
In restaurant operations, inventory purchase costs from suppliers are commercially sensitive financial data. Chefs need to compose technical recipes but should not have unauthorized visibility into wholesale margins, supplier costs, or overall profitability.

### Alternatives Evaluated
1. **Strict Separation of Concerns**: `CHEF` and `ADMIN` can manage technical recipes (`PUT /recipe`), but `GET /cost-audit` is exclusively restricted to `ADMIN`. Non-admin requests receive HTTP `403 Forbidden`.
2. **Open Visibility**: Both `CHEF` and `ADMIN` can query the cost audit endpoint.
3. **Masked Breakdown for Chef**: `CHEF` sees the total calculated Food Cost, but itemized unit purchase prices are redacted.

### Decision
**Option 1: Strict Separation of Concerns (Exclusive `ADMIN` for Cost Audit)**.

### Rationale
- Confirmed during the clarification session (2026-10-25).
- Follows the principle of least privilege and aligns with EasyEat's RBAC model.
- Prevents leakage of supplier negotiation terms and commercial margins to operational kitchen staff.

---

## Decision 5: Automated Dietary and Allergen Label Aggregation

### Context
User Story 2 (`ADM-5.1.2`) mandates that when the Financial Administrator evaluates a pending dish recipe, they can inspect both the raw material cost and the required dietary/allergen labels to assess business and regulatory impact.

### Alternatives Evaluated
1. **Dynamic Set Union from Inventory**: The cost audit service queries the ingredients in the recipe and aggregates the distinct union of all dietary restrictions and allergen tags configured on those ingredients.
2. **Static Dish Tags Only**: Reading only the existing `dish_dietary_restrictions` table on `Dish`.
3. **Manual Verification Flag**: Requiring the Chef to manually check off tags during recipe creation.

### Decision
**Option 1: Dynamic Set Union from Inventory**.

### Rationale
- Guarantees truth in advertising and consumer safety: if any ingredient contains an allergen (e.g., Gluten or Peanuts), the audit response highlights it automatically.
- Aligns with the clarification agreement and allows the Administrator to contrast the dish's declared tags against the recipe's mandatory tags before approving publication.

