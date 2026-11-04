-- ====================================================================
-- EasyEat V11: Critical Stock Threshold and Chef Dish Manual Pause
-- Owns: ingredients.critical_stock_threshold, dishes.paused_by_chef,
--       and performance indexes for critical stock and pause filtering.
-- ====================================================================

-- 1. Extender tabla ingredients con umbral crítico individual
ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS critical_stock_threshold INTEGER;

ALTER TABLE ingredients
    ADD CONSTRAINT chk_ingredient_critical_threshold
        CHECK (critical_stock_threshold IS NULL OR critical_stock_threshold >= 0);

COMMENT ON COLUMN ingredients.critical_stock_threshold IS
    'Individual critical stock threshold defined by the Administrator (ADM-4.1.3). '
    'NULL means no threshold configured. When current_stock < critical_stock_threshold, '
    'a visual alert is displayed in the back-office inventory (ADM-4.1.4).';

-- Índice para acelerar el filtro rápido de insumos críticos en el inventario (FR-020)
CREATE INDEX IF NOT EXISTS idx_ingredients_critical_alert
    ON ingredients (tenant_id, status, critical_stock_threshold)
    WHERE status = 'ACTIVE' AND critical_stock_threshold IS NOT NULL;

-- 2. Extender tabla dishes con control de pausa manual del chef
ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS paused_by_chef BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dishes.paused_by_chef IS
    'Manual operational pause toggled by the Chef during service (CHF-2.2.5). '
    'When TRUE, dish is completely hidden from diner catalog (INV-8). '
    'Independent of stock availability and administrative status (INV-10).';

-- Índice compuesto para consultas de catálogo activo del comensal y bandeja del chef
CREATE INDEX IF NOT EXISTS idx_dishes_catalog_visibility
    ON dishes (tenant_id, status, is_active, paused_by_chef);
