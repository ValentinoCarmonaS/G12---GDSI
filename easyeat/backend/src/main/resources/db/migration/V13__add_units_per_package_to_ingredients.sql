-- C-08 / spec 004 FR-015d: ingredient purchase-presentation conversion.
-- units_per_package is an integer that defaults to 1: direct units (kg, l, loose u) keep 1,
-- while packaged purchases (dozens, boxes, cartons) record how many base units they contain.
-- package_cost is the package's total purchase cost; the Food Cost engine derives the effective
-- base unit cost as package_cost / units_per_package before computing each recipe line subtotal.

ALTER TABLE ingredients
    ADD COLUMN IF NOT EXISTS package_cost NUMERIC(12, 4),
    ADD COLUMN IF NOT EXISTS units_per_package INTEGER NOT NULL DEFAULT 1;

ALTER TABLE ingredients
    ADD CONSTRAINT chk_ingredient_package_cost_non_negative
        CHECK (package_cost IS NULL OR package_cost >= 0),
    ADD CONSTRAINT chk_ingredient_units_per_package_positive
        CHECK (units_per_package >= 1);
