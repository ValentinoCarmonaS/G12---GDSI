-- Backfill ingredient master unit_purchase_cost from the latest batch unit_cost
-- for ingredients where unit_purchase_cost is zero or null.

UPDATE ingredients i
SET unit_purchase_cost = latest_batch.unit_cost
FROM (
    SELECT DISTINCT ON (ingredient_id) ingredient_id, unit_cost
    FROM ingredient_batches
    WHERE unit_cost > 0
    ORDER BY ingredient_id, created_at DESC, id DESC
) latest_batch
WHERE i.id = latest_batch.ingredient_id
  AND (i.unit_purchase_cost IS NULL OR i.unit_purchase_cost = 0);
