-- ====================================================================
-- EasyEat: Restaurant Operation Configuration (spec 010)
-- Owns: establishment_operating_schedules, establishment_slot_capacities,
--       establishment_payment_methods and the payment-method seed.
-- Owns NOTHING of any earlier spec's tables; only references establishments (V8).
-- Every table carries a version column for optimistic locking (EC-012).
-- Payment methods store a boolean visibility flag only: no credentials,
-- tokens or bank data (A-005).
-- ====================================================================

-- 1. Tabla de Horarios Comerciales Semanales
CREATE TABLE IF NOT EXISTS establishment_operating_schedules (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id UUID         NOT NULL REFERENCES establishments(id) ON DELETE CASCADE,
    day_of_week      VARCHAR(15)  NOT NULL,
    opening_time     TIME         NOT NULL DEFAULT '11:00:00',
    closing_time     TIME         NOT NULL DEFAULT '23:00:00',
    is_closed        BOOLEAN      NOT NULL DEFAULT FALSE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_schedule_establishment_day UNIQUE (establishment_id, day_of_week),
    CONSTRAINT chk_schedule_day CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT chk_schedule_times CHECK (is_closed = TRUE OR opening_time < closing_time)
);

CREATE INDEX IF NOT EXISTS idx_operating_schedules_lookup
    ON establishment_operating_schedules(establishment_id, day_of_week);

-- 2. Tabla de Capacidad Operativa por Franja Horaria (15 minutos)
CREATE TABLE IF NOT EXISTS establishment_slot_capacities (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id      UUID         NOT NULL REFERENCES establishments(id) ON DELETE CASCADE,
    slot_date             DATE         NOT NULL,
    start_time            TIME         NOT NULL,
    end_time              TIME         NOT NULL,
    max_capacity          INTEGER      NOT NULL DEFAULT 0,
    approved_orders_count INTEGER      NOT NULL DEFAULT 0,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_slot_establishment_date_time UNIQUE (establishment_id, slot_date, start_time),
    CONSTRAINT chk_slot_max_capacity CHECK (max_capacity >= 0),
    CONSTRAINT chk_slot_approved_count CHECK (approved_orders_count >= 0),
    CONSTRAINT chk_slot_interval CHECK (start_time < end_time)
);

CREATE INDEX IF NOT EXISTS idx_slot_capacities_lookup
    ON establishment_slot_capacities(establishment_id, slot_date, start_time);

-- 3. Tabla de Configuración de Métodos de Pago Online
CREATE TABLE IF NOT EXISTS establishment_payment_methods (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id UUID         NOT NULL REFERENCES establishments(id) ON DELETE CASCADE,
    payment_method   VARCHAR(50)  NOT NULL,
    is_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_establishment_payment_method UNIQUE (establishment_id, payment_method),
    CONSTRAINT chk_payment_method_enum CHECK (payment_method IN ('MERCADO_PAGO', 'STRIPE'))
);

CREATE INDEX IF NOT EXISTS idx_payment_methods_lookup
    ON establishment_payment_methods(establishment_id, is_enabled);

-- 4. Sembrado inicial para los establecimientos existentes en V8
INSERT INTO establishment_payment_methods (establishment_id, payment_method, is_enabled)
SELECT id, 'MERCADO_PAGO', TRUE FROM establishments
ON CONFLICT DO NOTHING;

INSERT INTO establishment_payment_methods (establishment_id, payment_method, is_enabled)
SELECT id, 'STRIPE', TRUE FROM establishments
ON CONFLICT DO NOTHING;
