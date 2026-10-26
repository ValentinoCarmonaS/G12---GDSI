# Technical Research & Decision Log: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature**: `006-recipe-food-cost`  
**Date**: 25/10/2026  
**Status**: Approved (Reconciled with Spec 005)

This document records the architectural investigations, trade-off evaluations, and design decisions informing the technical planning for the recipe persistence model and the automated raw material cost engine, aligned with the implementation baseline established in feature 005 (`005-chef-dish-management`).

---

## Decision 1: Direct Reuse of Spec 005 Normalized Recipe Table (`dish_recipe_ingredients`)

### Context
Feature 005 (`005-chef-dish-management`) specifies the dish technical file wizard, which includes step 2 for technical recipe ingredient composition (`CHF-2.2.2`). We investigated whether feature 006 should introduce a distinct join table (`recipe_items`) or build directly on `dish_recipe_ingredients`.

### Alternatives Evaluated
1. **Reutilizar directamente `dish_recipe_ingredients` (Spec 005 3NF Baseline)**: Build the `FoodCostEngine` and `FoodCostAuditService` directly over the composite entity `DishRecipeIngredient` and table `dish_recipe_ingredients` defined in Flyway migration `V6__chef_dish_management.sql`.
2. **Crear una tabla duplicada `recipe_items`**: Create a parallel table with an independent surrogate UUID key and a redundant `unit_of_measure` column.

### Decision
**Option 1: Reutilizar directamente `dish_recipe_ingredients`**.

### Rationale
- Completely eliminates schema duplication and synchronization drift between the kitchen file management (005) and the administrative cost audit (006).
- Follows third normal form (3NF): the unit of measure is functionally dependent on `ingredient_id` in the `ingredients` master catalog, avoiding data redundancy and multi-unit synchronization bugs.
- Confirmed during the grill-me alignment session (2026-10-25).

---

## Decision 2: State-Machine Gate for Cost Auditing (`PENDING_PRICE` Only)

### Context
User Story 2 (`ADM-5.1.2`) states: *"Dado que el admin abre un plato pendiente, Cuando evalúa la receta, Entonces puede visualizar los insumos y etiquetas requeridas"*. We evaluated whether the cost audit endpoint should accept dishes in any lifecycle state or only those formally submitted for review.

### Alternatives Evaluated
1. **Exclusively in `PENDING_PRICE`**: The endpoint `GET /api/v1/dishes/{id}/cost-audit` validates `dish.status == 'PENDING_PRICE'`. If the dish is still in `DRAFT`, it responds with HTTP `409 Conflict`.
2. **Permissive Audit in Any State (`DRAFT`, `PENDING_PRICE`, `ACTIVE`)**: Permitting cost audits on drafts before submission.
3. **Only in `PENDING_PRICE` and `ACTIVE`**: Permitting audits once submitted and post-publication, but blocking drafts.

### Decision
**Option 1: Exclusivamente en estado `PENDING_PRICE`**.

### Rationale
- Respects the domain lifecycle defined in Spec 005: while a dish is in `DRAFT`, kitchen staff may be actively adding or removing ingredients, making financial cost analysis premature and unstable.
- The Chef explicitly marks completion and submits for review (`POST /submit-review`), guaranteeing that the recipe is finalized and ready for administrative audit.
- Confirmed during the grill-me session (2026-10-25).

---

## Decision 3: Precision Arithmetic & 3NF Formula

### Context
Food cost calculation must be deterministic, preventing fractional-cent drift in accounting while supporting continuous units (grams, milliliters) and discrete units (u).

### Alternatives Evaluated
1. **Direct Multiplication with `BigDecimal`**: Since quantities in `dish_recipe_ingredients` use the ingredient's native inventory unit of measure, the formula simplifies directly to:
   $$\text{Line Subtotal} = \text{quantity} \times \text{unit\_purchase\_cost}$$
   Each line subtotal is rounded to 2 decimal places using `RoundingMode.HALF_UP`, and the total Food Cost is the sum of rounded subtotals.
2. **Dynamic Cross-Unit Conversion**: Permitting arbitrary unit choices per line item.

### Decision
**Option 1: Direct Multiplication with `BigDecimal` (3NF Integration)**.

### Rationale
- Eliminates unnecessary conversion overhead and avoids dimensional mismatch bugs.
- Guarantees exact monetary equality: $\text{Total Food Cost} = \sum \text{Line Subtotal}$ with zero penny drift.
- Unpriced items (cost 0.00 or null) are calculated as 0.00 with `has_unpriced_ingredients = true` and an advisory note, allowing partial cost inspection without failure.

---

## Decision 4: Exposure of Dietary and Allergen Labels

### Context
The Administrator must inspect the declared dietary and allergen tags alongside the food cost breakdown during recipe review.

### Alternatives Evaluated
1. **Direct Reading of `dish_dietary_restrictions`**: Returning the list of `restrictionCodes` associated with the dish.
2. **Dynamic Allergen Inference from Ingredients**: Deriving allergens dynamically by parsing ingredient taxonomy.

### Decision
**Option 1: Direct Reading of `dish_dietary_restrictions`**.

### Rationale
- Feature 005 already establishes the step for allergen selection (`CHF-2.2.3`), persisting validated codes into `dish_dietary_restrictions`.
- Provides maximum simplicity and reliability, directly fulfilling the acceptance criterion: *"puede visualizar los insumos y etiquetas requeridas"*.
- Confirmed during the grill-me session (2026-10-25).
