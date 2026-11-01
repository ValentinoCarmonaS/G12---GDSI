-- C-02: add the Administrator's terminal cancellation state and review event.
-- V7 and V9 may already be applied; extend their closed domains in a forward-only migration.

ALTER TABLE dishes
    DROP CONSTRAINT chk_dishes_status;

ALTER TABLE dishes
    ADD CONSTRAINT chk_dishes_status
        CHECK (status IN ('DRAFT', 'PENDING_PRICE', 'PUBLISHED', 'CANCELLED'));

ALTER TABLE dish_review_history
    DROP CONSTRAINT chk_drh_event_type;

ALTER TABLE dish_review_history
    ADD CONSTRAINT chk_drh_event_type
        CHECK (event_type IN ('APPROVED', 'REJECTED', 'CANCELLED'));
