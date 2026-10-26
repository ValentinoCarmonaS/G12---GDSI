# Feature Specification: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Feature Branch**: `008-diner-catalog-multi-tenant`

**Created**: 26/10/2026

**Status**: Draft

**Input**: User description: "Multi-Tenant Establishment Discovery, Real-Time Gastronomic Catalog, and Stock-Aware Menu Browsing — COM-1.5.1, COM-4.1.1, COM-4.1.2, COM-4.2.3"

## Clarifications

### Session 2026-10-26
- Q: ¿Qué acción concreta ejecutan los controles de selección de platos en el catálogo y cuál es el alcance del carrito? (FR-012) → A: El carrito de compras no está contemplado en esta especificación ni en este sprint (no existe carrito; pedidos y compra quedan fuera de alcance). La interacción sobre los platos consiste en la exploración visual y la apertura del modal informativo de detalle (foto de alta resolución, descripción, precio e ingredientes de la receta). Cuando un plato está fuera de horario o agotado por falta de stock de ingredientes, se visualiza en escala de grises con el badge "Agotado" y el modal refleja su estado de indisponibilidad.
- Q: ¿Cuál es el umbral máximo de latencia aceptable para que un cambio de stock se refleje en la disponibilidad del catálogo del comensal? (SC-004, FR-016) → A: Máximo 3 segundos desde que el cambio de stock impacta en el backend hasta que la disponibilidad de los platos dependientes se actualiza visualmente en la pantalla del comensal.
- Q: ¿Cómo debe presentarse la disponibilidad de stock al comensal en el catálogo? (FR-013, FR-014) → A: La disponibilidad se presenta de forma estrictamente binaria (plato a color con controles habilitados vs. plato atenuado en escala de grises con badge "Agotado" / "Out of Stock"). No se exponen conteos numéricos de porciones ni cantidades de stock de cocina por razones de confidencialidad comercial y simplicidad de interfaz.
- Q: ¿Cómo se gestiona el cambio de establecimiento si no hay carrito en este sprint? (FR-006) → A: Al no existir carrito de compras en esta especificación ni en este sprint, cambiar de establecimiento simplemente descarga los datos del catálogo y stock del establecimiento anterior y carga limpiamente el catálogo del nuevo establecimiento seleccionado, sin necesidad de confirmaciones ni limpieza de carrito.
- Q: ¿Debe soportarse el acceso directo al catálogo mediante URL o escaneo de código QR (/catalogo/:tenantId)? (FR-004) → A: El acceso directo por enlace externo o escaneo de código QR corresponde a una historia de usuario independiente y queda explícitamente FUERA DE ALCANCE para esta especificación. En esta iteración, el acceso al catálogo se origina exclusivamente a través del flujo de descubrimiento y selección en la barra central de búsqueda (COM-1.5.1).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Multi-Tenant Establishment Discovery & Selection (Priority: P1)

**COM-1.5.1**: As a Diner, I search for a restaurant by typing into a central search bar, so that I can discover available establishments and select one to explore its live menu.

The Diner accesses the establishment discovery screen and begins typing into a central search bar. Once at least 3 valid characters are entered (after trimming whitespace), the system presents an autocomplete dropdown of matching establishments. Matching is case-insensitive and diacritic-insensitive (e.g., "cafe" matches "Café Central"). Each autocomplete result displays the establishment's name. Selecting an active and open establishment redirects the Diner to that establishment's live catalog. Selecting an establishment that has been paused or disabled by an administrator presents a full-page blocking notice indicating temporary closure and preventing menu access, with an action to return to search. Selecting a different establishment cleanly replaces the current context with the newly chosen establishment's catalog and inventory data.

**Why this priority**: Establishment selection is the entry point for every subsequent user story. Without selecting an establishment, the Diner cannot browse a catalog, see stock availability, or search for dishes.

**Independent Test**: Can be fully tested by typing a search query, verifying autocomplete results appear after 3 characters, selecting an active establishment, and confirming the catalog loads for that specific tenant.

**Acceptance Scenarios**:

1. **Given** a Diner on the discovery screen, **When** they type fewer than 3 characters in the search bar, **Then** no autocomplete results are displayed and the current screen state is preserved.
2. **Given** a Diner typing 3 or more valid characters, **When** the search term matches one or more active establishments by name, **Then** the system displays an autocomplete dropdown listing all matching establishments.
3. **Given** a Diner typing a query with mixed-case or accented characters (e.g., "cafe"), **When** an establishment name contains diacritics or different casing (e.g., "Café Central"), **Then** the match is found and displayed in the autocomplete results.
4. **Given** a Diner selecting an active and open establishment from the autocomplete, **When** the selection is confirmed, **Then** the system sets the multi-tenant context to the selected establishment and loads its live catalog.
5. **Given** a Diner selecting an establishment that has been paused or disabled by an administrator, **When** the selection is confirmed, **Then** the system displays a full-page blocking notice ("This establishment is temporarily inactive and is not accepting orders") without exposing any catalog data, and provides an action to return to the search screen.
6. **Given** a Diner typing a query that matches zero establishments, **When** the search evaluates, **Then** the system displays an informative message ("No establishments found. Try modifying your search terms.") without error screens.
7. **Given** a Diner typing a query consisting exclusively of whitespace or special characters, **When** the input is evaluated, **Then** the system ignores the query without triggering autocomplete or altering the screen state.
8. **Given** a Diner currently browsing Establishment A's catalog, **When** they navigate back to search and select Establishment B, **Then** the system cleanly unloads Establishment A's data and loads Establishment B's catalog scoped exclusively to Establishment B's tenant.

---

### User Story 2 — Real-Time Gastronomic Catalog Exploration (Priority: P1)

**COM-4.1.1**: As a Diner, I browse the menu of the selected establishment organized into gastronomic categories with high-resolution photos, names, and current prices, so that I can explore the restaurant's offering and identify dishes I want.

After selecting an active establishment, the Diner views the catalog organized by gastronomic categories (Entradas, Platos principales, Postres, Bebidas, Ensaladas). Each dish card displays its name, price, optional description, and optional high-resolution photo. A sticky category navigation bar allows smooth jumping directly to any section. Categories that contain zero active dishes (all dishes decommissioned) are automatically hidden from the view and the category bar. If a high-resolution image fails to load, the interface displays a lightweight default placeholder immediately so that dish names, descriptions, and prices remain fully legible. When the establishment is outside its scheduled operating hours, the catalog is presented in a read-only mode: Diners can freely browse categories, photos, and prices, but all dish selection controls are disabled, accompanied by an informational banner indicating the restaurant's next opening schedule.

**Why this priority**: The catalog is the core value proposition for Diners. Without it, there is no menu to browse, no prices to see, and no stock availability to evaluate.

**Independent Test**: Can be tested by selecting an establishment with active dishes across multiple categories, verifying dishes are grouped correctly, categories with zero active dishes are hidden, and the sticky bar navigates to the correct section.

**Acceptance Scenarios**:

1. **Given** a Diner viewing the catalog of an active establishment, **When** the catalog loads, **Then** dishes are grouped under their gastronomic category headings (Entradas, Platos principales, Postres, Bebidas, Ensaladas) and each dish displays its name, current price, optional description, and optional photo.
2. **Given** a category where 100% of its dishes have been decommissioned (lifecycle status is not ACTIVE), **When** the catalog renders, **Then** that category is completely hidden from the catalog view and from the category navigation bar.
3. **Given** a category where all its active dishes are currently out of stock, **When** the catalog renders, **Then** the category remains visible, displaying its greyed-out dishes, so Diners can see the establishment's full offering.
4. **Given** a Diner using the sticky category bar, **When** they tap a category name, **Then** the view scrolls smoothly to that category's section.
5. **Given** a slow network connection, **When** a dish's high-resolution image fails to load within a reasonable time, **Then** the interface immediately displays a lightweight placeholder image without breaking layout, and the dish name, description, and price remain fully visible and legible.
6. **Given** an establishment currently outside its scheduled operating hours, **When** a Diner views its catalog, **Then** the catalog renders in read-only mode with an informational banner displayed indicating the restaurant's opening schedule, and clicking any dish card opens the detail modal displaying the outside-hours notice.
7. **Given** an establishment within its operating hours and an available dish, **When** a Diner views its catalog, **Then** the dish card is displayed in full color indicating available status, and clicking the card opens an informational dish detail modal showing high-resolution photo, description, price, and recipe ingredients.

---

### User Story 3 — Dynamic Stock-Based Dish Availability & Real-Time Invalidation (Priority: P1)

**COM-4.1.2**: As a Diner, I see which dishes are currently available and which are out of stock based on real-time kitchen ingredient inventory, so that I can make informed selections without encountering unavailable items.

Each dish in the catalog is evaluated against its recipe's ingredient requirements and the establishment's current ingredient stock. A dish is available if and only if every ingredient in its recipe has sufficient current stock to prepare at least one portion. Available dishes are rendered in full color with an active availability status. Dishes where any required ingredient has insufficient stock are rendered greyed-out with a visible "Out of Stock" badge. Clicking any dish card (available or out-of-stock) opens the informational detail modal showing ingredients and availability state. Decommissioned or permanently discontinued dishes are completely hidden from the catalog. When kitchen personnel update stock (adding ingredients or recording depletion), the changes are reflected in the Diner-facing catalog in real time without requiring manual page refresh. When a shared critical ingredient drops to zero, all dependent dishes across all categories are simultaneously invalidated.

**Why this priority**: Stock-driven availability is a core differentiator of EasyEat. Without it, Diners would select dishes that cannot be prepared, leading to failed orders and frustration.

**Independent Test**: Can be tested by creating an establishment with dishes whose recipes share ingredients, depleting a shared ingredient's stock to zero, and verifying that all dependent dishes are simultaneously greyed-out with the "Out of Stock" badge.

**Acceptance Scenarios**:

1. **Given** a dish whose recipe requires ingredients A (quantity: 2) and B (quantity: 1), and the establishment has stock A=5 and B=3, **When** the catalog loads, **Then** the dish is displayed in full color as available, and clicking the card opens the detail modal showing available status and recipe ingredients.
2. **Given** the same dish, and the establishment has stock A=1 (less than recipe requirement of 2), **When** the catalog loads, **Then** the dish is rendered greyed-out with an "Out of Stock" badge, and clicking the card opens the detail modal indicating the out-of-stock status.
3. **Given** a dish that has been decommissioned (lifecycle status is not ACTIVE), **When** the catalog loads, **Then** the dish is completely hidden from the catalog view.
4. **Given** two dishes sharing ingredient X (Dish A requires 1 unit, Dish B requires 2 units), and stock of X is 1, **When** the catalog evaluates availability, **Then** Dish A is available and Dish B is out of stock.
5. **Given** ingredient X with stock = 1 shared by Dish A (requires 1) and Dish B (requires 1), **When** stock of X drops to 0 (e.g., consumed by an order), **Then** both Dish A and Dish B are simultaneously invalidated (greyed-out, "Out of Stock" badge displayed).
6. **Given** a dish currently showing as out of stock because ingredient Y has stock = 0, **When** kitchen personnel add stock for ingredient Y bringing it above the recipe requirement, **Then** the dish is automatically re-enabled (shown in full color with active selection controls) upon the next catalog update.
7. **Given** a dish with no recipe defined (zero ingredients), **When** the catalog evaluates availability, **Then** the dish is treated as unavailable (out of stock) since its preparability cannot be determined.

---

### User Story 4 — Fast In-Catalog Text Search (Priority: P2)

**COM-4.2.3**: As a Diner, I type into an internal catalog search bar to filter dishes by name or ingredients in real time, so that I can quickly find specific items without scrolling through the entire menu.

The Diner uses a search bar within the catalog to filter the currently loaded menu. As the Diner types, the catalog filters in real time. The search matches against both dish titles and ingredient descriptions/names. Search evaluation is case-insensitive and diacritic-insensitive (e.g., "jamon" matches "Jamón Serrano"). Categories with zero matching dishes are dynamically hidden during the active search. If the Diner was already viewing a specific category when they begin searching, the search filters within that category; otherwise, it filters across all categories. Clearing the search input immediately restores the complete menu view without page reloads.

**Why this priority**: P2 because the catalog is fully usable without search (Diners can browse by category). Search is an accelerator for experienced users or those looking for specific ingredients.

**Independent Test**: Can be tested by loading a catalog, typing a partial dish name, verifying only matching dishes are displayed, then typing an ingredient name and verifying dishes containing that ingredient appear.

**Acceptance Scenarios**:

1. **Given** a Diner viewing the catalog, **When** they type "burg" in the search bar, **Then** only dishes whose title or ingredient descriptions contain "burg" are displayed, and categories with zero matches are hidden.
2. **Given** a Diner typing "jamon" (without accent), **When** a dish's ingredient list includes "Jamón" (with accent), **Then** the dish is included in the search results.
3. **Given** a Diner typing "QUESO" (uppercase), **When** dishes contain "queso" in their ingredients, **Then** the search is case-insensitive and the matching dishes are displayed.
4. **Given** a Diner was viewing the "Postres" category and types a search query, **When** the search evaluates, **Then** results are filtered within the "Postres" category only.
5. **Given** a Diner was viewing "All categories" and types a search query, **When** the search evaluates, **Then** results are filtered across all categories.
6. **Given** a search query that matches zero dishes, **When** the search evaluates, **Then** the system displays a clear message ("No dishes found. Try adjusting your search.") and all categories are hidden.
7. **Given** a Diner with an active search filter, **When** they clear the search input, **Then** the complete catalog is immediately restored (all categories and dishes re-appear) without a page reload.
8. **Given** a Diner typing a query consisting exclusively of whitespace, **When** the input is evaluated, **Then** the system ignores the query and the catalog state remains unchanged.

---

### Edge Cases

- **Fewer than 3 characters in establishment search**: Autocomplete is not triggered; current state is preserved.
- **Establishment search with only spaces or special characters**: Input is trimmed/sanitized; autocomplete is not triggered.
- **Shared critical ingredient drops to zero**: All dishes using that ingredient across all categories update to out-of-stock simultaneously.
- **Kitchen staff adds stock for a previously depleted ingredient**: All dependent dishes automatically become available upon the next catalog update.
- **Category where 100% of active dishes are out of stock**: The category remains visible, displaying its greyed-out dishes.
- **Category where all dishes are decommissioned**: The entire category is hidden from the catalog and the navigation bar.
- **Switching establishments via search**: The previous establishment's data is cleanly unloaded and the new establishment's catalog is loaded, scoped strictly to the new tenant.
- **Slow network / image loading timeout**: Placeholder image is shown immediately; text content (name, price, description) is never blocked.
- **Browsing outside operating hours**: Catalog visible in read-only mode; selection controls disabled; informational banner displayed.
- **Dish with no recipe (zero ingredients)**: Treated as unavailable since preparability cannot be determined.
- **Concurrent stock depletion**: If a dish's last available portion is consumed while a Diner is viewing it, the dish transitions to greyed-out with "Out of Stock" badge on the next real-time update.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an establishment search bar that triggers autocomplete only after the Diner types 3 or more valid characters (after trimming whitespace). Queries shorter than 3 valid characters MUST NOT trigger autocomplete.
- **FR-002**: Establishment autocomplete matching MUST be case-insensitive and diacritic-insensitive. The search MUST safely handle whitespace-only and special-character-only inputs by ignoring them.
- **FR-003**: The autocomplete results MUST display establishment name and MUST be strictly scoped to establishments that are active (not paused or disabled by an administrator).
- **FR-004**: When a Diner selects an active and open establishment, the system MUST set the multi-tenant session context to that establishment's tenant ID and load its live catalog.
- **FR-005**: When a Diner selects an establishment that is paused or disabled, the system MUST display a full-page blocking notice ("This establishment is temporarily inactive and is not accepting orders") without exposing any catalog data, and provide a return action to the search screen.
- **FR-006**: When a Diner switches from one establishment to another, the system MUST cleanly replace the previous tenant's data with the newly selected tenant's catalog and stock data.
- **FR-007**: The catalog MUST display active dishes grouped by gastronomic category (Entradas, Platos principales, Postres, Bebidas, Ensaladas). Each dish card MUST show: dish name, current price, optional description, optional photo, availability indicator, and allow clicking the card to open an informational dish detail modal.
- **FR-008**: The catalog MUST provide a sticky category navigation bar that allows smooth scrolling to any visible category section.
- **FR-009**: Categories where 100% of their dishes have a lifecycle status other than ACTIVE (all decommissioned) MUST be completely hidden from the catalog view and the category navigation bar.
- **FR-010**: Categories where all active dishes are currently out of stock MUST remain visible, displaying the greyed-out dishes so the Diner can see the restaurant's full offering.
- **FR-011**: When a high-resolution dish image fails to load, the system MUST immediately display a lightweight default placeholder image without breaking layout or obscuring text (dish name, price, description).
- **FR-012**: When the establishment is outside its scheduled operating hours, the catalog MUST be presented in read-only mode: an informational banner MUST be displayed indicating the restaurant's opening schedule, and clicking any dish card opens the detail modal displaying the outside-hours notice.
- **FR-013**: A dish MUST be evaluated as available if and only if: its lifecycle status is ACTIVE, the establishment is ACTIVE and within operating hours, and every ingredient in its recipe satisfies `CurrentStock(ingredient) >= RecipeQuantity(ingredient)`.
- **FR-014**: A dish where any required ingredient has `CurrentStock < RecipeQuantity` MUST be rendered greyed-out with a visible "Out of Stock" badge. Clicking the card opens the detail modal indicating the out-of-stock state. Stock availability presentation MUST be strictly binary (available vs. out-of-stock); internal kitchen ingredient levels and numeric portion counts MUST NOT be exposed to Diners.
- **FR-015**: A dish with lifecycle status other than ACTIVE (decommissioned, discontinued) MUST be completely hidden from the Diner-facing catalog.
- **FR-016**: When the stock of an ingredient changes (depletion or replenishment), the availability of all dependent dishes MUST be reflected in the Diner-facing catalog in real time within a maximum latency of 3 seconds without requiring manual page refresh.
- **FR-017**: When a shared ingredient drops to zero stock, all dishes whose recipes depend on that ingredient MUST be simultaneously invalidated (greyed-out with "Out of Stock" badge).
- **FR-018**: When stock for a previously depleted ingredient is replenished above recipe requirements, all dependent dishes MUST be automatically re-enabled upon the next catalog update.
- **FR-019**: A dish with no defined recipe (zero ingredient lines) MUST be treated as unavailable (out of stock) since preparability cannot be determined.
- **FR-020**: The catalog MUST provide an internal text search bar that filters the currently loaded menu in real time as the Diner types.
- **FR-021**: Dish search MUST match against both dish titles and ingredient names/descriptions. Matching MUST be case-insensitive and diacritic-insensitive (normalized by stripping diacritics, trimming whitespace, and converting to lowercase).
- **FR-022**: Categories with zero matching dishes during an active search MUST be dynamically hidden.
- **FR-023**: If the Diner is viewing a specific category when initiating search, results MUST be filtered within that category. If viewing all categories, results MUST be filtered across all categories.
- **FR-024**: Clearing the search input MUST immediately restore the complete catalog view without page reloads.
- **FR-025**: Search queries consisting exclusively of whitespace or invalid special characters MUST be ignored without altering the current catalog state.
- **FR-026**: Every catalog query, autocomplete result, category list, dish list, and stock evaluation MUST be strictly scoped to the tenant ID of the selected establishment. Zero cross-tenant data exposure is permitted.

### Key Entities

- **Establishment (Establecimiento/Sucursal)**: Represents a physical restaurant location. Key attributes: unique identifier (tenant ID), name, operational status (active, paused, disabled), operating hours schedule. This entity is assumed to exist from prior iterations; its administrative management is out of scope.
- **Dish (Plato)**: Culinary item visible in the catalog. Key attributes: identifier, tenant ID, name, description, price, category (from fixed list), optional photo, lifecycle status (ACTIVE for published dishes visible to Diners). The ACTIVE/published lifecycle state is assumed to be defined by a prior spec (005/006/007) that extends the dish state machine beyond DRAFT and PENDING_PRICE.
- **Recipe Ingredient Line (Línea de Receta)**: Association between a dish and an ingredient with a per-portion quantity. Established by specs 005/006 in the `dish_recipe_ingredients` table.
- **Ingredient (Ingrediente)**: Raw inventory item with current stock level. Key attributes: identifier, tenant ID, name, current stock, unit of measure, status. Established by spec 004.
- **Gastronomic Category**: Fixed closed set (Entradas, Platos principales, Postres, Bebidas, Ensaladas) defined in spec 005.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Diner can discover and select an establishment within 10 seconds of typing a valid search query.
- **SC-002**: 100% of catalog queries return only data scoped to the selected establishment's tenant. Zero cross-tenant data leakage occurs under any user interaction path.
- **SC-003**: A Diner browsing the catalog sees dish availability (in-stock or out-of-stock) that accurately reflects the current kitchen ingredient stock within a maximum latency of 3 seconds in real time.
- **SC-004**: When a shared ingredient's stock drops to zero, 100% of dependent dishes across all categories are simultaneously marked as out of stock within a real-time update window of <= 3 seconds.
- **SC-005**: 100% of decommissioned dishes are completely hidden from the Diner-facing catalog. Zero decommissioned dishes are ever visible.
- **SC-006**: 100% of categories with zero active dishes are hidden from the catalog view and navigation bar.
- **SC-007**: The in-catalog text search returns correct results for diacritic-insensitive and case-insensitive queries with 100% accuracy.
- **SC-008**: A Diner attempting to access a paused or disabled establishment is always blocked from viewing the catalog, with a clear informational notice displayed.
- **SC-009**: Image loading failures never block the display of dish names, prices, or descriptions. Placeholder images are displayed within 1 second of detecting a load failure.
- **SC-010**: Browsing outside operating hours presents the catalog in read-only mode with 100% of selection controls disabled.

---

## Assumptions

- The establishment entity and its administrative lifecycle states (active, paused, disabled) already exist from prior iterations. This spec does not model establishment CRUD or administrative management.
- The ingredient master catalog and live inventory tracking (with positive/zero stock adjustments) are fully operational as delivered by spec 004. Ingredient `currentStock` is the authoritative source for stock-driven availability calculations.
- The dish entity, its gastronomic category (fixed list from spec 005), recipe composition (dish_recipe_ingredients from specs 005/006), and dietary restriction labels are fully operational from prior iterations.
- A dish lifecycle state representing "published and visible to Diners" (e.g., ACTIVE or PUBLISHED) is defined by a prior spec that extends the state machine beyond DRAFT and PENDING_PRICE. This spec consumes that terminal state as a prerequisite. Dishes not in this published state are never visible to Diners.
- Operating hours data for establishments is available as a configuration attribute. If the operating hours model does not yet exist, this spec assumes it will be introduced as part of the planning phase or as a lightweight data extension. The administrative UI for configuring operating hours is out of scope.
- Authentication infrastructure (specs 001/002) and multi-tenant isolation mechanisms (spec 004) are fully operational. The Diner role does not require authentication to browse catalogs in this iteration (public access).
- Photo storage and retrieval for dish images are handled by existing infrastructure from spec 005. This spec only specifies Diner-facing display behavior and fallback on load failure.
- The real-time stock update mechanism (how changes are pushed or pulled to the Diner's view) is a planning-phase decision. This spec requires that stock changes are reflected without manual page refresh, but does not prescribe a specific technology (polling, SSE, WebSocket, etc.).
- Shopping cart (`CartContext`, `useCart`, `CartDrawer`), order creation, payment, and checkout workflows are explicitly OUT OF SCOPE for this specification and for this sprint. No shopping cart exists in this iteration; this feature is strictly limited to establishment discovery, categorized menu browsing, and real-time stock-based availability exploration.
- Direct catalog access via external deep links or table QR code scanning is OUT OF SCOPE for this specification and corresponds to an independent future user story. In this iteration, catalog access originates exclusively through the establishment discovery and selection flow (COM-1.5.1).
