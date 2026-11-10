-- ====================================================================
-- EasyEat V20: Payment Confirmation, Live Tracking & Multi-Tenant Branding
-- Governs: restaurant_brandings, establishment_kds_settings, order_receipts
-- and extends orders.status with READY_FOR_PICKUP.
-- ====================================================================

-- 1. Ampliar chk_order_status en la tabla orders para incorporar READY_FOR_PICKUP y COMPLETED
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status CHECK (
        status IN (
            'PENDING_PAYMENT',
            'PENDING_ACCREDITATION',
            'IN_PROGRESS',
            'READY_FOR_PICKUP',
            'COMPLETED',
            'CANCELLED',
            'EXPIRED'
        )
    );

-- 2. Crear tabla restaurant_brandings
CREATE TABLE IF NOT EXISTS restaurant_brandings (
    establishment_id         UUID         PRIMARY KEY REFERENCES establishments(id) ON DELETE CASCADE,
    fantasy_name             VARCHAR(150) NULL,
    description              TEXT         NULL,
    logo_url                 VARCHAR(500) NULL,
    primary_color            VARCHAR(7)   NOT NULL DEFAULT '#10B981',
    secondary_color          VARCHAR(7)   NOT NULL DEFAULT '#065F46',
    accent_color             VARCHAR(7)   NOT NULL DEFAULT '#F59E0B',
    primary_contrast_color   VARCHAR(7)   NOT NULL DEFAULT '#FFFFFF',
    secondary_contrast_color VARCHAR(7)   NOT NULL DEFAULT '#FFFFFF',
    accent_contrast_color    VARCHAR(7)   NOT NULL DEFAULT '#000000',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_primary_color_hex CHECK (primary_color ~* '^#[0-9A-F]{6}$'),
    CONSTRAINT chk_secondary_color_hex CHECK (secondary_color ~* '^#[0-9A-F]{6}$'),
    CONSTRAINT chk_accent_color_hex CHECK (accent_color ~* '^#[0-9A-F]{6}$'),
    CONSTRAINT chk_primary_contrast CHECK (primary_contrast_color IN ('#000000', '#FFFFFF')),
    CONSTRAINT chk_secondary_contrast CHECK (secondary_contrast_color IN ('#000000', '#FFFFFF')),
    CONSTRAINT chk_accent_contrast CHECK (accent_contrast_color IN ('#000000', '#FFFFFF'))
);

CREATE INDEX IF NOT EXISTS idx_brandings_establishment
    ON restaurant_brandings(establishment_id);

-- 3. Crear tabla establishment_kds_settings para demoras globales
CREATE TABLE IF NOT EXISTS establishment_kds_settings (
    establishment_id     UUID        PRIMARY KEY REFERENCES establishments(id) ON DELETE CASCADE,
    global_delay_minutes INTEGER     NOT NULL DEFAULT 0,
    updated_by_user_id   UUID        NULL REFERENCES users(id) ON DELETE SET NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kds_delay_positive CHECK (global_delay_minutes >= 0)
);

-- 4. Crear tabla order_receipts para despacho garantizado de correo
CREATE TABLE IF NOT EXISTS order_receipts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID         NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    recipient_email  VARCHAR(255) NOT NULL,
    tracking_url     VARCHAR(500) NOT NULL,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    error_message    TEXT         NULL,
    sent_at          TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_receipt_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_order_receipts_order
    ON order_receipts(order_id);

CREATE INDEX IF NOT EXISTS idx_order_receipts_status
    ON order_receipts(status);

-- 5. Sembrado inicial de KDS settings para establecimientos existentes
INSERT INTO establishment_kds_settings (establishment_id, global_delay_minutes)
SELECT id, 0 FROM establishments
ON CONFLICT (establishment_id) DO NOTHING;

-- 6. Garantizar existencia de kds_tickets para tracking y saturación si corre previo a V19
CREATE TABLE IF NOT EXISTS kds_tickets (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID           NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    establishment_id  UUID           NOT NULL REFERENCES establishments(id) ON DELETE RESTRICT,
    ticket_number     INTEGER        NOT NULL,
    status            VARCHAR(30)    NOT NULL DEFAULT 'QUEUED',
    fulfillment_mode  VARCHAR(30)    NOT NULL,
    pickup_date       DATE           NOT NULL,
    time_slot_start   TIME           NOT NULL,
    time_slot_end     TIME           NOT NULL,
    queued_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    started_at        TIMESTAMPTZ    NULL,
    ready_at          TIMESTAMPTZ    NULL,
    completed_at      TIMESTAMPTZ    NULL,
    CONSTRAINT uq_kds_tickets_order_id UNIQUE (order_id),
    CONSTRAINT chk_kds_ticket_status CHECK (status IN ('QUEUED', 'IN_PREPARATION', 'READY_FOR_PICKUP', 'COMPLETED')),
    CONSTRAINT chk_kds_fulfillment_mode CHECK (fulfillment_mode IN ('IMMEDIATE', 'SCHEDULED')),
    CONSTRAINT chk_kds_ticket_number CHECK (ticket_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_kds_tickets_establishment_status 
    ON kds_tickets(establishment_id, status, pickup_date, time_slot_start);
