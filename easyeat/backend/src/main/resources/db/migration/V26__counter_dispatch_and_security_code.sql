-- ====================================================================
-- EasyEat V26: Counter Dispatch, Coincident Timestamps & Security Code
-- ====================================================================

-- 1. Agregar columna ready_at a la tabla orders para coincidencia temporal estricta con kds_tickets
ALTER TABLE orders 
    ADD COLUMN IF NOT EXISTS ready_at TIMESTAMPTZ NULL;

-- 2. Agregar columna security_code (4 dígitos numéricos) a la tabla orders
ALTER TABLE orders 
    ADD COLUMN IF NOT EXISTS security_code VARCHAR(4) NULL;

-- 3. Constraint de formato para security_code (exactamente 4 dígitos entre 1000 y 9999)
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_security_code;
ALTER TABLE orders 
    ADD CONSTRAINT chk_orders_security_code 
    CHECK (security_code IS NULL OR security_code ~ '^[1-9][0-9]{3}$');

-- 4. Backfill para órdenes existentes sin código de seguridad
UPDATE orders 
SET security_code = LPAD(FLOOR(1000 + RANDOM() * 9000)::TEXT, 4, '0')
WHERE security_code IS NULL;

-- 5. Sincronizar ready_at para órdenes que ya figuren en READY_FOR_PICKUP a partir de kds_tickets
UPDATE orders o
SET ready_at = k.ready_at
FROM kds_tickets k
WHERE k.order_id = o.id
  AND o.status = 'READY_FOR_PICKUP'
  AND o.ready_at IS NULL
  AND k.ready_at IS NOT NULL;

-- 6. Crear índice compuesto especializado para la cola de mostrador (filtrado por establecimiento, jornada y estado; orden determinista por ready_at y ticket_number)
CREATE INDEX IF NOT EXISTS idx_kds_tickets_counter_ready 
    ON kds_tickets (establishment_id, status, pickup_date, ready_at ASC, ticket_number ASC);
