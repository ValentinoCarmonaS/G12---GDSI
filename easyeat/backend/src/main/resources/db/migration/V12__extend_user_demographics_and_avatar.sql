-- ====================================================================
-- EasyEat V10: Diner Demographics & Profile Avatar Persistence
-- ====================================================================

-- 1. Tabla para fotos de perfil de comensales (almacenamiento BYTEA)
CREATE TABLE IF NOT EXISTS user_photos (
    user_id      UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    file_size    BIGINT       NOT NULL,
    data         BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_photos_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_user_photos_file_size
        CHECK (file_size > 0 AND file_size <= 5242880)
);

-- 2. Extensión de users con datos demográficos (edad, género, domicilio de contacto)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS age        INTEGER,
    ADD COLUMN IF NOT EXISTS gender     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS address    VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT chk_users_age_range
        CHECK (age IS NULL OR (age >= 14 AND age <= 120)),
    ADD CONSTRAINT chk_users_gender_valid
        CHECK (gender IS NULL OR gender IN (
            'FEMALE', 'MALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY',
            'Femenino', 'Masculino', 'No binario', 'Prefiero no responder'
        )),
    ADD CONSTRAINT chk_users_address_length
        CHECK (address IS NULL OR length(address) <= 255);
