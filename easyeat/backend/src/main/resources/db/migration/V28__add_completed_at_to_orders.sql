-- ====================================================================
-- EasyEat V28: Delivery completion timestamp on orders (Spec 022)
-- Conforms to ENC-3.3.2 (RF-011, Invariante 8)
-- security_code already exists since V26 (spec 020); no index is added
-- because no query filters or sorts orders by completed_at.
-- ====================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN orders.completed_at IS
    'Instante inmutable de la entrega confirmada en mostrador (RF-011).';
