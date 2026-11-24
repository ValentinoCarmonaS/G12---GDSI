-- Spec 028 (FR-009, D-10): Composite performance indexes for catalog, batch dish reservations,
-- and active temporary reservations on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_dishes_public_catalog
    ON dishes (tenant_id, category)
    WHERE status = 'PUBLISHED' AND is_active = TRUE AND paused_by_chef = FALSE;

CREATE INDEX IF NOT EXISTS idx_order_items_dish_order
    ON order_items (dish_id, order_id);

CREATE INDEX IF NOT EXISTS idx_temp_reservations_active_expires_order
    ON temporary_reservations (expires_at, order_id)
    WHERE status = 'ACTIVE';
