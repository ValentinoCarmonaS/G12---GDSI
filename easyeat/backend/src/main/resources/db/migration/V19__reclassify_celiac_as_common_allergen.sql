-- ====================================================================
-- EasyEat V19: Reclassify CELIAC from DIETARY_LIFESTYLE to COMMON_ALLERGEN (C-11)
-- ====================================================================

UPDATE dietary_restrictions
SET category = 'COMMON_ALLERGEN',
    description = 'Presencia de gluten (trigo, avena, cebada, centeno) o trazas; no apto para celíacos.'
WHERE code = 'CELIAC';
