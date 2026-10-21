# Technical Research and Decision Log (Phase 0)

**Feature**: Staff Management, RBAC, and Initial Ingredient Inventory  
**Branch**: `004-staff-mgmt-rbac-inventory`  
**Date**: 2026-10-20  
**Status**: Completed  

---

## Executive Summary

This document consolidates the technical research and architecture design decisions for the staff management module, role-based access control (RBAC), multi-tenant isolation, and master ingredient catalog with stock adjustments and immutable audit logging.

The technical design builds upon and directly extends the foundation established in iteration 001 (`central-auth-recovery`):
- **Language and Runtime**: Java 21 (LTS).
- **Core Framework**: Spring Boot 3.3.4 with Spring Security 6.3.
- **Persistence**: PostgreSQL 16 with Flyway schema migration and Spring Data JPA.
- **Authentication**: JSON Web Tokens (JJWT 0.12.6) with cryptographic claims asserting identity, role, and `tenant_id`.
- **Testing Strategy**: JUnit 5, Mockito, and Testcontainers running real PostgreSQL for isolated integration tests.

---

## Architectural and Implementation Decision Log

### D-001 · Backend Modular Structure and Package Layout

- **Decision**: Organize the new components into two vertical domain packages under `easyeat/backend/src/main/java/com/easyeat/`:
  1. `com.easyeat.staff`: Staff member administration, role assignment, and initial credential controls.
  2. `com.easyeat.inventory`: Raw ingredient master catalog, lifecycle management, physical stock adjustments, and immutable audit log.
- **Rationale**: Preserves high cohesion within bounded contexts, cleanly separating administrative staff operations from kitchen ingredient management, while reusing `com.easyeat.auth` for core security infrastructure and JWT verification.
- **Alternatives Considered**:
  - *Layered monolith (`controllers`, `services`, `repositories` globally)*: Rejected because it dilutes domain cohesion and hinders maintainability as user stories grow.
  - *Separate Maven submodules (`staff-service`, `inventory-service`)*: Rejected for MVP scope; introduces build and deployment overhead without operational justification.

---

### D-002 · Role-Based Access Control (RBAC) and Multi-Tenant Isolation

- **Decision**:
  1. Implement a `TenantContextFilter` to extract and validate the `tenant_id` claim from verified JWTs, storing it in a `TenantContextHolder` backed by `ThreadLocal`.
  2. Enforce declarative method-level security using `@PreAuthorize("hasRole('ADMIN')")`, `@PreAuthorize("hasAnyRole('ADMIN', 'CHEF')")`, etc., enabled via `@EnableMethodSecurity`.
  3. Enforce mandatory tenant scoping in all persistence queries and mutations (`UserRepository`, `IngredientRepository`, `StockAuditEntryRepository`).
  4. On unauthorized access attempts (role violation or cross-tenant access attempt), respond with HTTP `403 Forbidden` structured according to RFC 9457 (`ProblemDetail`) and persist a structured security audit log entry via SLF4J (`SecurityAuditLogger`).
- **Rationale**: Guarantees strict isolation across transport, application, and data layers. Cryptographically signed tokens prevent clients from tampering with `tenant_id`. Enforcing tenant parameters in database queries prevents cross-tenant data leaks.
- **Alternatives Considered**:
  - *PostgreSQL Row-Level Security (RLS)*: Powerful, but increases connection pool complexity (requires setting session variables per connection in HikariCP) and adds friction to lightweight test suites.

---

### D-003 · Concurrency and Pessimistic Locking for Stock Adjustments

- **Decision**: Use pessimistic write locking (`SELECT ... FOR UPDATE`) on ingredient retrieval prior to any stock adjustment:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Ingredient i WHERE i.id = :id AND i.tenantId = :tenantId")
  Optional<Ingredient> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
  ```
- **Rationale**: Requirements FR-016 and FR-020 strictly mandate that resulting stock cannot be negative (`resulting_stock >= 0`) and that concurrent stock adjustments on the same ingredient must not cause race conditions or lost updates. Pessimistic locking serializes the ingredient row at the database level during the `@Transactional` boundary, guaranteeing atomic balance updates and audit logging.
- **Alternatives Considered**:
  - *Optimistic Locking (`@Version`)*: If two administrators adjust stock simultaneously, one transaction would fail with `OptimisticLockException`, requiring manual retry workflows that complicate physical warehouse inventory counts. Pessimistic locking provides deterministic execution.

---

### D-004 · Exact Numeric Precision for Physical Quantities and Costs

- **Decision**:
  1. In Java: Use `BigDecimal` for continuous physical quantities and financial costs.
  2. In PostgreSQL: Use `NUMERIC(12, 4)` for stock quantities, deltas, and unit purchase costs.
  3. For discrete units (`u`): Validate in the service layer that the effective scale is zero or that the value represents an integer (e.g., `value.stripTrailingZeros().scale() <= 0`).
  4. For continuous units (`g`, `kg`, `ml`, `l`): Accept and store decimal quantities with up to 3 decimal places for physical quantities (e.g., 1.750 kg, 250.500 g).
- **Rationale**: Strictly adheres to the inventory and accounting precision invariant (FR-025), eliminating rounding errors inherent to binary floating-point representations (`double` or `float`).
- **Alternatives Considered**:
  - *Integer-only smallest units (e.g., milligrams or cents)*: Increases conversion complexity across presentation and query layers, making direct database inspection and audit trail reviews error-prone. `NUMERIC(12, 4)` offers native exact precision.

---

### D-005 · Temporary Credential Provisioning and First Login Flow

- **Decision**:
  1. Generate a cryptographically secure random 12-character credential (including uppercase, lowercase, digits, and special characters) via `SecureRandom`.
  2. Store its hash using `BCryptPasswordEncoder` (cost factor 12).
  3. Create the staff member in `users` with status `PENDING_FIRST_LOGIN` (and boolean flag `must_change_password = true`).
  4. Return the plaintext credential exclusively in the HTTP 201 Created response to the registering Administrator for a one-time screen display with clipboard copy support.
  5. The temporary credential does not expire automatically by time; it remains valid until the first login or until an Administrator re-issues it.
  6. On successful initial authentication, the system detects `must_change_password = true` and blocks access to platform resources, requiring the user to invoke `POST /api/v1/auth/first-login-password-change`, updating the hash and transitioning the account to `ACTIVE`.
- **Rationale**: Aligns completely with clarification decisions (Session 2026-10-20) and requirements FR-003 and FR-004, preventing credential leakage in logs or insecure storage.
- **Alternatives Considered**:
  - *Automated email delivery*: Rejected in the clarification session in favor of one-time on-screen display with clipboard copy.

---

### D-006 · Orphaned Administrator and Self-Demotion Prevention

- **Decision**: In profile update operations (`PUT /api/v1/admin/staff/{id}`) and role reassignments:
  1. If an attempt is made to reassign a user with role `ADMIN` to another role (`CHEF` or `COUNTER_CLERK`):
  2. Execute a validation query: `SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.role = 'ADMIN' AND u.status = 'ACTIVE' AND u.id != :targetUserId`.
  3. If remaining active administrators count is 0, abort the transaction and respond with HTTP `409 Conflict` (or `422 Unprocessable Entity`) with message: "Cannot demote the sole active Administrator of the establishment".
- **Rationale**: Enforces governance invariant FR-006, ensuring restaurants never enter an unmanageable state without an active Administrator.
- **Alternatives Considered**:
  - *Client-side-only enforcement*: Rejected; business invariants must be strictly enforced at the backend layer.

---

### D-007 · Ingredient Lifecycle State Machine

- **Decision**: Model the ingredient lifecycle through three formal statuses:
  - `ACTIVE`: Available ingredient. Visible to both Administrator (inventory management) and Chef (kitchen catalog). Allows stock adjustments.
  - `DISCONTINUED`: Decommissioned ingredient with physical stock remaining greater than zero. Excluded from Chef's catalog. Retained in Administrator inventory and **allows stock adjustments** (e.g., spoilage write-offs, physical counts) until stock reaches 0. Can be reactivated to `ACTIVE` by an Administrator.
  - `ARCHIVED`: Permanently archived. Permitted only when `current_stock == 0`. Excluded from operational views. Does not allow stock adjustments.
- **Authorized Transitions**:
  - `ACTIVE` → `DISCONTINUED`: Permitted if `current_stock > 0`.
  - `ACTIVE` → `ARCHIVED`: Permitted strictly when `current_stock == 0`.
  - `DISCONTINUED` → `ACTIVE`: Permitted at any time by an Administrator (restores Chef visibility).
  - `DISCONTINUED` → `ARCHIVED`: Permitted strictly when `current_stock == 0`.
- **Unit of Measure Modification Restriction**: Changing unit of measure is allowed only if `current_stock == 0` and no conflicting stock history exists (FR-013).
- **Rationale**: Faithfully mirrors restaurant kitchen operations and incorporates all clarification decisions.
- **Alternatives Considered**:
  - *Hard physical deletion (`DELETE` in SQL)*: Inadmissible; would destroy historical referential integrity and audit records.

---

### D-008 · Pagination and Secure Projections for Chef Catalog

- **Decision**:
  1. Provide a dedicated kitchen endpoint: `GET /api/v1/chef/ingredients`.
  2. Query parameters: `page` (default 0), `size` (default 20, FR-021), `category` (optional), `search` (optional case-insensitive name filter).
  3. Mandatory default sort: Alphabetical ascending by name (`ORDER BY LOWER(name) ASC`).
  4. Response projection DTO (`ChefIngredientResponseDto`):
     - `id`: UUID.
     - `name`: String.
     - `category`: String.
     - `unitOfMeasure`: String.
     - `stockStatus`: String (`IN_STOCK` if `current_stock > 0`, `OUT_OF_STOCK` if `current_stock == 0`).
  5. **Deliberate omission of sensitive fields**: The DTO does not expose `unitPurchaseCost` or stock adjustment audit logs, strictly complying with FR-024.
- **Rationale**: Optimizes network payload via indexed pagination and enforces the principle of least privilege regarding business financial data.
- **Alternatives Considered**:
  - *Reuse Administrator endpoint with frontend filtering*: Critical security violation; would leak financial purchase costs to Chef clients.

---

### D-009 · Immutability and Traceability for Stock Audit Log

- **Decision**:
  1. Manage `stock_audit_entries` as append-only (`INSERT` only).
  2. No update (`UPDATE`) or delete (`DELETE`) methods in repository or API controllers.
  3. PostgreSQL database rules/triggers: Reject any `UPDATE` or `DELETE` statements on `stock_audit_entries` at the DDL/database level for strict immutability (FR-019).
  4. Stored attributes: `id`, `tenant_id`, `ingredient_id`, `user_id` (admin identity), `adjustment_mode` (`ABSOLUTE` or `RELATIVE`), `previous_stock`, `new_stock`, `net_delta`, `justification`, and `created_at`.
- **Rationale**: Guarantees forensic audit integrity required for food cost management, spoilage tracing, and inventory reconciliation.
- **Alternatives Considered**:
  - *Store only current balance in ingredient row*: Rejected; destroys historical traceability and justification mandated by FR-018.

---

## Technology Stack and Version Pinning Matrix

| Component | Technology Choice | Rationale |
|---|---|---|
| Execution Runtime | Java 21 (LTS) / Spring Boot 3.3.4 | Consistency with iteration 001, long-term LTS enterprise support |
| Security | Spring Security 6.3 + JJWT 0.12.6 | Cryptographic claim validation and declarative method-level RBAC |
| Persistence Engine | PostgreSQL 16 + Hibernate ORM 6.5 | Exact `NUMERIC` types, native UUIDs, and pessimistic row locking |
| Migrations | Flyway 10.x | Versioned, deterministic SQL schema evolution |
| Multi-Tenant Isolation | `TenantContextHolder` (ThreadLocal) + Filter | Cross-cutting `tenant_id` injection in service and data layers |
| Automated Testing | JUnit 5 + Mockito + Testcontainers (PostgreSQL) | Multithreaded concurrency tests and schema verification on real DB |
