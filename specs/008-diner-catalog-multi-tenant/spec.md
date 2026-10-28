# Feature Specification: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Feature Branch**: `008-diner-catalog-multi-tenant`

**Created**: 26/10/2026 | **Last Updated**: 28/10/2026

**Status**: Draft (Audited & Aligned with User Stories & Project Scope)

**Input**: User description: "Multi-Tenant Establishment Discovery, Real-Time Gastronomic Catalog, and Stock-Aware Menu Browsing — COM-1.5.1, COM-4.1.1, COM-4.1.2, COM-4.2.3"

---

## Clarifications

### Session 2026-10-28 (Audit & Grill-Me Alignment with User Stories & Scope)
- Q: ¿Cómo gestiona el buscador de autocompletado los establecimientos pausados o inhabilitados? (COM-1.5.1 CA 4, FR-003, FR-005) → A: El autocompletado excluye estrictamente los locales inactivos (preservando FR-003). COM-1.5.1 CA 4 y FR-005 se alinean: los locales pausados o inhabilitados no aparecen en los resultados del autocompletado. Si un local pasa a estado inactivo mientras un comensal navega o ante un intento de acceso directo/reconexión, el sistema despliega inmediatamente la pantalla de bloqueo temporal ("Este establecimiento se encuentra temporalmente inactivo y no está recibiendo pedidos") impidiendo cualquier visualización o pedido.
- Q: ¿Cómo se visualiza el catálogo cuando el establecimiento está fuera de su horario operativo? (COM-4.1.1 CA 5, COM-4.1.2 CA 6, FR-012, FR-013) → A: Se desacopla completamente el horario comercial de la disponibilidad culinaria de stock. Fuera de horario, el catálogo se presenta en modo lectura conservando fotos, nombres y precios a color, con un banner superior informativo indicando el horario de apertura. El estado atenuado en gris y el distintivo "Agotado" se reserva única y exclusivamente para platos cuyos ingredientes de receta no alcanzan el stock mínimo en cocina, respetando la coherencia con futuras reservas diferidas (Sprint 3).
- Q: ¿Cómo se tratan los criterios de aceptación que involucran Carrito de Compras (COM-1.5.1 CA 5, COM-4.1.2 CA 4, COM-4.1.2 CA 5, COM-4.2.3 CA 6)? → A: Se formaliza la sección de "Delimitación de Criterios por Sprint". Dado que `CartContext` ya existe en el frontend desde la spec 003 con persistencia en sesión, se incorpora `COM-1.5.1 CA 5` en el Sprint 2 (FR-029) mediante un modal interactivo de confirmación (`EstablishmentSwitchModal`) que alerta al comensal antes de vaciar su carrito al cambiar de establecimiento. En cambio, el módulo integral de Carrito de Compras (`COM-6.1.1`), la reserva concurrente de cupos (`COM-6.2.3`) y los filtros manuales de precio (`COM-4.2.2`) corresponden al Sprint 3; y la validación concurrente con remoción de stock en Checkout (`COM-7.2.1`, `CHF-5.1.1`) corresponde al Sprint 4.
- Q: ¿Cuál es el alcance de la búsqueda por cercanía de COM-1.5.1? → A: En el MVP, la búsqueda es exclusivamente textual por nombre en base de datos (5 SP, debouncing y consulta indexada). La geolocalización o proximidad física por GPS queda formalmente fuera de alcance para el MVP.
- Q: ¿Cómo se asegura la pureza funcional de la especificación frente a detalles técnicos? → A: Se eliminan las referencias a tablas SQL, migraciones Flyway y endpoints HTTP en `spec.md`, redactándolas como conceptos de negocio y contratos de comportamiento, reservando los detalles de implementación para `plan.md`, `data-model.md` y `contracts/openapi.yaml`.
- Q: ¿Cómo debe comportarse la interfaz del comensal si las consultas periódicas de disponibilidad en tiempo real fallan por pérdida momentánea de conexión a internet? → A: Degradación elegante: el catálogo mantiene visible su último estado conocido sin bloquear la pantalla y reintenta silenciosamente en el siguiente ciclo. Si se acumulan 3 fallos consecutivos, despliega un aviso discreto ("Sin conexión con la cocina - reintentando...") que se retira automáticamente al restablecer la conexión.

### Session 2026-10-27 (Grill-Me Alignment with Specs 005, 006 & 007)
- Q: ¿Qué estado del ciclo de vida del plato determina la visibilidad en el catálogo del comensal? (FR-009, FR-013, FR-015) → A: El estado aprobado es `PUBLISHED`, introducido por la spec 007 (`status = 'PUBLISHED' AND is_active = true`). Platos en `DRAFT` o `PENDING_PRICE` son estrictamente invisibles para el comensal. Spec 008 no define estados propios en la máquina de estados culinaria.
- Q: ¿Cuál es el origen de datos del precio del plato visible al comensal? (FR-007) → A: El precio proviene del PVP comercial fijado y aprobado por el Administrador en la spec 007. El contrato de catálogo expone este precio unitario de venta.
- Q: ¿Cómo se gestionan las fotos de los platos en el catálogo? (FR-007, FR-011) → A: Las fotos pertenecen al expediente culinario establecido por la spec 005. Si el plato no posee foto o falla su carga por mala conexión, el catálogo despliega inmediatamente el placeholder ligero predeterminado.
- Q: ¿Quién es el dueño de la relación de ingredientes de receta y cómo se evalúa el stock? (FR-013, FR-016) → A: La composición de receta pertenece a la spec 005. Spec 008 no crea ni modifica recetas, sino que las consulta en conjunto con el inventario de ingredientes (spec 004) para derivar la disponibilidad binaria en tiempo real (`isAvailable`).
- Q: ¿Cómo se integran las restricciones dietarias y alérgenos de la spec 003 en el catálogo de 008? → A: Cada plato en el catálogo categorizado incluye los indicadores de conformidad y restricciones en conflicto evaluados contra el perfil del comensal autenticado, asegurando continuidad con la seguridad alimentaria de la spec 003.
- Q: ¿Quién es el dueño de la entidad de establecimientos? → A: Spec 008 es la dueña exclusiva de la entidad de establecimientos (identificador, nombre comercial, estado operativo, horario de apertura, horario de cierre, zona horaria) para el descubrimiento y contexto multi-tenant.
- Q: ¿El comensal puede explorar catálogos de forma pública o debe estar autenticado? → A: El acceso no es público; el comensal debe estar obligatoriamente registrado e iniciar sesión con rol DINER (COMENSAL) para usar el buscador y ver los catálogos.

### Session 2026-10-26
- Q: ¿Qué acción concreta ejecutan los controles de selección de platos en el catálogo y cuál es el alcance del carrito? (FR-012) → A: El carrito de compras no está contemplado en esta especificación ni en este sprint (pedidos y compra quedan diferidos a Sprints 3 y 4). La interacción sobre los platos consiste en la exploración visual y la apertura del modal informativo de detalle (foto de alta resolución, descripción, precio, sellos dietarios e ingredientes de la receta).
- Q: ¿Cuál es el umbral máximo de latencia aceptable para que un cambio de stock se refleje en la disponibilidad del catálogo del comensal? (SC-004, FR-016) → A: Máximo 3 segundos desde que el cambio de stock impacta en el backend hasta que la disponibilidad de los platos dependientes se actualiza visualmente en la pantalla del comensal.
- Q: ¿Cómo debe presentarse la disponibilidad de stock al comensal en el catálogo? (FR-013, FR-014) → A: La disponibilidad se presenta de forma estrictamente binaria (plato a color con controles habilitados vs. plato atenuado en escala de grises con badge "Agotado"). No se exponen conteos numéricos de porciones ni cantidades de stock de cocina por razones de confidencialidad comercial y simplicidad de interfaz.
- Q: ¿Cómo se gestiona el cambio de establecimiento si no hay carrito en este sprint? (FR-006) → A: Al no existir carrito de compras en este sprint, cambiar de establecimiento descarga limpiamente los datos del catálogo y stock del establecimiento anterior y carga el catálogo del nuevo establecimiento seleccionado.
- Q: ¿Debe soportarse el acceso directo al catálogo mediante URL o escaneo de código QR? → A: El acceso directo por enlace externo o escaneo de código QR queda explícitamente FUERA DE ALCANCE para esta especificación y corresponde a una historia de usuario independiente. El acceso al catálogo se origina a través del flujo de descubrimiento y selección en la barra central de búsqueda (COM-1.5.1).

---

## Sprint Scope Delimitation Matrix

Para garantizar el cumplimiento de los Principios I, V y VII de la Constitución del proyecto, la siguiente matriz explicita la cobertura de los 24 criterios de aceptación de las historias de usuario de este módulo, desglosando qué aspectos se entregan en el presente Sprint 2 (Spec 008) y cuáles se difieren formalmente a sprints posteriores según el Roadmap oficial del MVP:

| Historia de Usuario | Criterio de Aceptación | Sprint Asignado | Tratamiento y Justificación en Spec 008 |
|---|---|:---:|---|
| **COM-1.5.1** | CA 1: Autocompletado >= 3 caracteres | **Sprint 2** | Satisfecho por FR-001 y FR-002 (Escenarios 1 y 2). |
| **COM-1.5.1** | CA 2: Clic cambia tenant y carga catálogo vivo | **Sprint 2** | Satisfecho por FR-004 (Escenario 4). |
| **COM-1.5.1** | CA 3: Búsqueda sin coincidencias / cercanía | **Sprint 2** | Búsqueda por nombre sin coincidencias satisfecha (FR-006). Cercanía por GPS/geolocalización excluida del MVP (Supuestos). |
| **COM-1.5.1** | CA 4: Local pausado / inhabilitado | **Sprint 2** | Satisfecho por FR-003 y FR-005: buscador excluye inactivos; transición concurrente o acceso directo despliega aviso de inactividad temporal y bloquea pedidos. |
| **COM-1.5.1** | CA 5: Alerta vaciado de carrito al cambiar local | **Sprint 2** | Satisfecho por FR-029: modal interactivo de confirmación (`EstablishmentSwitchModal`) al cambiar de local con ítems en el carrito de sesión (Escenario 8 y 12). |
| **COM-1.5.1** | CA 6: Espacios en blanco o vacío ignorados | **Sprint 2** | Satisfecho por FR-001 y FR-002 (Escenario 7). |
| **COM-4.1.1** | CA 1: Categorías, fotos HD, nombres y PVP | **Sprint 2** | Satisfecho por FR-007 (Escenario 1). 5 categorías gastronómicas cerradas. |
| **COM-4.1.1** | CA 2: Ocultar categoría vacía o dada de baja | **Sprint 2** | Satisfecho por FR-009 (Escenario 2). Categorías sin platos en estado `PUBLISHED` se ocultan. |
| **COM-4.1.1** | CA 3: Búsqueda interna filtra en tiempo real | **Sprint 2** | Satisfecho por FR-020 (Escenario 1 de US 4). |
| **COM-4.1.1** | CA 4: Imagen liviana por defecto (fallback) | **Sprint 2** | Satisfecho por FR-011 y SC-009 (Escenario 5). Nombre y precio nunca se bloquean. |
| **COM-4.1.1** | CA 5: Fuera de horario en modo lectura | **Sprint 2** | Satisfecho por FR-012 (Escenario 6). Catálogo a color en modo lectura con banner de horario; controles de compra desactivados. |
| **COM-4.1.1** | CA 6: Desplazamiento automático por categoría | **Sprint 2** | Satisfecho por FR-008 (Escenario 4). Sticky bar con scroll automático. |
| **COM-4.1.2** | CA 1: Insumo en cero muestra plato en gris | **Sprint 2** | Satisfecho por FR-013 y FR-014 (Escenarios 1 y 2). Badge "Agotado" y botón deshabilitado. |
| **COM-4.1.2** | CA 2: Reposición de cocina rehabilita producto | **Sprint 2** | Satisfecho por FR-016 y FR-018 (Escenario 6). Actualización en tiempo real <= 3s. |
| **COM-4.1.2** | CA 3: Agotamiento concurrente de insumo compartido | **Sprint 2** | Satisfecho por FR-016, FR-017 y SC-004 (Escenario 5). Invalidación simultánea <= 3s. |
| **COM-4.1.2** | CA 4: Remoción en checkout por stock agotado | **Sprint 4** | Diferido a Sprint 4 con la Pasarela de Pagos y Descuento Transaccional ACID de Stock (`COM-7.2.1`, `CHF-5.1.1`). |
| **COM-4.1.2** | CA 5: Reserva simultánea de cupo en carrito | **Sprint 3** | Diferido a Sprint 3 con la gestión de Carrito y Franjas Horarias (`COM-6.1.1`, `COM-6.2.3`). |
| **COM-4.1.2** | CA 6: Disponibilidad por stock y no por horario | **Sprint 2** | Satisfecho por FR-013: stock de receta gobierna disponibilidad independientemente de horarios. |
| **COM-4.2.3** | CA 1: Búsqueda en tiempo real por caracteres válidos | **Sprint 2** | Satisfecho por FR-020 (Escenario 1 de US 4). |
| **COM-4.2.3** | CA 2: Búsqueda en título y descripción/ingredientes | **Sprint 2** | Satisfecho por FR-021 (Escenarios 1 a 3 de US 4). |
| **COM-4.2.3** | CA 3: Mensaje sin resultados y sugerencia ortográfica | **Sprint 2** | Satisfecho por FR-024 y Escenario 6 de US 4. |
| **COM-4.2.3** | CA 4: Normalización sin tildes e insensible a mayúsculas | **Sprint 2** | Satisfecho por FR-021 y SC-007 (Escenarios 2 y 3). |
| **COM-4.2.3** | CA 5: Espacios o caracteres especiales ignorados | **Sprint 2** | Satisfecho por FR-025 (Escenario 8 de US 4). |
| **COM-4.2.3** | CA 6: Búsqueda sobre filtros manuales previos | **Sprint 2 / 3** | Búsqueda sobre filtro de categoría satisfecha en Sprint 2 (FR-023). Combinación con filtros manuales de precio diferida a Sprint 3 (`COM-4.2.2`). |

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Multi-Tenant Establishment Discovery & Selection (Priority: P1)

**COM-1.5.1**: As a Diner, I search for a restaurant by typing into a central search bar, so that I can discover available establishments and select one to explore its live menu.

The authenticated Diner (with role DINER) accesses the establishment discovery screen and begins typing into a central search bar. Once at least 3 valid characters are entered (after trimming whitespace), the system presents an autocomplete dropdown of matching establishments. Matching is case-insensitive and diacritic-insensitive (e.g., "cafe" matches "Café Central"). Each autocomplete result displays the establishment's commercial name. The autocomplete search strictly excludes paused or disabled establishments. Selecting an active and open establishment redirects the Diner to that establishment's live catalog. If an establishment is paused or disabled while an active Diner is viewing it, or if accessed directly, the system presents a full-page blocking notice indicating temporary closure and preventing menu access, with an action to return to search. Selecting a different establishment cleanly unloads the current context and loads the newly chosen establishment's catalog and inventory data.

**Why this priority**: Establishment selection is the entry point for every subsequent user story. Without selecting an establishment, the Diner cannot browse a catalog, see stock availability, or search for dishes.

**Independent Test**: Can be fully tested by typing a search query, verifying autocomplete results appear after 3 characters, selecting an active establishment, and confirming the catalog loads for that specific tenant.

**Acceptance Scenarios**:

1. **Given** a Diner on the discovery screen, **When** they type fewer than 3 characters in the search bar, **Then** no autocomplete results are displayed and the current screen state is preserved.
2. **Given** a Diner typing 3 or more valid characters, **When** the search term matches one or more active establishments by name, **Then** the system displays an autocomplete dropdown listing all matching active establishments.
3. **Given** a Diner typing a query with mixed-case or accented characters (e.g., "cafe"), **When** an establishment name contains diacritics or different casing (e.g., "Café Central"), **Then** the match is found and displayed in the autocomplete results.
4. **Given** a Diner selecting an active and open establishment from the autocomplete, **When** the selection is confirmed, **Then** the system sets the multi-tenant context to the selected establishment and loads its live catalog.
5. **Given** an establishment that transitions to paused or disabled by an administrator, **When** a Diner accesses or is actively viewing it, **Then** the system displays a full-page blocking notice ("This establishment is temporarily inactive and is not accepting orders") without exposing any catalog data, and provides an action to return to the search screen.
6. **Given** a Diner typing a query that matches zero establishments, **When** the search evaluates, **Then** the system displays an informative message ("No establishments found. Try modifying your search terms.") without error screens.
7. **Given** a Diner typing a query consisting exclusively of whitespace or special characters, **When** the input is evaluated, **Then** the system ignores the query without triggering autocomplete or altering the screen state.
8. **Given** a Diner currently browsing Establishment A's catalog, **When** they navigate back to search and select Establishment B, **Then** the system cleanly unloads Establishment A's data and loads Establishment B's catalog scoped exclusively to Establishment B's tenant.
9. **Given** an unauthenticated client, **When** they attempt to access the establishment discovery screen or invoke the search autocomplete endpoint, **Then** the backend rejects the request with HTTP 401 Unauthorized, zero establishment data is exposed, and the frontend redirects the client to the login screen.
10. **Given** two establishments (Tenant A and Tenant B) operating in parallel with homonymous dishes (e.g. "Hamburguesa Clásica") and homonymous ingredients (e.g. "Carne"), **When** a Diner accesses Tenant A where "Carne" stock is 0, and another Diner accesses Tenant B where "Carne" stock is 50, **Then** Tenant A displays the dish as "Agotado" and Tenant B displays the dish as fully available, verifying strict multi-tenant catalog and stock isolation with zero cross-tenant data bleed.
11. **Given** a request to catalog or availability endpoints with an invalid or non-existent tenant identifier, **When** the backend evaluates the query, **Then** it returns HTTP 404 Not Found with RFC 7807 ProblemDetail and zero catalog or establishment data.
12. **Given** a Diner browsing Establishment A with items in their active session cart, **When** they attempt to select Establishment B in the search bar, **Then** the system presents an interactive confirmation modal warning that switching establishments will empty their current cart; if the Diner cancels, they remain in Establishment A with cart intact; if they confirm, the cart is emptied and Establishment B's catalog loads.

---

### User Story 2 — Real-Time Gastronomic Catalog Exploration (Priority: P1)

**COM-4.1.1**: As a Diner, I browse the menu of the selected establishment organized into gastronomic categories with high-resolution photos, names, and current prices, so that I can explore the restaurant's offering and identify dishes I want.

After selecting an active establishment, the authenticated Diner views the catalog organized by gastronomic categories (Entradas, Platos principales, Postres, Bebidas, Ensaladas). Each dish card displays its name, approved retail price (PVP from spec 007), dietary tags/compliance (from spec 003), optional description, and optional photo (served from dish multimedia assets per spec 005). A sticky category navigation bar allows smooth jumping directly to any section. Categories that contain zero published dishes (all dishes in non-PUBLISHED status like DRAFT or PENDING_PRICE) are automatically hidden from the view and the category bar. If a photo is absent or fails to load, the interface displays a lightweight default placeholder immediately so that dish names, descriptions, and prices remain fully legible. When the establishment is outside its scheduled operating hours, the catalog is presented in read-only mode: Diners can freely browse categories, photos, and prices in full color, but all dish selection controls are disabled, accompanied by an informational banner indicating the restaurant's opening schedule.

**Why this priority**: The catalog is the core value proposition for Diners. Without it, there is no menu to browse, no prices to see, and no stock availability to evaluate.

**Independent Test**: Can be tested by selecting an establishment with published dishes across multiple categories, verifying dishes are grouped correctly, categories with zero published dishes are hidden, and the sticky bar navigates to the correct section.

**Acceptance Scenarios**:

1. **Given** a Diner viewing the catalog of an active establishment, **When** the catalog loads, **Then** dishes are grouped under their gastronomic category headings (Entradas, Platos principales, Postres, Bebidas, Ensaladas) and each dish displays its name, approved retail price (PVP), dietary tags, optional description, and optional photo.
2. **Given** a category where 100% of its dishes have a lifecycle status other than PUBLISHED (e.g., DRAFT or PENDING_PRICE), **When** the catalog renders, **Then** that category is completely hidden from the catalog view and from the category navigation bar.
3. **Given** a category where all its published dishes are currently out of stock, **When** the catalog renders, **Then** the category remains visible, displaying its greyed-out dishes, so Diners can see the establishment's full offering.
4. **Given** a Diner using the sticky category bar, **When** they tap a category name, **Then** the view scrolls smoothly to that category's section.
5. **Given** a slow network connection or missing image, **When** a dish photo fails to load or is not present, **Then** the interface immediately displays a lightweight placeholder image without breaking layout, and the dish name, description, and price remain fully visible and legible.
6. **Given** an establishment currently outside its scheduled operating hours, **When** a Diner views its catalog, **Then** the catalog renders in read-only mode in full color with an informational banner displayed indicating the restaurant's opening schedule, and clicking any dish card opens the detail modal displaying the outside-hours notice without labelling dishes as out of stock.
7. **Given** an establishment within its operating hours and an available dish, **When** a Diner views its catalog, **Then** the dish card is displayed in full color indicating available status, and clicking the card opens an informational dish detail modal showing photo, description, retail price, dietary tags, and recipe ingredients.
8. **Given** an unauthenticated client, **When** they attempt to load or view an establishment's catalog, **Then** the request is rejected with HTTP 401 Unauthorized, zero catalog or dish data is exposed, and the frontend redirects the client to the login screen.

---

### User Story 3 — Dynamic Stock-Based Dish Availability & Real-Time Invalidation (Priority: P1)

**COM-4.1.2**: As a Diner, I see which dishes are currently available and which are out of stock based on real-time kitchen ingredient inventory, so that I can make informed selections without encountering unavailable items.

Each dish in the catalog is evaluated against its recipe's ingredient requirements (composition from spec 005) and the establishment's current ingredient stock (inventory from spec 004). A dish is available if and only if every ingredient in its recipe has sufficient current stock to prepare at least one portion. Available dishes are rendered in full color with an active availability status. Dishes where any required ingredient has insufficient stock are rendered greyed-out with a visible "Out of Stock" (Agotado) badge. Clicking any dish card (available or out-of-stock) opens the informational detail modal showing ingredients, dietary compliance, and availability state. Dishes not in PUBLISHED status are completely hidden from the catalog. When kitchen personnel update stock (adding ingredients or recording depletion), the changes are reflected in the Diner-facing catalog in real time without requiring manual page refresh. When a shared critical ingredient drops to zero, all dependent dishes across all categories are simultaneously invalidated.

**Why this priority**: Stock-driven availability is a core differentiator of EasyEat. Without it, Diners would select dishes that cannot be prepared, leading to failed orders and frustration.

**Independent Test**: Can be tested by creating an establishment with dishes whose recipes share ingredients, depleting a shared ingredient's stock to zero, and verifying that all dependent dishes are simultaneously greyed-out with the "Out of Stock" badge.

**Acceptance Scenarios**:

1. **Given** a dish whose recipe requires ingredients A (quantity: 2) and B (quantity: 1), and the establishment has stock A=5 and B=3, **When** the catalog loads, **Then** the dish is displayed in full color as available, and clicking the card opens the detail modal showing available status and recipe ingredients.
2. **Given** the same dish, and the establishment has stock A=1 (less than recipe requirement of 2), **When** the catalog loads, **Then** the dish is rendered greyed-out with an "Out of Stock" badge, and clicking the card opens the detail modal indicating the out-of-stock status.
3. **Given** a dish whose lifecycle status is not PUBLISHED (e.g. DRAFT or PENDING_PRICE), **When** the catalog loads, **Then** the dish is completely hidden from the catalog view.
4. **Given** two dishes sharing ingredient X (Dish A requires 1 unit, Dish B requires 2 units), and stock of X is 1, **When** the catalog evaluates availability, **Then** Dish A is available and Dish B is out of stock.
5. **Given** ingredient X with stock = 1 shared by Dish A (requires 1) and Dish B (requires 1), **When** stock of X drops to 0 (e.g., consumed by kitchen or sale), **Then** both Dish A and Dish B are simultaneously invalidated (greyed-out, "Out of Stock" badge displayed).
6. **Given** a dish currently showing as out of stock because ingredient Y has stock = 0, **When** kitchen personnel add stock for ingredient Y bringing it above the recipe requirement, **Then** the dish is automatically re-enabled (shown in full color with active selection controls) upon the next catalog update.
7. **Given** a dish with no recipe defined (zero ingredients), **When** the catalog evaluates availability, **Then** the dish is treated safely as unavailable (out of stock) without triggering internal server errors (500), since preparability cannot be determined.
8. **Given** a dish requiring a fractional quantity of an ingredient (e.g., 0.250 kg of meat), **When** the kitchen inventory stock is exactly equal to the recipe requirement (0.250 kg), **Then** the dish is evaluated as available (exact boundary condition).
9. **Given** the same dish requiring 0.250 kg of meat, **When** the kitchen inventory stock has a fractional shortfall (e.g., 0.249 kg), **Then** the dish is evaluated as out of stock ("Agotado") using precise decimal arithmetic without floating-point rounding errors.
10. **Given** an ingredient in inventory that registers a negative stock value due to an operational inventory adjustment discrepancy (e.g., -1 unit), **When** the catalog evaluates availability for dishes depending on it, **Then** the system handles the negative value gracefully, evaluates the dish as out of stock ("Agotado"), and does not crash or throw exceptions.
11. **Given** a dish whose recipe includes an ingredient that has been deactivated or decommissioned in kitchen inventory, **When** the catalog evaluates availability, **Then** the dish is treated safely as out of stock ("Agotado") without internal server errors.
12. **Given** any authenticated Diner browsing the catalog or requesting availability, **When** the system generates the catalog response, **Then** the payload contains strictly binary availability (`isAvailable: true/false`) and zero numeric stock levels or ingredient portion counts are exposed to the client.

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
6. **Given** a search query that matches zero dishes, **When** the search evaluates, **Then** the system displays a clear message ("No dishes found. Try adjusting your search or checking spelling.") and all categories are hidden.
7. **Given** a Diner with an active search filter, **When** they clear the search input, **Then** the complete catalog is immediately restored (all categories and dishes re-appear) without a page reload.
8. **Given** a Diner typing a query consisting exclusively of whitespace or invalid special characters, **When** the input is evaluated, **Then** the system ignores the query and the catalog state remains unchanged.

---

### Edge Cases

- **Fewer than 3 characters in establishment search**: Autocomplete is not triggered; current state is preserved.
- **Establishment search with only spaces or special characters**: Input is trimmed/sanitized; autocomplete is not triggered.
- **Shared critical ingredient drops to zero**: All dishes using that ingredient across all categories update to out-of-stock simultaneously.
- **Kitchen staff adds stock for a previously depleted ingredient**: All dependent dishes automatically become available upon the next catalog update.
- **Category where 100% of active dishes are out of stock**: The category remains visible, displaying its greyed-out dishes.
- **Category where all dishes are decommissioned or non-published**: The entire category is hidden from the catalog and the navigation bar.
- **Switching establishments via search**: The previous establishment's data is cleanly unloaded and the new establishment's catalog is loaded, scoped strictly to the new tenant.
- **Slow network / image loading timeout**: Placeholder image is shown immediately; text content (name, price, description) is never blocked.
- **Browsing outside operating hours**: Catalog visible in read-only mode in full color; selection controls disabled; informational schedule banner displayed. Dishes with inventory stock are NOT marked as out of stock.
- **Dish with no recipe (zero ingredients)**: Treated as unavailable since preparability cannot be determined, handled safely without server errors.
- **Exact decimal stock boundary**: When current stock exactly matches recipe quantity (`CurrentStock == RecipeQuantity`), the dish is evaluated as available.
- **Sub-minimal decimal stock shortfall**: When current stock is less than recipe requirement by any fractional amount (e.g. 0.001 units), the dish is evaluated as out of stock without decimal precision errors.
- **Negative stock in inventory**: Handled safely as out of stock without throwing runtime exceptions.
- **Decommissioned or inactive recipe ingredient**: Treated as out of stock without crashing catalog rendering.
- **Cross-tenant homonymous dishes and ingredients**: Two establishments with identically named dishes and ingredients operate completely independently; inventory depletion in Tenant A has zero effect on Tenant B.
- **Invalid or non-existent tenant ID**: Endpoint rejects the request with HTTP 404 ProblemDetail with zero data leaked.
- **Concurrent stock depletion**: If a dish's last available portion is consumed while a Diner is viewing it, the dish transitions to greyed-out with "Out of Stock" badge on the next real-time update.
- **Unauthenticated client accessing discovery or catalog**: The backend rejects the request with HTTP 401 Unauthorized; the frontend intercepts the response and redirects the user to `/login` with zero establishment, category, dish, or stock data exposed.
- **Session token expiration during active browsing**: If the Diner's session token expires while exploring a catalog or during real-time updates, subsequent requests immediately return 401 Unauthorized, terminating the view and redirecting to `/login`.
- **Intermittent network loss during real-time availability updates**: The catalog gracefully degrades by retaining the last-known availability state without blocking the UI, continuing background retries; upon 3 consecutive failures, a non-intrusive notification banner ("Sin conexión con la cocina - reintentando...") is displayed until connection is restored.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an establishment search bar that triggers autocomplete only after the Diner types 3 or more valid characters (after trimming whitespace). Queries shorter than 3 valid characters MUST NOT trigger autocomplete.
- **FR-002**: Establishment autocomplete matching MUST be case-insensitive and diacritic-insensitive. The search MUST safely handle whitespace-only and special-character-only inputs by ignoring them.
- **FR-003**: The autocomplete results MUST display the establishment commercial name and MUST be strictly scoped to active establishments. Paused or disabled establishments MUST NOT be returned in autocomplete search results.
- **FR-004**: When a Diner selects an active establishment, the system MUST set the multi-tenant session context to that establishment's tenant identifier and load its live catalog.
- **FR-005**: If an establishment transitions to paused or disabled by an administrator while a Diner is active, or if accessed directly, the system MUST display a full-page blocking notice ("This establishment is temporarily inactive and is not accepting orders") without exposing catalog data, providing a return action to the search screen.
- **FR-006**: When a Diner switches from one establishment to another, the system MUST cleanly replace the previous tenant's data with the newly selected tenant's catalog and stock data.
- **FR-007**: The catalog MUST display published dishes grouped by gastronomic category (Entradas, Platos principales, Postres, Bebidas, Ensaladas). Each dish card MUST show: dish name, approved retail price (PVP from spec 007), dietary compliance tags (from spec 003), optional description, optional photo (with lightweight fallback), availability indicator, and allow clicking the card to open an informational dish detail modal.
- **FR-008**: The catalog MUST provide a sticky category navigation bar that allows smooth scrolling to any visible category section.
- **FR-009**: Categories where 100% of their dishes have a lifecycle status other than PUBLISHED (e.g., in DRAFT or PENDING_PRICE) MUST be completely hidden from the catalog view and the category navigation bar.
- **FR-010**: Categories where all published dishes are currently out of stock MUST remain visible, displaying the greyed-out dishes so the Diner can see the restaurant's full offering.
- **FR-011**: When a dish has no photo or when its high-resolution image fails to load, the system MUST immediately display a lightweight default placeholder image without breaking layout or obscuring text (dish name, retail price, description).
- **FR-012**: When the establishment is outside its scheduled operating hours, the catalog MUST be presented in read-only mode in full color: an informational banner MUST be displayed indicating the restaurant's opening schedule, and clicking any dish card opens the detail modal displaying the outside-hours notice without labelling dishes as out of stock.
- **FR-013**: A dish MUST be evaluated as available by stock if and only if: its lifecycle status is PUBLISHED and active, and every ingredient in its recipe satisfies `CurrentStock(ingredient) >= RecipeQuantity(ingredient)`. Operating hours schedule MUST NOT alter this culinary stock calculation.
- **FR-014**: A dish where any required ingredient has `CurrentStock < RecipeQuantity` MUST be rendered greyed-out with a visible "Out of Stock" (Agotado) badge. Clicking the card opens the detail modal indicating the out-of-stock state. Stock availability presentation MUST be strictly binary (available vs. out-of-stock); internal kitchen ingredient levels and numeric portion counts MUST NOT be exposed to Diners.
- **FR-015**: A dish with lifecycle status other than PUBLISHED (DRAFT, PENDING_PRICE) MUST be completely hidden from the Diner-facing catalog.
- **FR-016**: When the stock of an ingredient changes (depletion or replenishment), the availability of all dependent dishes MUST be reflected in the Diner-facing catalog in real time within a maximum latency of 3 seconds without requiring manual page refresh.
- **FR-017**: When a shared ingredient drops to zero stock, all dishes whose recipes depend on that ingredient MUST be simultaneously invalidated (greyed-out with "Out of Stock" badge).
- **FR-018**: When stock for a previously depleted ingredient is replenished above recipe requirements, all dependent dishes MUST be automatically re-enabled upon the next catalog update.
- **FR-019**: A dish with no defined recipe (zero ingredient lines) MUST be treated as unavailable (out of stock) since preparability cannot be determined.
- **FR-020**: The catalog MUST provide an internal text search bar that filters the currently loaded menu in real time as the Diner types.
- **FR-021**: Dish search MUST match against both dish titles and ingredient names/descriptions. Matching MUST be case-insensitive and diacritic-insensitive (normalized by stripping diacritics, trimming whitespace, and converting to lowercase).
- **FR-022**: Categories with zero matching dishes during an active search MUST be dynamically hidden.
- **FR-023**: If the Diner is viewing a specific category when initiating search, results MUST be filtered within that category. If viewing all categories, results MUST be filtered across all categories.
- **FR-024**: Clearing the search input MUST immediately restore the complete catalog view without page reloads. If a search query yields zero matches, an informative message MUST be displayed suggesting to adjust terms or check spelling.
- **FR-025**: Search queries consisting exclusively of whitespace or invalid special characters MUST be ignored without altering the current catalog state.
- **FR-026**: Every catalog query, autocomplete result, category list, dish list, and stock evaluation MUST be strictly scoped to the tenant identifier of the selected establishment. Zero cross-tenant data exposure is permitted.
- **FR-027**: The system MUST require user authentication with the DINER (COMENSAL) role for all establishment discovery and catalog viewing operations. Unauthenticated access attempts MUST be rejected with HTTP 401 Unauthorized, with zero data exposure to unauthenticated clients. In the frontend, unauthenticated clients MUST be redirected to the login screen.
- **FR-028**: If periodic availability updates fail due to network interruptions, the system MUST gracefully preserve the last known catalog availability state without blocking user browsing. If 3 consecutive update attempts fail, the interface MUST display a non-intrusive warning notice indicating connection retry status, clearing the notice immediately upon successful reconnection.
- **FR-029**: When a Diner attempts to switch from an active establishment to another while holding items in their active session cart (`CartContext`), the system MUST display an interactive confirmation modal warning that switching establishments will empty their current cart. If the Diner confirms, the system MUST empty the cart, clear previous tenant data, and load the new establishment's catalog; if the Diner cancels, the previous establishment context and cart items MUST remain intact.

---

## Key Entities

- **Establishment (Establecimiento/Sucursal)**: Represents a physical restaurant branch. Key attributes: unique tenant identifier, commercial name, operational status (active, paused, disabled), operating hours schedule (opening time, closing time, timezone).
- **Dish (Plato)**: Culinary item visible in the catalog. Key attributes: identifier, tenant identifier, commercial name, description, approved retail price (PVP from spec 007), gastronomic category, photo asset reference, lifecycle status (only `PUBLISHED` dishes with active flag are visible to Diners). Dishes with status `DRAFT` or `PENDING_PRICE` are strictly excluded.
- **Recipe Composition (Composición de Receta)**: Association between a dish and required kitchen ingredients with per-portion quantities. Consumed by the catalog engine to calculate real-time availability against inventory.
- **Ingredient (Ingrediente)**: Kitchen raw material with current physical stock level. Consumed by the catalog engine to derive dish preparability.
- **Gastronomic Category**: Closed classification set (Entradas, Platos principales, Postres, Bebidas, Ensaladas) established in the culinary master model.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Diner can discover and select an establishment within 10 seconds of typing a valid search query.
- **SC-002**: 100% of catalog queries return only data scoped to the selected establishment's tenant. Zero cross-tenant data leakage occurs under any user interaction path.
- **SC-003**: A Diner browsing the catalog sees dish availability (in-stock or out-of-stock) that accurately reflects the current kitchen ingredient stock within a maximum latency of 3 seconds in real time.
- **SC-004**: When a shared ingredient's stock drops to zero, 100% of dependent dishes across all categories are simultaneously marked as out of stock within a real-time update window of <= 3 seconds.
- **SC-005**: 100% of non-published dishes (DRAFT, PENDING_PRICE) are completely hidden from the Diner-facing catalog. Zero non-published dishes are ever visible.
- **SC-006**: 100% of categories with zero published dishes are hidden from the catalog view and navigation bar.
- **SC-007**: The in-catalog text search returns correct results for diacritic-insensitive and case-insensitive queries with 100% accuracy.
- **SC-008**: A Diner attempting to access a paused or disabled establishment is always blocked from viewing the catalog, with a clear informational notice displayed.
- **SC-009**: Image loading failures never block the display of dish names, prices, or descriptions. Placeholder images are displayed within 1 second of detecting a load failure.
- **SC-010**: Browsing outside operating hours presents the catalog in read-only mode in full color with 100% of selection controls disabled, without incorrectly marking dishes as out of stock.
- **SC-011**: 100% of unauthenticated access attempts to establishment discovery and catalog endpoints result in an HTTP 401 Unauthorized status, with zero establishment, category, dish, or stock data exposure to unauthenticated clients.

---

## Assumptions

- Establishment discovery in the MVP is strictly textual by establishment name. Geolocation, physical distance calculation, and GPS proximity parameters are explicitly OUT OF SCOPE for the MVP.
- General shopping cart management (`COM-6.1.1`), checkout, payment processing, and order creation are explicitly OUT OF SCOPE for this specification and are scheduled for Sprints 3 and 4 per the project roadmap. Only the tenant-switch cart clearance warning dialog (COM-1.5.1 CA 5 / FR-029) is handled in Sprint 2 to safeguard existing session carts (`CartContext`).
- The establishment entity and operating schedule attributes are introduced and owned by spec 008, including default development seeds. Administrative CRUD for establishments is out of scope.
- Live ingredient inventory tracking is operational as delivered by spec 004.
- Dish technical file, gastronomic category, recipe composition, and photo storage are provided by spec 005.
- The approved retail price (PVP) and approved lifecycle state (`PUBLISHED`) are provided by spec 007.
- Dietary restriction profile compliance tags are resolved by the compliance engine established in spec 003.
- Authentication infrastructure (specs 001/002) and multi-tenant isolation mechanisms are operational. The Diner MUST be authenticated with the DINER (COMENSAL) role to search establishments and browse live catalogs.
- Direct catalog access via external deep links or table QR code scanning is OUT OF SCOPE for this specification and corresponds to an independent future user story. Catalog access originates through establishment discovery and selection (COM-1.5.1).
