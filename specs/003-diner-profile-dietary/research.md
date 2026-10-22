# Research & Technical Decisions: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: `003-diner-profile-dietary` | **Date**: 2026-10-21 | **Phase**: 0

This document consolidates the technical research, architecture choices, and rationale derived from the interactive `/grill-me` design session and the cross-specification alignment with baseline features 001 (`central-auth-recovery`), 002 (`staff-auth-redirect-guards`), and 004 (`staff-mgmt-rbac-inventory`).

---

## Technical Decisions Index

| ID | Topic | Decision | Primary Rationale |
|---|---|---|---|
| D-001 | Compliance Engine Location | In-memory Java service (`CatalogComplianceService`) | Low latency (<1ms), decouples catalog queries from relational joins, 100% testable in unit tests, shared between catalog and cart. |
| D-002 | Email Resend Cooldown | `email_verification_last_sent_at` column in `users` | Atomic database persistence, survives server/pod restarts, works across multi-instance clusters without external cache. |
| D-003 | Relational Schema for Dishes & Restrictions | Normalized join table `dish_dietary_restrictions` in Flyway migration `V5` | Strict referential integrity with `dietary_restrictions`, prevents orphan strings, enables real integration testing in Testcontainers. Numbered `V5` to follow `V3__extend_users_staff.sql` and `V4__create_ingredients_and_stock_audit.sql` from 004. |
| D-004 | Master Toggle Client State | In-memory React Context / hook reset on reload or tenant change | Guarantees safety invariant (FR-023): page refresh or restaurant navigation automatically resets toggle to ON (0ms persistent leakage). |
| D-005 | Security Principal & Context Resolution | `@AuthenticationPrincipal User user` via `JwtAuthenticationFilter` | O(1) direct injection of the authenticated domain `User` entity populated by the existing `JwtAuthenticationFilter` from 001/002, eliminating redundant database lookups or unnecessary custom principal types. |
| D-006 | Session Revocation on Email Change | Increment `token_version` by 1 upon email confirmation | Aligns with 001 and 002 single-active-session and logout policy: immediately revokes all active JWT sessions across devices, enforcing re-login under the verified email address (FR-009). |
| D-007 | Email Verification Token Security | SHA-256 hash storage with raw token dispatched via email | Raw cryptographic token is never stored in the database; prevents token compromise in case of read-only DB breach. |
| D-008 | Checkout Dietary Risk Consent | Mandatory `acceptedDietaryRisks: boolean` in order payload | Ensures legally binding informed consent before order placement when cart contains incompatible dishes. |
| D-009 | Diner Name Attributes Decoupling | Independent `first_name` and `last_name` columns in `users` table | Comensal personal data operates on discrete first and last name fields without overwriting or coupling to the `full_name` column introduced for staff in 004, preserving distinct domain semantics. |
| D-010 | Public Catalog Access & Security Config | Configure `permitAll()` in `SecurityConfig` and remove strict guard in `AppRoutes.tsx` | Allows unauthenticated guests to browse restaurant catalogs freely (FR-026) with guest call-to-actions, while dynamically applying profile health filters when a valid JWT is present. Also opens `GET /api/v1/dietary-restrictions` and `POST /api/v1/profile/email-change/confirm`. |
| D-011 | Package & Repository Architecture | `ProfileUserRepository` in `com.easyeat.profile.repository` within monorepo layout | Follows the proven bounded-context pattern established by `StaffUserRepository` in 004, isolating profile-specific queries while targeting the existing `users` table under `easyeat/backend/`. |
| D-012 | Diner Profile Navigation Entry Point | Dedicated "Mi perfil" action in `WorkspaceLayout` | Exposes a direct link to `/profile` in the top operational header when the authenticated user role is `COMENSAL`, alongside the role badge and logout action. |

---

## Detailed Decisions & Baseline Alignment

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
  - *PostgreSQL SQL/JSONB query*: Complex SQL with array operators (`&&`, `<@`) inside Flyway migrations. Rejected due to tight database coupling, harder unit testing, and inability to reuse identical logic for cart re-evaluations.
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

### D-003: Relational Modeling of Dishes and Dietary Compliance (Flyway V5)

- **Decision**: Define a normalized join table `dish_dietary_restrictions` in Flyway migration `V5__extend_users_profile_and_dietary.sql`.
- **Baseline Alignment**:
  - Iteration 001 introduced `V1__create_users.sql` and `V2__create_password_reset_tokens.sql`.
  - Iteration 004 introduced `V3__extend_users_staff.sql` and `V4__create_ingredients_and_stock_audit.sql`.
  - Therefore, the migration version for feature 003 must be `V5`.
- **Schema**:
  - `dishes`: Represents items offered by an establishment. Columns: `id (UUID PK)`, `tenant_id (UUID NOT NULL)`, `name (VARCHAR NOT NULL)`, `description (TEXT)`, `price (NUMERIC(10,2) NOT NULL)`, `is_active (BOOLEAN DEFAULT TRUE)`, `created_at`, `updated_at`.
  - `dish_dietary_restrictions`: Composite PK `(dish_id, restriction_code)`. Foreign Key `dish_id` references `dishes(id)` ON DELETE CASCADE; Foreign Key `restriction_code` references `dietary_restrictions(code)` ON DELETE RESTRICT.

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

---

### D-005: Security Principal & Context Resolution

- **Decision**: Inject `@AuthenticationPrincipal User user` in Spring MVC controllers (`com.easyeat.auth.domain.User`).
- **Baseline Alignment**:
  - `JwtAuthenticationFilter` (established in 001 and enhanced in 002) populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` whose principal is the `User` entity instance.
  - This eliminates intermediate DTO conversions (`UserPrincipal`) and maintains complete consistency across controllers in `auth`, `staff`, `inventory`, and `profile`.

---

### D-006: Session Revocation on Email Change

- **Decision**: Increment `token_version` by 1 upon email confirmation.
- **Baseline Alignment**:
  - Feature 001 defined `token_version` on `users` and in JWT claims.
  - Feature 002's `JwtAuthenticationFilter` validates `token_version` on every request. If `token_version` differs, it immediately rejects the request with HTTP 401 Unauthorized (`"Tu sesión ha expirado o ha sido invalidada"`).
  - Calling `user.setTokenVersion(user.getTokenVersion() + 1)` on email confirmation instantly invalidates all active sessions across all devices, strictly enforcing FR-009.

---

### D-007: Email Verification Token Security

- **Decision**: Compute SHA-256 hash of the generated cryptographic token and store only the hash in `users.email_verification_token_hash`.
- **Mechanism**:
  - 32-byte secure random token generated (`SecureRandom`), base64url encoded.
  - Hashed via `MessageDigest.getInstance("SHA-256")` and stored in DB.
  - Verification link sends the raw token: `/verify-email?token=<rawToken>`.
  - Confirmation endpoint hashes the incoming token before querying `email_verification_token_hash`.

---

### D-008: Checkout Dietary Risk Consent

- **Decision**: Enforce `acceptedDietaryRisks: boolean` in order checkout requests whenever non-compliant items exist in the cart.
- **Mechanism**:
  - If cart evaluation reports non-compliant items, frontend presents a mandatory acknowledgment modal (`CheckoutDietaryRiskModal`).
  - Order submission includes `acceptedDietaryRisks: true`. Backend verifies this flag and records the consent in the order snapshot.

---

### D-009: Diner Name Attributes Decoupling

- **Decision**: Store `first_name` and `last_name` in separate columns in `users` table without modifying or synchronizing the `full_name` column introduced in 004.
- **Rationale**:
  - Iteration 004 introduced `full_name` for staff administration (`ADM-3.1.1`).
  - Comensal profile requirements (FR-001, FR-002) mandate individual validation for first name and last name (alphabetic and spaces only).
  - Keeping them independent avoids side effects on existing staff logic and fulfills comensal domain requirements cleanly.

---

### D-010: Public Catalog Browsing & Security Config

- **Decision**: Allow anonymous catalog browsing by configuring `permitAll()` for catalog read endpoints and adapting frontend routing.
- **Mechanism**:
  - In `SecurityConfig.java`:
    - Permit all for `GET /api/v1/establishments/{tenantId}/dishes`, `GET /api/v1/dietary-restrictions`, and `POST /api/v1/profile/email-change/confirm`.
    - Profile endpoints (`/api/v1/profile/**` except confirm) require authentication.
  - In `AppRoutes.tsx`:
    - `/catalog/*` is no longer restricted strictly by `RoleRouteGuard`.
    - Guests viewing `/catalog` receive unfiltered catalog data with a prominent banner prompting them to log in or register to enable health protections (FR-026).

---

### D-011: Package & Repository Architecture

- **Decision**: Create `ProfileUserRepository` in `com.easyeat.profile.repository` extending `JpaRepository<User, UUID>`.
- **Rationale**:
  - Follows the bounded-context separation model established in 004 with `StaffUserRepository`.
  - Keeps `UserRepository` in `com.easyeat.auth` focused on credentials, while `ProfileUserRepository` provides specialized methods (e.g. `findByEmailVerificationTokenHash`, `existsByPendingEmailIgnoreCase`).

---

### D-012: Diner Profile Navigation Entry Point

- **Decision**: Include a "Mi perfil" link in `WorkspaceLayout` top navigation bar for `COMENSAL` users.
- **Mechanism**:
  - In `WorkspaceLayout.tsx`, when `user.role === 'COMENSAL'`, render a navigation link pointing to `/profile` adjacent to the role badge and the logout button.
