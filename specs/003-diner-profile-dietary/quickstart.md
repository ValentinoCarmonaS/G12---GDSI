# Quickstart Validation Guide: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: `003-diner-profile-dietary` | **Date**: 2026-10-17 | **Phase**: 1

This guide documents runnable end-to-end validation scenarios that demonstrate the feature working across the API and domain layers.
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

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Server listens on `http://localhost:8080`. Flyway migration `V3__extend_users_profile_and_dietary.sql` executes automatically.

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
  -d '{ "newEmail": "valentino.new@example.com" }' | jq
```

Expected: **HTTP 202 Accepted**
```json
{
  "message": "Verification email sent to new address.",
  "pendingEmail": "valentino.new@example.com",
  "expiresAt": "2026-10-18T22:30:00Z"
}
```

**2b — Cooldown rate limiting on resend**

Immediately requesting resend:
```bash
curl -i -X POST http://localhost:8080/api/v1/profile/email-change/resend \
  -H "Authorization: Bearer $TOKEN"
```

Expected: **HTTP 429 Too Many Requests**
```text
Retry-After: 58
Content-Type: application/problem+json
```

**2c — Confirm change with token**

Retrieve the token from the Mailhog web interface (`http://localhost:8025`) and submit:

```bash
curl -s -X POST http://localhost:8080/api/v1/profile/email-change/confirm \
  -H "Content-Type: application/json" \
  -d '{ "token": "<RAW_TOKEN_FROM_EMAIL>" }' | jq
```

Expected: **HTTP 200 OK**
```json
{
  "message": "Email successfully verified and updated.",
  "activeEmail": "valentino.new@example.com"
}
```

Old JWT token is now rejected on subsequent calls due to `token_version` increment (**HTTP 401 Unauthorized**).

---

### Scenario 3 — Dietary Preferences Management

> Covers: FR-012, FR-013, FR-014 | Acceptance Scenarios 2.1–2.3

**3a — Query standardized platform taxonomy**

```bash
curl -s http://localhost:8080/api/v1/dietary-restrictions | jq
```

Expected: **HTTP 200 OK** returning array containing `CELIAC`, `VEGAN`, `VEGETARIAN`, `DAIRY`, `PEANUTS`, etc.

**3b — Save multiple preferences (Vegan + Peanut Allergy)**

```bash
curl -s -X PUT http://localhost:8080/api/v1/profile/dietary-restrictions \
  -H "Authorization: Bearer $NEW_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "restrictionCodes": ["VEGAN", "PEANUTS"] }' | jq
```

Expected: **HTTP 200 OK** returning updated active restrictions array.

---

### Scenario 4 — Automated Catalog Filtering & Conjunction Engine

> Covers: FR-017, FR-018, FR-019, FR-020, FR-024, FR-025 | Acceptance Scenarios 3.1–3.4

**4a — Retrieve establishment dishes as authenticated diner**

```bash
curl -s http://localhost:8080/api/v1/establishments/c1b2a3d4-0000-0000-0000-000000000001/dishes \
  -H "Authorization: Bearer $NEW_TOKEN" | jq
```

Expected: **HTTP 200 OK**
```json
[
  {
    "id": "d1e2f3a4-...",
    "name": "Ensalada Vegana",
    "price": 7500.00,
    "isCompliant": true,
    "conflictingRestrictions": []
  },
  {
    "id": "d5e6f7a8-...",
    "name": "Wok con Salsa de Maní",
    "price": 9200.00,
    "isCompliant": false,
    "conflictingRestrictions": ["PEANUTS"]
  }
]
```

Client-side behavior:
- Master toggle ON: client renders only item 1 (`isCompliant === true`).
- Master toggle OFF: client renders both items, attaching `<AllergenWarningBadge code="PEANUTS" />` to item 2.

---

### Scenario 5 — Live Cart Re-evaluation & Checkout Risk Confirmation

> Covers: FR-015, FR-021, FR-022 | Acceptance Scenarios 2.4, 3.5, 3.6

**5a — Re-evaluate cart items upon profile update**

```bash
curl -s -X POST http://localhost:8080/api/v1/cart/evaluate-compliance \
  -H "Authorization: Bearer $NEW_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "dishIds": ["d1e2f3a4-...", "d5e6f7a8-..."] }' | jq
```

Expected: **HTTP 200 OK** with item-level compliance mapping, allowing the frontend cart to flag conflicting dishes immediately.

**5b — Order checkout validation**

- Submitting order with non-compliant item and `acceptedDietaryRisks: false` → **HTTP 422 Unprocessable Entity**.
- Submitting order with `acceptedDietaryRisks: true` after modal confirmation → **HTTP 201 Created**.
