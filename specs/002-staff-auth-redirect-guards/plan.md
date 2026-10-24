# Implementation Plan: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Branch**: `refactor/002/auth-guards-logic` | **Date**: 2026-10-23 | **Spec**: [specs/002-staff-auth-redirect-guards/spec.md](spec.md)  
**Status**: Ready for Implementation  
**Constitution Compliance**: v1.1.0 (Principle X: Full-Stack Vertical Slice Integrity)  

---

## Summary

This feature delivers the integrated full-stack vertical slice for staff authentication, deterministic role-based post-login redirection, declarative client-side route guards, single active session enforcement, and dual-tier shift termination across EasyEat's web client and REST backend.

It covers the five official Sprint 1 backlog user stories:
- `ENC-1.1.1`: Counter Manager Credential Login
- `ENC-1.2.1`: Automatic Redirection to Counter Workspace (`/mostrador`)
- `CHF-1.1.1`: Kitchen Chef Credential Login
- `CHF-1.2.1`: Automatic Redirection to Kitchen Workspace (`/cocina`)
- `ADM-1.1.1`: Back-Office Administrator Portal Access and Tenant Isolation (`/admin`)

The technical architecture directly consumes and integrates with the centralized authentication service delivered in `specs/001-central-auth-recovery/` and `easyeat/backend`, introducing zero redundant authentication logic while strictly enforcing perimeter route isolation and session integrity.

---

## Technical Context

- **Language/Runtimes**: Java 21 LTS (Backend), TypeScript 5.6+ / Node.js 20+ (Frontend)
- **Primary Dependencies**:
  - **Backend**: Spring Boot 3.3.4, Spring Security 6.3.3, Spring Data JPA, `jjwt:0.12.6`, Flyway Migration, PostgreSQL JDBC Driver, Jackson Databind.
  - **Frontend**: React 18.3.1, Vite 5.4.8, React Router DOM v6.27.0, Axios 1.7.7, Tailwind CSS 3.4.13, Lucide React 0.453.0.
- **Storage**: PostgreSQL 16+ (database `easyeat_db`) with Flyway migrations (`V1__create_users.sql`).
- **Testing Toolchain**:
  - **Backend**: JUnit 5, Mockito, Spring Security Test, MockMvc, AssertJ.
  - **Frontend**: Vitest 2.1.3, React Testing Library (`@testing-library/react`), `@testing-library/user-event`, `@testing-library/jest-dom`, jsdom.
- **Target Platform**: Desktop and tablet modern browsers (Chrome, Firefox, Safari, Edge); Linux containerized backend service.
- **Performance Criteria**:
  - Post-authentication navigation transition executes in under 500 ms (SC-002).
  - Client-side route guard verification executes synchronously with zero layout flash or content flicker.
- **Architecture Standard**: Monorepo with clean separation of concerns (`easyeat/backend` REST service + `easyeat/frontend` SPA).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-evaluated post Phase 1 design.*

- **Principle I (Specification as Source of Truth)**: PASS. All planned components, routes, and behavior strictly mirror requirements FR-001 through FR-013 and user stories from [spec.md](spec.md).
- **Principle II (Specification Before Implementation)**: PASS. [spec.md](spec.md) is approved and committed; planning artifacts ([plan.md](plan.md), [data-model.md](data-model.md), [openapi.yaml](contracts/openapi.yaml), [research.md](research.md), [quickstart.md](quickstart.md)) precede any production code creation.
- **Principle III (Layered Verification Proportional to Risk)**: PASS. Verification combines frontend unit tests (email normalization, debounce, routing policy), guard integration tests (deep link preservation, silent redirection), backend filter security tests (token versioning), and manual end-to-end scenarios.
- **Principle IV (Simple, Cohesive, and Justified Design)**: PASS. Employs a single integer `token_version` on the `User` entity (KISS) instead of heavy distributed session tables or external Redis caches; employs declarative React Router guards instead of scattered page-level checks.
- **Principle V (Bounded Scope)**: PASS. Operational logic inside workspaces (order dispatch, kitchen timers, administrative menu CRUD) is strictly out of scope; only login, routing, perimeter guards, and session lifecycle are in scope.
- **Principle VI (Controlled Technical Debt)**: PASS. Standardized contracts, typed TypeScript interfaces, and pure routing functions prevent duplicate redirect code.
- **Principle VII (Traceability, Consistency, and Documentation)**: PASS. Every endpoint, contract schema, and test case traces back to specific backlog user stories.
- **Principle VIII (Evidence Over Assumption and Human Escalation)**: PASS. Ambiguities regarding Sprint 1 scope, authentication reuse from Spec 001, and canonical Spanish route naming (`/mostrador`, `/cocina`, `/admin`, `/catalogo`) were resolved and ratified.
- **Principle IX (Engineering Over Convenience)**: PASS. Full-stack verification with contract validation and automated testing suites.
- **Principle X (Full-Stack Vertical Slice Integrity)**: PASS. The plan encompasses both the client-side presentation/routing interaction and the server-side authentication, authorization, and tenant isolation contracts.

---

## Inter-Feature Dependencies

This feature is an incremental vertical slice built upon the foundational infrastructure delivered by `001-central-auth-recovery`:

| Dependency / Artifact | Origin Feature | Role in Feature 002 | Action in Feature 002 |
|---|---|---|---|
| `V1__create_users.sql` | Spec 001 | Provides `users` table with `token_version`, `role`, `status`, `tenant_id` | **Consumed as baseline**. Do NOT recreate or modify migration. |
| `User.java` / `UserRepository.java` | Spec 001 | Domain entity and JPA repository | **Consumed as baseline**. Uses existing `token_version` increment logic. |
| `POST /api/v1/auth/login` | Spec 001 | Authenticates credentials, validates active status, increments `token_version`, returns 8-hour JWT | **Consumed by frontend**. |
| `GET /api/v1/auth/me` | Spec 001 | Validates bearer token signature and asserts matching `token_version` | **Consumed by client on mount/interaction** to detect concurrent session invalidation. |
| `POST /api/v1/auth/logout` | Spec 001 | Atomically increments `token_version` in DB, revoking current token (returns 204) | **Consumed by frontend** on explicit logout action. |
| `JwtService.java` | Spec 001 | Generates and parses JWT with claims (`sub`, `email`, `role`, `tenant_id`, `token_version`) | **Consumed by backend security layer**. |
| `JwtAuthenticationFilter.java` | Spec 001 | Intercepts requests, asserts token signature, expiration, and matching `token_version` | **Active perimeter gate**. Rejects stale tokens with 401 ProblemDetail. |
| `SecurityConfig.java` | Spec 001 | Spring Security configuration | **Synchronize RBAC rules** for staff workspace API perimeters (`/api/v1/admin/**`, `/api/v1/counter/**`, `/api/v1/kitchen/**`). |

---

## Component Architecture (Frontend)

The frontend client resides in `easyeat/frontend/` (React 18 + Vite + TypeScript) and follows a clean layered architecture:

```text
easyeat/frontend/src/
├── api/
│   ├── apiClient.ts                # Axios instance with Bearer token injection & 401 response interceptor
│   └── authApi.ts                  # Typed API functions: login(), getCurrentUser(), logout()
├── context/
│   └── AuthContext.tsx             # Reactive context provider managing identity, role, tenant, and sessionStorage
├── hooks/
│   └── useAuth.ts                  # Consumer hook exposing auth state and login/logout methods
├── router/
│   ├── AppRoutes.tsx               # Declarative React Router v6 tree with route perimeters
│   ├── RoleRouteGuard.tsx          # Higher-order perimeter guard with history replacement (replace: true)
│   └── postAuthRouting.ts          # Pure routing policy: getRoleDefaultRoute(), resolvePostAuthRedirect()
├── components/
│   ├── auth/
│   │   └── LoginForm.tsx           # Controlled login form with debounce, input normalization, error display
│   └── common/
│       └── WorkspaceLayout.tsx     # Shell for operational views (header, role badge, tenant scope, logout)
├── pages/
│   ├── LoginPage.tsx               # Public authentication view
│   ├── CounterWorkspacePage.tsx    # /mostrador landing view (ENCARGADO)
│   ├── KitchenWorkspacePage.tsx    # /cocina landing view (CHEF)
│   ├── AdminWorkspacePage.tsx      # /admin landing view (ADMINISTRADOR)
│   └── CatalogPage.tsx             # /catalogo diner fallback view (COMENSAL)
└── types/
    └── auth.ts                     # TypeScript definitions (Role, AuthenticatedUser, LoginRequest, etc.)
```

### Key Frontend Mechanisms
1. **Input Normalization & Debouncing**:
   - `LoginForm.tsx` trims and lowercases email input before submission.
   - Disables submit button while the authentication request is in flight to prevent duplicate submissions.
2. **Post-Authentication Routing Policy (`postAuthRouting.ts`)**:
   - Pure, fully testable function:
     ```typescript
     export function resolvePostAuthRedirect(role?: Role | null, targetUrl?: string): string {
       const defaultRoute = getRoleDefaultRoute(role);
       if (!targetUrl || targetUrl === '/' || targetUrl === '/login') return defaultRoute;
       if (role && isRouteAllowedForRole(role, targetUrl)) return targetUrl;
       return defaultRoute;
     }
     ```
   - Canonical role workspace destinations:
     - `ENCARGADO` → `/mostrador`
     - `CHEF` → `/cocina`
     - `ADMINISTRADOR` → `/admin`
     - `COMENSAL` / Unassigned → `/catalogo`
3. **Route Perimeter Guard (`RoleRouteGuard.tsx`)**:
   - Evaluates `isAuthenticated` and `allowedRoles`.
   - If unauthenticated: redirects to `/login` with `state: { from: location }` and `replace: true`.
   - If authenticated but role unauthorized (cross-role navigation): silently redirects to `getRoleDefaultRoute(user.role)` with `replace: true` (no error banners, no history pollution).
4. **Session Eviction via Interceptor (`apiClient.ts`)**:
   - Global Axios response interceptor intercepts HTTP 401 responses.
   - Clears `sessionStorage`, resets `AuthContext`, and redirects to `/login` with `{ state: { sessionExpired: true } }`.

---

## Backend Architecture & Security Integration

The server resides in `easyeat/backend/` (Spring Boot 3.3.4 + Spring Security 6):

```text
easyeat/backend/src/main/java/com/easyeat/
├── auth/
│   ├── controller/AuthController.java          # REST endpoints: /login, /me, /logout
│   ├── dto/                                    # LoginRequest, AuthTokenResponse, AuthenticatedUserResponse
│   ├── security/
│   │   ├── JwtService.java                    # Token issuance & claims extraction
│   │   ├── JwtAuthenticationFilter.java       # Token validation & single active session assertion
│   │   └── SecurityAuditLogger.java           # Structured audit events
│   └── service/AuthService.java                # Authentication logic, token version increment
└── config/
    ├── SecurityConfig.java                     # SecurityFilterChain, RBAC rules, exception handling
    └── MethodSecurityConfig.java               # @EnableMethodSecurity for @PreAuthorize
```

### Security Enforcement Invariants
1. **Single Active Session**:
   - `AuthService.login()` atomically increments `user.token_version` in the database and embeds the new version in the JWT claim.
   - `JwtAuthenticationFilter` asserts `claim.token_version == user.getTokenVersion()`. If mismatched, it terminates the request with HTTP 401 ProblemDetail (`Tu sesión ha expirado o ha sido invalidada`).
2. **Explicit Shift Termination**:
   - `AuthController.logout()` calls `AuthService.logout()`, incrementing `token_version` in DB and returning HTTP 204 No Content.
3. **Role-Based Access Control (RBAC)**:
   - Workspace API perimeters enforce `@PreAuthorize("hasRole('ENCARGADO')")`, `@PreAuthorize("hasRole('CHEF')")`, `@PreAuthorize("hasRole('ADMINISTRADOR')")`.
   - Unauthorized attempts return HTTP 403 Forbidden with RFC 9457 `ProblemDetail`.
4. **Tenant Isolation**:
   - Queries and operational handlers scope records strictly by `tenant_id` extracted from the authenticated user principal.
5. **Cache Control**:
   - Enforces `Cache-Control: no-store` headers across all protected endpoints to prevent sensitive operational data caching.

---

## Design Artifacts & Contracts Synchronization

1. **Contracts**: [contracts/openapi.yaml](contracts/openapi.yaml) provides the formal OpenAPI 3.0.3 specification for `/api/v1/auth/login`, `/api/v1/auth/me`, and `/api/v1/auth/logout`, with RFC 9457 `ProblemDetail` error structures.
2. **Data Model**: [data-model.md](data-model.md) defines database schemas, JWT token claims, TypeScript client interfaces, route mapping matrices, and session state transitions.
3. **Research**: [research.md](research.md) records technical decisions on session invalidation, client persistence, declarative guard design, and deep link preservation.
4. **Quickstart**: [quickstart.md](quickstart.md) details automated test commands and 5 step-by-step manual validation scenarios.

---

## Testing & Layered Verification Strategy

### 1. Frontend Automated Tests (Vitest 2.1+)
- **Routing Unit Tests** (`tests/router/postAuthRouting.test.ts`):
  - Matrix test of all 4 roles to default routes (`/mostrador`, `/cocina`, `/admin`, `/catalogo`).
  - Fallback tests for undefined, null, or corrupted role values.
  - Deep link resolution tests: matching target preserved, mismatched target discarded.
- **Form Component Tests** (`tests/components/LoginForm.test.tsx`):
  - Validation blocking on empty fields.
  - Email trimming and lowercasing prior to submission.
  - Submit button disabling while authentication is in flight.
  - Anti-enumeration generic error display on 401.
- **Route Guard Tests** (`tests/router/RoleRouteGuard.test.tsx`):
  - Unauthenticated access redirects to `/login` preserving location in state.
  - Cross-role navigation triggers silent redirect to user's assigned workspace with `replace: true`.
  - Browser back button entrapment prevention.
- **Session Lifecycle Tests** (`tests/session/SessionLifecycle.test.tsx`):
  - Interceptor catches 401, evicts `sessionStorage`, and routes to `/login` with `"Tu sesión ha expirado"`.
  - Clean logout purges client state and redirects to `/login`.

### 2. Backend Automated Tests (JUnit 5 + MockMvc)
- **Controller Contract Tests** (`AuthControllerTest.java`):
  - `POST /api/v1/auth/login` returns valid JWT carrying `sub`, `role`, `tenant_id`, and `token_version`.
  - `GET /api/v1/auth/me` returns current user summary or 401 on stale session.
  - `POST /api/v1/auth/logout` increments `token_version` and returns HTTP 204.
- **Security Filter Tests** (`JwtAuthenticationFilterTest.java`):
  - Rejection of expired tokens (HTTP 401).
  - Rejection of tokens with outdated `token_version` (concurrent login simulation, HTTP 401).
- **RBAC & Isolation Tests**:
  - Verification of HTTP 403 Forbidden when requesting another role's endpoint.
  - Verification of strict tenant scoping on authenticated queries.

---

## Scope Control & Explicit Boundaries

### In Scope
- Web login interface and post-login deterministic redirection.
- Declarative client route guards for `/mostrador`, `/cocina`, `/admin`, and `/catalogo`.
- Session persistence in `sessionStorage` surviving page reloads during active shifts.
- Single active session enforcement via `token_version` across both tiers.
- Explicit logout endpoint and client eviction.
- Full automated test suites across frontend (Vitest) and backend (JUnit 5).

### Out of Scope
- Internal operational kitchen ticket lifecycles, timers, or KDS dragging logic.
- Counter QR code scanning, order handoff confirmation, or cash drawer operations.
- Administrative menu item CRUD, price configuration, or staff provisioning.
- Third-party OAuth2/SSO or biometric authentication.
