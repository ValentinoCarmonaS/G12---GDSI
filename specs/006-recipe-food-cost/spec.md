# Feature Specification: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature Branch**: `006-recipe-food-cost`

**Created**: 25/10/2026

**Status**: Ready for Planning (Aligned with Spec 005)

**Input**: User description: "Modelo de Recetas Many-to-Many y Motor de Food Cost del proyecto EasyEat. Sprint 2: Esquema de persistencia transaccional N:M para recetas, cálculo dinámico automatizado de costo de materia prima por porción (ADM-5.1.2) y endpoints de auditoría de costos en backend."

## Clarifications

### Session 2026-10-25 (Initial Clarification)

- Q: ¿Qué roles tienen autorización para consultar la auditoría de Food Cost y los costos de los insumos? (FR-010) → A: Exclusivo para Administrador (ADMIN). El Chef solo define ingredientes y cantidades en la receta técnica, sin acceso a los precios de compra de inventario ni a la auditoría de Food Cost financiero.
- Q: ¿Cómo debe responder el sistema si un ingrediente de la receta tiene costo de adquisición 0.00 o no cargado en inventario? (Edge Case) → A: Auditoría permisiva con advertencia. Calcula el total con costo 0.00 para ese insumo e incluye un flag explícito ("Unpriced ingredient"), permitiendo visualizar el costo parcial del plato.
- Q: ¿Cómo debe comportarse el sistema si el Chef intenta modificar la receta de un plato que ya se encuentra fuera de borrador? (Edge Case / Invariante) → A: Bloqueo de modificación en platos no borradores. La receta solo se edita en estado DRAFT; una vez enviado a revisión (PENDING_PRICE) o publicado, queda bloqueada in-place (HTTP 409 Conflict).

### Session 2026-10-25 (Spec 005 Reconciliation & Alignment)

- Q: ¿Cómo debe integrarse la persistencia de recetas en la spec 006 respecto a lo definido en la spec 005? → A: Reutilizar directamente la tabla `dish_recipe_ingredients` y la entidad compuesta `DishRecipeIngredient` de la spec 005 en 3NF. El motor de Food Cost (ADM-5.1.2) opera sobre este esquema existente, eliminando duplicación de tablas.
- Q: ¿En qué estados del plato puede el Administrador consultar la auditoría de costos (Food Cost)? (ADM-5.1.2) → A: Exclusivamente en estado `PENDING_PRICE`. La auditoría financiera solo se habilita cuando el Chef envía formalmente el plato a revisión del Administrador. Si el plato está en `DRAFT`, se rechaza con HTTP 409 Conflict.
- Q: ¿Cómo debe manejarse la unidad de medida en la receta técnica para el cálculo del Food Cost? → A: Seguir el modelo 3NF de la 005: la cantidad en la receta se expresa en la unidad base de inventario del ingrediente (`unit_of_measure`), resolviendo el costo de forma directa (`quantity × unit_purchase_cost`) con precisión decimal exacta y sin desvíos.
- Q: ¿Cómo debe exponer y auditar las etiquetas requeridas el endpoint de auditoría de costos? (ADM-5.1.2) → A: Solo lectura simple de los códigos de restricción del plato (`restrictionCodes` asociados en `dish_dietary_restrictions`), retornados en el payload de auditoría junto a la lista de insumos.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Technical Recipe Composition & Persistence (Priority: P1)

**CHF-2.2.2 Backend**: As a Restaurant Chef, I associate a structured list of raw ingredients with specific quantities to a dish so that the technical recipe is formally established in the system using the normalized `dish_recipe_ingredients` schema.

The Chef specifies the ingredients that compose a dish within their establishment. For each item in the recipe, the Chef selects an existing active ingredient from the establishment's inventory and defines the exact per-portion quantity expressed in the ingredient's inventory unit of measure. The system validates that all selected ingredients belong to the chef's tenant, are in active status, and carry positive non-zero quantities ($> 0$). The recipe is saved atomically via full replacement: either all items persist successfully, or no change is applied. Recipe editing is strictly permitted while the dish remains in `DRAFT` status; once transitioned to `PENDING_PRICE` or published, recipe modifications are blocked.

**Why this priority**: Recipe definition is the operational foundation linking culinary files to the inventory catalog. Without a persisted recipe, automated cost calculations cannot take place.

**Independent Test**: Can be tested by creating a draft dish, saving multiple inventory ingredients with portion quantities, verifying persistence in `dish_recipe_ingredients`, and asserting that invalid quantities ($0$, negative), duplicate items, or cross-tenant ingredients are rejected.

**Acceptance Scenarios**:

1. **Given** an authenticated Chef in an establishment with active inventory ingredients, **When** they submit a valid recipe for a draft dish with multiple ingredients and positive quantities ($> 0$), **Then** the system persists the Many-to-Many association atomically in `dish_recipe_ingredients` and links the complete recipe with the dish.
2. **Given** a Chef submitting a recipe, **When** the same ingredient is included more than once in the submission payload, **Then** the system rejects the operation with a validation error indicating that duplicate ingredients must be consolidated into a single line item.
3. **Given** a Chef submitting a recipe, **When** any ingredient has a quantity less than or equal to zero ($quantity \le 0$) or an invalid numeric value, **Then** the system rejects the submission with a validation error (HTTP 400 Bad Request).
4. **Given** a dish in `DRAFT` status with an existing recipe, **When** the Chef updates quantities of existing items, adds a new ingredient, or removes an existing ingredient, **Then** the system updates the entire recipe atomically and preserves referential integrity.
5. **Given** a Chef attempting to save a recipe, **When** any referenced ingredient has "Discontinued" status or belongs to a different establishment/tenant, **Then** the system rejects the transaction with an authorization/validation error and leaves the dish unchanged.
6. **Given** a Chef attempting to submit a recipe with an empty list of ingredients, **When** the save request is sent, **Then** the system rejects the operation, requiring at least one valid ingredient to form a recipe.
7. **Given** a dish that has already transitioned to `PENDING_PRICE` (submitted for review) or is published, **When** a user attempts to modify its recipe in-place, **Then** the system blocks the update with a state conflict error (HTTP 409 Conflict).

---

### User Story 2 — Recipe Audit, Ingredients, and Base Food Cost (ADM-5.1.2) (Priority: P1)

**ADM-5.1.2**: As a Financial Administrator, I open a pending dish submitted for review, read the ingredients that the Chef assigned, and analyze the calculated raw material cost (Food Cost) and declared dietary labels, so that I can evaluate profitability and operational compliance before assigning prices or approving publication.

The Financial Administrator accesses the cost audit endpoint for a dish that is currently in `PENDING_PRICE` status. The system dynamically computes the Food Cost by evaluating each ingredient in `dish_recipe_ingredients` against its current unit acquisition cost stored in `ingredients`. The calculation applies exact fixed-point arithmetic (`BigDecimal`, `RoundingMode.HALF_UP` to 2 decimal places) without floating-point precision loss. The Administrator receives an itemized breakdown detailing each ingredient, required portion quantity, inventory unit of measure, unit acquisition cost, line subtotal, warning flags for unpriced items, the aggregated total Food Cost per portion, and the declared dietary restrictions / allergen tags associated with the dish.

**Why this priority**: Precise cost auditing guarantees business profitability and compliance. Providing dynamic, automated Food Cost calculations eliminates manual spreadsheet errors and equips administrators with the financial truth needed before setting retail prices (PVP) or approving menu publication.

**Independent Test**: Can be tested independently by opening a dish in `PENDING_PRICE` status with an assigned recipe, requesting the cost audit endpoint as Administrator, and asserting that the returned data displays the itemized ingredients, declared dietary tags, and the exact mathematical sum of (quantity × unit acquisition cost).

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator and a dish in `PENDING_PRICE` status with a persisted recipe, **When** they request the cost audit for the dish, **Then** the system dynamically computes and returns the total Food Cost alongside an itemized list showing ingredient ID, name, portion quantity, unit of measure, unit acquisition cost, line subtotal, and the dish's declared dietary restriction codes.
2. **Given** an authenticated Administrator requesting a cost audit for a dish that is still in `DRAFT` status (not yet submitted for review by the Chef), **When** the request is evaluated, **Then** the system rejects the request with HTTP 409 Conflict, indicating that dishes must be in `PENDING_PRICE` status to be audited.
3. **Given** an ingredient in a recipe whose unit acquisition cost is subsequently updated in the inventory catalog, **When** the Administrator re-queries the cost audit of the dish, **Then** the system dynamically reflects the updated unit cost and recalculated total Food Cost immediately without requiring recipe re-submission.
4. **Given** an active ingredient in the recipe that has an acquisition cost of 0.00 or unpriced in inventory, **When** the cost audit is computed, **Then** the system computes the line subtotal as 0.00, sets `has_unpriced_ingredients = true`, lists the affected ingredient in `warningNotes`, and calculates the remaining items normally.
5. **Given** an authenticated Administrator attempting to query the cost audit of a dish belonging to a different tenant, **When** the request is evaluated, **Then** the system denies access and returns an authorization/not-found error (HTTP 403 / 404), guaranteeing strict multi-tenant boundary isolation.
6. **Given** a dish that has no ingredients assigned (empty recipe), **When** a cost audit is requested, **Then** the system returns an informative error (HTTP 400 Bad Request) stating that the dish lacks a defined recipe.
7. **Given** an authenticated Chef or non-admin user attempting to access the cost audit endpoint, **When** the request is received, **Then** the system denies access with an authorization error (HTTP 403 Forbidden), preserving financial confidentiality.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST enforce multi-tenant isolation on all recipe and cost operations: dishes and ingredients MUST belong to the authenticated user's establishment tenant.
- **FR-002**: The system MUST persist dish recipes in the normalized `dish_recipe_ingredients` table using a canonical full-replacement (idempotent PUT) strategy: the submission MUST include the complete list of ingredients for the portion, and the system MUST synchronize the Many-to-Many association in a single transaction.
- **FR-003**: The system MUST validate that every ingredient assigned to a recipe has a quantity strictly greater than zero ($quantity > 0$). Discrete units (`U`) MUST use integer values; continuous units accept up to 4 decimal places.
- **FR-004**: The system MUST reject duplicate ingredients within the same recipe payload, requiring a single consolidated line item per ingredient.
- **FR-005**: The system MUST prevent assigning ingredients that are in "Discontinued" or "Archived" status at the time of recipe creation or update.
- **FR-006**: The system MUST permit recipe creation and modification exclusively while the dish is in `DRAFT` status. In-place recipe modifications on dishes in `PENDING_PRICE` or published status MUST be blocked (HTTP 409 Conflict).
- **FR-007**: The system MUST expose the cost audit endpoint `GET /api/v1/dishes/{id}/cost-audit` exclusively for dishes in `PENDING_PRICE` status. Requests for dishes in `DRAFT` status MUST be rejected with HTTP 409 Conflict.
- **FR-008**: The system MUST dynamically calculate the total Food Cost per portion as the exact sum of its ingredient line costs:
  $$\text{Food Cost} = \sum_{i=1}^{n} (\text{quantity}_i \times \text{unit\_purchase\_cost}_i)$$
  using `java.math.BigDecimal` with `RoundingMode.HALF_UP` rounded to 2 decimal places for subtotals and the total, guaranteeing zero fractional-cent drift.
- **FR-009**: The system MUST dynamically reflect changes made to ingredient inventory unit costs upon subsequent cost audit queries without requiring manual re-calculation or caching stale prices.
- **FR-010**: The system MUST restrict cost audit queries exclusively to the Administrator (`ADMIN`) role, returning HTTP 403 Forbidden to non-admin roles (including `CHEF`).
- **FR-011**: The cost audit response MUST include: dish ID, dish name, total Food Cost, `has_unpriced_ingredients` flag, `warningNotes` list, the list of declared dietary restriction codes (`dish_dietary_restrictions`), and an itemized breakdown of ingredients with portion quantity, unit of measure, unit acquisition cost, and line subtotal.
- **FR-012**: If any ingredient in the recipe has zero or unpriced unit cost, the system MUST compute that line as 0.00, set `has_unpriced_ingredients = true`, and include a warning note specifying the affected ingredient name without aborting the audit.
- **FR-013**: The system MUST reject cost audit requests for dishes that have no assigned recipe with HTTP 400 Bad Request.

---

### Key Entities

- **Dish (Plato)**: Culinary file entity authored by kitchen staff (`dishes`), holding identity, `category`, `status` (`DRAFT`, `PENDING_PRICE`), optional description, and photo.
- **Ingredient (Materia Prima / Insumo)**: Raw inventory item (`ingredients`), holding name, category, unit of measure (`G`, `KG`, `ML`, `L`, `U`), unit purchase cost (`unit_purchase_cost`), and operational status (`ACTIVE`, `DISCONTINUED`).
- **Dish Recipe Ingredient (Línea de Receta N:M)**: Normalized association (`dish_recipe_ingredients`) with composite primary key `(dish_id, ingredient_id)` and portion `quantity`. Unit of measure is resolved functionally from `Ingredient`.
- **Food Cost Audit Breakdown**: Dynamic financial projection exposing itemized ingredient costs, declared dietary restriction tags, and the exact Food Cost per portion for dishes under administrative review.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% mathematical precision with zero fractional-cent drift between the sum of line-item costs and the reported total Food Cost across all currency amounts.
- **SC-002**: Dynamic cost audit queries execute and return the itemized breakdown in under 100 ms for recipes with up to 50 ingredients.
- **SC-003**: 100% of attempts by non-ADMIN users to access the cost audit endpoint are rejected with HTTP 403 Forbidden.
- **SC-004**: 100% of attempts to audit dishes in `DRAFT` status are blocked with HTTP 409 Conflict.
- **SC-005**: 100% of ingredient unit price updates in the inventory are reflected in subsequent dish cost audits on the immediate next request.
- **SC-006**: Zero cross-tenant data leakage: 100% of unauthorized attempts to link ingredients or inspect costs across different tenants are rejected with strict access boundaries.

---

## Assumptions

- Database tables `dishes`, `dish_recipe_ingredients`, `dish_dietary_restrictions`, and `admin_notifications` are provisioned via Flyway migration `V6__chef_dish_management.sql` established in feature 005.
- The dish lifecycle transitions from `DRAFT` to `PENDING_PRICE` via the review submission endpoint (`POST /api/v1/dishes/{id}/submit-review`) defined in feature 005.
- Retail sales pricing (PVP) and commercial margin assignment (`ADM-5.2.1`) belong to subsequent feature iterations.
