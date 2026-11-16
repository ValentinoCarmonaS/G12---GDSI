-- V25__idx_kds_tickets_active_queue.sql
-- Optimiza la consulta y ordenamiento determinista de la cola de preparación activa (KDS)
-- Cobertura de campos de filtrado y orden total: establishment_id, status, pickup_date, time_slot_start, queued_at, id

CREATE INDEX IF NOT EXISTS idx_kds_tickets_active_queue
    ON kds_tickets (establishment_id, status, pickup_date, time_slot_start, queued_at, id);
