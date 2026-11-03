-- ====================================================================
-- EasyEat V13: Diner Order Fulfillment Mode & Time-Slot Reservation Engine
-- ====================================================================

-- 1. Extender tabla establishments con parámetros operativos
ALTER TABLE establishments
    ADD COLUMN IF NOT EXISTS lead_time_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS max_capacity_per_slot INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN IF NOT EXISTS operating_days VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY';

ALTER TABLE establishments
    ADD CONSTRAINT chk_establishment_lead_time CHECK (lead_time_minutes > 0),
    ADD CONSTRAINT chk_establishment_slot_capacity CHECK (max_capacity_per_slot > 0);

-- 2. Crear tabla orders
CREATE TABLE IF NOT EXISTS orders (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id  UUID           NOT NULL REFERENCES establishments(id) ON DELETE RESTRICT,
    user_id           UUID           NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    fulfillment_mode  VARCHAR(30)    NOT NULL,
    pickup_date       DATE           NOT NULL,
    time_slot_start   TIME           NOT NULL,
    time_slot_end     TIME           NOT NULL,
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING_PAYMENT',
    total_amount      DECIMAL(12, 2) NOT NULL,
    expires_at        TIMESTAMPTZ    NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_fulfillment_mode CHECK (fulfillment_mode IN ('IMMEDIATE', 'SCHEDULED')),
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_order_total_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_order_slot_times CHECK (time_slot_start < time_slot_end)
);

CREATE INDEX IF NOT EXISTS idx_orders_slot_capacity
    ON orders(establishment_id, pickup_date, time_slot_start, status, expires_at);

CREATE INDEX IF NOT EXISTS idx_orders_user_pending
    ON orders(user_id, status);

CREATE INDEX IF NOT EXISTS idx_orders_expires_sweep
    ON orders(status, expires_at);

-- 3. Crear tabla order_items
CREATE TABLE IF NOT EXISTS order_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    dish_id     UUID           NOT NULL REFERENCES dishes(id) ON DELETE RESTRICT,
    dish_name   VARCHAR(150)   NOT NULL,
    unit_price  DECIMAL(10, 2) NOT NULL,
    quantity    INTEGER        NOT NULL,
    subtotal    DECIMAL(12, 2) NOT NULL,
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_unit_price CHECK (unit_price > 0),
    CONSTRAINT chk_order_item_subtotal CHECK (subtotal > 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id
    ON order_items(order_id);
