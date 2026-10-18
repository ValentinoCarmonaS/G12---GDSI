# Phase 0 Research: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Feature**: `002-staff-auth-redirect-guards`  
**Date**: 2026-10-17  
**Status**: Completed  

---

## 1. Single Active Session & Token Invalidation Mechanism

### Decision
Implement an integer `token_version` column on the `User` entity in PostgreSQL. The `token_version` is embedded as a numeric claim in the issued JWT access token. 

- On every successful login (`POST /api/v1/auth/login`), the backend atomically increments `user.token_version` in the database and embeds the new version in the token. This immediately invalidates any existing active tokens held by other browsers, devices, or tabs for that account.
- On explicit logout (`POST /api/v1/auth/logout`), the backend increments `user.token_version`, instantly invalidating the current token.
- `JwtAuthenticationFilter` extracts the `token_version` claim from the incoming JWT and compares it against the persisted `user.token_version`. If they do not match, the filter rejects the request with HTTP 401 Unauthorized.

### Rationale
- Applies KISS (Constitution Principle IV: Simple, Cohesive, and Justified Design).
- Avoids the overhead and latency of a separate session state table or distributed cache lookup for simple single-session invalidation.
- Operates deterministically without requiring distributed locks or stateful session replication.

### Alternatives Considered
- **Dedicated `user_sessions` Table**: Storing individual session IDs with revocation flags. Rejected as overengineered for the current single active session requirement, adding unnecessary join complexity and persistence maintenance.
- **Redis Revocation Blacklist**: Storing revoked JTI tokens in Redis. Rejected because it introduces an external infrastructure dependency not currently in the project stack.

---

## 2. Frontend State Management & Token Persistence

### Decision
Manage authenticated user state (`user_id`, `email`, `role`, `tenant_id`, `accessToken`) in a dedicated React `AuthContext` coupled with `useAuth()` custom hook. 

- To prevent disruptive session loss during page reloads (e.g. accidental `F5` by kitchen staff or counter operators during a busy shift), the active access token and user context are backed by `sessionStorage`.
- `sessionStorage` scopes the persistence to the browser tab/window, ensuring that closing the shift terminal tab clears transient local storage while surviving soft reloads.
- The short-lived token (8-hour shift lifespan per spec 001) is kept in React state for immediate hook consumption and synchronized with the storage layer.

### Rationale
- Provides seamless operational UX for restaurant workstations without requiring constant re-entry of credentials upon page refreshes.
- Keeps client-side state synchronized and reactive across all route guards and layout components.

### Alternatives Considered
- **Purely In-Memory State**: Token exists only in React component memory. Rejected because any browser refresh (`F5`) or accidental navigation would log the staff member out immediately, disrupting restaurant operations.
- **`localStorage`**: Persists indefinitely across tabs and restarts. Rejected because restaurant POS/KDS terminals are shared physical devices; `sessionStorage` provides safer boundary isolation.

---

## 3. Declarative Route Guard Architecture with React Router v6

### Decision
Implement a declarative `RoleRouteGuard` component (`RoleRouteGuard.tsx`) using React Router v6 `<Outlet />` and `<Navigate />` primitives.

- The guard accepts an `allowedRoles: Role[]` property.
- When unauthenticated: redirects to `/login` preserving the attempted path in location state (`<Navigate to="/login" state={{ from: location }} replace />`).
- When authenticated but role not in `allowedRoles` (cross-role navigation): performs a silent redirect to the user's assigned default workspace using `<Navigate to={getRoleDefaultRoute(user.role)} replace />`.
- Always passes `replace: true` to prevent polluting browser history and avoid back-button entrapment loops.

### Rationale
- Clean separation of routing policy from view rendering.
- Enforces strict cross-role perimeter defense declaratively at the router root.
- `replace: true` ensures the browser back button does not bounce users between forbidden routes.

### Alternatives Considered
- **Imperative Navigation in `useEffect` within Page Components**: Checking permissions inside individual page components. Rejected because it allows a flash of forbidden content (or empty layout) before navigation triggers, and duplicates authorization logic across all pages.

---

## 4. Deep Link Preservation & Post-Login Redirection Logic

### Decision
Centralize the post-authentication routing policy in a dedicated routing helper (`getPostAuthRedirect(userRole, preservedLocation)`):

- If `preservedLocation` exists:
  - If the target pathname is authorized for `userRole`, redirect to `preservedLocation.pathname`.
  - If the target pathname does NOT belong to `userRole`, ignore it and redirect to `getRoleDefaultRoute(userRole)`.
- If no `preservedLocation` exists: redirect to `getRoleDefaultRoute(userRole)`.
- Default workspace mapping:
  - `ENCARGADO` → `/counter`
  - `CHEF` → `/kds`
  - `ADMINISTRADOR` → `/admin`
  - `COMENSAL` (or unassigned/missing role) → `/catalog`

### Rationale
- Satisfies all invariants from `spec.md` with zero ambiguity.
- Prevents cross-role privilege escalation via deep-linking while honoring legitimate deep links (e.g. chef bookmarking a specific KDS station).

---

## 5. Session Expiration Interception & In-Flight Mutation Handling

### Decision
Configure an HTTP client response interceptor (using Axios or Fetch wrapper) that inspects every response:

- When an HTTP 401 Unauthorized status is received:
  1. Aborts in-flight requests and mutations via `AbortController`.
  2. Clears the `AuthContext` and purges `sessionStorage`.
  3. Dispatches navigation to `/login` with an expiration state flag (`{ expired: true }`).
- The `LoginForm` component detects the `expired: true` navigation state and displays a prominent warning notification: *"Tu sesión ha expirado"*.

### Rationale
- Protects data integrity: ensures stale sessions cannot commit partial transactions or leave backend entities in inconsistent states.
- Provides immediate, unmistakable user feedback instead of broken or frozen interfaces.

---

## 6. Testing Strategy & Framework Alignment

### Decision
Employ Jest + React Testing Library (`@testing-library/react`, `@testing-library/user-event`) for frontend component and routing tests, and JUnit 5 + MockMvc for backend controller and filter tests.

- **Frontend Tests**:
  - `MemoryRouter` simulating all route entry points (`/login`, `/counter`, `/kds`, `/admin`, `/catalog`).
  - Form validation, email trimming/lowercasing, submission debouncing, anti-enumeration messages.
  - Guard redirection tests verifying silent redirect for cross-role attempts and deep-link role evaluation.
  - Simulated 401 responses verifying context cleanup and expiration alert.
- **Backend Tests**:
  - Unit tests for `User` entity token versioning.
  - Slice tests for `AuthController` verifying `login` and `logout` contracts and response schemas.
  - Security filter tests verifying rejection of outdated `token_version` with RFC 9457 ProblemDetail 401.
