-- ====================================================================
-- EasyEat V21: Binary Restaurant Logo Persistence & Order Status Check Extension
-- Governs: restaurant_logos (BYTEA storage matching user_photos pattern)
-- and broadens chk_order_status to include all OrderStatus enum states.
-- ====================================================================

CREATE TABLE IF NOT EXISTS restaurant_logos (
    establishment_id UUID         PRIMARY KEY REFERENCES establishments(id) ON DELETE CASCADE,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(50)  NOT NULL,
    file_size        BIGINT       NOT NULL,
    data             BYTEA        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_restaurant_logos_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/jpg', 'image/svg+xml')),
    CONSTRAINT chk_restaurant_logos_file_size
        CHECK (file_size > 0 AND file_size <= 5242880)
);

CREATE INDEX IF NOT EXISTS idx_restaurant_logos_establishment
    ON restaurant_logos(establishment_id);

-- Broaden chk_order_status to guarantee compatibility with all OrderStatus values
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status CHECK (
        status IN (
            'PENDING_PAYMENT',
            'PENDING_ACCREDITATION',
            'IN_PROGRESS',
            'READY_FOR_PICKUP',
            'COMPLETED',
            'PAID',
            'CONFIRMED',
            'CANCELLED',
            'EXPIRED'
        )
    );
