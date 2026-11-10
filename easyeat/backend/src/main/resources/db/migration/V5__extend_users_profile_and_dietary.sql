-- ====================================================================
-- EasyEat V5: Diner Profile Extension & Dietary Restrictions Taxonomy
-- ====================================================================

-- 1. Extend users table for personal data and email change workflow
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name                      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name                       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone                           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_email                   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verification_token_hash   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS email_token_expires_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_verification_last_sent_at  TIMESTAMPTZ;

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
