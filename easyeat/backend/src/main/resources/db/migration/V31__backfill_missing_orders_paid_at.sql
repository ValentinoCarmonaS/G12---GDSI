-- Spec 028 US5 (FR-011, D-12): Backfill missing orders.paid_at on historically accredited orders
-- so Admin Analytics queries using idx_orders_analytics include all accredited sales.
UPDATE orders o
SET paid_at = COALESCE(
    (SELECT MAX(p.approved_at) FROM payments p WHERE p.order_id = o.id AND p.approved_at IS NOT NULL),
    o.completed_at,
    o.ready_at,
    o.updated_at,
    o.created_at
)
WHERE o.status IN ('PAID', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED')
  AND o.paid_at IS NULL;
