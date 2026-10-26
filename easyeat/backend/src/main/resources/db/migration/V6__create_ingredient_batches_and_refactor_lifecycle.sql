-- 1. Create ingredient_batches table
CREATE TABLE ingredient_batches (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    ingredient_id      UUID           NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    initial_quantity   NUMERIC(12, 4) NOT NULL,
    remaining_quantity NUMERIC(12, 4) NOT NULL,
    unit_cost          NUMERIC(12, 4) NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_batch_initial_qty          CHECK (initial_quantity > 0),
    CONSTRAINT chk_batch_remaining_qty        CHECK (remaining_quantity >= 0),
    CONSTRAINT chk_batch_remaining_le_initial CHECK (remaining_quantity <= initial_quantity),
    CONSTRAINT chk_batch_unit_cost            CHECK (unit_cost >= 0)
);

CREATE INDEX idx_ingredient_batches_active 
    ON ingredient_batches (tenant_id, ingredient_id, remaining_quantity, created_at ASC);

-- 2. Extend stock_audit_entries with batch and financial tracking
ALTER TABLE stock_audit_entries
    ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES ingredient_batches(id),
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS financial_loss NUMERIC(12, 4);

-- Update audit mode check constraint
ALTER TABLE stock_audit_entries DROP CONSTRAINT IF EXISTS chk_audit_mode;
ALTER TABLE stock_audit_entries ADD CONSTRAINT chk_audit_mode 
    CHECK (adjustment_mode IN ('ADD', 'SUBTRACT', 'ABSOLUTE', 'RELATIVE'));

-- 3. Refactor ingredients lifecycle constraint (remove ARCHIVED)
UPDATE ingredients SET status = 'DISCONTINUED' WHERE status = 'ARCHIVED';

ALTER TABLE ingredients DROP CONSTRAINT IF EXISTS chk_ingredient_status;
ALTER TABLE ingredients ADD CONSTRAINT chk_ingredient_status 
    CHECK (status IN ('ACTIVE', 'DISCONTINUED'));
