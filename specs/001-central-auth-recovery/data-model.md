# Data Model: Central Authentication Service and Password Recovery

**Feature**: `001-central-auth-recovery` | **Date**: 2026-10-17 | **Phase**: 1

---

## Entity Catalog

### 1. User

Represents any individual operating the EasyEat platform. This is the **global identity table** —
multi-tenant isolation for business domain data (menus, orders, staff assignments) is handled
separately and does **NOT** use Hibernate `@TenantId` on this entity.

**Table**: `users`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Surrogate key |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Normalized (trimmed + lowercase) before storage |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt hash (work factor 12); never plaintext |
| `role` | `VARCHAR(50)` | NOT NULL, CHECK | `COMENSAL`, `ENCARGADO`, `CHEF`, `ADMINISTRADOR` |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'ACTIVE'`, CHECK | `ACTIVE`, `SUSPENDED`, `UNVERIFIED` |
| `tenant_id` | `UUID` | NULLABLE | NULL for COMENSAL; required for staff roles |
| `token_version` | `INTEGER` | NOT NULL, DEFAULT `0` | Incremented on password reset to invalidate all prior JWTs |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Account creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Last modification timestamp |

**Indexes**:
- `idx_users_email` on `email` — O(log n) login/lookup by email (primary auth hot path)
- Implicit PK index on `id`

**Validation Rules** (enforced at DTO layer before persistence):

| Field | Rule | Spec Ref |
|---|---|---|
| `email` | RFC 5322 format; normalized (trim + lowercase) before all operations | FR-002 |
| `password` (input) | 8–72 UTF-8 bytes; ≥1 uppercase, ≥1 lowercase, ≥1 digit | FR-003 |
| `password` (input) | `@MaxBytes(72)` custom constraint — rejects inputs exceeding 72 bytes to prevent BCrypt silent truncation | FR-003 |
| `role` | One of the 4 defined roles | FR-005 |
| `status` | One of `ACTIVE`, `SUSPENDED`, `UNVERIFIED` | FR-009 |

**Account Status Transitions**:

```
UNVERIFIED ──► ACTIVE      (admin activation or email verification — future scope)
ACTIVE     ──► SUSPENDED   (admin action)
SUSPENDED  ──► ACTIVE      (admin reinstatement)
```

> Note: `UNVERIFIED` state exists for future email verification scope (excluded from Sprint 1 MVP).
> All registrations in Sprint 1 set status = `ACTIVE` immediately.

**Flyway Migration**: `V1__create_users.sql`

```sql
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id     UUID,
    token_version INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email   UNIQUE (email),
    CONSTRAINT chk_users_role   CHECK (role   IN ('COMENSAL','ENCARGADO','CHEF','ADMINISTRADOR')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','SUSPENDED','UNVERIFIED'))
);

CREATE INDEX idx_users_email ON users(email);
```

---

### 2. PasswordResetToken

Represents a temporary, single-use credential recovery authorization. Stored in a **separate table**
to keep `users` single-responsibility and to enable token history and audit (see D-006 in research.md).

**Table**: `password_reset_tokens`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT `gen_random_uuid()` | Surrogate key |
| `user_id` | `UUID` | NOT NULL, FK → `users(id)` ON DELETE CASCADE | Owner of the reset request |
| `token_hash` | `VARCHAR(64)` | NOT NULL, UNIQUE | SHA-256 hex digest of the raw token (raw token is **never** stored) |
| `status` | `VARCHAR(50)` | NOT NULL, DEFAULT `'PENDING'`, CHECK | `PENDING`, `CONSUMED`, `REVOKED` |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL | Creation time + 15 minutes |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `NOW()` | Token issuance timestamp |
| `consumed_at` | `TIMESTAMPTZ` | NULLABLE | Set atomically when status changes to CONSUMED or REVOKED |

**Indexes**:
- `idx_prt_token_hash` on `token_hash` — primary lookup path at confirm time (O(log n))
- `idx_prt_user_id` on `user_id` — supports querying pending tokens per user (cooldown check)

**Token Lifecycle**:

```
[issued]
   │
   ▼
PENDING ──► CONSUMED   (atomic: token valid + not expired + account active → password updated)
   │
   └──────► REVOKED    (if a newer reset is requested for the same user before expiry,
                        or on admin account suspension)
```

**Atomicity guarantee**: The transition `PENDING → CONSUMED` is executed in a single `@Transactional`
block that also updates `users.password_hash` and increments `users.token_version`.

**Flyway Migration**: `V2__create_password_reset_tokens.sql`

```sql
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    status      VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ,
    CONSTRAINT uq_prt_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_prt_status    CHECK  (status IN ('PENDING','CONSUMED','REVOKED'))
);

CREATE INDEX idx_prt_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_prt_user_id    ON password_reset_tokens(user_id);
```

---

### 3. Session Context (non-persisted — JWT)

The authenticated session is **entirely stateless**. No `sessions` table exists. The JWT is the
session. Validated by `JwtAuthenticationFilter` on every authenticated request.

**JWT Claims**:

| Claim | Type | Required | Description |
|---|---|---|---|
| `sub` | `string` (UUID) | Yes | `users.id` of the authenticated user |
| `role` | `string` | Yes | `COMENSAL` / `ENCARGADO` / `CHEF` / `ADMINISTRADOR` |
| `tenant_id` | `string` (UUID) or absent | Conditional | Absent/null for unassociated comensales; mandatory for staff roles |
| `token_version` | `integer` | Yes | Must match `users.token_version` at validation time |
| `iat` | `number` (Unix epoch) | Yes | Issued-at timestamp (managed by jjwt) |
| `exp` | `number` (Unix epoch) | Yes | `iat + 28800` seconds (8 hours) |

**Algorithm**: HS256 (HMAC-SHA256). Key: `${JWT_SECRET}` env var (min 256 bits).

**Revocation Strategy — token_version**:
1. On login: JWT includes `token_version` = current `users.token_version`.
2. On each authenticated request: `JwtAuthenticationFilter` reads `token_version` from JWT,
   fetches `users.token_version` from DB (indexed PK lookup ≤ 1ms), and rejects if stale.
3. On password reset: `users.token_version` is incremented atomically. All prior JWTs
   immediately fail validation on their next request.

---

### 4. Caffeine Rate-Limit Counters (non-persisted — in-memory)

Two Caffeine caches managed by `RateLimitingService`. Not persisted; reset on restart.

| Cache | Key | Value | Expiry | Threshold |
|---|---|---|---|---|
| `loginAttemptsByEmail` | Normalized email | `AtomicInteger` | 15 min after write | 5 failures → lockout |
| `loginAttemptsByIp` | Client IP string | `AtomicInteger` | 60 min after write | 50 failures → lockout |
| `resetAttemptsByEmail` | Normalized email | `AtomicInteger` | 15 min after write | 3 requests → cooldown |

---

## Requirement Traceability

| Requirement | Mapped Entity / Layer |
|---|---|
| FR-001 (registration) | `users` (id, email, password_hash, role, status) |
| FR-002 (email normalization) | Service layer — before all DB operations |
| FR-003 (password complexity + `@MaxBytes(72)`) | DTO validation layer (RegisterRequest, PasswordResetConfirmRequest) |
| FR-004 (BCrypt hashing, cost 12) | `users.password_hash` via `BCryptPasswordEncoder` |
| FR-005 (unified login for all roles) | `users.role` enum check in `AuthService` |
| FR-006 (JWT with 8h validity + claims) | Session Context — jjwt builder |
| FR-007 (anti-enumeration — DUMMY_HASH) | `AuthService` — constant-time path for non-existent email |
| FR-008 (rate limiting, 5 attempts / 15 min) | `RateLimitingService` — `loginAttemptsByEmail` cache |
| FR-009 (reject suspended accounts) | `users.status` check in `AuthService` before token issuance |
| FR-010 (reset request accepted) | `PasswordResetService` — `password_reset_tokens` insert |
| FR-011 (single-use token, 15 min expiry) | `password_reset_tokens.expires_at`, `token_hash` |
| FR-012 (uniform response + conditional email) | `PasswordResetService` — always 200 OK; email sent only if user found + ACTIVE |
| FR-013 (reset request rate limit) | `RateLimitingService` — `resetAttemptsByEmail` cache |
| FR-014 (validate token before accepting new password) | `PasswordResetService` — hash lookup + expiry + status check |
| FR-015 (atomic reset: consume token + update hash + increment version) | `PasswordResetService` — single `@Transactional` method |
| FR-016 (admins use same recovery service) | No separate code path — same `PasswordResetService` handles all roles |
| FR-017 (structured audit logging) | `RateLimitingService` + `AuthService` + `PasswordResetService` — SLF4J structured events |
