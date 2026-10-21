CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id     UUID,
    token_version INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email            UNIQUE (email),
    CONSTRAINT chk_users_role            CHECK (role IN ('COMENSAL','ENCARGADO','CHEF','ADMINISTRADOR')),
    CONSTRAINT chk_users_status          CHECK (status IN ('ACTIVE','SUSPENDED','UNVERIFIED')),
    CONSTRAINT chk_users_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT chk_users_role_tenant     CHECK (
        (role = 'COMENSAL' AND tenant_id IS NULL) OR
        (role IN ('ENCARGADO', 'CHEF', 'ADMINISTRADOR') AND tenant_id IS NOT NULL)
    )
);

CREATE INDEX idx_users_email ON users(email);
