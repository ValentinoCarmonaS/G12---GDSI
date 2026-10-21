# Implementation Plan: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Branch**: `002-staff-auth-redirect-guards` | **Date**: 2026-10-17 | **Spec**: [specs/002-staff-auth-redirect-guards/spec.md](spec.md)

**Input**: Feature specification from `/specs/002-staff-auth-redirect-guards/spec.md`

## Summary

This feature establishes the web login gateway, declarative client-side route guards, post-authentication role routing, and single active session lifecycle for EasyEat's staff profiles (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`) and diner fallback (`COMENSAL`). 

The technical approach introduces:
1. A unified frontend React login view (`LoginForm.tsx`) consuming the centralized `POST /api/v1/auth/login` endpoint with input normalization and anti-enumeration handling.
2. An automatic post-authentication routing policy in React Router v6 that immediately navigates the user to their designated workspace (`/counter`, `/kds`, `/admin`, or `/catalog`) while resolving deep links.
3. Declarative route guards (`RoleRouteGuard.tsx`) executing perimeter authorization with silent cross-role redirection and history replacement (`replace: true`).
4. Single active session enforcement using an atomically incremented `token_version` on the `User` entity in PostgreSQL, verified on every request by Spring Security's `JwtAuthenticationFilter`, coupled with client-side 401 response interception and session expiration handling.
5. An explicit shift termination endpoint (`POST /api/v1/auth/logout`) invalidating tokens and clearing client storage.

## Technical Context

**Language/Version**: Java 21 LTS (Backend), TypeScript 5.5+ / Node.js 20+ (Frontend)  
**Primary Dependencies**: 
- Backend: Spring Boot 3.3.4, Spring Security 6.3.x, Spring Data JPA, `jjwt-api:0.12.6`, Flyway Migration
- Frontend: React 18.3+, Vite 5+, React Router v6.26+, Axios / Fetch API wrapper, Tailwind CSS
**Storage**: PostgreSQL 16+ with Flyway schema migration  
**Testing**: 
- Backend: JUnit 5, Mockito, Spring Security Test, MockMvc
- Frontend: Jest 29+, React Testing Library (`@testing-library/react`, `@testing-library/user-event`)
**Target Platform**: Standard modern web browsers (Chrome, Firefox, Safari, Edge) on desktop and tablet viewports; Linux/Docker container backend  
**Project Type**: Monorepo Web Application (`easyeat/backend` REST service + `easyeat/frontend` SPA client)  
**Performance Goals**: Automated redirection to role workspace completes in < 2 seconds; route guard perimeter checks execute synchronously in < 10ms with zero visual layout flicker  
**Constraints**: Zero cross-role data/view exposure; atomic token version verification; single active session per staff account; RFC 9457 ProblemDetail compliance for error envelopes  
**Scale/Scope**: 4 primary personas, 3 staff workspace routes, 1 catalog route, unified authentication contract  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Specification as Source of Truth)**: PASS. All planned components, routes, and behavior strictly mirror requirements FR-001 through FR-018 from `spec.md`.
- **Principle II (Specification Before Implementation)**: PASS. `spec.md` is approved and committed; planning artifacts (`research.md`, `data-model.md`, `openapi.yaml`, `quickstart.md`, `plan.md`) precede any code creation.
- **Principle III (Layered Verification Proportional to Risk)**: PASS. Verification combines unit validation (email normalization, debounce), router integration tests (guards, deep link resolution, history replacement), backend filter security tests, and end-to-end scenario guides.
- **Principle IV (Simple, Cohesive, and Justified Design)**: PASS. Uses single integer `token_version` on `User` entity (KISS) instead of heavy session tables or external Redis caches; uses declarative React Router guards instead of scattered ad-hoc page checks.
- **Principle V (Bounded Scope)**: PASS. Workspace internal operational logic (tickets, cooking timers, admin CRUD) is explicitly excluded; only authentication, routing guards, and landing layouts are designed.
- **Principle VI (Controlled Technical Debt)**: PASS. Standardized contracts, typed TypeScript models, and centralized routing helper eliminate duplicate redirect logic.
- **Principle VII (Traceability, Consistency, and Documentation)**: PASS. Every test case and contract endpoint maps directly to user stories and functional requirements.
- **Principle VIII (Evidence Over Assumption and Human Escalation)**: PASS. All cross-role, concurrent session, and expiration ambiguities were escalated and formally resolved via `/grill-me`.
- **Principle IX (Engineering Over Convenience)**: PASS. Quality gates and test matrices are strictly maintained.
- **Principle X (Agnostic Constitution & Specific Artifacts)**: PASS. Technical stack choices are declared strictly in specification and planning artifacts.

## Inter-Feature Dependencies

> **CRITICAL**: This feature depends on `001-central-auth-recovery` for all backend infrastructure.
> Phase 2 (Foundational) of this feature MUST NOT begin until the `001-central-auth-recovery`
> branch is merged into the shared base branch and its Flyway migrations have been applied.

| Dependency | Provider | Required By | Protocol |
|---|---|---|---|
| `V1__create_users.sql` — `users` table with `token_version`, `role`, `status`, `tenant_id` | 001 | Phase 2 prerequisite | Verify existence. **DO NOT recreate.** |
| `V2__create_password_reset_tokens.sql` — `password_reset_tokens` table | 001 | Phase 2 prerequisite | Verify existence. **DO NOT recreate.** |
| `User.java` — JPA entity (all columns, including `token_version`) | 001 | T036 (`JwtAuthenticationFilter`) | Verify existence. **DO NOT recreate.** |
| `UserRepository.java` — `findByEmail()`, `findById()` | 001 | T036, T015 | Verify existence. **DO NOT recreate.** |
| `POST /api/v1/auth/login` endpoint — JWT issuance with `role`, `tenant_id`, `token_version` | 001 | T019 (frontend `authApi.ts`), all US1 frontend tests | 002 **consumes** this endpoint. Does not re-implement it. |
| `JwtService.java` — `validateToken()`, `extractClaims()` | 001 | T036 (`JwtAuthenticationFilter`) | Verify existence. **DO NOT recreate.** |
| `RateLimitingService.java` — Caffeine per-email and per-IP limiters | 001 | Referenced only — rate limiting on login is handled by 001 | No action required from 002. |
| `SecurityConfig.java` — base `SecurityFilterChain`, BCrypt bean, stateless session policy | 001 | T037 (register `JwtAuthenticationFilter`) | 002 **extends** this config to add the filter. Does not replace it. |
| `GlobalExceptionHandler.java` — RFC 9457 `ProblemDetail` `@RestControllerAdvice` | 001 | T007 reference | Do not create a second advice bean. Extend 001's handler if new exception types are needed. |

**Coordination requirement**: `AuthService.login()` in 001 MUST atomically increment `token_version`
on every successful login. This is required for single active session enforcement (US4, FR-013).
This behavior is within 001's implementation scope but is a **contract requirement of 002**.
The 002 developer must communicate this to the 001 developer before 001's tasks are implemented.

---

## Project Structure

### Documentation (this feature)

```text
specs/002-staff-auth-redirect-guards/
├── spec.md              # Functional specification (approved)
├── plan.md              # Implementation plan (this file)
├── research.md          # Phase 0 research & technical decisions
├── data-model.md        # Phase 1 data entities, claims, and state transitions
├── quickstart.md        # Phase 1 verification and manual testing guide
├── checklists/
│   └── requirements.md  # Spec quality validation checklist
└── contracts/
    └── openapi.yaml     # Phase 1 OpenAPI 3.0 contract specification
```

### Source Code Layout (monorepo layout under `easyeat/`)

```text
easyeat/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/easyeat/
│       │   │   ├── auth/
│       │   │   │   ├── controller/AuthController.java          # ← FROM 001 | 002 adds: POST /api/v1/auth/logout
│       │   │   │   ├── dto/LoginRequest.java                   # ← FROM 001 | do not recreate
│       │   │   │   ├── dto/LoginResponse.java                  # ← FROM 001 | do not recreate
│       │   │   │   ├── service/AuthService.java                # ← FROM 001 | 002 adds: logout() method
│       │   │   │   ├── security/
│       │   │   │   │   ├── JwtService.java                    # ← FROM 001 | do not recreate
│       │   │   │   │   ├── JwtAuthenticationFilter.java       # ★ NEW in 002 | single active session check
│       │   │   │   │   └── RateLimitingService.java           # ← FROM 001 | do not recreate
│       │   │   │   ├── entity/
│       │   │   │   │   ├── User.java                          # ← FROM 001 | do not recreate
│       │   │   │   │   └── PasswordResetToken.java            # ← FROM 001 | do not recreate
│       │   │   │   └── repository/
│       │   │   │       └── UserRepository.java                # ← FROM 001 | do not recreate
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java                    # ← FROM 001 | 002 extends: registers JwtAuthenticationFilter
│       │   │   │   └── AppConfig.java                         # ← FROM 001 | do not recreate
│       │   │   └── exception/
│       │   │       └── GlobalExceptionHandler.java            # ← FROM 001 | extend only if new exception types needed
│       │   └── resources/
│       │       ├── application.yml                            # ← FROM 001 | do not recreate
│       │       ├── application-dev.yml                        # ← FROM 001 | do not recreate
│       │       └── db/migration/
│       │           ├── V1__create_users.sql                   # ← FROM 001 | DO NOT recreate — Flyway conflict
│       │           └── V2__create_password_reset_tokens.sql   # ← FROM 001 | DO NOT recreate — Flyway conflict
│       └── test/java/com/easyeat/
│           ├── auth/
│           │   ├── controller/AuthControllerTest.java
│           │   └── security/
│           │       ├── JwtServiceTest.java
│           │       └── JwtAuthenticationFilterTest.java
│           └── user/service/AuthServiceTest.java
│
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── src/
    │   ├── api/
    │   │   ├── apiClient.ts                # Axios/Fetch client with 401 interceptor
    │   │   └── authApi.ts                  # login and logout endpoint calls
    │   ├── context/
    │   │   └── AuthContext.tsx             # Auth state and sessionStorage sync
    │   ├── hooks/
    │   │   └── useAuth.ts                  # Custom auth hook
    │   ├── router/
    │   │   ├── AppRoutes.tsx               # Main route tree
    │   │   ├── RoleRouteGuard.tsx          # Declarative perimeter guard
    │   │   └── postAuthRouting.ts          # Role mapping & deep link resolver
    │   ├── components/
    │   │   ├── common/
    │   │   │   └── WorkspaceLayout.tsx     # Base landing layout (header, badge, logout)
    │   │   └── auth/
    │   │       └── LoginForm.tsx           # Unified login form (validation, debouncing)
    │   └── pages/
    │       ├── LoginPage.tsx
    │       ├── CounterWorkspacePage.tsx    # /counter landing view (ENCARGADO)
    │       ├── KitchenWorkspacePage.tsx    # /kds landing view (CHEF)
    │       ├── AdminWorkspacePage.tsx      # /admin landing view (ADMINISTRADOR)
    │       └── CatalogPage.tsx             # /catalog landing view (COMENSAL / Fallback)
    └── tests/
        ├── components/LoginForm.test.tsx
        ├── router/RoleRouteGuard.test.tsx
        ├── router/postAuthRouting.test.ts
        └── session/SessionLifecycle.test.tsx
```

**Structure Decision**: Monorepo split between `easyeat/backend` and `easyeat/frontend`. Allows clean separation of Spring Boot REST controllers/security filters from React Router v6 guards and context providers.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations. All architectural patterns are justified and maintain KISS principles.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | N/A | N/A |
