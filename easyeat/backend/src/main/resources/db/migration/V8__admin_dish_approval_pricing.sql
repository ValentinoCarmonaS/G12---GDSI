-- ====================================================================
-- EasyEat: Administrative Back-Office - PVP, Approval and Audit (spec 007)
-- Owns: dishes.retail_price / published_at, dish_review_history,
--       dish_price_history, the audit indexes, idx_dishes_review_inbox_published,
--       and the retail_price backfill.
-- Owns NOTHING of spec 005's or spec 008's migrations.
-- ====================================================================

-- 1. Columns owned by this feature.
--    No version column: FR-025 is enforced by conditional UPDATEs that restate
--    the state the Administrator acted upon, not by a row version. Adding one
--    would change failure behaviour for every writer of this shared table.
--    See data-model.md 1.2 and plan.md R-005-4 (withdrawn).
ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS retail_price NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

ALTER TABLE dishes
    ADD CONSTRAINT chk_dishes_retail_price_positive
        CHECK (retail_price IS NULL OR retail_price > 0);

COMMENT ON COLUMN dishes.retail_price IS
    'Retail price (PVP) assigned by the Administrator. NULL means "not yet priced"; '
    'that distinction is why spec 003''s NOT NULL price column could not be reused (D2).';

COMMENT ON COLUMN dishes.published_at IS
    'Instant of the PENDING_PRICE -> PUBLISHED transition. NULL for any dish never '
    'approved. Never cleared here: unpublishing belongs to the Sprint 3 catalog feature.';

COMMENT ON COLUMN dishes.price IS
    'DEPRECATED by spec 007: superseded by retail_price. Retained for backward '
    'compatibility with spec 003 artifacts; never written by application code '
    'after 007. Removal owned by the Sprint 3 catalog-lifecycle feature (CHF-2.2.5).';

-- 2. Backfill the PVP from the legacy price (D2).
--    price is nullable since spec 005 and may be 0.00 since V5 allowed >= 0.
--    Neither can become a retail_price: FR-013 requires PVP > 0, and copying
--    0.00 would turn "free or unset" into "priced at nothing". Those rows stay
--    unpriced, which is exactly what NULL retail_price means.
UPDATE dishes
   SET retail_price = price
 WHERE retail_price IS NULL
   AND price IS NOT NULL
   AND price > 0;

-- 3. Status domain extension: nothing to do here.
--    R-005-2 was satisfied upstream -- spec 005's V7 already declares
--    chk_dishes_status CHECK (status IN ('DRAFT','PENDING_PRICE','PUBLISHED')).
--    This migration must neither drop nor replace that constraint: it belongs to
--    spec 005 (D1), and two owners of one constraint object is the collision D1
--    exists to prevent. The fallback that would have replaced it is withdrawn.

-- 4. Immutable administrative review history (FR-034, FR-035).
CREATE TABLE IF NOT EXISTS dish_review_history (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dish_id      UUID         NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    tenant_id    UUID         NOT NULL,
    cycle_number INTEGER      NOT NULL,
    event_type   VARCHAR(20)  NOT NULL,
    admin_id     UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_drh_dish_cycle UNIQUE (dish_id, cycle_number),
    CONSTRAINT chk_drh_cycle_positive CHECK (cycle_number >= 1),
    CONSTRAINT chk_drh_event_type CHECK (event_type IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_drh_rejection_comment CHECK (
        event_type <> 'REJECTED'
        OR (comment IS NOT NULL AND LENGTH(BTRIM(comment)) BETWEEN 10 AND 1000)
    )
);

COMMENT ON TABLE dish_review_history IS
    'Append-only. No UPDATE or DELETE path exists in the application: the entity '
    'exposes no setters and its repository exposes only save and queries (FR-034). '
    'Retained for as long as the dish exists (FR-035), hence ON DELETE CASCADE.';

COMMENT ON COLUMN dish_review_history.tenant_id IS
    'Denormalised from the dish so every audit query is tenant-filtered without a '
    'join: a query that forgets the join still cannot read across tenants.';

COMMENT ON COLUMN dish_review_history.cycle_number IS
    'Monotonic from 1 per dish, assigned inside the transaction that performs the '
    'transition. UNIQUE (dish_id, cycle_number) is the proof of the invariant, not '
    'its mechanism: a violation means a write path bypassed the status predicate.';

CREATE INDEX IF NOT EXISTS idx_drh_dish_created
    ON dish_review_history (dish_id, created_at);
CREATE INDEX IF NOT EXISTS idx_drh_tenant_created
    ON dish_review_history (tenant_id, created_at DESC);

-- 5. Immutable retail price history (FR-010, FR-034, FR-035).
CREATE TABLE IF NOT EXISTS dish_price_history (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    dish_id            UUID           NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
    tenant_id          UUID           NOT NULL,
    admin_id           UUID           NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_price     NUMERIC(10, 2),
    new_price          NUMERIC(10, 2) NOT NULL,
    food_cost_snapshot NUMERIC(10, 2) NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dph_new_price_positive CHECK (new_price > 0),
    CONSTRAINT chk_dph_food_cost_non_negative CHECK (food_cost_snapshot >= 0)
);

COMMENT ON TABLE dish_price_history IS
    'Append-only, same enforcement as dish_review_history.';

COMMENT ON COLUMN dish_price_history.previous_price IS
    'NULL on the first pricing of a dish (FR-010), which is a fact about the dish '
    'rather than missing data.';

COMMENT ON COLUMN dish_price_history.food_cost_snapshot IS
    'The Food Cost prevailing at the instant of the change, read from spec 006 '
    'inside the same transaction. Already scale 2 upstream, so it is stored without '
    'rounding here. 0.00 is legitimate: a recipe whose every ingredient is unpriced.';

CREATE INDEX IF NOT EXISTS idx_dph_dish_created
    ON dish_price_history (dish_id, created_at DESC);

-- 6. Index for the published tab of the review inbox.
--    The FIFO pending index idx_dishes_review_inbox_pending
--    (tenant_id, status, submitted_at) is created by spec 005's V7 and is
--    consumed here, deliberately not re-declared: two owners of one index
--    object would violate D1.
CREATE INDEX IF NOT EXISTS idx_dishes_review_inbox_published
    ON dishes (tenant_id, status, published_at DESC);
