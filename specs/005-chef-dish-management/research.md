# Technical Research & Decisions: Chef Dish Technical File Management

**Branch**: `005-chef-dish-management` | **Date**: 25/10/2026 | **Specification**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/005-chef-dish-management/spec.md)

---

## 1. Seed data audit and dish schema evolution

### Context
In iteration 003 (`V5__extend_users_profile_and_dietary.sql`), an initial version of the `dishes` table was created:
- Columns: `id UUID`, `tenant_id UUID`, `name VARCHAR(150)`, `description TEXT`, `price NUMERIC(10, 2) NOT NULL`, `is_active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at`, `updated_at`.
- Association table: `dish_dietary_restrictions(dish_id, restriction_code)`.
- Repeatable development seed file: `R__seed_dev_dishes_and_dietary.sql`, which inserts 5 sample dishes for the synthetic administration tenant (`a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`):
  1. `Ensalada Mediterránea` (CELIAC, VEGETARIAN) - Price 4500.00
  2. `Pad Thai con Maní` (PEANUTS, SOY) - Price 6200.00
  3. `Hamburguesa Clásica con Queso` (WHEAT, DAIRY) - Price 7500.00
  4. `Bowl Vegano Proteico` (VEGAN, VEGETARIAN, CELIAC, SOY) - Price 5800.00
  5. `Salmón Grillado con Espárragos` (CELIAC, FISH) - Price 9500.00
- Associated test: `DevSeedDishesMigrationTest.java`, validating the existence of at least 5 dishes and their restriction mappings for that tenant.

### Identified conflict
Functional specification 005 introduces a lifecycle where:
1. The Chef creates technical file drafts without a price (`price` does not exist on the Chef's technical file and is only assigned by the Administrator in a subsequent stage). Keeping `price NOT NULL` in `dishes` would block draft creation.
2. Every dish must mandatory have a closed category (`category` in 'Entradas', 'Platos principales', 'Postres', 'Bebidas', 'Ensaladas').
3. The dish must have a formal status (`status` in 'DRAFT', 'PENDING_PRICE'), a boolean allergen confirmation flag (`allergens_confirmed`), creator reference (`created_by`), and a 100-character name limit with whitespace-trimmed, case-insensitive uniqueness (`LOWER(TRIM(name))`).
4. The 5 existing seed dishes lack `category`, `status`, `allergens_confirmed`, and `created_by`.

### Decision
- Modify the existing `dishes` schema via Flyway migration `V6__chef_dish_management.sql`:
  - `ALTER TABLE dishes ALTER COLUMN price DROP NOT NULL;`
  - `ALTER TABLE dishes ADD COLUMN IF NOT EXISTS category VARCHAR(50);`
  - `ALTER TABLE dishes ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'DRAFT';`
  - `ALTER TABLE dishes ADD COLUMN IF NOT EXISTS allergens_confirmed BOOLEAN NOT NULL DEFAULT FALSE;`
  - `ALTER TABLE dishes ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);`
  - `ALTER TABLE dishes ALTER COLUMN name TYPE VARCHAR(100);`
  - Add `CHECK` constraints:
    - `chk_dishes_category`: `category IN ('Entradas', 'Platos principales', 'Postres', 'Bebidas', 'Ensaladas')`
    - `chk_dishes_status`: `status IN ('DRAFT', 'PENDING_PRICE')`
  - Create case-insensitive, whitespace-trimmed unique index:
    `CREATE UNIQUE INDEX uq_dishes_tenant_lower_trimmed_name ON dishes (tenant_id, LOWER(TRIM(name)));`
- Adapt `R__seed_dev_dishes_and_dietary.sql`:
  - Assign valid categories: `Ensalada Mediterránea` -> 'Ensaladas'; `Pad Thai con Maní`, `Hamburguesa Clásica con Queso`, `Bowl Vegano Proteico`, `Salmón Grillado con Espárragos` -> 'Platos principales'.
  - Assign `status = 'PENDING_PRICE'`, `allergens_confirmed = true`, and `created_by = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'` (synthetic admin user).
  - Keep `dish_dietary_restrictions` mappings intact.

### Rationale
Preserves backward compatibility with existing migration and diner catalog tests, while aligning the relational definition with the Chef lifecycle without introducing inconsistencies or integrity violations.

---

## 2. Dish photo persistence and storage

### Context
The specification requires the Chef to be able to upload an optional photo per dish (JPEG, PNG, WebP formats; maximum size 5 MB), retrieve it, and delete it in Draft status.
The repository does not have cloud storage services (AWS S3, Cloudinary, or similar) or `.env` files configured for external infrastructure.

### Decision
- Persist multimedia binaries directly in PostgreSQL within a dedicated relational table `dish_photos` in third normal form:
  - `dish_id UUID PRIMARY KEY REFERENCES dishes(id) ON DELETE CASCADE`
  - `file_name VARCHAR(255) NOT NULL`
  - `content_type VARCHAR(50) NOT NULL` (with validation `CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))`)
  - `file_size BIGINT NOT NULL` (with validation `CHECK (file_size > 0 AND file_size <= 5242880)`)
  - `data BYTEA NOT NULL`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- Expose the following endpoints in `DishPhotoController`:
  - `POST /api/v1/dishes/{id}/photo`: Receives `multipart/form-data`, validates MIME type and maximum size, and inserts or updates the photo in `dish_photos`.
  - `GET /api/v1/dishes/{id}/photo`: Returns the binary byte array with the corresponding `Content-Type` header and cache headers (`Cache-Control: private, max-age=86400`).
  - `DELETE /api/v1/dishes/{id}/photo`: Deletes the record from `dish_photos` while the dish is in `DRAFT` status.
- In the dish DTO and read model, the `photo_url` field is exposed as `/api/v1/dishes/{id}/photo` when an associated record exists.

### Rationale
Complies with the project's architectural directive of maintaining strict relational persistence in PostgreSQL without external dependencies, isolates binaries in their own table so as not to degrade sequential scanning of the main `dishes` table, and allows the frontend to render images using standard URLs.

---

## 3. Recipe relational model (BOM) and third normal form (3NF)

### Context
The Chef defines the recipe by associating active catalog ingredients (`ingredients`, created in migration V4) with a strictly positive per-serving quantity.
In V4, the `ingredients` table already contains:
- `id UUID PRIMARY KEY`
- `unit_of_measure VARCHAR(20)` ('g', 'kg', 'ml', 'l', 'u')
- `status VARCHAR(50)` ('ACTIVE', 'DISCONTINUED', 'ARCHIVED')

### Decision
- Implement the association table `dish_recipe_ingredients` under strict 3NF:
  - `dish_id UUID NOT NULL REFERENCES dishes(id) ON DELETE CASCADE`
  - `ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT`
  - `quantity NUMERIC(12, 4) NOT NULL`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - `CONSTRAINT pk_dish_recipe_ingredients PRIMARY KEY (dish_id, ingredient_id)`
  - `CONSTRAINT chk_recipe_quantity_positive CHECK (quantity > 0)`
- Do not duplicate `unit_of_measure` in `dish_recipe_ingredients`. Measurement unit and active condition are resolved via `JOIN` with the `ingredients` table when querying the technical file.
- Precision rules in backend validation:
  - If `unit_of_measure` is 'u' (discrete), `quantity.stripTrailingZeros().scale() <= 0` (positive integers only).
  - If `unit_of_measure` is continuous ('g', 'kg', 'ml', 'l'), positive decimals with up to 4 decimal places are allowed.
- Reject duplicate ingredients: the primary key constraint `(dish_id, ingredient_id)` rejects duplicates at database level; the API offers an idempotent endpoint `PUT /api/v1/dishes/{id}/recipe` that updates or inserts the line items list.

### Rationale
Guarantees pure relational normalization without data redundancy. Eliminates update anomalies if ingredient descriptions change and ensures uniform unit validation matching specification 004.

---

## 4. Internal alerts and administrator notifications mechanism

### Context
Upon completing the transition to `PENDING_PRICE`, the system must generate an internal alert visible in the Administrator panel, identifying the dish name and submitting Chef, without introducing WebSockets or external message brokers.

### Decision
- Create the relational table `admin_notifications`:
  - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
  - `tenant_id UUID NOT NULL`
  - `type VARCHAR(50) NOT NULL` (e.g. `'DISH_SUBMITTED_FOR_REVIEW'`)
  - `title VARCHAR(200) NOT NULL`
  - `message TEXT NOT NULL`
  - `dish_id UUID NOT NULL REFERENCES dishes(id) ON DELETE CASCADE`
  - `chef_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT`
  - `is_read BOOLEAN NOT NULL DEFAULT FALSE`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
  - Index: `CREATE INDEX idx_admin_notifications_tenant ON admin_notifications (tenant_id, is_read, created_at DESC);`
- Expose endpoints under `/api/v1/admin/notifications`:
  - `GET /api/v1/admin/notifications`: Lists notifications for the authenticated tenant with optional filter `unreadOnly=true`.
  - `PATCH /api/v1/admin/notifications/{id}/read`: Marks a notification as read.

### Rationale
Decoupled, auditable, and low-operational-cost relational solution. The Administrator panel can query notifications upon loading or via manual refresh.

---

## 5. Submission gate ("Send for Review") and immutability

### Context
A dish can only transition to `PENDING_PRICE` if it meets four concurrent conditions:
1. Complete basic data (valid name and category belonging to the closed list).
2. Recipe with at least 1 ingredient with strictly positive quantity.
3. All ingredients in the recipe in `ACTIVE` status in the catalog at submission time.
4. Allergen section formally confirmed (`allergens_confirmed = true`).

### Decision
- Endpoint `POST /api/v1/dishes/{id}/submit-review`:
  - Executes a transaction locking the dish row (`PESSIMISTIC_WRITE`).
  - Verifies that the current status is `DRAFT`. If already `PENDING_PRICE`, rejects with `409 Conflict`.
  - Evaluates the full list of conditions. If any fails, accumulates all failures and returns `422 Unprocessable Entity` with a `ProblemDetail` detailing each unmet condition.
  - If all conditions are met:
    - Updates `dishes.status = 'PENDING_PRICE'`.
    - Updates `dishes.updated_at = NOW()`.
    - Inserts the record in `admin_notifications`.
- Strict immutability:
  - Modification endpoints (`PUT /dishes/{id}`, `DELETE /dishes/{id}`, `POST /dishes/{id}/photo`, `DELETE /dishes/{id}/photo`, `PUT /dishes/{id}/recipe`, `DELETE /dishes/{id}/recipe/ingredients/{ingredientId}`, `PUT /dishes/{id}/allergens`) verify that `status == DRAFT`. If the dish is in `PENDING_PRICE`, they return `409 Conflict` with explanatory detail.

### Rationale
Protects the integrity of the culinary state and prevents concurrent modifications while the Administrator reviews costs and prices.

---

## 6. Frontend component architecture

### Context
The Chef manages dishes from the kitchen workspace (`/cocina`). They must be able to explore existing dishes with pagination and filters, and compose the technical file through a structured form.

### Decision
- Editor pattern: Guided sequential flow (Stepper) with 4 mandatory steps:
  1. **Step 1 — Basic identity**: Name (max 100 characters, trimming), category (closed selector), description (optional, max 500 characters), and photo uploader with preview and delete action.
  2. **Step 2 — Recipe (BOM)**: Modal active ingredients browser (paginated to 20 items, alphabetical), inputs with unit-dependent numeric masks (decimal or integer), duplicate warning, and lines table with calculation and removal action.
  3. **Step 3 — Allergens and restrictions**: Differentiated groups (Dietary Lifestyles / Allergen Warnings) with checkboxes/toggles and explicit "Confirm allergen section" action.
  4. **Step 4 — Review and submission**: General summary of the technical file, dynamic completeness checklist, and "Send for Review" button enabled exclusively when satisfying all criteria.
- Chef dish management view (`/cocina/platos`):
  - Table with status badge, updated date, 20-item pagination, and status filter: "All", "Draft", and "Pending Price".
- Read-only mode:
  - When a dish is in `PENDING_PRICE` status, the Stepper displays all sections disabled with a locked notice.
- Administrator panel integration (`/admin`):
  - Incorporate a card or badge for pending approval alerts querying `/api/v1/admin/notifications`.
