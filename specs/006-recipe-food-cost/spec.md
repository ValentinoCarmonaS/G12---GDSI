# Feature Specification: Automated Food Cost Engine and Recipe Audit Breakdown (ADM-5.1.2)

**Feature Branch**: `006-recipe-food-cost`

**Created**: 25/10/2026 | **Last Amended**: 01/11/2026

**Status**: Resolved (Bug C-03 Fix Verified)

**Input**: User Story ADM-5.1.2: "Como administrador financiero, quiero leer los ingredientes que el Chef cargó en el plato y analizar su impacto, para entender qué rentabilidad me va a dejar. Criterios de Aceptación: Dado que el admin abre un plato pendiente, Cuando evalúa la receta, Entonces puede visualizar los insumos y etiquetas requeridas."

---

## Clarifications

### Session 2026-10-25 (Initial Clarification)

- Q: ¿Qué roles tienen autorización para consultar la auditoría de Food Cost y los costos de los insumos? (FR-010) → A: Exclusivo para Administrador (ADMIN / ADMINISTRADOR). El Chef solo define ingredientes y cantidades en la receta técnica, sin acceso a los precios de compra de inventario ni a la auditoría financiera.
- Q: ¿Cómo debe responder el sistema si un ingrediente de la receta tiene costo de adquisición 0.00 o no cargado en inventario? (Edge Case) → A: Auditoría permisiva con advertencia. Calcula el subtotal con costo 0.00 para ese insumo e incluye un flag explícito (`has_unpriced_ingredients = true`) y notas de advertencia, permitiendo visualizar el costo parcial del plato.
- Q: ¿Cómo debe exponer y auditar las etiquetas requeridas el endpoint de auditoría de costos? → A: Solo lectura de los códigos de restricción y alérgenos declarados en el plato (`restrictionCodes` asociados en `dish_dietary_restrictions`), retornados en el payload de auditoría junto a la lista itemizada de insumos.

### Session 2026-10-28 (Purge, Re-alignment & Spec 007 Unblocking)

- Q: ¿Cómo se delimita el alcance de la spec 006 respecto a la historia de usuario CHF-2.2.2? (Constitución Principios V y X) → A: Purga formal. La persistencia y edición de recetas técnicas por parte del Chef (`CHF-2.2.2`, endpoint `PUT /api/v1/dishes/{id}/recipe`) es propiedad exclusiva de la spec 005. La spec 006 se delimita al 100% como un vertical slice enfocado únicamente en **ADM-5.1.2** (Auditoría de Receta y Food Cost para el Administrador).
- Q: ¿Dónde debe residir la compuerta de estado `PENDING_PRICE`? (`R-006-1`) → A: La validación de estado `PENDING_PRICE` se desacopla y se aplica en el Controller (`AdminDishCostAuditController`), devolviendo HTTP 409 Conflict si un plato no está en revisión. El servicio (`FoodCostAuditService.auditDishCost`) queda liberado para que servicios aguas abajo (como la spec 007 para platos `PUBLISHED`) puedan auditar costos sin duplicar lógica ni DTOs.
- Q: ¿Cuál es la fuente autoritativa para el costo unitario de adquisición en el Food Cost? (`R-004-1`) → A: El catálogo maestro `ingredients.unit_purchase_cost`. Representa el costo estándar o último precio de compra registrado al agregar stock en inventario (actualizado automáticamente por cada recepción de lote con costo > 0), permitiendo auditar y presupuestar platos independientemente de la existencia de stock físico transitorio en lotes (`ingredient_batches`). Redondeo exacto a 2 decimales (`HALF_UP`) en subtotales y total. (Nota: Se enmienda la decisión original del 2026-10-28 que desconectaba el inventario, asegurando la sincronización automática entre ingresos de stock y el costo maestro).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Recipe Audit, Ingredients, and Base Food Cost (ADM-5.1.2) (Priority: P1)

**ADM-5.1.2**: As a Financial Administrator, I open a pending dish submitted for review, read the ingredients that the Chef assigned, and analyze the calculated raw material cost (Food Cost) and declared dietary labels, so that I can evaluate profitability and operational compliance before assigning prices or approving publication.

The Financial Administrator accesses the cost audit endpoint and UI panel for a dish that is currently in `PENDING_PRICE` status. The system dynamically computes the Food Cost by evaluating each ingredient in `dish_recipe_ingredients` against its current unit purchase cost stored in `ingredients`. The calculation applies exact fixed-point arithmetic (`BigDecimal`, `RoundingMode.HALF_UP` to 2 decimal places) without floating-point precision loss. The Administrator receives an itemized breakdown detailing each ingredient, required portion quantity, inventory unit of measure, unit acquisition cost, line subtotal, warning flags for unpriced items, the aggregated total Food Cost per portion, and the declared dietary restrictions / allergen tags associated with the dish.

**Why this priority**: Precise cost auditing guarantees business profitability and compliance. Providing dynamic, automated Food Cost calculations eliminates manual spreadsheet errors and equips administrators with the financial truth needed before setting retail prices (PVP) or approving menu publication.

**Independent Test**: Can be tested independently by opening a dish in `PENDING_PRICE` status with an assigned recipe, requesting the cost audit endpoint as Administrator, and asserting that the returned data displays the itemized ingredients, declared dietary tags, and the exact mathematical sum of (quantity × unit purchase cost).

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator and a dish in `PENDING_PRICE` status with a persisted recipe, **When** they request the cost audit for the dish (via API or UI), **Then** the system dynamically computes and returns the total Food Cost alongside an itemized list showing ingredient ID, name, portion quantity, unit of measure, unit acquisition cost, line subtotal, and the dish's declared dietary restriction codes.
2. **Given** an authenticated Administrator requesting a cost audit via HTTP for a dish that is still in `DRAFT` status (not yet submitted for review by the Chef), **When** the request is evaluated by the controller, **Then** the system rejects the request with HTTP 409 Conflict, indicating that dishes must be in `PENDING_PRICE` status to be audited via the public endpoint.
3. **Given** an ingredient in a recipe whose unit acquisition cost is subsequently updated in the inventory catalog (`ingredients.unit_purchase_cost`), **When** the Administrator re-queries the cost audit of the dish, **Then** the system dynamically reflects the updated unit cost and recalculated total Food Cost immediately without requiring recipe re-submission.
4. **Given** an active ingredient in the recipe that has an acquisition cost of 0.00 or unpriced in inventory (`null` or $\le 0$), **When** the cost audit is computed, **Then** the system computes the line subtotal as 0.00, sets `has_unpriced_ingredients = true`, lists the affected ingredient in `warningNotes`, and calculates the remaining items normally.
5. **Given** an authenticated Administrator attempting to query the cost audit of a dish belonging to a different tenant, **When** the request is evaluated, **Then** the system denies access and returns an authorization/not-found error (HTTP 403 / 404), guaranteeing strict multi-tenant boundary isolation.
6. **Given** a dish that has no ingredients assigned (empty recipe), **When** a cost audit is requested, **Then** the system returns an informative error (HTTP 400 Bad Request) stating that the dish lacks a defined recipe.
7. **Given** an authenticated Chef or non-admin user attempting to access the cost audit endpoint, **When** the request is received, **Then** the system denies access with an authorization error (HTTP 403 Forbidden), preserving financial confidentiality.
8. **Given** an authenticated Administrator on the Back-Office Workspace viewing pending dishes, **When** they click "Auditar Costo" on a pending dish notification, **Then** the interface opens `DishCostAuditPage` displaying the `CostAuditPanel` with full itemized costs, unpriced alerts, and dietary badges.
9. **Given** an ingredient recorded with a purchase presentation of 12 base units for a package cost of \$3,600.00, **When** the Food Cost audit evaluates a recipe line of 2 units, **Then** the effective unit acquisition cost is \$300.00 per unit, the line subtotal is \$600.00, and the audit item reports \$300.00 as its unit acquisition cost — never the \$3,600.00 package total.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST enforce multi-tenant isolation on all cost audit operations: dishes and ingredients MUST belong to the authenticated user's establishment tenant.
- **FR-002**: The system MUST restrict cost audit queries exclusively to the Administrator (`ADMIN` / `ADMINISTRADOR`) role, returning HTTP 403 Forbidden to non-admin roles (including `CHEF`).
- **FR-003**: The HTTP endpoint `GET /api/v1/dishes/{id}/cost-audit` MUST enforce at the controller layer that the requested dish is in `PENDING_PRICE` status. Requests for dishes in `DRAFT` status MUST be rejected with HTTP 409 Conflict.
- **FR-004**: The domain service `FoodCostAuditService` MUST calculate and assemble the audit breakdown independently of dish status gates, allowing internal consumers (such as spec 007 for `PUBLISHED` dishes) to compute food costs without duplicate logic.
- **FR-005**: The authoritative unit acquisition cost for each ingredient MUST be retrieved from `ingredients.unit_purchase_cost` (the master catalog price), after applying the purchase-presentation conversion of spec 004's FR-015d when `package_cost` is recorded: $\text{effective\_unit\_cost} = \text{package\_cost} / \text{units\_per\_package}$ (`HALF_UP`, 4 decimals), where `units_per_package` is an integer that defaults to `1`. For mass or volume units acquired directly (kg, l, loose `u`) and for ingredients without a recorded presentation, `unit_purchase_cost` is used as-is.
- **FR-006**: The system MUST dynamically calculate the total Food Cost per portion as the exact sum of its ingredient line costs, applying the presentation conversion **before** each line subtotal:
  $$\text{line\_subtotal}_i = \text{quantity}_i \times \left(\frac{\text{package\_cost}_i}{\text{units\_per\_package}_i}\right)$$
  falling back to $\text{quantity}_i \times \text{unit\_purchase\_cost}_i$ when no presentation is recorded, using `java.math.BigDecimal` with `RoundingMode.HALF_UP` rounded to 2 decimal places for each line subtotal and the total cost, guaranteeing zero fractional-cent drift.
- **FR-007**: If any ingredient in the recipe has zero or unpriced unit cost (`null` or $\le 0$), the system MUST compute that line as 0.00, set `has_unpriced_ingredients = true`, and include a warning note specifying the affected ingredient name without aborting the audit.
- **FR-008**: The cost audit response MUST include: dish ID, dish name, total Food Cost, `has_unpriced_ingredients` flag, `warningNotes` list, the list of declared dietary restriction codes (`dish_dietary_restrictions`), and an itemized breakdown of ingredients with portion quantity, unit of measure, unit acquisition cost, and line subtotal.
- **FR-009**: The system MUST reject cost audit requests for dishes that have no assigned recipe with HTTP 400 Bad Request (`EmptyRecipeException`).
- **FR-010**: The frontend MUST provide a dedicated audit view (`CostAuditPanel` within `DishCostAuditPage`) accessible to Administrators from the workspace pending review tray, displaying the full itemized table, total Food Cost, declared dietary badges, and unpriced warnings.
- **FR-011**: The frontend UI itemized table MUST explicitly bind the "CANTIDAD" column to the quantity property received from the API audit DTO, ensuring fallback default handling (e.g. `0` or `0.00`) to prevent `NaN` rendering errors.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% mathematical precision with zero fractional-cent drift between the sum of line-item costs and the reported total Food Cost across all currency amounts.
- **SC-002**: Dynamic cost audit queries execute and return the itemized breakdown in under 100 ms for recipes with up to 50 ingredients.
- **SC-003**: 100% of attempts by non-ADMIN users to access the cost audit endpoint are rejected with HTTP 403 Forbidden.
- **SC-004**: 100% of attempts to audit dishes in `DRAFT` status via HTTP endpoint are blocked with HTTP 409 Conflict.
- **SC-005**: 100% of ingredient unit price updates in the inventory are reflected in subsequent dish cost audits on the immediate next request.
- **SC-006**: Zero cross-tenant data leakage: 100% of unauthorized attempts to inspect costs across different tenants are rejected with strict access boundaries.
- **SC-007**: 100% end-to-end frontend integration: an Administrator can navigate from pending notifications directly to the audit screen and inspect costs with full visual feedback.

---

## Assumptions

- Database tables `dishes`, `dish_recipe_ingredients`, and `dish_dietary_restrictions` are provisioned via Flyway migration `V7__chef_dish_management.sql` established in feature 005.
- Recipe composition and persistence (`PUT /api/v1/dishes/{id}/recipe`) are authored by Chefs in `DRAFT` status under feature 005 (`CHF-2.2.2`).
- Dish review submission transitions the dish to `PENDING_PRICE` under feature 005 (`CHF-2.2.4`).
- Commercial margin assignment and retail sales pricing (PVP) belong to subsequent feature 007 (`ADM-5.2.1`).
