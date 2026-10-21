-- Create ingredients master table
CREATE TABLE ingredients (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    name               VARCHAR(255)   NOT NULL,
    category           VARCHAR(50)    NOT NULL,
    unit_of_measure    VARCHAR(20)    NOT NULL,
    unit_purchase_cost NUMERIC(12, 4) NOT NULL,
    status             VARCHAR(50)    NOT NULL DEFAULT 'ACTIVE',
    current_stock      NUMERIC(12, 4) NOT NULL DEFAULT 0.0000,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ingredient_cost_positive      CHECK (unit_purchase_cost >= 0),
    CONSTRAINT chk_ingredient_stock_non_negative CHECK (current_stock >= 0),
    CONSTRAINT chk_ingredient_category           CHECK (category IN ('Produce', 'Meat', 'Dairy', 'Dry Goods', 'Spices')),
    CONSTRAINT chk_ingredient_unit               CHECK (unit_of_measure IN ('g', 'kg', 'ml', 'l', 'u')),
    CONSTRAINT chk_ingredient_status             CHECK (status IN ('ACTIVE', 'DISCONTINUED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_ingredient_tenant_lower_name ON ingredients (tenant_id, LOWER(name));
CREATE INDEX idx_ingredient_chef_catalog ON ingredients (tenant_id, status, name);

-- Create immutable stock adjustments audit table
CREATE TABLE stock_audit_entries (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    ingredient_id   UUID           NOT NULL REFERENCES ingredients(id),
    user_id         UUID           NOT NULL REFERENCES users(id),
    adjustment_mode VARCHAR(20)    NOT NULL,
    previous_stock  NUMERIC(12, 4) NOT NULL,
    new_stock       NUMERIC(12, 4) NOT NULL,
    net_delta       NUMERIC(12, 4) NOT NULL,
    justification   TEXT           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_mode                  CHECK (adjustment_mode IN ('ABSOLUTE', 'RELATIVE')),
    CONSTRAINT chk_audit_previous_stock       CHECK (previous_stock >= 0),
    CONSTRAINT chk_audit_new_stock            CHECK (new_stock >= 0),
    CONSTRAINT chk_audit_nonzero_delta        CHECK (net_delta <> 0),
    CONSTRAINT chk_audit_justification_length CHECK (LENGTH(TRIM(justification)) > 0)
);

CREATE INDEX idx_stock_audit_ingredient_history ON stock_audit_entries (tenant_id, ingredient_id, created_at DESC);

-- Strict immutability rules: prevent UPDATE and DELETE on stock_audit_entries
CREATE OR REPLACE RULE no_update_stock_audit AS ON UPDATE TO stock_audit_entries DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_stock_audit AS ON DELETE TO stock_audit_entries DO INSTEAD NOTHING;
