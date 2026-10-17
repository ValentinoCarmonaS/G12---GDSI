# Quickstart Validation Guide: Central Authentication Service and Password Recovery

**Feature**: `001-central-auth-recovery` | **Date**: 2026-10-17 | **Phase**: 1

This guide documents runnable validation scenarios that prove the feature works end-to-end.
It is **not** an implementation guide — implementation details belong in `tasks.md`.

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Build and run the backend |
| Maven | 3.9+ | Build tool (`mvn`) |
| Docker | 24+ | Testcontainers (PostgreSQL) + local dev DB |
| `curl` or `httpie` | any | Manual endpoint testing |
| [jwt.io](https://jwt.io) | browser | Decode and inspect JWT tokens |
| PostgreSQL client | any | Verify DB state during manual testing |

---

## Setup

### 1. Navigate to the backend project

```bash
cd "sdd - gdsi - grupo 12/easyeat/backend"
```

### 2. Set required environment variables

```bash
# JWT signing secret — minimum 256 bits (64 hex characters)
export JWT_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

# Mail configuration (use any SMTP or a local mock like Mailhog)
export SPRING_MAIL_HOST="localhost"
export SPRING_MAIL_PORT="1025"
export SPRING_MAIL_USERNAME=""
export SPRING_MAIL_PASSWORD=""
```

> **Dev tip**: Run [Mailhog](https://github.com/mailhog/MailHog) locally with Docker to
> capture outgoing reset emails without a real SMTP relay:
> ```bash
> docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
> # View captured emails at http://localhost:8025
> ```

### 3. Start the application (dev profile)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway migrations run automatically on startup. Server listens on `http://localhost:8080`.

---

## Validation Scenarios

### Scenario 1 — Comensal Registration

> Covers: FR-001, FR-002, FR-003, FR-004 | SC-001, SC-003

**1a — Happy path: successful registration**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com", "password": "SecurePass1"}' | jq
```

Expected: **HTTP 201**
```json
{ "message": "Account created successfully." }
```

Verify in DB:
```sql
SELECT email, status, role, LEFT(password_hash, 7) AS hash_prefix
FROM users WHERE email = 'nuevo@example.com';
-- email must be lowercase
-- status = 'ACTIVE'
-- role = 'COMENSAL'
-- hash_prefix = '$2a$12' (BCrypt cost 12)
```

**1b — Duplicate email: HTTP 409**

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com", "password": "AnotherPass1"}'
# Expected output: 409
```

**1c — Weak password: HTTP 422 with field errors**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "otro@example.com", "password": "weak"}' | jq
```

Expected: **HTTP 422** — `errors` array listing specific policy violations.

**1d — Email normalization (FR-002)**

```bash
# Register with uppercase and spaces
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "  NORMA@EXAMPLE.COM  ", "password": "SecurePass1"}' | jq
```

Expected: **HTTP 201** — email stored as `norma@example.com` (verify in DB).

---

### Scenario 2 — Unified Login

> Covers: FR-005, FR-006, FR-007, FR-008, FR-009 | SC-002, SC-004, SC-006

**2a — Happy path: valid login**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com", "password": "SecurePass1"}' | jq
```

Expected: **HTTP 200**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 28800
}
```

Verify token at **[jwt.io](https://jwt.io)** — paste `access_token` and confirm:
- `sub`: valid UUID matching `users.id`
- `role`: `"COMENSAL"`
- `tenant_id`: absent or null
- `token_version`: `0`
- `exp - iat`: exactly `28800` seconds

**2b — Anti-enumeration: identical error for wrong password vs. non-existent email (FR-007)**

```bash
# Wrong password — existing email
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com", "password": "WrongPass1"}' | jq

# Non-existent email
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "ghost@example.com", "password": "AnyPass1"}' | jq
```

Expected: **HTTP 401** for both — **identical** `detail` text and comparable response times
(DUMMY_HASH applied for non-existent email path).

**2c — Rate limiting: 5 failures → lockout (FR-008)**

```bash
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "Attempt $i: %{http_code}\n" \
    -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email": "nuevo@example.com", "password": "WrongPass1"}'
done
```

Expected: Attempts 1–5 → HTTP 401. Attempt 6 → **HTTP 429** with `Retry-After` header.

**2d — Suspended account: HTTP 403 (FR-009)**

```sql
-- Suspend the account
UPDATE users SET status = 'SUSPENDED' WHERE email = 'nuevo@example.com';
```

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com", "password": "SecurePass1"}' | jq
# Expected: HTTP 403
```

Restore before next scenario:
```sql
UPDATE users SET status = 'ACTIVE' WHERE email = 'nuevo@example.com';
```

---

### Scenario 3 — Password Reset Request

> Covers: FR-010, FR-011, FR-012, FR-013 | SC-005, SC-006

**3a — Happy path: registered email**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "nuevo@example.com"}' | jq
```

Expected: **HTTP 200**
```json
{ "message": "If that email address is registered, a recovery link has been sent." }
```

Verify token in DB:
```sql
SELECT prt.token_hash, prt.status, prt.expires_at,
       (prt.expires_at - prt.created_at) AS ttl
FROM password_reset_tokens prt
JOIN users u ON u.id = prt.user_id
WHERE u.email = 'nuevo@example.com'
ORDER BY prt.created_at DESC
LIMIT 1;
-- status = 'PENDING'
-- ttl ≈ 15 minutes
```

View the reset link in Mailhog at `http://localhost:8025`.

**3b — Anti-enumeration: unregistered email returns identical 200 (FR-012)**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "ghost@example.com"}' | jq
# Expected: HTTP 200 — same message body; no token created in DB; no email sent
```

---

### Scenario 4 — Password Reset Confirm

> Covers: FR-014, FR-015 | SC-007

*Extract the raw token from the Mailhog-captured reset email URL query parameter `?token=<value>`.*

**4a — Happy path: valid token + new password**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{"token": "<raw-token-from-email>", "new_password": "NewSecurePass9"}' | jq
```

Expected: **HTTP 200**
```json
{ "message": "Password updated successfully. All previous sessions have been invalidated." }
```

Verify (SC-007 — full session revocation):

```sql
-- Token status must be CONSUMED
SELECT status, consumed_at FROM password_reset_tokens WHERE token_hash = sha256('<raw-token>');

-- token_version must have incremented
SELECT token_version FROM users WHERE email = 'nuevo@example.com';
-- Expected: 1 (was 0 before reset)
```

Old JWT (from Scenario 2a) — any protected endpoint must now return **HTTP 401** (stale `token_version`).

**4b — Expired token: HTTP 400 (FR-014)**

Manually set `expires_at` to the past in DB:
```sql
UPDATE password_reset_tokens SET expires_at = NOW() - INTERVAL '1 minute'
WHERE status = 'PENDING';
```

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{"token": "<same-token>", "new_password": "NewSecurePass9"}' | jq
# Expected: HTTP 400, type: token-expired
```

**4c — Double use: HTTP 400 (FR-015)**

Use the same valid token twice — first call returns 200, second call must return **HTTP 400**
with `type: token-already-consumed`.

---

### Scenario 5 — Email Normalization (End-to-End)

> Covers: FR-002

```bash
# Register with mixed case
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "NORM.test@EXAMPLE.COM", "password": "SecurePass1"}' | jq

# Login with lowercase + trailing space
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "norm.test@example.com   ", "password": "SecurePass1"}' | jq
# Expected: HTTP 200 — successful authentication
```

---

## Running Automated Tests

### Unit tests (no Docker required)

```bash
mvn test -Dtest="*Test" -DfailIfNoTests=false
```

Expected: all unit tests pass; `JavaMailSender` interactions verified via Mockito.

### Integration tests (Docker required — Testcontainers)

```bash
mvn verify -Dtest="*IntegrationTest"
```

Expected: Testcontainers launches a PostgreSQL 16 container, Flyway runs all migrations,
Spring context starts, and all integration scenarios pass.

### Full test suite + coverage

```bash
mvn verify
```

### Security audit: verify no credential leakage in test output (SC-003, SC-008)

```bash
# Should return zero matches
grep -rni "password\|secret\|token_hash\|raw.*token" target/surefire-reports/ | wc -l
```

---

## References

- [OpenAPI Contract](./contracts/openapi.yaml) — full endpoint and schema definitions
- [Data Model](./data-model.md) — entity tables, Flyway migrations, state machines
- [Feature Specification](./spec.md) — functional requirements FR-001..FR-017
- [Research & Decisions](./research.md) — technology choices and rationale
