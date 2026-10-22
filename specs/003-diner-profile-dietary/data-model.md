# Data Model: Diner Profile Management, Dietary Restrictions & Automated Catalog Filtering

**Feature**: `003-diner-profile-dietary` | **Date**: 2026-10-21 | **Phase**: 1

---

## Entity Catalog

### 1. User (Extension)

Represents an authenticated user in EasyEat. In this iteration, the table is extended with personal contact information, pending email verification state, and resend rate-limit tracking. Note that `full_name` already exists on the table from migration `V3__extend_users_staff.sql` (iteration 004); `first_name` and `last_name` operate independently for comensales.

**Table**: `users` (extended)

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL | Inherited from `V1__create_users.sql` |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Active login and notification email |
| `first_name` | `VARCHAR(100)` | NULLABLE | Letters and spaces only (via CHECK constraint) |
| `last_name` | `VARCHAR(100)` | NULLABLE | Letters and spaces only (via CHECK constraint) |
| `phone` | `VARCHAR(20)` | NULLABLE | Standard phone number format (8–15 digits) |
| `pending_email` | `VARCHAR(255)` | NULLABLE | Proposed new email address pending verification |
| `email_verification_token_hash` | `VARCHAR(64)` | NULLABLE, UNIQUE | SHA-256 hash of raw verification token |
| `email_token_expires_at` | `TIMESTAMPTZ` | NULLABLE | Expiration deadline (created_at + 24 hours) |
| `email_verification_last_sent_at` | `TIMESTAMPTZ` | NULLABLE | Timestamp of last email dispatch (for 60s cooldown) |
| `token_version` | `INTEGER` | NOT NULL, DEFAULT 0 | Incremented on email confirmation to revoke active JWTs (001/002) |

**CHECK Constraints**:
- `chk_users_first_name_format`: `CHECK (first_name IS NULL OR first_name ~ '^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$')`
- `chk_users_last_name_format`: `CHECK (last_name IS NULL OR last_name ~ '^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$')`
- `chk_users_phone_format`: `CHECK (phone IS NULL OR phone ~ '^\+?[0-9 -]{8,20}$')`
- `ck_users_pending_email_lowercase`: `CHECK (pending_email IS NULL OR pending_email = lower(pending_email))`

**Indexes**:
- `idx_users_pending_email`: ON `users(pending_email)` (WHERE pending_email IS NOT NULL) for global uniqueness checks
- `idx_users_email_token_hash`: ON `users(email_verification_token_hash)` (WHERE email_verification_token_hash IS NOT NULL) for O(log n) confirmation lookup

---

### 2. DietaryRestriction

Standardized, predefined platform reference taxonomy representing dietary lifestyles and common food allergens.

**Table**: `dietary_restrictions`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `code` | `VARCHAR(50)` | PK, NOT NULL | Standardized uppercase identifier (e.g. `CELIAC`, `PEANUTS`) |
| `name` | `VARCHAR(100)` | NOT NULL | Human-readable localized label (e.g. `Sin TACC / Celíaco`) |
| `category` | `VARCHAR(50)` | NOT NULL, CHECK | Category enum: `DIETARY_LIFESTYLE` or `COMMON_ALLERGEN` |
| `description` | `VARCHAR(255)` | NULLABLE | Explanation of exclusion criteria |

**Seed Data**:

```sql
INSERT INTO dietary_restrictions (code, name, category, description) VALUES
-- Dietary Lifestyles
('CELIAC', 'Sin TACC / Celíaco', 'DIETARY_LIFESTYLE', 'Excluye trigo, avena, cebada, centeno y derivados.'),
('VEGAN', 'Vegano', 'DIETARY_LIFESTYLE', 'Excluye carne, lácteos, huevos, miel y cualquier producto de origen animal.'),
('VEGETARIAN', 'Vegetariano', 'DIETARY_LIFESTYLE', 'Excluye carnes y pescados, admite lácteos y huevos.'),
-- Common Allergens
('DAIRY', 'Lácteos / Lactosa', 'COMMON_ALLERGEN', 'Presencia de leche de vaca, caseína o derivados lácteos.'),
('PEANUTS', 'Maní', 'COMMON_ALLERGEN', 'Presencia de maní o derivados.'),
('TREE_NUTS', 'Frutos secos', 'COMMON_ALLERGEN', 'Presencia de nueces, almendras, avellanas o castañas.'),
('EGGS', 'Huevos', 'COMMON_ALLERGEN', 'Presencia de huevo o derivados en preparación o rebozado.'),
('FISH', 'Pescado', 'COMMON_ALLERGEN', 'Presencia de pescados de cualquier variedad.'),
('SHELLFISH', 'Mariscos / Crustáceos', 'COMMON_ALLERGEN', 'Presencia de camarones, langostinos, calamares o moluscos.'),
('SOY', 'Soja', 'COMMON_ALLERGEN', 'Presencia de proteína de soja, lecitina o derivados.'),
('WHEAT', 'Trigo / Gluten', 'COMMON_ALLERGEN', 'Presencia de harina de trigo, salvado o gluten.');
```

---

### 3. UserDietaryRestriction

Join table associating a diner with their configured active dietary preferences and allergens.

**Table**: `user_dietary_restrictions`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `user_id` | `UUID` | PK (part 1), NOT NULL, FK → `users(id)` ON DELETE CASCADE | Target diner |
| `restriction_code` | `VARCHAR(50)` | PK (part 2), NOT NULL, FK → `dietary_restrictions(code)` ON DELETE RESTRICT | Assigned restriction |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Timestamp when preference was marked |

**Indexes**:
- Implicit composite PK index on `(user_id, restriction_code)`
- `idx_udr_restriction_code`: ON `user_dietary_restrictions(restriction_code)` for analytics and reverse lookups

---

### 4. Dish & DishDietaryRestriction (Catalog Baseline)

Represents dishes offered by a restaurant establishment and their recipe dietary compliance/allergen mapping.

**Table**: `dishes`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, NOT NULL, DEFAULT gen_random_uuid() | Dish unique identifier |
| `tenant_id` | `UUID` | NOT NULL | Establishment identifier (multi-tenant scope) |
| `name` | `VARCHAR(150)` | NOT NULL | Name of the dish |
| `description` | `TEXT` | NULLABLE | Detailed description and ingredients list |
| `price` | `NUMERIC(10,2)` | NOT NULL, CHECK (price >= 0) | Selling price |
| `is_active` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | Visibility flag controlled by restaurant |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Update timestamp |

**Table**: `dish_dietary_restrictions`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `dish_id` | `UUID` | PK (part 1), NOT NULL, FK → `dishes(id)` ON DELETE CASCADE | Associated dish |
| `restriction_code` | `VARCHAR(50)` | PK (part 2), NOT NULL, FK → `dietary_restrictions(code)` ON DELETE RESTRICT | Lifestyle tag or Allergen present |

> **Compliance Semantics**:
> - If `restriction_code` has `category = 'DIETARY_LIFESTYLE'`: it indicates that the dish is **COMPLIANT** with this lifestyle (e.g. dish has `VEGAN` tag → it IS suitable for vegans).
> - If `restriction_code` has `category = 'COMMON_ALLERGEN'`: it indicates that the dish **CONTAINS** this allergen (e.g. dish has `PEANUTS` tag → it contains peanuts, thus conflicting with peanut-allergic diners).

---

## Flyway Migration: `V5__extend_users_profile_and_dietary.sql`

```sql
-- ====================================================================
-- EasyEat V5: Diner Profile Extension & Dietary Restrictions Taxonomy
-- ====================================================================

-- 1. Extend users table for personal data and email change workflow
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name                     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name                      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone                          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_email                  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verification_token_hash  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_token_expires_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_verification_last_sent_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_first_name_format
        CHECK (first_name IS NULL OR first_name ~ '^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$'),
    ADD CONSTRAINT chk_users_last_name_format
        CHECK (last_name IS NULL OR last_name ~ '^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$'),
    ADD CONSTRAINT chk_users_phone_format
        CHECK (phone IS NULL OR phone ~ '^\+?[0-9 -]{8,20}$'),
    ADD CONSTRAINT ck_users_pending_email_lowercase
        CHECK (pending_email IS NULL OR pending_email = lower(pending_email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_token_hash
    ON users(email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_pending_email
    ON users(pending_email)
    WHERE pending_email IS NOT NULL;

-- 2. Dietary Restrictions Taxonomy
CREATE TABLE IF NOT EXISTS dietary_restrictions (
    code        VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT chk_dietary_category CHECK (category IN ('DIETARY_LIFESTYLE', 'COMMON_ALLERGEN'))
);

-- Seed standardized taxonomy
INSERT INTO dietary_restrictions (code, name, category, description) VALUES
    ('CELIAC', 'Sin TACC / Celíaco', 'DIETARY_LIFESTYLE', 'Excluye trigo, avena, cebada, centeno y derivados.'),
    ('VEGAN', 'Vegano', 'DIETARY_LIFESTYLE', 'Excluye carne, lácteos, huevos, miel y cualquier producto animal.'),
    ('VEGETARIAN', 'Vegetariano', 'DIETARY_LIFESTYLE', 'Excluye carnes y pescados, admite lácteos y huevos.'),
    ('DAIRY', 'Lácteos / Lactosa', 'COMMON_ALLERGEN', 'Presencia de leche de vaca, caseína o derivados lácteos.'),
    ('PEANUTS', 'Maní', 'COMMON_ALLERGEN', 'Presencia de maní o derivados.'),
    ('TREE_NUTS', 'Frutos secos', 'COMMON_ALLERGEN', 'Presencia de nueces, almendras, avellanas o castañas.'),
    ('EGGS', 'Huevos', 'COMMON_ALLERGEN', 'Presencia de huevo o derivados en preparación o rebozado.'),
    ('FISH', 'Pescado', 'COMMON_ALLERGEN', 'Presencia de pescados de cualquier variedad.'),
    ('SHELLFISH', 'Mariscos / Crustáceos', 'COMMON_ALLERGEN', 'Presencia de camarones, langostinos, calamares o moluscos.'),
    ('SOY', 'Soja', 'COMMON_ALLERGEN', 'Presencia de proteína de soja, lecitina o derivados.'),
    ('WHEAT', 'Trigo / Gluten', 'COMMON_ALLERGEN', 'Presencia de harina de trigo, salvado o gluten.')
ON CONFLICT (code) DO NOTHING;

-- 3. User Dietary Preferences Join Table
CREATE TABLE IF NOT EXISTS user_dietary_restrictions (
    user_id          UUID        NOT NULL,
    restriction_code VARCHAR(50) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_dietary_restrictions PRIMARY KEY (user_id, restriction_code),
    CONSTRAINT fk_udr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_udr_restriction FOREIGN KEY (restriction_code) REFERENCES dietary_restrictions(code) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_udr_restriction ON user_dietary_restrictions(restriction_code);

-- 4. Dishes and Catalog Baseline Schema
CREATE TABLE IF NOT EXISTS dishes (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    name        VARCHAR(150)   NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dishes_price CHECK (price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_dishes_tenant ON dishes(tenant_id, is_active);

CREATE TABLE IF NOT EXISTS dish_dietary_restrictions (
    dish_id          UUID        NOT NULL,
    restriction_code VARCHAR(50) NOT NULL,
    CONSTRAINT pk_dish_dietary_restrictions PRIMARY KEY (dish_id, restriction_code),
    CONSTRAINT fk_ddr_dish FOREIGN KEY (dish_id) REFERENCES dishes(id) ON DELETE CASCADE,
    CONSTRAINT fk_ddr_restriction FOREIGN KEY (restriction_code) REFERENCES dietary_restrictions(code) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_ddr_dish ON dish_dietary_restrictions(dish_id);
```

---

## Requirement Traceability Matrix

| Spec Requirement | Target Entity / Table | Column / Constraint | Enforced At |
|---|---|---|---|
| FR-001 (Edit Personal Data) | `users` | `first_name`, `last_name`, `phone`, `email` | Controller (`@Valid`), DB |
| FR-002 (Alphabetic Names) | `users` | `chk_users_first_name_format`, `chk_users_last_name_format` | DTO `@Pattern`, DB CHECK |
| FR-003 (Optional Phone Syntax) | `users` | `chk_users_phone_format` | DTO `@Pattern`, DB CHECK |
| FR-004 (Global Email Uniqueness) | `users` | `uq_users_email`, `idx_users_pending_email` | Service, DB UNIQUE |
| FR-005 (Async 24h Token) | `users` | `email_verification_token_hash`, `email_token_expires_at` | Service, DB |
| FR-006 (Old Email Active) | `users` | `email` preserved while `pending_email` is set | Service logic |
| FR-007 (Pending Status in Profile) | `users` | `pending_email` populated | DTO `ProfileResponse` |
| FR-008 (Revocation on New Email) | `users` | Overwriting `pending_email` + new hash | Service `@Transactional` |
| FR-009 (Session Revocation) | `users` | `token_version = token_version + 1` | Service `@Transactional` (001/002) |
| FR-011 (Immutable Past Orders) | N/A (Orders) | Orders store denormalized customer snapshot | Order Service (out of scope) |
| FR-012 (Standard Taxonomy) | `dietary_restrictions` | `code`, `category` | Flyway Seed, Service |
| FR-013 (Multi-restriction Selection) | `user_dietary_restrictions` | Composite PK `(user_id, restriction_code)` | Service `@Transactional` |
| FR-014 (Explicit Error Persistence) | `user_dietary_restrictions` | Transaction rollbacks with RFC 9457 error | Service `@Transactional` |
| FR-015 (Live Cart Re-evaluation) | N/A (In-memory) | `POST /api/v1/cart/evaluate-compliance` | `CatalogComplianceService` |
| FR-017 / FR-018 (AND Logic Filtering) | `dish_dietary_restrictions` | In-memory evaluation of tags | `CatalogComplianceService` |
| FR-019 / FR-020 (Catalog Master Toggle) | N/A (Frontend) | React Context / `useCatalog` hook | Frontend component |
| FR-022 (Checkout Informed Risk) | Checkout DTO | `acceptedDietaryRisks: boolean` | Checkout Controller |
| FR-023 (Session-scoped Toggle Reset) | N/A (Frontend) | Component unmount / route listener | React lifecycle |
| FR-026 (Unauthenticated Guest Browsing) | `dishes` | Anonymous catalog retrieval with guest banner | Controller / SecurityConfig / Frontend |
| FR-027 (Dual Client-Server Validation) | All tables | Spring Bean Validation + PostgreSQL constraints | Layered Architecture |
