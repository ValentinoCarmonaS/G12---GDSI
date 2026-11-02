-- Migration to support C-07: Store proposed changes snapshot in review history
ALTER TABLE dish_review_history 
ADD COLUMN proposed_changes_json JSONB;

COMMENT ON COLUMN dish_review_history.proposed_changes_json IS 'Snapshot of changes proposed by the Administrator during rejection (C-07).';
