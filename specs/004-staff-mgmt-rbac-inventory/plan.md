# Technical Implementation Plan: Staff Management, RBAC, and Initial Ingredient Inventory

**Branch**: `004-staff-mgmt-rbac-inventory` | **Date**: 2026-10-20 | **Spec**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/spec.md)

**Input**: Feature specification from `/specs/004-staff-mgmt-rbac-inventory/spec.md` and clarification agreements from 2026-10-20.

---

## Executive Summary

This feature extends the EasyEat multi-tenant platform by introducing restaurant staff roster administration (`ADM-3.1.1`), role-based access control with strict tenant isolation (`ADM-3.1.2`), the master ingredient catalog with operational lifecycle management (`ADM-4.1.1`), manual physical stock adjustments with immutable audit trail (`ADM-4.1.2`), and paginated kitchen catalog exploration for the Chef (`CHF-2.1.1`).

The technical design reuses the architecture established in iteration 001 (`central-auth-recovery`), deploying REST services built on **Java 21** and **Spring Boot 3.3.4**, relational persistence in **PostgreSQL 16** with schema evolution managed by **Flyway**, signed **JWT** tokens with role and establishment claims, database pessimistic locking to guarantee the non-negative stock invariant under concurrency, and automated testing via **JUnit 5**, **Mockito**, and **Testcontainers**.

---

## Technical Context

**Language / Version**: Java 21 (LTS)  
**Primary Dependencies**:
- Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`)
- JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- Flyway Core + Flyway PostgreSQL Database Extension 10.x
- PostgreSQL JDBC Driver 42.7.x
- Lombok (for boilerplate reduction across DTOs and entities)

**Storage & Persistence**:
- PostgreSQL 16 with native `UUID` (`gen_random_uuid()`), `TIMESTAMPTZ`, and `NUMERIC(12, 4)`.
- Pessimistic write locking (`SELECT ... FOR UPDATE`) during inventory adjustments.
- Immutable append-only audit tables with rules preventing `UPDATE` and `DELETE`.

**Testing Strategy**:
- JUnit 5 and Mockito for domain service and validator unit tests.
- Spring Security Test (`MockMvc`, `@WithMockUser`) for security filter and role authorization verification.
- Testcontainers running PostgreSQL 16 for integration tests, Flyway migration execution, and multithreaded concurrency tests.

**Target Platform**: Linux containers / Docker (production); local JVM execution for development.  
**Project Type**: Web service / REST API (modular backend within monorepo).  

**Performance Goals**:
- Staff registration and roster retrieval: p95 latency ≤ 150ms.
- Stock adjustment transaction under pessimistic lock: p95 latency ≤ 100ms.
- Chef paginated catalog query (20 items indexed by tenant and name): p95 latency ≤ 50ms.

**Domain & Security Constraints**:
- Temporary credentials hashed with BCrypt (cost factor 12) and displayed once on screen.
- Mandatory password change on first login (`must_change_password = true`).
- Multi-tenant isolation: all queries must be scoped to `tenant_id`.
- Exact accounting numeric precision with `BigDecimal` and `NUMERIC(12, 4)`.
- Orphaned admin prevention: reject operations that leave the establishment with zero Administrators.

**Scale / Scope**: MVP — 4 formal platform roles (`ADMIN`, `CHEF`, `COUNTER_CLERK`, `DINER`), multi-tenant shared-schema architecture with `tenant_id` discrimination.

---

## Constitution Check

*GATE: Pre-design and post-design compliance evaluation against `.specify/memory/constitution.md`.*

| Principle | Pre-Design Status | Post-Design Status | Evidence and Justification |
|---|---|---|---|
| **I. The Specification Is the Source of Truth** | ✅ PASS | ✅ PASS | The plan, data model, and OpenAPI contracts derive strictly from FR-001 through FR-027. No unsolicited features were added. |
| **II. Specification Before Implementation** | ✅ PASS | ✅ PASS | Completed `spec.md`, clarifications, `research.md`, `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md` prior to code generation. |
| **III. Layered Verification Proportional to Risk** | ✅ PASS | ✅ PASS | Testing strategy defined with unit tests (Mockito), security slice tests (MockMvc), and real integration tests (Testcontainers with concurrency testing). |
| **IV. Simple, Cohesive, and Justified Design** | ✅ PASS | ✅ PASS | Bounded context package structure (`com.easyeat.staff`, `com.easyeat.inventory`). Pessimistic locking justified to eliminate stock race conditions. |
| **V. Bounded Scope** | ✅ PASS | ✅ PASS | Strictly bounded (no recipes N:M or payment gateways in this iteration; scheduled for Sprints 2 and 4). |
| **VI. Controlled Technical Debt** | ✅ PASS | ✅ PASS | All architectural decisions and trade-offs documented in `research.md`. |
| **VII. Traceability, Consistency, and Documentation** | ✅ PASS | ✅ PASS | Exhaustive traceability matrix in `data-model.md` linking each functional requirement to entities and relational constraints. |
| **VIII. Evidence Over Assumption and Human Escalation** | ✅ PASS | ✅ PASS | Executed interactive clarification session resolving 5 key domain ambiguities before technical design. |
| **IX. Engineering Over Convenience** | ✅ PASS | ✅ PASS | Exact decimal precision (`BigDecimal` / `NUMERIC`), pessimistic locking in concurrency, and immutable audit logs instead of superficial shortcuts. |
| **X. Technology-Agnostic Governance** | ✅ PASS | ✅ PASS | Architecture follows sound software engineering principles while maintaining stack continuity. |

**GATE RESULT: ALL PASS**

---

## Project Structure

### Documentation (`specs/004-staff-mgmt-rbac-inventory/`)

```text
specs/004-staff-mgmt-rbac-inventory/
├── plan.md              # Technical implementation plan
├── research.md          # Technical research and decision log (Phase 0)
├── data-model.md        # Entities, SQL schema, state transitions, and traceability matrix (Phase 1)
├── quickstart.md        # End-to-end executable validation guide (Phase 1)
├── contracts/
│   └── openapi.yaml     # OpenAPI 3.0 interface specification (Phase 1)
└── checklists/
    └── requirements.md  # Requirements quality checklist
```

### Source Code (`easyeat/backend/`)

```text
easyeat/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/easyeat/
    │   │   ├── EasyEatApplication.java
    │   │   ├── auth/                                    # Auth Infrastructure (Iteration 001)
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── dto/FirstLoginPasswordChangeRequest.java
    │   │   │   ├── entity/User.java
    │   │   │   └── security/JwtService.java
    │   │   ├── common/
    │   │   │   ├── context/TenantContextHolder.java     # ThreadLocal context for current tenant
    │   │   │   ├── filter/TenantContextFilter.java      # Extracts tenant_id from JWT claims
    │   │   │   └── audit/SecurityAuditLogger.java       # Structured security audit logger
    │   │   ├── staff/                                   # Staff Management & RBAC (Iteration 004)
    │   │   │   ├── controller/StaffController.java      # /api/v1/admin/staff/** endpoints
    │   │   │   ├── dto/StaffRegistrationRequest.java
    │   │   │   ├── dto/StaffRegistrationResponse.java
    │   │   │   ├── dto/StaffUpdateRequest.java
    │   │   │   ├── dto/StaffMemberResponse.java
    │   │   │   ├── service/StaffService.java            # Registration, update, one-time credential, orphaned admin check
    │   │   │   └── repository/StaffUserRepository.java
    │   │   ├── inventory/                               # Catalog & Inventory (Iteration 004)
    │   │   │   ├── controller/AdminIngredientController.java # /api/v1/admin/ingredients/** endpoints
    │   │   │   ├── controller/ChefCatalogController.java     # /api/v1/chef/ingredients endpoints
    │   │   │   ├── dto/CreateIngredientRequest.java
    │   │   │   ├── dto/UpdateIngredientRequest.java
    │   │   │   ├── dto/AdminIngredientDetailResponse.java
    │   │   │   ├── dto/StockAdjustmentRequest.java
    │   │   │   ├── dto/StockAdjustmentResponse.java
    │   │   │   ├── dto/StockAuditEntryResponse.java
    │   │   │   ├── dto/ChefIngredientPageResponse.java
    │   │   │   ├── entity/Ingredient.java
    │   │   │   ├── entity/StockAuditEntry.java
    │   │   │   ├── repository/IngredientRepository.java      # Query with pessimistic write lock
    │   │   │   ├── repository/StockAuditEntryRepository.java # Append-only repository
    │   │   │   ├── service/IngredientService.java            # Master catalog CRUD & lifecycle
    │   │   │   └── service/StockAdjustmentService.java       # Atomic inventory adjustment & audit
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java                  # Role configurations and WebSecurityCustomizer
    │   │   │   └── MethodSecurityConfig.java            # Enables @EnableMethodSecurity
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java          # RFC 9457 ProblemDetail handler
    │   │       ├── NegativeStockException.java
    │   │       ├── OrphanedAdminException.java
    │   │       └── TenantViolationException.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           ├── V1__create_users.sql                 # Baseline users (001)
    │           ├── V2__create_password_reset_tokens.sql # Password reset tokens (001)
    │           ├── V3__extend_users_staff.sql           # Staff members and first login (004)
    │           └── V4__create_ingredients_and_stock_audit.sql # Master catalog & stock audit (004)
    └── test/
        ├── java/com/easyeat/
        │   ├── staff/
        │   │   ├── controller/StaffControllerTest.java  # MockMvc RBAC and staff endpoint tests
        │   │   └── service/StaffServiceTest.java        # Registration & orphaned admin unit tests
        │   ├── inventory/
        │   │   ├── controller/IngredientControllerTest.java # MockMvc catalog and Chef endpoint tests
        │   │   ├── service/StockAdjustmentServiceTest.java  # Stock adjustment invariant unit tests
        │   │   └── concurrency/StockAdjustmentConcurrencyTest.java # Multithreaded Testcontainers test
        │   └── security/
        │       └── TenantIsolationIntegrationTest.java  # Strict multi-tenant isolation integration test
        └── resources/
            └── application-test.yml
```

**Structure Decision**: Modular domain-driven organization (`staff`, `inventory`, `auth`) within a single deployable backend application (`easyeat/backend/`), maximizing cohesion while simplifying observability and deployment.

---

## Complexity Tracking

> **Note**: No violations or deviations from the Project Constitution were detected.
> All introduced patterns (pessimistic locking, tenant filter, projection DTOs) directly satisfy functional requirements FR-001 through FR-027.

| Component / Pattern | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Pessimistic locking (`SELECT ... FOR UPDATE`) | Atomically guarantees non-negative stock (`>= 0`) under concurrent physical adjustments. | Optimistic locking (`@Version`): rejects concurrent adjustments with exceptions, requiring cumbersome retries for warehouse staff. |
| Immutable `stock_audit_entries` table | Explicit requirement for forensic accounting and untampered inventory trail (FR-019). | Storing only balance in `ingredients`: loses user identity, delta, timestamp, and justification mandated by FR-018. |
| Tenant filter & context (`ThreadLocal`) | Guarantees queries never expose or alter data from another restaurant establishment. | Manual `tenant_id` parameters in every controller: prone to human error and cross-tenant data leaks. |
| Dedicated Chef projection DTO | Protects sensitive commercial data (unit purchase costs) per principle of least privilege. | Reusing Administrator DTO: leaks internal financial costs to kitchen clients. |
