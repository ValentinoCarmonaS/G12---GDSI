# Feature Specification: Staff Management, RBAC, and Initial Ingredient Inventory

**Feature Branch**: `004-staff-mgmt-rbac-inventory`

**Created**: 17/10/2026

**Status**: Ready for Implementation

**Input**: User description: "Staff Management, RBAC, and Initial Ingredient Inventory — ADM-3.1.1, ADM-3.1.2, ADM-4.1.1, ADM-4.1.2, CHF-2.1.1"

## Clarifications

### Session 2026-10-20

- Q: How must the temporary credential be delivered to the new staff member upon registration by the Administrator? (FR-003) → A: One-time screen display to the Administrator upon registration, with a copy-to-clipboard action.
- Q: What should be the default page size and initial sort order for the Chef's ingredient catalog? (FR-021) → A: 20 ingredients per page, ordered alphabetically ascending (A-Z) by name.
- Q: Can an Administrator reactivate an ingredient from "Discontinued" status back to "Active", or is the lifecycle transition strictly one-way until archival? (FR-014) → A: Allow explicit reactivation from "Discontinued" to "Active" by an Administrator, restoring it to the Chef's catalog.
- Q: Do temporary staff credentials have an automatic time expiration before the first login? (FR-003) → A: No automatic time expiration; remains in "Pending First Login" status until the first login or until an Administrator re-issues the credential.
- Q: Are manual stock adjustments allowed on ingredients that are in "Discontinued" status? (FR-014, FR-015) → A: Allow stock adjustments (both absolute and relative) on "Discontinued" ingredients to record spoilage, waste, or physical counts until stock reaches zero.

### Session 2026-10-23 (Backlog-US 2.0 Reconciliation)

- Q: What is the delivery scope of feature 004 regarding user interface views? → A: Full End-to-End delivery: the specification mandates both the backend REST services and the frontend React interfaces (Admin staff roster/creation with temporary credentials modal, first-login mandatory password change view, Admin ingredient catalog CRUD & stock adjustment modal, and Chef read-only ingredient catalog view).
- Q: How do Backlog criteria mentioning future features (automatic stock deduction on orders in ADM-4.1.2, and recipe ingredient selection in CHF-2.1.1) map to this specification? → A: Explicit temporal decoupling: Sprint 1 delivers manual inventory adjustments, stock persistence, and catalog queries. Automatic order-based inventory deduction integrates in Sprint 4 (Order Processing), and chef recipe ingredient binding integrates in Sprint 2 (Recipe Management).

### Session 2026-10-24 (SDD Reconciliation & Navigation Alignment)

- Q: What navigation approach must be implemented for the mandatory first-login password change flow (FR-030)? → A: Dedicated route `/first-login-password-change` guarded against unauthenticated access and protecting operational workspaces. When a user has `must_change_password = true`, the system routes them to `/first-login-password-change` and prevents access to operational workspaces until the password change is confirmed.
- Q: Where does the Chef access the read-only ingredient catalog (FR-032)? → A: Dedicated sub-route `/cocina/ingredientes` (with alias `/kds/ingredients`) with a navigation tab/link in `WorkspaceLayout` and from `KitchenWorkspacePage`.
- Q: Where does the Administrator access the staff roster and ingredient inventory (FR-028, FR-031)? → A: Dedicated sub-routes `/admin/staff` and `/admin/inventory`, accessible from cards in `AdminWorkspacePage` and links in `WorkspaceLayout`.


## User Scenarios & Testing *(mandatory)*

### User Story 1 — Staff Member Registration & Profile Management (Priority: P1)

As a Restaurant Administrator, I register new employees into my establishment so they can access the platform with the appropriate operational role.

The Administrator opens the staff management section and creates a new staff member by providing their full name, email address, and one operational role (ADMIN, CHEF, or COUNTER_CLERK). The system validates that the email is not already registered anywhere on the platform. Upon successful creation, the employee is provisioned with a temporary credential displayed once on the Administrator's screen with a clipboard copy action. This temporary credential remains valid without automatic expiration until first login (at which point a mandatory password change is enforced) or manual reset by an Administrator. The Administrator can view all staff members of the establishment in a roster that displays name, email, role, and account status, and can edit a staff member's details or reassign their role.

**Why this priority**: Staff registration is the foundational prerequisite for every other user story in this iteration. Without registered staff, role-based access control has no subjects, and kitchen staff cannot access the ingredient catalog.

**Independent Test**: Can be fully tested by creating a staff member, verifying the roster displays the new entry, editing the member's role, and confirming the temporary credential policy triggers a forced password change on first login.

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator on their establishment's staff management page, **When** they submit a valid staff registration form with full name, email, and role, **Then** the new staff member appears in the roster with status "Pending First Login", the secure temporary credential is displayed to the Administrator exactly once with a copy-to-clipboard action, and is not retrievable again after dismissal.
2. **Given** an authenticated Administrator submitting a staff registration form, **When** the provided email address is already registered on the platform (any tenant), **Then** the system rejects the registration with a clear validation message indicating the email is already in use.
3. **Given** an authenticated Administrator submitting a staff registration form, **When** the role field is missing or contains an invalid value, **Then** the system rejects the registration with a message listing the valid roles.
4. **Given** a newly registered staff member logging in for the first time with temporary credentials, **When** they authenticate successfully, **Then** the system forces a mandatory password change before granting access to any platform functionality.
5. **Given** an authenticated Administrator viewing the staff roster, **When** they edit a staff member's name, email, or role, **Then** the changes are persisted and reflected immediately in the roster.
6. **Given** an authenticated Administrator editing a staff member's email, **When** the new email is already in use by another account on the platform, **Then** the system rejects the change with an appropriate uniqueness violation message.
7. **Given** a staff registration form, **When** the Administrator enters an email with mixed-case characters (e.g., "User@Domain.COM"), **Then** the system normalizes the email to lowercase before uniqueness validation and storage.

---

### User Story 2 — Role-Based Access Control & Tenant Isolation (Priority: P1)

As the platform, I enforce strict role-based access control and multi-tenant isolation so that each user can only access the functions and data authorized for their role within their own establishment.

The system enforces four role levels: ADMIN has full authority over staff management, ingredient CRUD, and stock adjustment within their tenant; CHEF has read-only access to the active ingredient catalog; COUNTER_CLERK has no access to staff, ingredient, or inventory resources; DINER has no access to any back-office functionality. Every data query and mutation is scoped to the authenticated user's establishment. Attempts to access resources belonging to another tenant or resources above the user's authorization level are rejected with appropriate denial responses, and every unauthorized access attempt is logged for security auditing.

**Why this priority**: RBAC is a co-equal P1 because it is the security foundation upon which all other stories depend. Without enforced access boundaries, staff registration and ingredient management would be insecure and non-compliant with multi-tenant isolation requirements.

**Independent Test**: Can be tested by authenticating as each role and verifying that permitted operations succeed and prohibited operations are denied with correct responses and audit entries.

**Acceptance Scenarios**:

1. **Given** a user authenticated with the ADMIN role, **When** they attempt to create, view, edit staff members or manage ingredients and stock within their own establishment, **Then** all operations succeed.
2. **Given** a user authenticated with the CHEF role, **When** they attempt to access the active ingredient catalog (read-only), **Then** the request succeeds and returns only active ingredients from their own establishment.
3. **Given** a user authenticated with the CHEF role, **When** they attempt to create, edit, or delete an ingredient, adjust stock, or access any staff management resource, **Then** the request is denied with a 403 Forbidden semantic response.
4. **Given** a user authenticated with the COUNTER_CLERK role, **When** they attempt to access any staff management, ingredient, or inventory resource, **Then** the request is denied with a 403 Forbidden semantic response.
5. **Given** a user authenticated with the DINER role, **When** they attempt to access any back-office staff, ingredient, or inventory resource, **Then** the request is denied with a 403 Forbidden semantic response.
6. **Given** any authenticated user, **When** they attempt to query or modify staff or inventory data belonging to a different establishment, **Then** the system denies the request and the data of the other tenant is never exposed in any response.
7. **Given** any unauthorized access attempt (role violation or cross-tenant access), **When** the denial occurs, **Then** the system persists a security audit log entry recording the identity, timestamp, attempted resource, and denial reason.

---

### User Story 3 — Ingredient Master Catalog CRUD & Lifecycle Management (Priority: P2)

As a Restaurant Administrator, I define and manage the raw ingredient catalog for my restaurant so that the kitchen team can reference valid ingredients for recipe planning.

The Administrator creates an ingredient by specifying a name (unique within the establishment), a category or family (e.g., Produce, Meat, Dairy, Dry Goods, Spices), a unit of measure (e.g., g, kg, ml, l, u), and an initial reference unit purchase cost. The Administrator can view all ingredients, edit their details, and manage their lifecycle through a clean, reversible two-state model: "Active" and "Discontinued". When an Administrator discontinues an ingredient, it is removed from the Chef's visible catalog while retaining all historical stock batches and audit records. An Administrator can explicitly reactivate a Discontinued ingredient back to "Active" at any time, instantly restoring it to the Chef's visible catalog without restriction or terminal state locks.

**Why this priority**: The ingredient catalog is the data prerequisite for stock management and the Chef's catalog view, but it depends on RBAC being in place to enforce that only Administrators can perform these operations.

**Independent Test**: Can be tested by creating ingredients with valid and invalid inputs, verifying uniqueness constraints, editing ingredient properties, discontinuing an ingredient, reactivating it, and verifying the visibility toggle in the Chef catalog.

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator, **When** they submit a valid ingredient creation form with name, category, unit of measure, and unit purchase cost, **Then** the ingredient is created and appears in the ingredient list with an "Active" status and initial stock of 0.
2. **Given** an authenticated Administrator creating an ingredient, **When** the ingredient name already exists within their establishment, **Then** the system rejects the creation with a uniqueness violation message.
3. **Given** an authenticated Administrator creating an ingredient, **When** the unit purchase cost is negative or the unit of measure is not in the accepted set, **Then** the system rejects the creation with a clear validation error.
4. **Given** an authenticated Administrator viewing their ingredient catalog, **When** they edit an ingredient's name, category, or unit purchase cost, **Then** the changes are persisted and reflected in the catalog.
5. **Given** an authenticated Administrator editing an ingredient's unit of measure, **When** any stock records already exist for that ingredient (stock quantity is not zero), **Then** the system rejects the unit-of-measure change to prevent data corruption.
6. **Given** an authenticated Administrator viewing an Active ingredient, **When** they select "Discontinuar", **Then** the ingredient transitions to "Discontinued" status, is excluded from the Chef catalog, and cannot receive new stock additions.
7. **Given** an authenticated Administrator viewing a Discontinued ingredient, **When** they select "Reactivar", **Then** the ingredient transitions back to "Active" status and immediately becomes visible again in the Chef's catalog.

---

### User Story 4 — Stock Adjustments, Batch Tracking & Financial Loss Auditing (Priority: P2)

As a Restaurant Administrator, I record stock deliveries as distinct batches and impute spoilage or shrinkage to specific purchases so that inventory balances and financial losses are accurately tracked.

The Administrator manages stock through two explicit domain operations for both Active and Discontinued ingredients:
1. **Agregar stock (Creación de Lote)**: When receiving new goods, the Administrator enters the received quantity and the mandatory unit purchase cost of that specific shipment. Each addition creates an independent, timestamped stock batch (*batch / lote*) preserving its acquisition price and tracking its remaining available balance.
2. **Restar stock (Imputación a Lote y Cálculo de Pérdida)**: When recording shrinkage, spoilage, expiration, or waste, the system presents the active batches for that ingredient that have available stock (> 0). The Administrator selects the specific batch being discarded and enters the quantity to deduct (bounded by the batch's remaining balance). The system calculates the exact financial loss (`quantity × unit_cost`) and deducts the balance from both the batch and the ingredient's global stock.

Every adjustment requires a mandatory justification note and persists an immutable audit log entry capturing timestamp, Administrator identity, previous stock, new stock, net delta, associated batch, unit cost, and total financial loss.

**Why this priority**: Accurate recipe costing and financial loss tracking require precise acquisition costs per shipment rather than an arbitrary static average.

**Independent Test**: Can be tested by adding stock to create Lot A ($2.000/kg) and Lot B ($1.000/kg), subtracting 2 kg from Lot A, and asserting that the audit record captures a $4.000 financial loss and decrements Lot A's remaining quantity.

**Acceptance Scenarios**:

1. **Given** an ingredient with 0 stock, **When** the Administrator adds 5 kg at a unit purchase cost of $2.000, **Then** the system creates Lot A (initial: 5 kg, remaining: 5 kg, unit cost: $2.000), updates the ingredient current stock to 5 kg, and records an audit entry with positive delta and unit cost.
2. **Given** an ingredient with Lot A (5 kg @ $2.000) and Lot B (5 kg @ $1.000), **When** the Administrator selects "Restar stock", **Then** the system displays both Lot A and Lot B with their respective remaining balances, creation dates, and acquisition prices.
3. **Given** an Administrator subtracting 2 kg from Lot A, **When** they submit the adjustment with justification, **Then** the system decrements Lot A's remaining balance to 3 kg, reduces global stock to 8 kg, and records an audit log entry with delta -2 kg, unit cost $2.000, and financial loss $4.000.
4. **Given** Lot A with 3 kg remaining, **When** an Administrator attempts to subtract 4 kg from Lot A, **Then** the system rejects the operation, enforcing that deductions cannot exceed the available balance of the selected batch.
5. **Given** a batch whose remaining balance reaches exactly 0 after an adjustment, **When** subsequent subtractions are initiated, **Then** the depleted batch is excluded from the active selection list.
6. **Given** two concurrent stock subtractions targeting the same batch, **When** executed simultaneously, **Then** the system uses pessimistic locking to serialize the updates, preventing negative balances and guaranteeing consistent remaining quantities.
7. **Given** an existing audit log entry for a stock adjustment, **When** any user or process attempts to modify or delete that entry, **Then** the system prevents the modification, preserving audit immutability.

---

### User Story 5 — Kitchen Ingredient Catalog Exploration (Priority: P3)

As a Chef, I browse the active ingredient catalog of my establishment so that I can plan recipes and identify available ingredients, even when some are temporarily out of stock.

The Chef accesses a dedicated paginated catalog showing all active ingredients registered by their establishment. Each entry displays the ingredient name, category, unit of measure, and a stock availability indicator ("In Stock" or "Out of Stock"). Active ingredients with zero stock remain visible so the Chef can design recipes even when an ingredient is temporarily unavailable. Discontinued ingredients are excluded from the Chef's view. The Chef can filter by category and search by ingredient name. The catalog defaults to 20 items per page ordered alphabetically ascending (A-Z) by ingredient name. Financial cost information and stock adjustment history are never displayed in this view.

**Why this priority**: This is a consumption-only view that depends on the ingredient catalog (User Story 3) and stock data (User Story 4) being available. It delivers value to the kitchen team but does not block other administrative workflows.

**Independent Test**: Can be tested by authenticating as a Chef, verifying the catalog displays active ingredients with correct stock indicators, confirming that discontinued ingredients are hidden, and validating pagination, category filtering, and text search functionality.

**Acceptance Scenarios**:

1. **Given** an authenticated Chef, **When** they navigate to the ingredient catalog, **Then** they see a paginated list of up to 20 active ingredients from their establishment ordered alphabetically ascending (A-Z) by name, showing name, category, unit of measure, and stock status.
2. **Given** an active ingredient with current stock of zero, **When** the Chef views the catalog, **Then** the ingredient appears with an "Out of Stock" indicator.
3. **Given** a discontinued ingredient, **When** the Chef views the catalog, **Then** the discontinued ingredient is not visible.
4. **Given** the Chef viewing the ingredient catalog, **When** they filter by a specific category (e.g., "Dairy"), **Then** only ingredients in that category are displayed.
5. **Given** the Chef viewing the ingredient catalog, **When** they search by ingredient name (e.g., "tomato"), **Then** the catalog filters to show matching ingredients.
6. **Given** any Chef catalog view, **When** displayed, **Then** unit purchase cost and stock adjustment audit history are never shown.
7. **Given** a catalog with more ingredients than a single page, **When** the Chef navigates between pages, **Then** pagination works correctly without dropping or duplicating items, including when ingredients are added concurrently.

---

### Edge Cases

- **Email case normalization**: Registering a staff member with "User@Domain.COM" and then attempting to register another with "user@domain.com" must be treated as a duplicate.
- **Self-demotion prevention**: An Administrator must not be able to demote themselves from ADMIN if doing so would leave the establishment with zero Administrators.
- **Zero-delta rejection**: A relative stock adjustment of exactly zero must be rejected as a non-operation since it would generate a misleading audit entry with no actual change.
- **Unit-of-measure change restriction**: Changing an ingredient's unit of measure must be blocked when any stock records exist to prevent data corruption from incompatible quantity interpretations.
- **Decimal vs. integer quantities**: Continuous units (g, kg, ml, l) must accept and store decimal quantities with exact precision (e.g., 1.750 kg). Discrete units (u) must accept only whole numbers.
- **Concurrent stock adjustments**: Two simultaneous adjustments on the same ingredient must be serialized atomically, ensuring no lost updates and a correct final stock value.
- **Large catalog pagination stability**: Paginating through a large ingredient catalog must remain stable even when new ingredients are added concurrently by another Administrator session.
- **Discontinued ingredient stock adjustments**: An ingredient in Discontinued status accepts stock reductions (spoilage write-offs or shrinkage adjustments) under the non-negative stock invariant, but rejects new stock additions. Discontinued ingredients can be freely reactivated to Active status at any time.
- **Batch depletion and exhaustion**: When a batch's remaining balance reaches zero, it is marked depleted and excluded from subsequent subtraction options, but remains permanently in historical audit records.
- **Orphaned Administrator prevention**: The system must prevent operations that would result in an establishment having no user with the ADMIN role.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an Administrator to register a new staff member by specifying full name, email address, and exactly one operational role (ADMIN, CHEF, or COUNTER_CLERK).
- **FR-002**: The system MUST enforce global email uniqueness across all tenants, normalizing email addresses to lowercase before validation and storage.
- **FR-003**: The system MUST provision newly registered staff members with a secure temporary credential displayed exactly once to the Administrator upon creation with a copy-to-clipboard action, which remains valid without automatic time expiration until the employee's initial login (mandating an immediate password change) or until re-issued by an Administrator.
- **FR-004**: The system MUST provide the Administrator with a roster view of all staff members in their establishment, displaying name, email, assigned role, and account status.
- **FR-005**: The system MUST allow the Administrator to edit a staff member's full name, email address, and role, subject to the same uniqueness and validation constraints as registration.
- **FR-006**: The system MUST prevent any operation that would result in an establishment having zero users with the ADMIN role.
- **FR-007**: The system MUST enforce role-based access control with the following permissions: ADMIN has full CRUD authority over staff, ingredients, and stock within their tenant; CHEF has read-only access to the active ingredient catalog within their tenant; COUNTER_CLERK has no access to staff, ingredient, or inventory resources; DINER has no access to back-office resources.
- **FR-008**: The system MUST enforce strict multi-tenant data isolation so that no user can view, query, or modify data belonging to a different establishment.
- **FR-009**: The system MUST respond to unauthorized access attempts with a 403 Forbidden semantic response and persist a security audit log entry recording the identity, timestamp, attempted resource, and denial reason.
- **FR-010**: The system MUST allow an Administrator to create an ingredient by specifying a name (unique within the establishment), category, unit of measure (from the set: g, kg, ml, l, u), and unit purchase cost (non-negative value with exact decimal precision), initializing current stock to 0.
- **FR-011**: The system MUST support the following ingredient categories: Produce, Meat, Dairy, Dry Goods, Spices. The category set is fixed for this iteration.
- **FR-012**: The system MUST allow the Administrator to view all ingredients in their establishment's catalog and edit an ingredient's name, category, and unit purchase cost.
- **FR-013**: The system MUST prevent changing an ingredient's unit of measure when any stock records exist for that ingredient (current stock is not zero).
- **FR-014**: The system MUST implement a clean, reversible two-state ingredient lifecycle: an Administrator MAY discontinue an Active ingredient (transitioning to "Discontinued", excluding it from the Chef catalog and blocking new stock additions) and MAY reactivate a Discontinued ingredient back to "Active" at any time. The permanent "Archived" state is eliminated.
- **FR-015**: The system MUST support two domain stock operations: "Agregar stock" (`action = ADD`) and "Restar stock" (`action = SUBTRACT`), applicable to both Active and Discontinued ingredients (discontinued items permit subtractions only).
  - **FR-015a**: Every stock addition (`ADD`) MUST create an independent batch record (`IngredientBatch`) storing the received quantity, the mandatory unit purchase cost (`unit_cost >= 0`), the remaining quantity (initialized to received quantity), and the acquisition timestamp.
  - **FR-015b**: Every stock subtraction (`SUBTRACT`) MUST designate a target batch with `remaining_quantity > 0`; the system MUST enforce that the subtracted quantity does not exceed the batch's remaining balance (`subtracted <= remaining_quantity`) and MUST calculate the exact financial loss (`quantity × unit_cost`).
  - **FR-015c**: The system MUST maintain the global inventory invariant: an ingredient's `current_stock` MUST strictly equal the sum of `remaining_quantity` across all its active batches.
- **FR-016**: The system MUST enforce the non-negative stock invariant: no adjustment or subtraction may produce a resulting batch or global stock below zero.
- **FR-017**: The system MUST reject stock adjustments with a quantity of zero or negative.
- **FR-018**: The system MUST require a mandatory justification note for every stock adjustment.
- **FR-019**: The system MUST persist an immutable, append-only audit log entry for every stock adjustment, recording: timestamp, Administrator identity, ingredient reference, target batch reference (for subtractions and batch additions), previous stock quantity, new stock quantity, net delta, unit cost, financial loss, and justification note.
- **FR-020**: The system MUST guarantee atomic transactional isolation (via pessimistic locking) for concurrent stock adjustments and batch deductions on the same ingredient.
- **FR-021**: The system MUST provide Chefs with a paginated catalog of all active ingredients in their establishment, displaying name, category, unit of measure, and stock availability status ("In Stock" or "Out of Stock"), defaulting to 20 items per page ordered alphabetically ascending (A-Z) by ingredient name.
- **FR-022**: The Chef's catalog MUST include active ingredients with zero stock (shown as "Out of Stock") and MUST exclude discontinued ingredients.
- **FR-023**: The Chef's catalog MUST support filtering by category and text search by ingredient name.
- **FR-024**: The Chef's catalog MUST never display unit purchase cost, batch details, or stock adjustment audit history.
- **FR-025**: The system MUST store all monetary values (unit purchase cost, financial loss) and physical quantities with exact decimal precision, avoiding binary floating-point rounding errors.
- **FR-026**: The system MUST validate all input fields in real time on staff and ingredient forms, highlighting missing required fields and invalid formats before submission.
- **FR-027**: The stock adjustment interface MUST provide an "Agregar stock" view (requiring quantity and unit purchase cost) and a "Restar stock" view (listing available batches with dates, costs, and remaining quantities, displaying computed financial loss in real time).
- **FR-028**: The system MUST provide an Administrative Staff Management view accessible at `/admin/staff` displaying the establishment's staff roster (name, email, role, status) with actions to create and edit staff members.
- **FR-029**: Upon successful staff creation, the Administrative view MUST display a modal containing the generated temporary password with a single-click "Copiar credenciales" action, warning the Administrator that it cannot be retrieved again after closing.
- **FR-030**: The system MUST provide a mandatory First Login Password Change view intercepted upon initial authentication of any account with `must_change_password = true`, blocking access to operational workspaces until a compliant new password is submitted and confirmed.
- **FR-031**: The system MUST provide an Administrative Ingredient Inventory view accessible at `/admin/inventory` displaying all tenant ingredients with their lifecycle status (`ACTIVE`, `DISCONTINUED`), category, unit of measure, unit cost, and current stock, with controls for ingredient creation, editing, status change ("Desactivar" / "Reactivar"), and opening the stock adjustment modal.
- **FR-032**: The system MUST provide a dedicated Kitchen Ingredient Catalog view accessible to Chefs at `/kds/ingredients` (or kitchen workspace tab) displaying the paginated active ingredient list with category filters and text search, displaying stock availability indicators ("In Stock" / "Out of Stock") and excluding cost and audit details.

### Key Entities

- **Staff Member**: Represents an employee of a specific establishment. Key attributes: full name, email address (globally unique, normalized to lowercase), operational role (ADMIN / CHEF / COUNTER_CLERK), account status (Pending First Login / Active), associated establishment.
- **Establishment (Tenant)**: Represents a restaurant on the platform. Key attribute: unique identifier. All staff members, ingredients, stock records, and audit entries belong to exactly one establishment.
- **Ingredient**: Represents a raw material in a restaurant's kitchen catalog. Key attributes: name (unique within establishment), category (Produce / Meat / Dairy / Dry Goods / Spices), unit of measure (g / kg / ml / l / u), unit purchase cost (non-negative, exact decimal), lifecycle status (`ACTIVE` / `DISCONTINUED`, with fully reversible transitions: Active <-> Discontinued), current stock quantity (non-negative, exact decimal).
- **Ingredient Batch**: Represents an individual shipment or lot of an ingredient. Key attributes: unique identifier, tenant identifier, ingredient reference, initial quantity, remaining available quantity, unit purchase cost, and creation timestamp.
- **Stock Audit Entry**: Represents an immutable record of a stock adjustment. Key attributes: timestamp, Administrator identity, ingredient reference, batch reference, previous stock quantity, new stock quantity, net delta, unit cost, financial loss, and justification note. Relationship: each entry belongs to one ingredient and one establishment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Administrator can register a new staff member and see them in the roster within 30 seconds from opening the registration form.
- **SC-002**: Unauthorized access attempts by CHEF, COUNTER_CLERK, or DINER users are blocked 100% of the time and produce a corresponding security audit log entry.
- **SC-003**: Cross-tenant data leakage is zero: no user can ever retrieve staff, ingredient, or stock data from an establishment other than their own.
- **SC-004**: An Administrator can create an ingredient and perform the first stock adjustment in under 60 seconds.
- **SC-005**: 100% of stock adjustments produce a verifiable, immutable audit trail entry that accurately records the previous stock, new stock, delta, and justification.
- **SC-006**: No stock adjustment ever results in a negative stock balance under any circumstances, including concurrent operations.
- **SC-007**: A Chef can load the first page of the ingredient catalog and locate a specific ingredient by name search in under 10 seconds.
- **SC-008**: The ingredient catalog correctly reflects stock status changes (Active with stock, Active out of stock, Discontinued hidden) within 5 seconds of the change occurring.
- **SC-009**: [Post-Release Usability Metric] 95% of Administrators successfully complete the staff registration flow on their first attempt without encountering a validation error they did not expect (monitored via production telemetry/analytics, not gated by local automated test suites).

## Assumptions

- The central authentication system (user registration, login, JWT issuance, and password recovery) is already implemented and operational as delivered by iteration 001 (central-auth-recovery). This specification builds upon that existing authentication infrastructure.
- The Establishment (Tenant) entity already exists in the platform data model and each authenticated user is already associated with exactly one establishment.
- The DINER role and its authentication flow already exist from the central auth iteration; this specification only adds the CHEF and COUNTER_CLERK roles as new platform roles alongside the existing ADMIN role.
- Password hashing, JWT token management, and session lifecycle are handled by the existing authentication infrastructure and are not re-specified here.
- The ingredient category set (Produce, Meat, Dairy, Dry Goods, Spices) is fixed for this iteration. Extensibility to custom categories is deferred to a future iteration.
- The accepted units of measure (g, kg, ml, l, u) are fixed for this iteration. Unit conversion between compatible measures (e.g., g to kg) is out of scope.
- This iteration delivers foundational inventory tracking and manual stock adjustments. Automatic inventory deduction upon order placement (mentioned in ADM-4.1.2) is formally integrated in Sprint 4 (Order Processing), and the ingredient selector within recipe authoring (mentioned in CHF-2.1.1) is formally integrated in Sprint 2 (Recipe Management). Both backend REST services and frontend React presentation views for staff administration, inventory adjustments, and chef catalog browsing are fully in scope as end-to-end deliverables for this feature.
- Real-time form validation refers to client-side validation providing immediate user feedback; server-side validation remains the authoritative enforcement layer.
- The audit log is append-only and not exposed through any user-facing deletion or edit interface. Audit retention policies are not addressed in this iteration.
- Temporary credentials for staff accounts do not automatically expire by time; they remain valid in "Pending First Login" status until the employee logs in and completes the mandatory password change, or until an Administrator manually updates the member's details or re-issues the credential.
