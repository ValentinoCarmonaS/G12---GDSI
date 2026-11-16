-- ====================================================================
-- EasyEat V27: Granular Kitchen Delay (+5) on KDS Tickets & Security Code (Spec 021)
-- ====================================================================

ALTER TABLE kds_tickets
    ADD COLUMN IF NOT EXISTS specific_delay_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE kds_tickets
    DROP CONSTRAINT IF EXISTS chk_kds_specific_delay_minutes;

ALTER TABLE kds_tickets
    ADD CONSTRAINT chk_kds_specific_delay_minutes
    CHECK (specific_delay_minutes >= 0);

COMMENT ON COLUMN kds_tickets.specific_delay_minutes IS 
    'Demora acumulada en minutos (+5 por pulsacion) agregada por el personal de cocina.';

-- Garantizar la columna de codigo de seguridad numerico de 4 digitos en orders para retiro
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS security_code VARCHAR(4) NULL;

COMMENT ON COLUMN orders.security_code IS 
    'Codigo numerico de seguridad de 4 digitos para retiro verbal en mostrador (RF-021).';
