# Quickstart & Verification Guide: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Feature**: `002-staff-auth-redirect-guards`  
**Date**: 2026-10-17  
**Status**: Completed  

---

## Prerequisites

1. **Backend Environment**:
   - Java 21 JDK installed (`java -version`).
   - Maven 3.9+ installed (`mvn -version`).
   - PostgreSQL 16+ running locally or in Docker with target database `easyeat_db`.
2. **Frontend Environment**:
   - Node.js 20+ and npm 10+ installed (`node -v`, `npm -v`).
3. **Database Seed Data**:
   Ensure test accounts exist with passwords `Password123` (hashed via bcrypt):
   - Counter Manager: `maria.encargada@laesquina.com` (Role: `ENCARGADO`, Tenant: `8bb12c8a-...`)
   - Kitchen Chef: `carlos.chef@comedorcentral.com` (Role: `CHEF`, Tenant: `9cc23d9b-...`)
   - Administrator: `laura.admin@cafeparque.com` (Role: `ADMINISTRADOR`, Tenant: `1aa45e0c-...`)
   - Diner: `juan.comensal@gmail.com` (Role: `COMENSAL`, Tenant: `NULL`)
   - Unassigned Account: `corrupt.user@test.com` (Role: `NULL` or unassigned in DB)

---

## 1. Running Automated Test Suites

### Frontend Verification (Jest & React Testing Library)
Run the dedicated suite of routing and component unit/integration tests:

```bash
cd easyeat/frontend
npm test -- --testPathPattern="auth|guards|router" --watchAll=false
```

**Expected Results**:
- `LoginForm.test.tsx`: Validates empty field highlighting, syntax checks, input lowercasing/trimming, debounce, and anti-enumeration generic error.
- `RoleRouteGuard.test.tsx`: Validates interception of unauthenticated access to `/counter`, `/kds`, `/admin`; verifies silent redirect on cross-role access; verifies `replace: true` history replacement.
- `postAuthRouting.test.ts`: Validates role-to-workspace mapping and deep link resolution.
- `SessionLifecycle.test.tsx`: Validates HTTP 401 response interceptor clearing `AuthContext`, aborting requests, and triggering redirect to `/login` with `"Tu sesión ha expirado"`.

### Backend Verification (JUnit 5 & MockMvc)
Run the backend authentication and filter test suite:

```bash
cd easyeat/backend
mvn test -Dtest=AuthControllerTest,JwtAuthenticationFilterTest,UserServiceTest
```

**Expected Results**:
- `AuthControllerTest`: `POST /api/v1/auth/login` returns JWT with claims (`user_id`, `role`, `tenant_id`, `token_version`).
- `AuthControllerTest`: `POST /api/v1/auth/logout` increments `token_version` on the `User` entity and returns HTTP 204.
- `JwtAuthenticationFilterTest`: Rejects requests where JWT `token_version` != persisted `user.token_version` with HTTP 401 ProblemDetail.

---

## 2. End-to-End Manual Validation Scenarios

### Scenario 1: Automatic Redirect to Counter Dispatch View
1. Navigate to `http://localhost:5173/login`.
2. Enter email `maria.encargada@laesquina.com` and password `Password123`.
3. Click **Iniciar sesión**.
4. **Verification**: In under 2 seconds, URL transitions immediately to `http://localhost:5173/counter` without intermediate prompts, displaying the Counter Manager workspace header with tenant name and role badge.

### Scenario 2: Cross-Role Silent Redirection (Chef attempting Counter view)
1. Log in as `carlos.chef@comedorcentral.com` → Lands automatically on `http://localhost:5173/kds`.
2. In the browser address bar, manually type `http://localhost:5173/counter` and hit Enter.
3. **Verification**: The route guard intercepts navigation; user is silently redirected to `http://localhost:5173/kds`. No error page or forbidden content is rendered.
4. Press the browser's **Back** button.
5. **Verification**: User remains on `http://localhost:5173/kds`; back navigation does not expose the forbidden `/counter` view.

### Scenario 3: Single Active Session Invalidation
1. Open Browser Tab A (or Chrome) and log in as `laura.admin@cafeparque.com` → Lands on `/admin`.
2. Open Browser Tab B (or Firefox) and log in as the same user `laura.admin@cafeparque.com` → Lands on `/admin`.
3. Return to Browser Tab A and attempt an action or refresh the page.
4. **Verification**: Tab A receives an HTTP 401 Unauthorized because the backend `token_version` was incremented by Tab B's login. Tab A's interface clears authentication state and redirects to `/login` with the alert: *"Tu sesión ha expirado"*.

### Scenario 4: Deep Link Role Resolution
1. Clear browser session / open an incognito window.
2. Directly navigate to `http://localhost:5173/kds`.
3. **Verification**: Guard intercepts unauthenticated request and redirects to `http://localhost:5173/login`.
4. Enter credentials for `carlos.chef@comedorcentral.com` (Chef) and submit.
5. **Verification**: System detects that requested deep link (`/kds`) matches the authenticated Chef role and redirects directly to `/kds`.
6. Repeat steps 1–3, but log in with `maria.encargada@laesquina.com` (Counter Manager).
7. **Verification**: System detects mismatch between requested target (`/kds`) and authenticated role (`ENCARGADO`), ignores deep link, and safely redirects to `/counter`.

### Scenario 5: Explicit Shift Termination (Logout)
1. From any staff workspace (`/counter`, `/kds`, `/admin`), click the **Cerrar sesión** button in the header.
2. **Verification**: Client calls `POST /api/v1/auth/logout`, clears local session state, and redirects to `/login`.
3. Press the browser's **Back** button.
4. **Verification**: Route guard intercepts unauthenticated state, prevents display of cached workspace content, and remains on `/login`.
