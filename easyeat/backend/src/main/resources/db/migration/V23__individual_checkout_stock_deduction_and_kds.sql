-- ====================================================================
-- EasyEat V23: Individual Checkout, Stock Deduction & KDS Queue Dispatch
-- ====================================================================

-- 1. Ampliar el check constraint de status en la tabla orders para incluir PENDING_ACCREDITATION e IN_PROGRESS
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;
ALTER TABLE orders ADD CONSTRAINT chk_order_status 
    CHECK (status IN (
        'PENDING_PAYMENT', 
        'PENDING_ACCREDITATION', 
        'IN_PROGRESS', 
        'READY_FOR_PICKUP',
        'COMPLETED',
        'CONFIRMED', 
        'PAID', 
        'CANCELLED', 
        'EXPIRED'
    ));

-- 2. Crear tabla kds_tickets para la cola del Kitchen Display System
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

CREATE INDEX IF NOT EXISTS idx_kds_tickets_order_id 
    ON kds_tickets(order_id);

-- 3. Crear secuencia o soporte para ticket_number por día/restaurante
CREATE SEQUENCE IF NOT EXISTS kds_ticket_daily_seq START 1;
