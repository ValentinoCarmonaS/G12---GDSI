# Research & Technical Decisions: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: `003-diner-profile-dietary` | **Date**: 2026-10-17 | **Phase**: 0

This document consolidates the technical research, architecture choices, and rationale derived from the interactive `/grill-me` design session.

---

## Technical Decisions Index

| ID | Topic | Decision | Primary Rationale |
|---|---|---|---|
| D-001 | Compliance Engine Location | In-memory Java service (`CatalogComplianceService`) | Low latency (<1ms), decouples catalog queries from relational joins, 100% testable in unit tests, shared between catalog and cart |
| D-002 | Email Resend Cooldown | `email_verification_last_sent_at` column in `users` | Atomic database persistence, survives server/pod restarts, works across multi-instance clusters without external cache |
| D-003 | Relational Schema for Dishes & Restrictions | Normalized join table `dish_dietary_restrictions` | Strict referential integrity with `dietary_restrictions`, prevents orphan strings, enables real integration testing in Testcontainers |
| D-004 | Master Toggle Client State | In-memory React Context / hook reset on reload or tenant change | Guarantees safety invariant (FR-023): page refresh or restaurant navigation automatically resets toggle to ON (0ms persistent leakage) |
| D-005 | Security Principal Extraction | `@AuthenticationPrincipal UserPrincipal` via `JwtAuthenticationFilter` | O(1) in-memory extraction of `userId` and `role` from JWT claims without redundant database round-trips |
| D-006 | Email Change Invalidation | Increment `token_version` by 1 upon email confirmation | Immediately revokes all active JWT sessions across devices, enforcing re-login under the verified email address |
| D-007 | Email Verification Token Security | SHA-256 hash storage with raw token dispatched via email | Raw cryptographic token is never stored in the database; prevents token compromise in case of read-only DB breach |
| D-008 | Checkout Dietary Risk Consent | Mandatory `acceptedDietaryRisks: boolean` in order payload | Ensures legally binding informed consent before order placement when cart contains incompatible dishes |

---

## Detailed Decisions & Alternatives Considered

### D-001: Dietary Compliance Engine Architecture

- **Decision**: Execute dietary compliance filtering and conflict identification in-memory within the Spring application layer via `CatalogComplianceService`.
- **Mechanism**:
  1. For `GET /api/v1/establishments/{tenantId}/dishes`, the backend fetches the active dishes of the tenant and their assigned dietary/allergen tags.
  2. If the user is authenticated as a `COMENSAL`, the service retrieves the user's active dietary restriction codes.
  3. The service evaluates the strict intersection (AND logic):
     - For lifestyle restrictions (`CELIAC`, `VEGAN`, `VEGETARIAN`): the dish must possess compliance tags for all of the diner's selected lifestyles.
     - For allergen restrictions (`DAIRY`, `PEANUTS`, etc.): the dish must **not** contain any of the diner's configured allergens.
     - `is_compliant = (lifestylesMet == true) && (conflictingAllergens.isEmpty())`.
     - `conflicting_restrictions` collects all violated lifestyle and allergen codes.
  4. The exact same evaluation logic is exposed via `POST /api/v1/cart/evaluate-compliance` taking a list of `dishIds`.
- **Alternatives Considered**:
  - *PostgreSQL SQL/JSONB query*: Writing complex SQL with array operators (`&&`, `<@`) inside Flyway migrations. Rejected due to tight database coupling, harder unit testing, and inability to reuse identical logic for cart re-evaluations.
  - *Frontend-only filtering*: Returning raw dish allergens and letting React filter. Rejected because it violates the spec safety invariant requiring dual client/server validation (FR-027) and exposes business logic to client tampering.

---

### D-002: Email Change Resend Rate Limiting (Cooldown)

- **Decision**: Persist `email_verification_last_sent_at TIMESTAMPTZ` on the `users` table.
- **Mechanism**:
  - When `POST /api/v1/profile/email-change/resend` is called:
    - Check if `NOW() - email_verification_last_sent_at < INTERVAL '60 seconds'`.
    - If violated, throw `RateLimitExceededException` mapped to RFC 9457 `429 Too Many Requests` with a `Retry-After: <seconds>` header.
    - If valid, update `email_verification_last_sent_at = NOW()` and dispatch email asynchronously via Spring `@Async`.
- **Alternatives Considered**:
  - *In-memory Caffeine Cache*: Used in Feature 001 for IP login rate-limiting. Rejected here because email change is an account-level modification where state should survive container restarts and work consistently across multiple backend instances.
  - *Redis Distributed Lock/Counter*: Overkill for single-instance MVP; constitution Principle IV (Simple, Cohesive, Justified Design) advises against adding infrastructure dependencies without justification.

---

### D-003: Relational Modeling of Dishes and Dietary Compliance

- **Decision**: Define a normalized join table `dish_dietary_restrictions` in Flyway migration `V3__extend_users_profile_and_dietary.sql`.
- **Schema**:
  - `dishes`: Represents items offered by an establishment. Columns: `id (UUID PK)`, `tenant_id (UUID NOT NULL)`, `name (VARCHAR NOT NULL)`, `description (TEXT)`, `price (NUMERIC(10,2) NOT NULL)`, `is_active (BOOLEAN DEFAULT TRUE)`, `created_at`, `updated_at`.
  - `dish_dietary_restrictions`: Composite PK `(dish_id, restriction_code)`. Foreign Key `dish_id` references `dishes(id)` ON DELETE CASCADE; Foreign Key `restriction_code` references `dietary_restrictions(code)` ON DELETE RESTRICT.
- **Alternatives Considered**:
  - *PostgreSQL native ARRAY `VARCHAR[]`*: Lacks referential integrity; a typo in an allergen code could corrupt food safety evaluations.
  - *In-memory stubs only*: Would prevent integration testing with real database queries via Testcontainers.

---

### D-004: Catalog Master Toggle Client State Management

- **Decision**: Store the catalog master toggle in-memory within a React Context (`CatalogFilterContext`) or custom hook (`useCatalog`), scoped strictly to the current component lifecycle and active `tenantId`.
- **Mechanism**:
  - On mount: `isFilterActive` defaults to `true`.
  - If diner has 0 restrictions: toggle is hidden from UI; `isFilterActive` remains `false`.
  - On page refresh (F5) or route change to another establishment (`/establishments/:tenantId`): React state re-initializes to default `true`.
  - The toggle flips between client-side filtering:
    - `isFilterActive === true`: filter array where `dish.is_compliant === true`.
    - `isFilterActive === false`: render all dishes, rendering `<AllergenWarningBadge />` for each entry in `dish.conflicting_restrictions`.
    - Since compliance is pre-calculated by the backend, toggling produces **0ms latency** with no extra network requests.
- **Alternatives Considered**:
  - *`sessionStorage`*: Persists across F5 browser refreshes, which directly violates acceptance scenario 7 ("reloading the page resets the master toggle back to ON").

---

### D-005: Security Principal & Context Resolution

- **Decision**: Inject `@AuthenticationPrincipal UserPrincipal principal` in Spring MVC controllers.
- **Mechanism**:
  - `JwtAuthenticationFilter` intercepts requests with `Authorization: Bearer <jwt>`.
  - Validates token signature, expiration, and checks that token `token_version` matches the active user's version.
  - Constructs `UserPrincipal(UUID id, String email, String role, UUID tenantId)` and places an `AuthenticatedUserToken` into `SecurityContextHolder`.
  - Controllers obtain the authenticated diner's `UUID` directly from parameter injection with zero database overhead.
- **Alternatives Considered**:
  - *Database lookup per request*: Calling `userRepository.findByEmail(...)` in every endpoint adds 5–15ms latency unnecessarily to stateless endpoints.

---

### D-006: Session Revocation on Email Change

- **Decision**: Atomically increment `users.token_version` by 1 upon email confirmation.
- **Mechanism**:
  - In `EmailChangeService.confirmEmail(token)`:
    - Verify token validity and expiration.
    - Set `users.email = users.pending_email`.
    - Clear `users.pending_email`, `users.email_verification_token_hash`, `users.email_token_expires_at`.
    - Execute `users.token_version = users.token_version + 1`.
    - All previously issued JWT tokens containing old `token_version` become immediately invalid on their next request.
- **Alternatives Considered**:
  - *Token blacklisting in Redis*: Adds operational complexity. `token_version` is fully stateless, standard, and already established in Feature 001.

---

### D-007: Cryptographic Storage of Verification Tokens

- **Decision**: Store only the SHA-256 hash of the verification token in `users.email_verification_token_hash`.
- **Mechanism**:
  - System generates a cryptographically secure random token: `SecureRandom` 32 bytes URL-safe base64.
  - Raw token is sent in the verification email URL query param: `?token=...`.
  - Database stores `DigestUtils.sha256Hex(rawToken)`.
  - When confirming, the incoming raw token is hashed and queried: `findByEmailVerificationTokenHash(sha256Hex(token))`.
- **Alternatives Considered**:
  - *Plaintext token storage*: Security risk if database snapshots or logs leak.

---

### D-008: Checkout Dietary Risk Informed Consent

- **Decision**: Require `acceptedDietaryRisks: boolean` in the checkout order submission contract.
- **Mechanism**:
  - If a diner's cart contains any dish with `is_compliant === false`, the checkout button opens a mandatory `CheckoutDietaryRiskModal`.
  - The modal explicitly lists each conflicting item alongside the specific allergens or diets it violates.
  - The diner must check "Entiendo y asumo el riesgo alimentario" to enable submission.
  - Backend checkout endpoint validates: if any cart item is non-compliant and `acceptedDietaryRisks != true`, the request is rejected with `422 Unprocessable Entity` (`DIETARY_RISK_NOT_ACCEPTED`).
