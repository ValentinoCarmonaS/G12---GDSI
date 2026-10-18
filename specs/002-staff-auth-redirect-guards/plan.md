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
│       │   │   │   ├── controller/AuthController.java
│       │   │   │   ├── dto/LoginRequest.java
│       │   │   │   ├── dto/LoginResponse.java
│       │   │   │   ├── service/AuthService.java
│       │   │   │   ├── security/
│       │   │   │   │   ├── JwtService.java             # Generates/validates tokens and token_version
│       │   │   │   │   ├── JwtAuthenticationFilter.java # Enforces single active session check
│       │   │   │   │   └── RateLimitingService.java    # Caffeine rate limiters (from 001)
│       │   │   │   ├── entity/
│       │   │   │   │   ├── User.java                   # User entity with token_version (from 001)
│       │   │   │   │   └── PasswordResetToken.java     # From 001
│       │   │   │   └── repository/
│       │   │   │       └── UserRepository.java         # findByEmail, existsByEmail (from 001)
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java         # SecurityFilterChain configuration
│       │   │   │   └── AppConfig.java              # Cache beans & general config
│       │   │   └── exception/
│       │   │       └── GlobalExceptionHandler.java # RFC 9457 ProblemDetail handler
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       └── db/migration/
│       │           ├── V1__create_users.sql            # Base users schema with token_version (from 001)
│       │           └── V2__create_password_reset_tokens.sql # From 001
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
