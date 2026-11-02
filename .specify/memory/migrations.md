# Flyway Migration Version Registry

**Purpose**: the single authoritative record of which Flyway version numbers are consumed, so that two feature branches cannot claim the same one.

**Why this file exists.** A version reserved in a planning document reserves nothing. Spec 005's plan claimed `V6`; spec 004 wrote and merged `V6__create_ingredient_batches_and_refactor_lifecycle.sql` first, so 005 had to move to `V7` — which spec 008's plan had already claimed for itself. The collision moved instead of disappearing, and nobody detected it because the three claims lived in three plans that do not read each other. The version belongs to whoever writes and merges the file first; this registry makes that visible before the merge rather than after.

## Rules

1. The authoritative source of consumed versions is `easyeat/backend/src/main/resources/db/migration/` **on the integration branch**, not any planning document. This file mirrors it and adds the claims that are in flight.
2. A feature takes the lowest integer strictly greater than every version present on the integration branch, **confirmed at the moment the migration file is authored** — never reserved during planning.
3. The PR that adds a migration updates this file in the same PR. A migration without a row here is incomplete.
4. A claim marked *in flight* is a courtesy, not a reservation. If two branches hold the same claim, the one that merges first keeps it and the other renumbers.
5. Tests must not carry a version number in their class name, so an upstream renumbering cannot invalidate them.

## Consumed — present on `develop/sprint-2`

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
| V12 | `V12__restaurant_operation_config.sql` | 010 | in flight on `010-restaurant-operation-config`. `V10` and `V11` are consumed on `develop/sprint-3` (not yet on this branch), so `V12` is the next free number there. Test `OperationConfigMigrationTest` locates it by Flyway description, so a renumbering touches only the filename and this row (rule 5). |

Spec 006 (`006-recipe-food-cost`, PR #170, merged 2026-10-28) added **no** migration: its recipe model reuses `dish_recipe_ingredients` from 005's `V7`. A feature that needs no schema change takes no version, and that is worth recording so nobody looks for a missing version from it.

Repeatable migrations carry no version and are re-applied whenever their content changes: `db/dev/R__seed_dev_users.sql`, `db/dev/R__seed_dev_dishes_and_dietary.sql`.

## In flight — claimed but not yet merged

None. The `V8` contest between specs 007 and 008 (below) is resolved: 008 merged first and kept `V8`; 007 renumbered to `V9` per rule 4, exactly as this table anticipated.

**Renumbering either was cheap, and that was by design.** Spec 007's migration test is named `AdminPricingMigrationTest` and **nothing inside it hardcodes a version**: it locates the migration by its Flyway description and reads the preceding version from `flyway.info()`. A rename changes the filename and this table, nothing else. Spec 005's `V7MigrationAndSeedDevTest` does carry its version in the class name, which rule 5 exists to prevent; it is noted for that spec's owner.

**Why 007 did not simply yield `V9` in advance.** Deferring a number only helps when the deferrer merges second, which nobody could promise ahead of time — the rule is merge order, decided only once an actual integration happens, not by courtesy. As it played out, 008 did merge first (it was already on `develop/sprint-2` when 007's integration PR #182 was opened), so 007 renumbered its already-written `V8__admin_dish_approval_pricing.sql` to `V9__admin_dish_approval_pricing.sql` during that integration, exactly as this table anticipated.

The two files are independent in content, so renumbering either is a rename plus a row here: 007's alters `dishes` and creates the two audit tables; 008's creates `establishments` and, verified on its branch, does **not** touch `dishes.status` or `chk_dishes_status`, despite `008/data-model.md` planning to redefine that constraint. If 008 ever writes that redefinition it collides with spec 007's `PUBLISHED`, which is a design conflict rather than a numbering one — recorded in `specs/007-admin-dish-approval-pricing/research.md §3.7`.

Spec 007's migration test is named `AdminPricingMigrationTest`, with no version in the name, so a renumbering cannot invalidate it (rule 5).

## Operational note

Flyway runs with `outOfOrder` disabled (the default; no `spring.flyway.out-of-order` is set in any profile). A migration whose version is lower than the highest already applied is therefore **rejected on any database that was not recreated**. Version order must match merge order, which is why a late-merging branch renumbers rather than keeping its claim.
