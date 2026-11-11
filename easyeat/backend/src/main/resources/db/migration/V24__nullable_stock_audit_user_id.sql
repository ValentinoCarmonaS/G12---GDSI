-- ====================================================================
-- EasyEat V24: Allow NULL user_id on stock_audit_entries for automated system deductions
-- ====================================================================

ALTER TABLE stock_audit_entries
    ALTER COLUMN user_id DROP NOT NULL;

