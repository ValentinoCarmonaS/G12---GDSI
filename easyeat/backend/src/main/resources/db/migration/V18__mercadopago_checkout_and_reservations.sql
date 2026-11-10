-- ====================================================================
-- EasyEat V18: Mercado Pago Checkout Integration and Atomic Reservations
-- ====================================================================

-- 1. Tabla de reservas temporales conjuntas (INV-01, INV-04, INV-05)
CREATE TABLE IF NOT EXISTS temporary_reservations (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    idempotency_key    VARCHAR(100) NOT NULL,
    status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    release_reason     VARCHAR(50)  NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    confirmed_at       TIMESTAMPTZ  NULL,
    released_at        TIMESTAMPTZ  NULL,
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT chk_reservation_release_reason CHECK (release_reason IS NULL OR release_reason IN ('REJECTED', 'CANCELLED_BY_USER', 'EXPIRED', 'GATEWAY_ERROR')),
    CONSTRAINT uq_reservation_idempotency_key UNIQUE (idempotency_key)
);

-- Garantiza que una orden sólo tenga una reserva activa simultánea (INV-05)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_reservation_per_order
    ON temporary_reservations (order_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_reservations_sweep
    ON temporary_reservations (status, expires_at);

-- 2. Tabla de transacciones de pago delegadas a Mercado Pago
CREATE TABLE IF NOT EXISTS payments (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    reservation_id        UUID           NOT NULL REFERENCES temporary_reservations(id) ON DELETE CASCADE,
    payment_method        VARCHAR(30)    NOT NULL DEFAULT 'MERCADO_PAGO',
    gateway_preference_id VARCHAR(100)   NULL,
    gateway_payment_id    VARCHAR(100)   NULL,
    external_reference    VARCHAR(100)   NOT NULL,
    status                VARCHAR(30)    NOT NULL DEFAULT 'IN_PROCESS',
    status_detail         VARCHAR(100)   NULL,
    transaction_amount    DECIMAL(12, 2) NOT NULL,
    init_point            VARCHAR(500)   NULL,
    sandbox_init_point    VARCHAR(500)   NULL,
    idempotency_key       VARCHAR(100)   NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    approved_at           TIMESTAMPTZ    NULL,
    rejected_at           TIMESTAMPTZ    NULL,
    CONSTRAINT chk_payment_status CHECK (status IN ('IN_PROCESS', 'APPROVED', 'REJECTED', 'CANCELLED', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_payment_amount CHECK (transaction_amount > 0),
    CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payments_order_status
    ON payments (order_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_gateway_id
    ON payments (gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;

