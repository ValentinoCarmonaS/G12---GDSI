-- ====================================================================
-- EasyEat V29: Payment accreditation timestamp & analytics index (Spec 023)
-- ====================================================================

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN orders.paid_at IS
    'Instante exacto de acreditacion del pago de la orden (Spec 023).';

UPDATE orders o
SET paid_at = p.approved_at
FROM payments p
WHERE p.order_id = o.id
  AND o.paid_at IS NULL
  AND p.approved_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_analytics
    ON orders (establishment_id, paid_at)
    WHERE status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED');
