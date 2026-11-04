# Flyway Migration Version Registry

**Purpose**: the single authoritative record of which Flyway version numbers are consumed, so that two feature branches cannot claim the same one.

**Why this file exists.** A version reserved in a planning document reserves nothing. Spec 005's plan claimed `V6`; spec 004 wrote and merged `V6__create_ingredient_batches_and_refactor_lifecycle.sql` first, so 005 had to move to `V7` — which spec 008's plan had already claimed for itself. The collision moved instead of disappearing, and nobody detected it because the three claims lived in three plans that do not read each other. The version belongs to whoever writes and merges the file first; this registry makes that visible before the merge rather than after.

## Rules

1. The authoritative source of consumed versions is `easyeat/backend/src/main/resources/db/migration/` **on the integration branch**, not any planning document. This file mirrors it and adds the claims that are in flight.
2. A feature takes the lowest integer strictly greater than every version present on the integration branch, **confirmed at the moment the migration file is authored** — never reserved during planning.
3. The PR that adds a migration updates this file in the same PR. A migration without a row here is incomplete.
4. A claim marked *in flight* is a courtesy, not a reservation. If two branches hold the same claim, the one that merges first keeps it and the other renumbers.
5. Tests must not carry a version number in their class name, so an upstream renumbering cannot invalidate them.

## Consumed — present on `develop/sprint-3`

| Version | File | Spec | Landed via |
|---|---|---|---|
| V1 | `V1__create_users.sql` | 001 | — |
| V2 | `V2__create_password_reset_tokens.sql` | 001 | — |
| V3 | `V3__extend_users_staff.sql` | 004 | — |
| V4 | `V4__create_ingredients_and_stock_audit.sql` | 004 | — |
| V5 | `V5__extend_users_profile_and_dietary.sql` | 003 | — |
| V6 | `V6__create_ingredient_batches_and_refactor_lifecycle.sql` | 004 | PR #126 |
| V7 | `V7__chef_dish_management.sql` | 005 | PR #160 |
| V8 | `V8__create_establishments.sql` | 008 | merged into `develop/sprint-2` ahead of spec 007 (exact PR not confirmed from this integration; present on `develop/sprint-2` when spec 007 was integrated 2026-10-31) |
| V9 | `V9__admin_dish_approval_pricing.sql` | 007 | renumbered from `V8` during the `007-admin-dish-approval-pricing` → `develop/sprint-2` integration (PR #182), per rule 4: spec 008 merged first and kept `V8` |
| V10 | `V10__support_admin_dish_cancellation.sql` | 007 / C-02 | adds `CANCELLED` to the dish-status and immutable review-event CHECK constraints; V7/V9 remain unchanged |
| V11 | `V11__sync_ingredient_unit_purchase_cost.sql` | 006 / C-03 | backfills ingredient master unit_purchase_cost from latest batch |
| V12 | `V12__extend_user_demographics_and_avatar.sql` | 009 | adds age, gender, address to users and creates user_photos (BYTEA avatar storage) |
| V13 | `V13__dish_review_history_snapshot.sql` | 007 / C-07 | adds proposed_changes_json JSONB to dish_review_history |
| V14 | `V14__add_units_per_package_to_ingredients.sql` | 007 / C-08 | adds package_cost and units_per_package to ingredients |
| V15 | `V15__restaurant_operation_config.sql` | 010 | creates establishment operational schedules, slot capacity and payment methods |
| V16 | `V16__add_time_slot_and_order_fulfillment.sql` | 012 | creates time slot orders, fulfillment and reservations |

Spec 006 (`006-recipe-food-cost`, PR #170, merged 2026-10-28) added **no** migration: its recipe model reuses `dish_recipe_ingredients` from 005's `V7`. A feature that needs no schema change takes no version, and that is worth recording so nobody looks for a missing version from it.

Repeatable migrations carry no version and are re-applied whenever their content changes: `db/dev/R__seed_dev_users.sql`, `db/dev/R__seed_dev_dishes_and_dietary.sql`.

## In flight — claimed but not yet merged

**Spec 013 (`013-critical-stock-chef-pause`) claims `V17__critical_stock_and_chef_pause.sql`** (renumbered from V11 when integrating `develop/sprint-3`, which had already consumed V11 through V16; rule 4, the branch that merges later renumbers). Adds `critical_stock_threshold` to `ingredients`, `paused_by_chef` to `dishes`, and indexes `idx_ingredients_critical_alert` and `idx_dishes_catalog_visibility`.

## Operational note

Flyway runs with `outOfOrder` disabled (the default; no `spring.flyway.out-of-order` is set in any profile). A migration whose version is lower than the highest already applied is therefore **rejected on any database that was not recreated**. Version order must match merge order, which is why a late-merging branch renumbers rather than keeping its claim.
