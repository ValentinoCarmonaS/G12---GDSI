# Phase 1 Data Model: Staff Authentication Views and Automatic Role-Based Redirect Guards

**Feature**: `002-staff-auth-redirect-guards`  
**Date**: 2026-10-17  
**Status**: Completed  

---

## 1. Backend Persistence Entities (Spring Data JPA / PostgreSQL)

### Entity: `User` (`users` table)

Represents an identity within the platform (diner, staff, or admin).

| Field Name | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key. Unique account identifier. |
| `email` | `VARCHAR(255)` | No | N/A | Normalized lowercase email address. Unique index. |
| `password_hash` | `VARCHAR(255)` | No | N/A | Salted bcrypt cryptographic password hash (cost 12). |
| `role` | `VARCHAR(50)` | No | `'COMENSAL'` | Assigned system role: `COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`. |
| `status` | `VARCHAR(50)` | No | `'ACTIVE'` | Operational status: `ACTIVE`, `SUSPENDED`, `UNVERIFIED`. |
| `tenant_id` | `UUID` | Yes | `NULL` | Establishment identifier. Mandatory for staff roles; null for platform comensales. |
| `token_version` | `INTEGER` | No | `0` | Session version sequence (from `V1__create_users.sql`). Incremented on login & logout. |
| `created_at` | `TIMESTAMPTZ` | No | `NOW()` | Account creation timestamp. |
| `updated_at` | `TIMESTAMPTZ` | No | `NOW()` | Last profile update timestamp. |

#### Database Constraints & Invariants (Inherited from `V1__create_users.sql` in 001)
- `uq_users_email`: Unique constraint on `email`.
- `idx_users_email`: B-tree index on `email` for O(log n) login lookup.
- `chk_users_role`: Check constraint `CHECK (role IN ('COMENSAL','ENCARGADO','CHEF','ADMINISTRADOR'))`.
- `chk_users_status`: Check constraint `CHECK (status IN ('ACTIVE','SUSPENDED','UNVERIFIED'))`.

---

## 2. Security Token Structure (JWT Claims)

The issued Bearer JWT access token includes standard and custom claims validating identity and operational boundaries.

```json
{
  "sub": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "carlos.chef@comedorcentral.com",
  "role": "CHEF",
  "tenant_id": "8bb12c8a-2d4e-4f76-a0b2-9a0910f13341",
  "token_version": 4,
  "iat": 1729209600,
  "exp": 1729238400
}
```

| Claim | Key | Type | Description |
|---|---|---|---|
| Subject | `sub` | `String (UUID)` | Authenticated user ID. |
| Email | `email` | `String` | Normalized email address. |
| Role | `role` | `String` | Role claim (`COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR`). |
| Tenant ID | `tenant_id` | `String (UUID)` | Associated establishment for staff multi-tenant partitioning. |
| Token Version | `token_version` | `Integer` | Version counter validating single active session integrity. |
| Issued At | `iat` | `Integer` | Timestamp of issuance. |
| Expiration | `exp` | `Integer` | Expiration timestamp (8 hours from issuance). |

---

## 3. Frontend Client Models (TypeScript)

### Type: `Role`
```typescript
export type Role = 'COMENSAL' | 'ENCARGADO' | 'CHEF' | 'ADMINISTRADOR';
```

### Interface: `AuthenticatedUser`
```typescript
export interface AuthenticatedUser {
  userId: string;
  email: string;
  role: Role;
  tenantId: string | null;
}
```

### Interface: `AuthContextState`
```typescript
export interface AuthContextState {
  user: AuthenticatedUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}
```

### Interface: `LoginRequest`
```typescript
export interface LoginRequest {
  email: string;
  password: string;
}
```

### Interface: `LoginResponse` (Wire format matching 001)
```typescript
export interface LoginResponse {
  access_token: string;
  token_type: 'Bearer';
  expires_in: number; // 28800 (8 hours)
}
```

### Interface: `JwtClaims` (Decoded payload from access_token)
```typescript
export interface JwtClaims {
  sub: string; // userId (UUID)
  email: string;
  role: Role;
  tenant_id: string | null;
  token_version: number;
  iat: number;
  exp: number;
}
```

---

## 4. Route Access Matrix & Canonical Mapping

Defines the declarative mapping between application routes, authorized roles, and landing policies.

| Route Path | Allowed Roles | Default For Role | Public / Protected | Redirect On Mismatch / Unauth |
|---|---|---|---|---|
| `/login` | All (when unauth) | None | Public | If authenticated → Redirect to role default workspace |
| `/counter` | `['ENCARGADO']` | `ENCARGADO` | Protected | Unauth → `/login` (preserve path)<br>Cross-role → Role default workspace |
| `/kds` | `['CHEF']` | `CHEF` | Protected | Unauth → `/login` (preserve path)<br>Cross-role → Role default workspace |
| `/admin` | `['ADMINISTRADOR']` | `ADMINISTRADOR` | Protected | Unauth → `/login` (preserve path)<br>Cross-role → Role default workspace |
| `/catalog` | `['COMENSAL']` | `COMENSAL` / Fallback | Protected / Catalog | Unauth → `/login` (preserve path)<br>Cross-role → Role default workspace |
| `*` | Any | None | Catch-all | If auth → Role default workspace<br>If unauth → `/login` |

---

## 5. Session State Transitions

```mermaid
stateDiagram-v2
    [*] --> Unauthenticated: App Launch / Cold Start

    Unauthenticated --> Authenticating: Submit Login Form
    Authenticating --> Unauthenticated: 401 Credential Failure / Network Error
    Authenticating --> Authenticated: 200 OK (token_version = N)

    state Authenticated {
        [*] --> WorkspaceRendered
        WorkspaceRendered --> InFlightAction: User Mutation / Dispatch
        InFlightAction --> WorkspaceRendered: 200 OK
    }

    Authenticated --> Invalidated: Concurrent Login on Device B (DB token_version becomes N+1)
    Authenticated --> Terminated: Explicit Logout Action (POST /api/v1/auth/logout)
    Authenticated --> Expired: Token Expiry (exp reached)

    Invalidated --> SessionExpiredRedirect: Next HTTP Request returns 401
    Expired --> SessionExpiredRedirect: Next HTTP Request returns 401
    
    SessionExpiredRedirect --> Unauthenticated: Purge State & Redirect to /login (Alert: "Tu sesión ha expirado")
    Terminated --> Unauthenticated: Purge State & Redirect to /login
```
