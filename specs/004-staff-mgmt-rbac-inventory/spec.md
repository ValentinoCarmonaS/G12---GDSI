# Feature Specification: Staff Management, RBAC, and Initial Ingredient Inventory

**Feature Branch**: `004-staff-mgmt-rbac-inventory`

**Created**: 17/10/2026

**Status**: Draft

**Input**: User description: "Staff Management, RBAC, and Initial Ingredient Inventory — ADM-3.1.1, ADM-3.1.2, ADM-4.1.1, ADM-4.1.2, CHF-2.1.1"

## Clarifications

### Session 2026-10-20

- Q: How must the temporary credential be delivered to the new staff member upon registration by the Administrator? (FR-003) → A: One-time screen display to the Administrator upon registration, with a copy-to-clipboard action.
- Q: What should be the default page size and initial sort order for the Chef's ingredient catalog? (FR-021) → A: 20 ingredients per page, ordered alphabetically ascending (A-Z) by name.
- Q: Can an Administrator reactivate an ingredient from "Discontinued" status back to "Active", or is the lifecycle transition strictly one-way until archival? (FR-014) → A: Allow explicit reactivation from "Discontinued" to "Active" by an Administrator, restoring it to the Chef's catalog.
- Q: Do temporary staff credentials have an automatic time expiration before the first login? (FR-003) → A: No automatic time expiration; remains in "Pending First Login" status until the first login or until an Administrator re-issues the credential.
- Q: Are manual stock adjustments allowed on ingredients that are in "Discontinued" status? (FR-014, FR-015) → A: Allow stock adjustments (both absolute and relative) on "Discontinued" ingredients to record spoilage, waste, or physical counts until stock reaches zero.

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

The Administrator creates an ingredient by specifying a name (unique within the establishment), a category or family (e.g., Produce, Meat, Dairy, Dry Goods, Spices), a unit of measure (e.g., g, kg, ml, l, u), and a unit purchase cost. The Administrator can view all ingredients, edit their details, and manage their lifecycle. When an Administrator discontinues an ingredient that still has physical stock greater than zero, the ingredient transitions to a "Discontinued" state: it is removed from the Chef's visible catalog but retained in the inventory until stock depletes to zero. An Administrator can explicitly reactivate a Discontinued ingredient back to "Active", restoring it to the Chef's visible catalog. An ingredient can only be permanently archived once its stock reaches exactly zero.

**Why this priority**: The ingredient catalog is the data prerequisite for stock management and the Chef's catalog view, but it depends on RBAC being in place to enforce that only Administrators can perform these operations.

**Independent Test**: Can be tested by creating ingredients with valid and invalid inputs, verifying uniqueness constraints, editing ingredient properties, triggering the two-phase decommission lifecycle by discontinuing an ingredient with positive stock, and verifying reactivation from Discontinued back to Active.

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator, **When** they submit a valid ingredient creation form with name, category, unit of measure, and unit purchase cost, **Then** the ingredient is created and appears in the ingredient list with an "Active" status.
2. **Given** an authenticated Administrator creating an ingredient, **When** the ingredient name already exists within their establishment, **Then** the system rejects the creation with a uniqueness violation message.
3. **Given** an authenticated Administrator creating an ingredient, **When** the unit purchase cost is negative or the unit of measure is not in the accepted set, **Then** the system rejects the creation with a clear validation error.
4. **Given** an authenticated Administrator viewing their ingredient catalog, **When** they edit an ingredient's name, category, or unit purchase cost, **Then** the changes are persisted and reflected in the catalog.
5. **Given** an authenticated Administrator editing an ingredient's unit of measure, **When** any stock records already exist for that ingredient (stock quantity is not zero), **Then** the system rejects the unit-of-measure change to prevent data corruption.
6. **Given** an authenticated Administrator discontinuing an ingredient, **When** the ingredient's current stock is greater than zero, **Then** the ingredient transitions to "Discontinued" status, is excluded from the Chef's catalog, and remains in the inventory until stock reaches zero.
7. **Given** an authenticated Administrator attempting to permanently archive an ingredient, **When** the ingredient's stock is exactly zero, **Then** the ingredient is permanently archived and removed from all views.
8. **Given** an authenticated Administrator attempting to permanently archive an ingredient, **When** the ingredient's stock is greater than zero, **Then** the system rejects the archive operation and requires the ingredient to first pass through the Discontinued lifecycle phase.
9. **Given** an authenticated Administrator viewing a Discontinued ingredient, **When** they trigger the reactivation action, **Then** the ingredient transitions back to "Active" status and immediately becomes visible again in the Chef's catalog.

---

### User Story 4 — Initial Stock Load & Manual Inventory Adjustments with Audit Trail (Priority: P2)

As a Restaurant Administrator, I initialize and adjust ingredient stock quantities so that the inventory reflects the actual physical state of my kitchen, and every change is traceable.

The Administrator can adjust stock through two modes for both Active and Discontinued ingredients (allowing spoilage/loss adjustments on discontinued items until stock reaches zero). In Absolute Adjustment Mode, they enter the exact counted physical inventory (e.g., "14 units"), and the system replaces the current stock with that value. In Relative Adjustment Mode, they apply a positive or negative delta (e.g., "+25 kg" for a delivery or "-2 kg" for spoilage). In both modes, the system computes the resulting stock and enforces the invariant that stock can never go below zero. Every adjustment requires a mandatory justification note (e.g., "Initial inventory load", "Damaged goods", "Physical count correction"). The system persists an immutable audit log entry for every adjustment recording the timestamp, Administrator identity, previous stock, new stock, net delta, and justification.

**Why this priority**: Stock management depends on the ingredient catalog being defined (User Story 3), and produces the stock data that the Chef's catalog view (User Story 5) will display.

**Independent Test**: Can be tested by performing absolute and relative adjustments on an ingredient, verifying resulting stock values, confirming negative-stock rejections, verifying that zero-delta adjustments are rejected, and inspecting the audit log for completeness and immutability.

**Acceptance Scenarios**:

1. **Given** an authenticated Administrator viewing an ingredient with current stock of 10 units, **When** they submit an Absolute Adjustment of 14, **Then** the stock is updated to 14 and an audit record is created showing previous stock = 10, new stock = 14, delta = +4, and the provided justification.
2. **Given** an authenticated Administrator viewing an ingredient with current stock of 10 kg, **When** they submit a Relative Adjustment of +25, **Then** the stock is updated to 35 kg and an audit record is created showing previous stock = 10, new stock = 35, delta = +25, and the provided justification.
3. **Given** an authenticated Administrator viewing an ingredient with current stock of 5 kg, **When** they submit a Relative Adjustment of -8, **Then** the system rejects the adjustment because the resulting stock would be -3, violating the non-negative stock invariant, and displays an explanatory error.
4. **Given** an authenticated Administrator attempting a stock adjustment, **When** the justification/reason field is empty, **Then** the system rejects the adjustment and requires a mandatory reason.
5. **Given** an authenticated Administrator attempting a Relative Adjustment with a delta of zero, **When** they submit the adjustment, **Then** the system rejects it as a non-operation.
6. **Given** an existing audit log entry for a stock adjustment, **When** any user or process attempts to modify or delete that entry, **Then** the system prevents the modification, preserving audit immutability.
7. **Given** an authenticated Administrator entering an Absolute Adjustment, **When** the interface displays the adjustment form, **Then** it shows the computed delta relative to the current stock in real time before submission.
8. **Given** an authenticated Administrator entering a Relative Adjustment, **When** the interface displays the adjustment form, **Then** it shows the projected new total stock in real time before submission.
9. **Given** two Administrators adjusting the same ingredient concurrently, **When** both submit their adjustments simultaneously, **Then** the system guarantees atomic transactional isolation so that neither adjustment is lost or produces an inconsistent stock value.
10. **Given** an authenticated Administrator viewing a Discontinued ingredient with remaining stock of 3 kg, **When** they apply a relative adjustment of -3 kg with justification "Spoilage write-off", **Then** the stock updates to 0 kg, an immutable audit record is created, and the ingredient becomes eligible for permanent archival.

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
- **Discontinued ingredient stock adjustments and depletion**: An ingredient in Discontinued status accepts stock adjustments (e.g. spoilage write-offs or physical count reconciliations) under the non-negative stock invariant, enabling remaining stock to reach exactly zero so the ingredient can subsequently be archived.
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
- **FR-010**: The system MUST allow an Administrator to create an ingredient by specifying a name (unique within the establishment), category, unit of measure (from the set: g, kg, ml, l, u), and unit purchase cost (non-negative value with exact decimal precision).
- **FR-011**: The system MUST support the following ingredient categories: Produce, Meat, Dairy, Dry Goods, Spices. The category set is fixed for this iteration.
- **FR-012**: The system MUST allow the Administrator to view all ingredients in their establishment's catalog and edit an ingredient's name, category, and unit purchase cost.
- **FR-013**: The system MUST prevent changing an ingredient's unit of measure when any stock records exist for that ingredient (current stock is not zero).
- **FR-014**: The system MUST implement an ingredient decommission lifecycle: discontinuing an ingredient with stock > 0 transitions it to "Discontinued" status (excluded from Chef's catalog but retained in inventory); the Administrator MAY explicitly reactivate a Discontinued ingredient back to "Active"; permanent archival is only permitted when stock == 0.
- **FR-015**: The system MUST support two stock adjustment modes: Absolute Adjustment (sets stock to the specified value) and Relative Adjustment (applies a positive or negative delta to the current stock), applicable to both Active and Discontinued ingredients.
- **FR-016**: The system MUST enforce the non-negative stock invariant: no adjustment may produce a resulting stock below zero.
- **FR-017**: The system MUST reject relative stock adjustments with a delta of exactly zero as non-operations.
- **FR-018**: The system MUST require a mandatory justification note for every stock adjustment.
- **FR-019**: The system MUST persist an immutable, append-only audit log entry for every stock adjustment, recording: timestamp, Administrator identity, previous stock quantity, new stock quantity, net delta, and justification note.
- **FR-020**: The system MUST guarantee atomic transactional isolation for concurrent stock adjustments on the same ingredient.
- **FR-021**: The system MUST provide Chefs with a paginated catalog of all active ingredients in their establishment, displaying name, category, unit of measure, and stock availability status ("In Stock" or "Out of Stock"), defaulting to 20 items per page ordered alphabetically ascending (A-Z) by ingredient name.
- **FR-022**: The Chef's catalog MUST include active ingredients with zero stock (shown as "Out of Stock") and MUST exclude discontinued or archived ingredients.
- **FR-023**: The Chef's catalog MUST support filtering by category and text search by ingredient name.
- **FR-024**: The Chef's catalog MUST never display unit purchase cost or stock adjustment audit history.
- **FR-025**: The system MUST store all monetary values (unit purchase cost) and physical quantities with exact decimal precision, avoiding binary floating-point rounding errors.
- **FR-026**: The system MUST validate all input fields in real time on staff and ingredient forms, highlighting missing required fields and invalid formats before submission.
- **FR-027**: The stock adjustment interface MUST display computed deltas (in Absolute mode) and projected totals (in Relative mode) in real time, and MUST block submission if the resulting stock would be negative.

### Key Entities

- **Staff Member**: Represents an employee of a specific establishment. Key attributes: full name, email address (globally unique, normalized to lowercase), operational role (ADMIN / CHEF / COUNTER_CLERK), account status (Pending First Login / Active), associated establishment.
- **Establishment (Tenant)**: Represents a restaurant on the platform. Key attribute: unique identifier. All staff members, ingredients, stock records, and audit entries belong to exactly one establishment.
- **Ingredient**: Represents a raw material in a restaurant's kitchen catalog. Key attributes: name (unique within establishment), category (Produce / Meat / Dairy / Dry Goods / Spices), unit of measure (g / kg / ml / l / u), unit purchase cost (non-negative, exact decimal), lifecycle status (Active / Discontinued / Archived, with transitions: Active <-> Discontinued, and Discontinued -> Archived only when stock == 0), current stock quantity (non-negative, exact decimal).
- **Stock Audit Entry**: Represents an immutable record of a stock adjustment. Key attributes: timestamp, Administrator identity, ingredient reference, previous stock quantity, new stock quantity, net delta, adjustment mode (Absolute / Relative), justification note. Relationship: each entry belongs to one ingredient and one establishment.

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
- **SC-009**: 95% of Administrators successfully complete the staff registration flow on their first attempt without encountering a validation error they did not expect.

## Assumptions

- The central authentication system (user registration, login, JWT issuance, and password recovery) is already implemented and operational as delivered by iteration 001 (central-auth-recovery). This specification builds upon that existing authentication infrastructure.
- The Establishment (Tenant) entity already exists in the platform data model and each authenticated user is already associated with exactly one establishment.
- The DINER role and its authentication flow already exist from the central auth iteration; this specification only adds the CHEF and COUNTER_CLERK roles as new platform roles alongside the existing ADMIN role.
- Password hashing, JWT token management, and session lifecycle are handled by the existing authentication infrastructure and are not re-specified here.
- The ingredient category set (Produce, Meat, Dairy, Dry Goods, Spices) is fixed for this iteration. Extensibility to custom categories is deferred to a future iteration.
- The accepted units of measure (g, kg, ml, l, u) are fixed for this iteration. Unit conversion between compatible measures (e.g., g to kg) is out of scope.
- This iteration does not include employee deactivation/dismissal workflows, automatic low-stock alerts, recipe management, automatic inventory deduction on orders, or supplier integrations. These are explicitly deferred per the scope boundaries stated in the feature description.
- Real-time form validation refers to client-side validation providing immediate user feedback; server-side validation remains the authoritative enforcement layer.
- The audit log is append-only and not exposed through any user-facing deletion or edit interface. Audit retention policies are not addressed in this iteration.
- Temporary credentials for staff accounts do not automatically expire by time; they remain valid in "Pending First Login" status until the employee logs in and completes the mandatory password change, or until an Administrator manually updates the member's details or re-issues the credential.
