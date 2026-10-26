# Technical Research: Diner Real-Time Catalog & Multi-Tenant Establishment Discovery

**Feature Branch**: `008-diner-catalog-multi-tenant`  
**Date**: 26/10/2026  
**Status**: Completed  
**Spec Reference**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/008-diner-catalog-multi-tenant/spec.md)

---

## 1. Establishment Discovery and Autocomplete Engine

### Decision
Implement `GET /api/v1/establishments/search?q={query}` in Spring Boot using Spring Data JPA repository with a PostgreSQL `ILIKE` pattern and normalized diacritic stripping (via SQL `unaccent` or application-level normalization), enforcing `trimmed.length() >= 3` and filtering strictly by `status = 'ACTIVE'`. In the frontend, implement an autocomplete input in a dedicated `/descubrimiento` route with a 300 ms debounce.

### Rationale
- **Performance**: Establishment lookup with `>= 3` characters and a 300 ms debounce limits unnecessary database queries while diners type.
- **Accuracy**: Diacritic-insensitive and case-insensitive matching allows diners typing "cafe", "café", or "CAFE" to reliably match "Café Central" (FR-002, SC-007).
- **Security & Multi-Tenancy**: Filtering strictly by `status = 'ACTIVE'` ensures that administrative or paused establishments are never exposed in autocomplete recommendations (FR-003).

### Alternatives Considered
- **Client-side list filtering**: Fetching all establishments to the browser and filtering in JavaScript. Rejected because it violates multi-tenant isolation, leaks inactive or confidential establishments, and does not scale as the number of restaurants grows.
- **Full-text search engine (Elasticsearch / Solr)**: Premature optimization that adds operational complexity and infrastructure overhead without concrete need at the current scale.

---

## 2. Real-Time Stock Availability Synchronization (<= 3s SLA)

### Decision
Implement a lightweight conditional polling mechanism on `GET /api/v1/establishments/{tenantId}/catalog/availability` with standard HTTP `ETag` / `If-None-Match` caching, polled by the frontend every 2.5 seconds.
- If ingredient stock in kitchen has not changed, backend returns `304 Not Modified` with zero response body bytes.
- If ingredient stock changed, backend computes binary dish availability map `{ [dishId: string]: boolean }` and returns `200 OK` with a new `ETag`.
- The frontend updates the in-memory state of only the affected dish cards (greyscale class and "Agotado" badge), preventing visual flickers, scroll resets, or image re-downloads.

### Rationale
- **Strict SLA Compliance**: A 2.5-second polling interval guarantees that stock depletions or replenishments are reflected on the diner screen within `<= 3` seconds (FR-016, SC-003, SC-004).
- **Resource Efficiency**: When stock is stable (99% of requests), the `304 Not Modified` response consumes negligible network bandwidth and CPU cycles.
- **Stateless & Scalable**: Works seamlessly across multiple load-balanced backend instances without sticky sessions or distributed WebSocket connection state.

### Alternatives Considered
- **Server-Sent Events (SSE) / WebSockets**: Requires stateful connection management, heartbeat timers, reconnect loops, and distributed pub/sub infrastructure (e.g., Redis) when running multi-node backends. Overkill for unidirectional low-frequency availability updates.
- **Full Catalog Polling**: Polling the complete catalog payload (`GET /api/v1/establishments/{tenantId}/catalog`) every 2.5 seconds. Rejected because transmitting image URLs, descriptions, and full recipe structures on every tick wastes bandwidth and causes unnecessary garbage collection.

---

## 3. Dynamic Stock-Based Dish Availability Calculation

### Decision
Compute dish availability dynamically on the backend using the formula:
$$\text{Available}(d) \iff \text{Status}(d) = \text{ACTIVE} \land \text{IsOpen}(e) \land \forall (i, q) \in \text{Recipe}(d): \text{Stock}(i) \ge q$$
- If a dish has zero recipe lines, it is automatically marked as unavailable (`false`) per FR-019.
- The calculation executes as an efficient projection join between `dishes`, `dish_recipe_ingredients`, and `ingredients`.
- Internal kitchen ingredient stock quantities and numeric portion counts are strictly omitted from the diner response; only a binary boolean flag is exposed per dish (FR-014).

### Rationale
- **Business Rule Integrity**: Guarantees that diners cannot view dishes as available when kitchen supplies cannot fulfill at least one portion.
- **Simultaneous Cross-Category Invalidation**: When a shared critical ingredient (e.g., milk, flour) reaches zero stock, all dependent dishes across Entradas, Platos principales, and Postres evaluate to unavailable in the same calculation cycle (FR-017, SC-004).
- **Commercial Confidentiality**: Keeps internal inventory levels hidden from public view.

### Alternatives Considered
- **Stored portion counter on Dish entity**: Incrementing/decrementing a `portions_available` field on the dish table. Rejected because dishes share ingredients; decrementing one dish's counter would not correctly synchronize availability across other dishes sharing the same raw supply.

---

## 4. Operating Hours & Timezone Evaluation

### Decision
Store operating schedule attributes on the `establishments` entity (`opening_time`, `closing_time`, `timezone` defaulted to `'America/Argentina/Buenos_Aires'`). The backend evaluates whether the establishment is currently open based on the establishment's local time:
$$\text{IsOpen}(e) \iff \text{now}_{\text{tz}} \in [\text{opening\_time}, \text{closing\_time})$$
The establishment state and formatted schedule notice (e.g., `"Abre hoy a las 11:00"`) are included in the catalog response. When closed:
- Catalog is presented in read-only mode (FR-012, SC-010).
- All dish cards reflect closed status, and clicking a card opens the detail modal displaying the outside-hours notice.

### Rationale
- **Authoritative Server Time**: Prevents client-side device clock manipulation from bypassing operating schedules.
- **Timezone Awareness**: Handles restaurants in different time zones deterministically.
- **Clean Read-Only Experience**: Allows diners to browse menus, photos, and prices even when the kitchen is closed, while preventing ordering expectations.

### Alternatives Considered
- **Client-side evaluation**: Sending raw timestamps and letting browser evaluate `isOpen`. Rejected because client clocks are unreliable and easily altered.

---

## 5. Security & Mandatory Authentication Architecture

### Decision
Enforce authentication with `ROLE_COMENSAL` (or `ROLE_DINER`) on all establishment discovery and catalog viewing endpoints (`/api/v1/establishments/**`).
- Update Spring Security `SecurityConfig.java` to remove `permitAll()` on `/api/v1/establishments/*/dishes` and require `.hasRole("COMENSAL")`.
- Configure `SecurityFilterChain` exception handling to return HTTP 401 with standard `ProblemDetail` (RFC 7807) for unauthenticated access attempts, with zero data exposure (FR-027, SC-011).
- In the frontend, protect `/descubrimiento` and `/catalogo` routes using `RoleRouteGuard allowedRoles={['COMENSAL']}`, redirecting unauthenticated users to `/login`.

### Rationale
- **Specification Compliance**: Fully aligns with approved spec clarifications and FR-027 / SC-011.
- **Data Protection**: Zero establishment, category, dish, recipe, or inventory data is leaked to anonymous clients.

### Alternatives Considered
- **Guest / Public Browsing**: Rejected by human stakeholder in clarification session; authentication is mandatory.

---

## 6. Database Migration Strategy (`V7__create_establishments_and_dish_catalog.sql`)

### Decision
Implement Flyway migration `V7__create_establishments_and_dish_catalog.sql` following immediately after `V5__extend_users_profile_and_dietary.sql`:
1. Create `establishments` table with UUID primary key, name, status, operating hours, and timezone.
2. Extend existing `dishes` table with `category` (closed enum check), `photo_url`, and `status` (`DRAFT`, `PENDING_PRICE`, `ACTIVE`, `DECOMMISSIONED`).
3. Create `dish_recipe_ingredients` table `(dish_id, ingredient_id, quantity)` in 3NF referencing `dishes(id)` and `ingredients(id)`.
4. Index foreign keys and search columns (`idx_establishments_active_name`, `idx_dishes_tenant_catalog`, `idx_dri_dish`, `idx_dri_ingredient`).

### Rationale
- **Version Cleanliness**: `V7` respects the planned `V6__chef_dish_management.sql` from spec 005 and leaves `V8` open for subsequent iterations.
- **3NF Relational Integrity**: Connects existing inventory supplies from migration `V4` to dishes from migration `V5` without redundant data.
