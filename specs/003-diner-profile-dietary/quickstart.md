# Quickstart Validation Guide: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: `003-diner-profile-dietary` | **Date**: 2026-10-21 | **Phase**: 1

This guide documents runnable end-to-end validation scenarios that demonstrate the feature working across the API and domain layers within the `easyeat/` monorepo.
Refer to [openapi.yaml](./contracts/openapi.yaml) for formal schemas and [data-model.md](./data-model.md) for database structure.

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21 (LTS) | Backend runtime |
| Maven | 3.9+ | Build and test tool (`mvn`) |
| Docker | 24+ | PostgreSQL 16 container for tests (`Testcontainers`) |
| `curl` and `jq` | any | CLI endpoint testing |
| Node.js / npm | Node 20+ | Frontend React testing |

---

## Setup & Environment

### 1. Configure Environment Variables

```bash
export JWT_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
export SPRING_MAIL_HOST="localhost"
export SPRING_MAIL_PORT="1025"
```

> **Mailhog tip**: Run Mailhog locally to capture verification emails:
> ```bash
> docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
> # Access web UI at http://localhost:8025
> ```

### 2. Run Database Migrations and Start Service

From repository root:
```bash
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Server listens on `http://localhost:8080`. Flyway migrations `V1` through `V5` (including `V5__extend_users_profile_and_dietary.sql`) execute automatically.

---

## Validation Scenarios

### Scenario 1 — Personal Data Update & Format Validation

> Covers: FR-001, FR-002, FR-003, FR-010 | Acceptance Scenarios 1.1–1.5

**1a — Update name and valid phone**

```bash
curl -s -X PUT http://localhost:8080/api/v1/profile/personal-data \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Valentino",
    "lastName": "Carmona",
    "phone": "+54 9 11 2345-6789"
  }' | jq
```

Expected: **HTTP 200 OK**
```json
{
  "id": "e4b6c8d2-...",
  "firstName": "Valentino",
  "lastName": "Carmona",
  "phone": "+54 9 11 2345-6789",
  "email": "valentino@example.com",
  "pendingEmail": null,
  "dietaryRestrictions": []
}
```

**1b — Rejection of digits/symbols in name**

```bash
curl -s -X PUT http://localhost:8080/api/v1/profile/personal-data \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Valentino123",
    "lastName": "Carmona!"
  }' | jq
```

Expected: **HTTP 400 Bad Request** / **422 Unprocessable Entity** (RFC 9457 `ProblemDetail` with invalidParams).

---

### Scenario 2 — Asynchronous Email Verification Workflow & Cooldown

> Covers: FR-004, FR-005, FR-006, FR-007, FR-008, FR-009 | Acceptance Scenarios 1.6–1.11

**2a — Request email change**

```bash
curl -s -X POST http://localhost:8080/api/v1/profile/email-change/request \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "newEmail": "nuevo.email@example.com"
  }' | jq
```

Expected: **HTTP 202 Accepted**
```json
{
  "pendingEmail": "nuevo.email@example.com",
  "status": "PENDING_VERIFICATION",
  "expiresAt": "2026-10-22T19:00:00Z"
}
```

**2b — Immediate resend rejected by rate-limiting cooldown (<60s)**

```bash
curl -s -i -X POST http://localhost:8080/api/v1/profile/email-change/resend \
  -H "Authorization: Bearer $TOKEN"
```

Expected: **HTTP 429 Too Many Requests** with header `Retry-After: <seconds>`

**2c — Confirm email change with token (Session Revocation)**

```bash
curl -s -X POST http://localhost:8080/api/v1/profile/email-change/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "token": "raw-token-from-mailhog-link"
  }' | jq
```

Expected: **HTTP 200 OK**
```json
{
  "email": "nuevo.email@example.com",
  "status": "VERIFIED",
  "requiresReauthentication": true
}
```

*Verification*: Old `$TOKEN` will now be rejected with **HTTP 401 Unauthorized** ("Tu sesión ha expirado o ha sido invalidada") due to `token_version` incrementation (D-006).

---

### Scenario 3 — Standardized Taxonomy & Dietary Preferences Persistence

> Covers: FR-012, FR-013, FR-014 | Acceptance Scenarios 2.1–2.3

**3a — Retrieve taxonomy (Public endpoint)**

```bash
curl -s http://localhost:8080/api/v1/dietary-restrictions | jq
```

Expected: **HTTP 200 OK** (Array of 11 items: 3 Lifestyles, 8 Allergens).

**3b — Save diner dietary preferences**

```bash
curl -s -X PUT http://localhost:8080/api/v1/profile/dietary-restrictions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "restrictionCodes": ["CELIAC", "PEANUTS"]
  }' | jq
```

Expected: **HTTP 200 OK** returning updated restrictions array.

---

### Scenario 4 — Automated Catalog Filtering & Conjunction Rules

> Covers: FR-017, FR-018, FR-019, FR-020, FR-026 | Acceptance Scenarios 3.1–3.4, 3.10

**4a — Retrieve catalog as guest (Public endpoint without Bearer token)**

```bash
curl -s http://localhost:8080/api/v1/establishments/00000000-0000-0000-0000-000000000001/dishes | jq
```

Expected: **HTTP 200 OK** (Dishes returned with all available items; `isCompliant` defaults to true or null for guest).

**4b — Retrieve catalog as authenticated diner with `["CELIAC", "PEANUTS"]`**

```bash
curl -s http://localhost:8080/api/v1/establishments/00000000-0000-0000-0000-000000000001/dishes \
  -H "Authorization: Bearer $TOKEN" | jq
```

Expected: **HTTP 200 OK**. Dishes evaluated server-side upfront:
```json
[
  {
    "id": "11111111-...",
    "name": "Ensalada Mediterránea",
    "price": 4500.00,
    "allergens": [],
    "lifestyles": ["CELIAC", "VEGETARIAN"],
    "isCompliant": true,
    "conflictingRestrictions": []
  },
  {
    "id": "22222222-...",
    "name": "Pad Thai con Maní",
    "price": 6200.00,
    "allergens": ["PEANUTS", "SOY"],
    "lifestyles": [],
    "isCompliant": false,
    "conflictingRestrictions": ["PEANUTS", "CELIAC"]
  }
]
```

---

### Scenario 5 — Live Cart Dietary Re-evaluation & Preventive Risk Assumption

> Covers: FR-015, FR-021, FR-022 | Acceptance Scenarios 2.4, 3.5, 3.6

When a diner adds a non-compliant dish to the cart (with master toggle OFF), the frontend triggers `AddToCartRiskModal.tsx` requiring explicit confirmation. Once accepted, items enter the cart flagged with inline warning badges. In the cart drawer and checkout review, warning indicators remain visible without requiring redundant blocking modals at checkout.

**5a — Live evaluate cart dishes**

```bash
curl -s -X POST http://localhost:8080/api/v1/cart/evaluate-compliance \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "dishIds": ["11111111-...", "22222222-..."]
  }' | jq
```

Expected: **HTTP 200 OK**
```json
{
  "hasConflicts": true,
  "conflictingDishes": [
    {
      "dishId": "22222222-...",
      "name": "Pad Thai con Maní",
      "conflictingRestrictions": ["PEANUTS", "CELIAC"]
    }
  ]
}
```
