-- ====================================================================
-- EasyEat V7: Chef Dish Management - Schema Evolution and BOM Model
-- ====================================================================

-- 1. Modificar tabla dishes existente
ALTER TABLE dishes
    ALTER COLUMN price DROP NOT NULL,
    ALTER COLUMN name TYPE VARCHAR(100),
    ADD COLUMN IF NOT EXISTS category VARCHAR(50),
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS allergens_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);

-- Restricciones CHECK para dishes
ALTER TABLE dishes
    ADD CONSTRAINT chk_dishes_category
        CHECK (category IS NULL OR category IN ('Entradas', 'Platos principales', 'Postres', 'Bebidas', 'Ensaladas')),
    ADD CONSTRAINT chk_dishes_status
        CHECK (status IN ('DRAFT', 'PENDING_PRICE')),
    ADD CONSTRAINT chk_dishes_name_length
        CHECK (LENGTH(TRIM(name)) > 0 AND LENGTH(name) <= 100);

-- Indice unico insensible a mayusculas y recortado por tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_dishes_tenant_lower_trimmed_name
    ON dishes (tenant_id, LOWER(TRIM(name)));

-- Indice para listado y ordenamiento del Chef
CREATE INDEX IF NOT EXISTS idx_dishes_chef_list
    ON dishes (tenant_id, status, updated_at DESC);

-- 2. Tabla para almacenamiento de fotos en base de datos (PostgreSQL BYTEA)
CREATE TABLE IF NOT EXISTS dish_photos (
    dish_id      UUID         PRIMARY KEY REFERENCES dishes(id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    file_size    BIGINT       NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dish_photos_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_dish_photos_file_size
        CHECK (file_size > 0 AND file_size <= 5242880)
);

-- 3. Tabla relacional de receta / Bill of Materials (3NF estricta)
CREATE TABLE IF NOT EXISTS dish_recipe_ingredients (
    dish_id       UUID           NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    ingredient_id UUID           NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    quantity      NUMERIC(12, 4) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dish_recipe_ingredients PRIMARY KEY (dish_id, ingredient_id),
    CONSTRAINT chk_recipe_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_lookup
    ON dish_recipe_ingredients (ingredient_id);

-- 4. Tabla de notificaciones administrativas internas
CREATE TABLE IF NOT EXISTS admin_notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    message    TEXT         NOT NULL,
    dish_id    UUID         NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    chef_id    UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_notifications_tenant_read
    ON admin_notifications (tenant_id, is_read, created_at DESC);
