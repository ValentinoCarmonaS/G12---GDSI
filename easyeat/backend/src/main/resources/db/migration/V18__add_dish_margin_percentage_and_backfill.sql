-- ====================================================================
-- EasyEat V18: Automatic Dish PVP Adjustment on Ingredient Cost Increase (C-09)
-- Owns: dishes.margin_percentage, dish_price_history audit details,
--       and backfill from prevailing PVP and Food Cost.
-- ====================================================================

-- 1. Agregar columna margin_percentage DECIMAL(7, 4) en dishes
ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS margin_percentage DECIMAL(7, 4);

ALTER TABLE dishes
    ADD CONSTRAINT chk_dishes_margin_percentage
        CHECK (margin_percentage IS NULL OR margin_percentage < 100);

COMMENT ON COLUMN dishes.margin_percentage IS
    'Commercial profit margin percentage assigned by Administrator or derived upon pricing. '
    'Preserved during automatic repricing triggered by ingredient cost increases (C-09).';

-- 2. Extender dish_price_history con columnas de detalle de ajuste automático (C-09)
ALTER TABLE dish_price_history
    ADD COLUMN IF NOT EXISTS triggering_ingredient_id UUID REFERENCES ingredients(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS ingredient_previous_cost NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS ingredient_new_cost NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS previous_food_cost NUMERIC(10, 2);

COMMENT ON COLUMN dish_price_history.triggering_ingredient_id IS
    'Ingredient whose cost variation triggered this automatic price adjustment (C-09).';
COMMENT ON COLUMN dish_price_history.ingredient_previous_cost IS
    'Unit purchase cost of the ingredient before the stock adjustment (C-09).';
COMMENT ON COLUMN dish_price_history.ingredient_new_cost IS
    'Unit purchase cost of the ingredient after the stock addition (C-09).';
COMMENT ON COLUMN dish_price_history.previous_food_cost IS
    'Dish Food Cost before the ingredient cost change (C-09).';

-- 3. Backfill inicial para platos actualmente en estado PUBLISHED con retail_price > 0
--    margin_percentage = ((PVP_actual - Food_Cost_actual) / PVP_actual) * 100
--    Food_Cost_actual se calcula sumando (cantidad * unit_purchase_cost) de los ingredientes de la receta.
WITH dish_food_costs AS (
    SELECT
        dri.dish_id,
        COALESCE(SUM(ROUND(dri.quantity * COALESCE(i.unit_purchase_cost, 0), 2)), 0) AS current_food_cost
    FROM dish_recipe_ingredients dri
    JOIN ingredients i ON dri.ingredient_id = i.id
    GROUP BY dri.dish_id
)
UPDATE dishes d
SET margin_percentage = ROUND(
    ((d.retail_price - COALESCE(dfc.current_food_cost, 0)) / d.retail_price) * 100,
    4
)
FROM dish_food_costs dfc
WHERE d.id = dfc.dish_id
  AND d.status = 'PUBLISHED'
  AND d.retail_price IS NOT NULL
  AND d.retail_price > 0
  AND d.margin_percentage IS NULL;
