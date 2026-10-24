# Feature Specification: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Feature Branch**: `002-staff-auth-redirect-guards`  
**Created**: 2026-10-17  
**Last Updated**: 2026-10-23  
**Status**: Approved  
**Constitution Compliance**: v1.1.0 (Principle X: Full-Stack End-to-End Integrity / Vertical Slices)  
**Input**: Official Backlog User Stories: ENC-1.1.1, ENC-1.2.1, CHF-1.1.1, CHF-1.2.1, ADM-1.1.1.  

---

## Clarifications & Constitution Alignment

### Full-Stack Scope Ratification (Principle X)
In strict accordance with Project Constitution Principle X, this feature is formulated as an integrated, full-stack vertical slice. Each of the five user stories comprises both client-facing presentation/routing interactions and corresponding server-side authentication, authorization, and tenant isolation contracts.

### Session 2026-10-23

- Q: How does this feature integrate with the existing authentication backend and the Sprint 1 scope? → A: Spec 002 strictly consumes the centralized authentication infrastructure already specified in Spec 001 and implemented in `easyeat/backend` (`POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, 8-hour JWT TTL / 28800000 ms, BCrypt cost 12, rate limit 5 attempts/15 min). Spec 002 introduces zero redundant auth logic; its scope is strictly the 5 Sprint 1 backlog user stories (`ENC-1.1.1`, `ENC-1.2.1`, `CHF-1.1.1`, `CHF-1.2.1`, `ADM-1.1.1`) focusing on role-based client views, automatic workspace redirections (`/mostrador`, `/cocina`, `/admin`), client route guards, and server-side RBAC protection.
- Q: When an authenticated staff member navigates directly to a workspace outside their role (cross-role navigation), how must both client and server enforce isolation? → A: The client-side guard must immediately execute a silent redirection to the user's assigned operational workspace without intermediate error banners, replacing browser history. The server-side security layer must reject any unauthorized API request targeting another role's operational resources with HTTP `403 Forbidden`.
- Q: How is concurrent multi-device login handled across both tiers? → A: Enforced single active session: when a staff member authenticates from a new terminal, the server establishes a new session token version. Any subsequent API request made with a stale token version is rejected with HTTP `401 Unauthorized`, triggering client-side session eviction.
- Q: How does the system handle an authenticated identity without an assigned role or with an unassigned profile? → A: The system defaults client routing and server access permissions to the `COMENSAL` role, redirecting the user to the public dining catalog workspace (`/catalogo`).
- Q: How are shift terminations (logout) handled between tiers? → A: Explicit logout requires client-side storage clearance paired with a server-side session termination contract (`POST /api/v1/auth/logout`) that permanently invalidates the issued token session and returns HTTP `204 No Content`.

### Session 2026-10-24 (Backlog Audit & SOLID Clarification)

- Q: How should the client hydrate session state and validate single active session (`token_version`)? → A: `AuthContext` hydrates session state synchronously from the stored JWT in `sessionStorage` upon mounting to eliminate blocking network waterfalls and render delays. Concurrency eviction is handled reactively by the HTTP client interceptor (`apiClient`) capturing any `401 Unauthorized` ProblemDetail response on subsequent operational requests and clearing `sessionStorage`. `GET /api/v1/auth/me` remains available on the backend as a profile endpoint, but workspace views and guards do not execute blocking polling on mount.
- Q: Where does `LoginForm` belong in the component lifecycle? → A: Following SRP, `LoginForm` is implemented once in Phase 2 (Foundational) as a decoupled, role-agnostic component accepting credentials, delegating authentication to `authApi.login()`, setting session state via `AuthContext`, and redirecting via `resolvePostAuthRedirect()`. Subsequent role-specific user stories (ENC, CHF, ADM) strictly test integration and role-specific redirection without modifying `LoginForm` internals or attempting client-side security audit dispatching.
- Q: What is the canonical role name for administrators across all artifacts and code? → A: Strictly `ADMINISTRADOR`. Any reference to `ADMIN` is deprecated and normalized to `ADMINISTRADOR` across data models, route guards, and Spring Security configurations.
- Q: What is the implementation status of `POST /api/v1/auth/logout` and tenant isolation in the backend? → A: Both `AuthController.logout()` / `AuthService.logout()` (with atomic `token_version` increment) and `TenantContextFilter` / `StaffController` (`/api/v1/admin/staff`) are already implemented in `easyeat/backend` and covered by integration tests (`AuthControllerLogoutTest`, `TenantIsolationIntegrationTest`). Spec 002 development focuses on the frontend integration, route guards, and full-stack contract validation.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1: ENC-1.1.1 — Counter Manager Credential Login (Priority: P1) 🎯 MVP

As a Counter Manager (`ENCARGADO`),  
I want to log into the web platform using my administrative credentials through the unified login portal,  
So that the system authenticates my identity and establishes my counter operational session.

**Why this priority**: Core onboarding gateway for counter staff. Without secure credential verification and token issuance, dispatch and delivery management tools cannot be operated.

**Independent Test**: Submit valid counter manager credentials through the shared authentication interface. Verify that the client validates inputs, the backend authenticates the hash and issues an identity token with `role: ENCARGADO`, tenant binding, and active status, and access is granted.

#### Acceptance Scenarios (Given-When-Then)

1. **Successful Counter Authentication**:
   - **Given** an unauthenticated user on the login interface with registered `ENCARGADO` credentials,
   - **When** they submit their email and correct password,
   - **Then** the backend validates the credentials, records a security audit log entry, and returns an authenticated response carrying an identity token with claims `role: ENCARGADO`, `user_id`, `tenant_id`, and `token_version`, and the client stores the session context.
2. **Email Normalization**:
   - **Given** a counter manager submitting an email containing leading/trailing whitespace and uppercase characters,
   - **When** the form is submitted with the valid password,
   - **Then** the client normalizes the email (trimmed and lowercased) prior to transmission, and the backend authenticates the normalized record without case-sensitivity mismatch.
3. **Anti-Enumeration Protection**:
   - **Given** a user submitting an unregistered email or an invalid password,
   - **When** the backend evaluates the request,
   - **Then** it returns HTTP `401 Unauthorized` with a generic RFC 9457 problem detail without disclosing whether the email exists, and the client displays a generic error message.
4. **Suspended Account Enforcement**:
   - **Given** a counter manager account flagged with inactive or suspended status,
   - **When** valid credentials are submitted,
   - **Then** the backend rejects authentication with HTTP `403 Forbidden` citing suspended account status, and the client alerts the user without granting workspace entry.
5. **Client Input Validation and Debouncing**:
   - **Given** an empty or malformed email string in the login form,
   - **When** the submission action is triggered,
   - **Then** the client displays visual field validation errors without firing a network request, and disables the submission control while any request is in flight.

---

### User Story 2: ENC-1.2.1 — Automatic Redirection to Counter Workspace (Priority: P1)

As a Counter Manager (`ENCARGADO`),  
I want to be automatically redirected to the Counter Workspace (`/mostrador`) immediately upon successful authentication,  
So that I can manage dispatch orders without navigating through irrelevant menus (such as Kitchen KDS or Diner views).

**Why this priority**: Eliminates operational delay during shift startup and prevents counter managers from accessing out-of-scope operational views.

**Independent Test**: Authenticate as `ENCARGADO`. Verify that the post-authentication routing policy resolves `/mostrador`, replaces navigation history, and authorizes access against the backend counter status endpoint.

#### Acceptance Scenarios (Given-When-Then)

1. **Automatic Landing Redirection**:
   - **Given** an `ENCARGADO` user completing login with no deep link preserved,
   - **When** authentication succeeds,
   - **Then** the client immediately routes to the Counter Workspace (`/mostrador`) using history replacement, and the backend authorizes access to counter endpoints.
2. **Authorized Deep Link Preservation**:
   - **Given** an unauthenticated user attempting to access a protected counter URL (e.g., `/mostrador/pedidos/42`),
   - **When** redirected to login and authenticated with role `ENCARGADO`,
   - **Then** the client routes directly to the requested deep link destination.
3. **Unauthorized Deep Link Discarding**:
   - **Given** an unauthenticated user attempting to access a kitchen view (e.g., `/cocina`),
   - **When** authenticated with role `ENCARGADO`,
   - **Then** the client discards the kitchen destination and redirects the user to their default `/mostrador` workspace.
4. **Server-Side Counter Protection**:
   - **Given** a client request to backend counter operational endpoints,
   - **When** the bearer token carries `role: ENCARGADO`,
   - **Then** the server grants access; if the token carries any non-authorized role, the server rejects the request with HTTP `403 Forbidden`.

---

### User Story 3: CHF-1.1.1 — Kitchen Chef Credential Login (Priority: P1)

As a Kitchen Chef (`CHEF`),  
I want to log into the web platform using my employee credentials,  
So that the system authenticates my identity and opens my secure kitchen shift.

**Why this priority**: Primary access gateway for culinary personnel. Guarantees that food preparation and kitchen ticket operations can only be initiated by verified chefs.

**Independent Test**: Submit valid chef credentials through the login interface. Verify that the client transmits the request, the backend validates credentials and issues a token with `role: CHEF`, tenant binding, and security audit log.

#### Acceptance Scenarios (Given-When-Then)

1. **Successful Chef Authentication**:
   - **Given** a chef user submitting their valid registered employee email and password,
   - **When** the authentication request is processed,
   - **Then** the backend returns an authenticated response with `role: CHEF`, `user_id`, `tenant_id`, and `token_version`, and the client initializes the active chef session.
2. **Chef Account Status Verification**:
   - **Given** a chef account with `UNVERIFIED` or `SUSPENDED` status,
   - **When** credentials are submitted,
   - **Then** the backend returns an authorization rejection, and the client prevents shift initiation.
3. **Audit Logging on Chef Authentication**:
   - **Given** an authentication attempt for a chef account,
   - **When** the outcome is evaluated (success or failure),
   - **Then** the backend logs a structured security event containing event type, timestamp, IP, email, and role, without logging raw passwords.

---

### User Story 4: CHF-1.2.1 — Automatic Redirection to Kitchen Display System (KDS) (Priority: P1)

As a Kitchen Chef (`CHEF`),  
I want to be automatically redirected to the Kitchen Workspace (`/cocina`) immediately after logging in,  
So that I can start reviewing and preparing pending kitchen tickets without manual navigation.

**Why this priority**: Operational velocity in kitchen environments requires immediate display of order tickets upon shift login.

**Independent Test**: Authenticate as `CHEF`. Verify that the post-authentication routing policy resolves `/cocina`, replaces navigation history, and validates authorized communication with kitchen backend endpoints.

#### Acceptance Scenarios (Given-When-Then)

1. **Automatic Kitchen Landing**:
   - **Given** a `CHEF` user completing authentication,
   - **When** post-auth navigation executes,
   - **Then** the client immediately routes to `/cocina` (KDS view), and the backend authorizes calls to kitchen order stream endpoints.
2. **Cross-Role Isolation against Kitchen**:
   - **Given** a user authenticated as `ENCARGADO` or `COMENSAL` attempting to access `/cocina`,
   - **When** navigation is evaluated,
   - **Then** the client silently redirects the user to their own workspace with history replacement, and the backend denies API access with HTTP `403 Forbidden`.
3. **Deep Link Resolution for Chef**:
   - **Given** an unauthenticated access attempt to a kitchen station URL (`/cocina/estacion-1`),
   - **When** the user logs in as `CHEF`,
   - **Then** the client routes to `/cocina/estacion-1`; if an unassigned target was requested, it falls back to `/cocina`.

---

### User Story 5: ADM-1.1.1 — Back-Office Administrator Portal Access and Tenant Isolation (Priority: P1)

As a Restaurant Administrator (`ADMINISTRADOR`),  
I want to log into the web platform and be routed to the Back-Office Administration panel (`/admin`),  
So that I can manage my restaurant's configuration within my strict tenant isolation boundaries without viewing other establishments.

**Why this priority**: Foundational tenant management and store governance capability. Enforces multi-tenant data isolation and administrative authority.

**Independent Test**: Authenticate as `ADMINISTRADOR`. Verify that the system routes to `/admin`, the backend enforces tenant ID scoping across all administration endpoints, and cross-tenant data leakage is strictly blocked.

#### Acceptance Scenarios (Given-When-Then)

1. **Automatic Admin Redirection**:
   - **Given** an authenticated user with `role: ADMINISTRADOR`,
   - **When** authentication completes,
   - **Then** the client immediately routes to the Back-Office panel (`/admin`), and the backend authorizes access to administrative resources matching the user's `tenant_id`.
2. **Strict Multi-Tenant Scoping**:
   - **Given** an administrator for Tenant A (`tenant_id: tenant-alpha`),
   - **When** accessing administrative endpoints and querying records,
   - **Then** the backend filters all queries strictly by `tenant-alpha`, ensuring that data, staff, and configurations of other tenants are completely invisible.
3. **Cross-Role Perimeter Defense**:
   - **Given** a non-admin user (`CHEF`, `ENCARGADO`, `COMENSAL`) attempting to navigate to `/admin`,
   - **When** the route or backend API is requested,
   - **Then** the client silently redirects the non-admin to their operational workspace, and the server returns HTTP `403 Forbidden`.

---

## Cross-Cutting System Capabilities (Full-Stack Lifecycle)

### Single Active Session and Invalidation
- **Server**: Every request to protected API routes validates that the bearer token signature is authentic, unexpired, and matches the user's current `token_version` in persistent storage. A new login increments the stored version, invalidating previously issued tokens with HTTP `401 Unauthorized`.
- **Client**: Any HTTP `401 Unauthorized` response received by the HTTP client immediately evicts local session storage, aborts in-flight actions, and routes the user to `/login` with an expiration alert banner.

### Shift Termination (Logout)
- **Client**: The common layout provides an explicit "Cerrar sesión" action. Triggering logout invokes the server logout endpoint, purges all stored tokens and identity state, and navigates to `/login` with history replacement (`replace: true`).
- **Server**: Exposes `POST /api/v1/auth/logout`. Atomically increments the user's persistent `token_version` and returns HTTP `204 No Content`, ensuring that even if a token is intercepted, it cannot be reused.

---

## Edge Cases

1. **Token Expiration During Active Form Submission**:
   - If a staff member's token expires while submitting an operational form, the backend rejects the request with HTTP `401`. The client must discard unsaved state, evict the session, and redirect to login with a clear expiration notice.
2. **Rapid Multi-Click Submissions**:
   - Submitting the login button repeatedly must disable interactive controls during flight, preventing duplicate authentication calls and concurrency locks.
3. **Browser Back-Button Navigation after Redirection**:
   - All role-based and post-auth redirections must use history replacement (`replace: true`). Pressing the browser back button must not return to an unauthorized workspace or trap the user in a redirect loop.
4. **Network Outage During Redirection**:
   - If the network drops immediately after token issuance but before landing view resources load, the client displays a retryable offline notice while retaining session state for reconnection.
5. **Simultaneous Multi-Tab Access**:
   - If a user logs out in Tab A, Tab B must detect the invalidation on its next API interaction, evict local state, and redirect to login.

---

## Functional Requirements

- **FR-001**: The system MUST provide a shared web login interface accepting email and password for all staff roles (`ENCARGADO`, `CHEF`, `ADMINISTRADOR`, `COMENSAL`).
- **FR-002**: The client MUST validate email syntax and presence of required fields prior to sending requests, and MUST normalize emails by trimming whitespace and lowercasing characters.
- **FR-003**: The backend MUST authenticate staff credentials using cost-12 BCrypt hashing and issue a signed JWT containing claims for `user_id`, `role`, `tenant_id`, and `token_version`.
- **FR-004**: The system MUST reject invalid credentials with HTTP `401 Unauthorized` without disclosing whether the email exists (anti-enumeration).
- **FR-005**: Upon successful authentication, the client router MUST automatically navigate the user to their designated workspace based on token role:
  - `ENCARGADO` → `/mostrador`
  - `CHEF` → `/cocina`
  - `ADMINISTRADOR` → `/admin`
  - `COMENSAL` or unassigned → `/catalogo`
- **FR-006**: Client redirection MUST preserve authorized deep links if the target matches the authenticated role, and MUST discard deep links that do not match the role.
- **FR-007**: Client route guards MUST protect `/mostrador`, `/cocina`, `/admin`, and `/catalogo`, silently redirecting unauthorized users to their own workspace with history replacement.
- **FR-008**: Backend API endpoints for each workspace MUST enforce role-based access control (RBAC), returning HTTP `403 Forbidden` for cross-role attempts.
- **FR-009**: Backend endpoints MUST enforce tenant isolation based on the authenticated `tenant_id` claim, preventing access to cross-tenant data.
- **FR-010**: The backend MUST enforce single active session by validating token `token_version` on every protected request against persistent storage, rejecting stale versions with HTTP `401`.
- **FR-011**: The client MUST intercept HTTP `401` responses, evict local session data, and redirect to `/login` with an expiration notification.
- **FR-012**: The system MUST provide an explicit shift logout endpoint (`POST /api/v1/auth/logout`) that increments `token_version`, returns HTTP `204`, and triggers client session purge and redirection.
- **FR-013**: Protected endpoints and views MUST enforce `Cache-Control: no-store` headers to prevent browser caching of sensitive operational data.

---

## Key Entities & Data Contracts

- **User Identity Context**:
  - `userId`: Globally unique identifier (UUID) of the staff member.
  - `email`: Normalized unique corporate email.
  - `role`: One of `COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`.
  - `tenantId`: Tenant identifier to which the staff member belongs.
  - `tokenVersion`: Monotonically incrementing integer tracking active session epoch.
  - `accountStatus`: Active state (`ACTIVE`, `SUSPENDED`, `UNVERIFIED`).

- **Workspace Route Map**:
  - Counter Workspace: `/mostrador` (authorized: `ENCARGADO`).
  - Kitchen Workspace: `/cocina` (authorized: `CHEF`).
  - Back-Office Panel: `/admin` (authorized: `ADMINISTRADOR`).
  - Dining Catalog: `/catalogo` (authorized: `COMENSAL`, fallback).

---

## Success Criteria

- **SC-001**: 100% of valid credentials for each staff role successfully authenticate and issue tokens with the complete four-claim payload (`user_id`, `role`, `tenant_id`, `token_version`).
- **SC-002**: Post-login redirection reaches the exact role-assigned landing view within 500ms of authentication completion under standard broadband conditions.
- **SC-003**: 100% of cross-role navigation attempts (both in browser URL and API requests) are blocked: client silently redirects to own workspace; backend returns HTTP `403 Forbidden`.
- **SC-004**: Stale session requests (after login on another terminal or explicit logout) are rejected by the backend with HTTP `401` within 100% of test scenarios.
- **SC-005**: 100% of cross-tenant data isolation checks pass, ensuring zero cross-tenant leakages across administrative endpoints.
- **SC-006**: Zero password enumeration leakages: identical response payload and timing variation within acceptable margin for non-existent vs. mismatched credential requests.

---

## Assumptions & Out-of-Scope Boundaries

### In Scope
- Client login interface and client routing tree with role guards for counter, kitchen, admin, and catalog.
- Server authentication, token issuance, single-active-session validation, RBAC route protection, and explicit logout endpoint.
- Integration tests validating end-to-end credential submission, token issuance, workspace isolation, and session revocation.

### Out of Scope
- Implementation of full operational business features inside workspaces (e.g., kitchen order ticket lifecycle algorithms, POS cash register hardware integrations, and full tenant menu management belong to their dedicated sprint features).
- Social login (OAuth2/Google/Apple) or two-factor authentication (SMS/TOTP), which are not scheduled for Sprint 1.
- Self-registration for staff members (staff accounts are provisioned exclusively by tenant administrators).
