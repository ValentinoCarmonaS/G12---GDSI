# Feature Specification: Recipe Many-to-Many Model and Automated Food Cost Engine

**Feature Branch**: `006-recipe-food-cost`

**Created**: 25/10/2026

**Status**: Draft

**Input**: User description: "Modelo de Recetas Many-to-Many y Motor de Food Cost del proyecto EasyEat. Sprint 2: Esquema de persistencia transaccional N:M para recetas, cálculo dinámico automatizado de costo de materia prima por porción (ADM-5.1.2) y endpoints de auditoría de costos en backend."

## Clarifications

### Session 2026-10-25

- Q: ¿Qué roles tienen autorización para consultar la auditoría de Food Cost y los costos de los insumos? (FR-011) → A: Exclusivo para Administrador (ADMIN). El Chef solo define ingredientes y cantidades en la receta técnica, sin acceso a los precios de compra de inventario ni a la auditoría de Food Cost financiero.
- Q: ¿Cómo debe responder el sistema si un ingrediente de la receta tiene costo de adquisición 0.00 o no cargado en inventario? (Edge Case) → A: Auditoría permisiva con advertencia. Calcula el total con costo 0.00 para ese insumo e incluye un flag explícito ("Unpriced ingredient"), permitiendo visualizar el costo parcial del plato.
- Q: ¿Cómo debe validar el sistema la compatibilidad de unidades entre la receta y el inventario (masa, volumen y unidad discreta 'u')? (FR-008) → A: Validación dimensional estricta. Masa (g, kg) solo convierte con masa; volumen (ml, l) solo con volumen; y unidades discretas (u) requieren coincidencia exacta (u con u). Cualquier incompatibilidad dimensional rechaza el guardado de la receta (HTTP 400 Bad Request).
- Q: ¿Cómo debe comportarse el sistema si el Chef intenta modificar la receta de un plato que ya se encuentra Activo/Publicado en el catálogo? (Edge Case / Invariante) → A: Bloqueo de modificación en platos activos. Si el plato está Activo/Publicado en catálogo, su receta no puede modificarse in-place (HTTP 409 Conflict). Requiere desactivarlo o pasarlo a revisión antes de editar insumos.
- Q: ¿Qué estrategia de guardado y actualización debe emplear la API para la persistencia de recetas? (FR-002 / FR-006) → A: Reemplazo canónico completo (Full Replace idempotente). El Chef envía la lista completa de ingredientes del plato en un solo payload y el sistema sincroniza atómicamente la relación N:M en una sola transacción.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Technical Recipe Composition & Persistence (Priority: P1)

As a Restaurant Chef, I associate a structured list of raw ingredients with specific quantities and measurement units to a dish so that the technical recipe is formally established and ready for production cost analysis.

The Chef specifies the ingredients that compose a dish within their establishment. For each item in the recipe, the Chef selects an existing active ingredient from the establishment's inventory, defines the exact quantity needed per portion, and indicates the applicable unit of measurement. The system validates that all selected ingredients belong to the chef's tenant, are in active status, and carry positive non-zero quantities. The entire recipe composition is saved atomically: either all recipe items persist successfully, or no change is applied. If a recipe already exists for the dish, the Chef can adjust quantities, add new ingredients, or remove items, updating the composition in an atomic transactional operation.

**Why this priority**: Recipe definition is the absolute operational prerequisite for menu items. Without a formal recipe composition, automated raw material cost calculations cannot take place, and subsequent pricing or approval workflows remain blocked.

**Independent Test**: Can be fully tested by creating a draft dish, linking multiple inventory ingredients with defined portion quantities, persisting the recipe, and verifying that the dish retrieves the exact itemized recipe composition while rejecting invalid, duplicate, or cross-tenant ingredients.

**Acceptance Scenarios**:

1. **Given** an authenticated Chef in an establishment with active inventory ingredients, **When** they submit a valid recipe composition for a dish with multiple ingredients, positive quantities ($> 0$), and compatible units of measure, **Then** the system persists the Many-to-Many relationship atomically and associates the complete recipe with the dish.
2. **Given** a Chef submitting a recipe composition, **When** the same ingredient is included more than once in the submission payload, **Then** the system rejects the operation with a validation error indicating that duplicate ingredients must be consolidated into a single line item.
3. **Given** a Chef submitting a recipe composition, **When** any ingredient has a quantity less than or equal to zero ($quantity \le 0$) or an invalid numeric value, **Then** the system rejects the submission and highlights the invalid ingredient quantities.
4. **Given** a dish with an existing persisted recipe, **When** the Chef updates quantities of existing items, adds a new ingredient, or removes an existing ingredient, **Then** the system updates the entire recipe atomically and preserves referential integrity.
5. **Given** a Chef attempting to save a recipe, **When** any referenced ingredient has "Discontinued" status or belongs to a different establishment/tenant, **Then** the system rejects the transaction with an authorization/validation error and leaves the dish unchanged.
6. **Given** a Chef attempting to submit a recipe with an empty list of ingredients, **When** the save request is sent, **Then** the system rejects the operation, requiring at least one valid ingredient to form a recipe.
7. **Given** a Chef submitting a recipe, **When** an ingredient specifies a unit of measure dimensionally incompatible with the inventory purchase unit (e.g., mass 'g' vs. volume 'ml', or mass 'g' vs. discrete 'u'), **Then** the system rejects the submission with a validation error (HTTP 400 Bad Request) enforcing dimensional consistency.
8. **Given** a dish that is in "Active" or published status in the catalog, **When** the Chef attempts to modify its recipe in-place, **Then** the system blocks the update with a state conflict error (HTTP 409 Conflict), requiring the dish to be transitioned out of active status before modifying its recipe composition.

---

### User Story 2 — Automated Dynamic Food Cost Calculation & Audit (Priority: P1)

As a Financial Administrator, I view the real-time calculated raw material cost (Food Cost) per portion and its itemized breakdown for any dish with an assigned recipe, so that I can audit production costs and evaluate profitability.

The Financial Administrator accesses the cost audit information for a dish. The system dynamically computes the Food Cost by evaluating each ingredient in the recipe against its current unit acquisition cost stored in the establishment's inventory. The calculation automatically applies metric unit conversions (e.g., converting recipe grams to inventory purchase kilograms) without floating-point precision loss. The Administrator is presented with an itemized breakdown detailing each ingredient, portion quantity, measurement unit, unit cost, line-item subtotal, and the aggregated Food Cost per portion.

**Why this priority**: Precise cost auditing guarantees commercial viability. Providing dynamic, automated Food Cost calculations eliminates manual spreadsheet errors and equips administrators with the operational truth needed before setting retail prices (PVP) or approving menu publication.

**Independent Test**: Can be tested independently by taking a dish with an assigned recipe of known ingredient unit costs, requesting the cost audit endpoint, and asserting that the line items and total Food Cost match the exact mathematical sum of (quantity × unit cost) with proper unit conversions.

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator and a dish with a persisted recipe composed of valued inventory ingredients, **When** the Administrator requests the cost audit for the dish, **Then** the system dynamically computes and returns the aggregated Food Cost alongside an itemized list showing ingredient name, required portion quantity, measurement unit, unit cost, and line subtotal.
2. **Given** an ingredient whose recipe unit differs from its inventory purchase unit (e.g., 250 grams in recipe vs. \$4.00 per kilogram in inventory), **When** the system calculates the line cost, **Then** it applies standard metric unit conversion ($250\text{ g} \times \$0.004\text{/g} = \$1.00$) with exact decimal precision and zero fractional-cent drift.
3. **Given** an ingredient in a recipe whose unit acquisition cost is subsequently updated in the inventory catalog, **When** the Administrator re-queries the cost audit of the dish, **Then** the system dynamically reflects the updated unit cost and recalculated total Food Cost immediately without requiring recipe re-submission.
4. **Given** an authenticated Administrator attempting to query the cost audit of a dish belonging to a different tenant, **When** the request is evaluated, **Then** the system denies access and returns an authorization/not-found error, guaranteeing strict multi-tenant boundary isolation.
5. **Given** a dish that has no ingredients assigned (empty recipe), **When** a cost audit is requested, **Then** the system returns an informative response stating that the dish lacks a defined recipe and cannot be audited for food cost.
6. **Given** an authenticated Chef or non-admin user attempting to access the cost audit endpoint, **When** the request is received, **Then** the system denies access with an authorization error (HTTP 403 Forbidden), preserving financial confidentiality.

---

### Edge Cases

- **Zero-Cost or Unpriced Ingredients**: If an active ingredient in the recipe has an acquisition cost of 0.00 or undefined, the system must compute the line cost as 0.00, include an explicit audit advisory note ("Unpriced ingredient"), and allow the remaining lines to calculate normally without failing the audit request.
- **Incompatible Unit Conversion**: If a recipe specifies an ingredient quantity in volume (e.g., milliliters) while the inventory tracks it exclusively in mass (e.g., kilograms) without a configured density factor, the system must reject the recipe assignment or flag the audit line as an incompatible unit conversion.
- **Fractional Cent Rounding**: When multiplying fractional ingredient quantities by unit costs (e.g., 3.5 grams at \$0.0175 per gram), sub-cent intermediate values must be computed with high precision (at least 4 decimal places) and rounded deterministically to 2 decimal places using standard half-up rounding for the final line subtotal and overall total.
- **Recipe Modification on Locked/Active Dishes**: If a dish has transitioned to an "Active" or published state, the system must enforce state-machine rules preventing uncontrolled modification of its recipe without a dedicated revision cycle.
- **Discontinued Ingredient in Existing Recipe**: If an ingredient was previously assigned to a valid recipe and is subsequently marked as "Discontinued" in the inventory, existing historical cost audits must flag the ingredient as "Discontinued in Inventory" without crashing the audit view.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST enforce multi-tenant isolation on all recipe and cost operations: dishes and ingredients MUST belong to the same establishment tenant.
- **FR-002**: The system MUST provide transactional, atomic persistence for dish recipes using a canonical full-replacement (idempotent PUT) strategy: the submission MUST include the complete list of ingredients for the portion, and the system MUST synchronize the Many-to-Many association in a single transaction (inserting new items, updating modified quantities, and deleting omitted items). Partial or non-atomic persistence of recipe items MUST NOT occur.
- **FR-003**: The system MUST validate that every ingredient assigned to a recipe has a quantity strictly greater than zero ($quantity > 0$).
- **FR-004**: The system MUST reject duplicate ingredients within the same recipe payload, requiring a single consolidated line item per ingredient.
- **FR-005**: The system MUST prevent assigning ingredients that are in "Discontinued" status at the time of recipe creation or update.
- **FR-006**: The system MUST support updating existing recipes by replacing, adding, or removing ingredient lines atomically while preserving referential integrity, provided the dish is in an editable lifecycle state (e.g., Draft or In Review). If the dish is in "Active" (published) status, in-place recipe modifications MUST be blocked (HTTP 409 Conflict).
- **FR-007**: The system MUST dynamically calculate the total Food Cost per portion for a dish as the sum of its ingredient line costs:
  $$\text{Food Cost} = \sum_{i=1}^{n} (\text{quantity}_i \times \text{effective\_unit\_cost}_i)$$
- **FR-008**: The system MUST enforce strict dimensional unit compatibility between recipe items and inventory units: mass units (g, kg) MUST only convert to mass units; volume units (ml, l) MUST only convert to volume units; and discrete units (u) MUST match exactly without cross-dimensional conversion. Any dimensional incompatibility MUST be rejected upon recipe submission (HTTP 400 Bad Request). Standard metric conversions (1 kg = 1000 g, 1 l = 1000 ml) MUST be applied without precision loss.
- **FR-009**: The system MUST compute monetary cost values using exact decimal precision (fixed-point arithmetic), avoiding binary floating-point representation and precision drift.
- **FR-010**: The system MUST dynamically reflect changes made to ingredient inventory unit costs upon subsequent cost audit queries without requiring manual re-calculation or caching stale prices.
- **FR-011**: The system MUST restrict cost audit queries exclusively to the Administrator (ADMIN) role, returning HTTP 403 Forbidden to non-admin roles (including CHEF). The audit response MUST detail for each item: ingredient ID, ingredient name, recipe quantity, measurement unit, current unit cost, unit cost measurement unit, line subtotal, and total Food Cost. If any ingredient has zero or unpriced unit cost, the response MUST compute that line as 0.00, flag `has_unpriced_ingredients: true`, and list the affected ingredients in a warning notes section.
- **FR-012**: The system MUST reject cost audit requests for dishes that have no assigned recipe, indicating that the recipe is empty.

---

### Key Entities

- **Dish (Plato)**: Represents the culinary menu item belonging to a specific tenant, possessing a title, category, operational lifecycle status, and an associated recipe.
- **Ingredient (Materia Prima / Insumo)**: Represents a raw material tracked in the tenant's inventory with a name, purchase unit of measure, unit acquisition cost, and lifecycle status (Active/Discontinued).
- **Recipe Item (Ítem de Receta / Detalle N:M)**: The relational link between a Dish and an Ingredient, specifying the exact quantity and unit of measure consumed per individual portion of the dish.
- **Food Cost Audit Breakdown**: A dynamic projection representing the point-in-time calculation of all recipe items, their converted unit costs, line subtotals, and the aggregated production cost per portion.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% mathematical precision with zero fractional-cent drift between the sum of line-item costs and the reported total Food Cost across all currency amounts.
- **SC-002**: Recipe persistence and update transactions complete execution in under 200 ms under normal operational loads.
- **SC-003**: Dynamic cost audit queries execute and return the itemized breakdown in under 150 ms for recipes with up to 50 ingredients.
- **SC-004**: Zero cross-tenant data leakage: 100% of unauthorized attempts to link ingredients or inspect costs across different tenants are rejected with strict access boundaries.
- **SC-005**: 100% of ingredient unit price updates in the inventory are reflected in subsequent dish cost audits on the immediate next request.
- **SC-006**: 100% of non-ADMIN attempts to query the cost audit endpoint are rejected with HTTP 403 Forbidden.

---

## Assumptions

- Ingredient costs are tracked in the establishment's primary operational currency as established in feature 004 (`004-staff-mgmt-rbac-inventory`).
- Dishes are created with standard lifecycle statuses (e.g., Draft, Pending Review, Active, Inactive) before or concurrently with recipe attachment.
- Compatible unit conversions are limited to standard metric mass (mg, g, kg) and volume (ml, l). Custom volumetric-to-mass conversions requiring density factors are out of scope unless density is explicitly configured.
- Retail sales pricing (PVP), commercial margin assignment, and public menu visibility belong to subsequent feature iterations (`ADM-5.2.1`, `ADM-5.2.2`).
