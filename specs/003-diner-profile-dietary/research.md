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
| D-008 | Preventive Add-to-Cart Dietary Risk Consent | Intercept non-compliant dishes upon clicking "Agregar al carrito" via `AddToCartRiskModal` | Single-point risk confirmation (FR-021, FR-022, SC-005). Once accepted, cart drawer and checkout display permanent visible warning badges without redundant blocking modals at checkout. |
| D-009 | Diner Name Attributes & Full Name Synchronization | Independent `first_name` and `last_name` columns with automatic `full_name` sync | Enforces strict alphabetic validation on first and last name for comensales (COM-1.4.1), while keeping `full_name` synchronized on `users` for full consistency with Specs 001, 002, and 004 (`AuthContext`, `WorkspaceLayout`). |
| D-010 | Public Catalog Access & Canonical Spanish Routing | Configure `permitAll()` in `SecurityConfig`, canonical Spanish routes in `AppRoutes.tsx` | Unauthenticated guests browse catalogs freely (FR-026) with guest CTA banner; comensales receive filtered catalogs. Routes follow Spec 002: canonical `/perfil` and `/verificar-email` with redirects from `/profile` and `/verify-email`. |
| D-011 | Package & Repository Architecture | `ProfileUserRepository` in `com.easyeat.profile.repository` within monorepo layout | Follows the proven bounded-context pattern established by `StaffUserRepository` in 004, isolating profile-specific queries while targeting the existing `users` table under `easyeat/backend/`. |
| D-012 | Diner Navigation in WorkspaceLayout | "Catálogo" and "Mi perfil" navigation links in `WorkspaceLayout` | Exposes `/catalogo` and `/perfil` in the top navigation bar when user role is `COMENSAL`, adhering to the navigation pattern introduced for `CHEF` and `ADMINISTRADOR` in 004. |

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
  - Verification link sends the raw token: `/verificar-email?token=<rawToken>`.
  - Confirmation endpoint hashes the incoming token before querying `email_verification_token_hash`.

---

### D-008: Preventive Add-to-Cart Dietary Risk Consent (Single-Point Confirmation)

- **Decision**: Enforce single-point risk confirmation via `AddToCartRiskModal.tsx` upon clicking "Agregar al carrito" when a dish conflicts with active dietary restrictions (with master toggle OFF).
- **Mechanism**:
  - When the diner attempts to add a non-compliant dish to the cart, the frontend intercepts the action with `AddToCartRiskModal`, listing the conflicting allergens/lifestyles and requesting explicit risk assumption (FR-021).
  - If canceled, cart state remains untouched (Scenario 3.5).
  - If confirmed, the item is added to `CartContext` permanently flagged with warning indicators.
  - In the cart drawer and checkout review, conflicting items prominently display their inline `<AllergenWarningBadge />` without presenting a redundant blocking modal or extra consent checkbox at checkout (FR-022, SC-005).

---

### D-009: Diner Name Attributes & Full Name Synchronization

- **Decision**: Store `first_name` and `last_name` in separate columns in `users` table (Flyway `V5`), and automatically synchronize `full_name = first_name.trim() + " " + last_name.trim()` in `ProfileService`.
- **Rationale**:
  - Fulfills comensal profile requirements (FR-001, FR-002) with individual validation for first name and last name (alphabetic and spaces only).
  - Simultaneously maintains complete consistency with `full_name` introduced for staff in 004 and consumed by `AuthContext`, `WorkspaceLayout`, and `/api/v1/auth/me`.

---

### D-010: Public Catalog Browsing & Canonical Spanish Routing

- **Decision**: Allow anonymous catalog browsing by configuring `permitAll()` for catalog read endpoints in `SecurityConfig.java`, and establish canonical Spanish routes in `AppRoutes.tsx`.
- **Mechanism**:
  - In `SecurityConfig.java`:
    - Permit all for `GET /api/v1/establishments/{tenantId}/dishes`, `GET /api/v1/dietary-restrictions`, and `POST /api/v1/profile/email-change/confirm`.
    - Profile endpoints (`/api/v1/profile/**` except confirm) require authentication.
  - In `AppRoutes.tsx`:
    - `/catalogo/*` is publicly accessible. Unauthenticated guests receive the full unfiltered menu with a prominent call-to-action banner inviting them to log in or register to enable health filters (FR-026). Authenticated comensales receive automated dietary filtering.
    - `/perfil` is protected by `RoleRouteGuard` exclusively for `COMENSAL`.
    - `/verificar-email` handles verification link callbacks.
    - Canonical routes use Spanish URLs with redirection aliases: `/catalog/*` → `/catalogo`, `/profile/*` → `/perfil`, `/verify-email/*` → `/verificar-email`.

---

### D-011: Package & Repository Architecture

- **Decision**: Create `ProfileUserRepository` in `com.easyeat.profile.repository` extending `JpaRepository<User, UUID>`.
- **Rationale**:
  - Follows the bounded-context separation model established in 004 with `StaffUserRepository`.
  - Keeps `UserRepository` in `com.easyeat.auth` focused on credentials, while `ProfileUserRepository` provides specialized methods (e.g. `findByEmailVerificationTokenHash`, `existsByPendingEmailIgnoreCase`).

---

### D-012: Diner Navigation in WorkspaceLayout

- **Decision**: Include "Catálogo" (`/catalogo`) and "Mi perfil" (`/perfil`) links in `WorkspaceLayout` top navigation bar for `COMENSAL` users.
- **Mechanism**:
  - In `WorkspaceLayout.tsx`, when `user.role === 'COMENSAL'`, render subnavigation links pointing to `/catalogo` and `/perfil`, mirroring the subnav structure implemented for `CHEF` and `ADMINISTRADOR`.
