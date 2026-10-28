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

Spec 006 (`006-recipe-food-cost`, PR #170, merged 2026-10-28) added **no** migration: its recipe model reuses `dish_recipe_ingredients` from 005's `V7`. A feature that needs no schema change takes no version, and that is worth recording so nobody looks for a missing `V8` from it.

Repeatable migrations carry no version and are re-applied whenever their content changes: `db/dev/R__seed_dev_users.sql`, `db/dev/R__seed_dev_dishes_and_dietary.sql`.

## In flight — claimed but not yet merged

| Version | Spec | Status |
|---|---|---|
| V8 | **008 and 007, contested** | `V8__create_establishments.sql` is written on `008-diner-catalog-multi-tenant` (PRs #168, #169, merged into that feature branch only). Spec 007 takes the same number for `V8__admin_dish_approval_pricing.sql`, because rule 2 yields it against the integration branch and rule 4 makes 008's claim a courtesy. **Whichever merges to `develop/sprint-2` first keeps `V8`; the other renames its file to `V9` and updates this table.** |

**Why 007 does not simply yield `V9`.** It would not help 008 and would cost something. With `outOfOrder` disabled, a `V8` merged *after* an applied `V9` sits below the highest applied version and is rejected on every database not recreated — so 008 would have to renumber to `V10` rather than `V9`, and `V8` would be left as a permanent gap. Deferring a number only helps when the deferrer merges second, which nobody can promise in advance. The rule is merge order, and it is the same rule for both.

The two files are independent in content, so renumbering either is a rename plus a row here: 007's alters `dishes` and creates the two audit tables; 008's creates `establishments` and, verified on its branch, does **not** touch `dishes.status` or `chk_dishes_status`, despite `008/data-model.md` planning to redefine that constraint. If 008 ever writes that redefinition it collides with spec 007's `PUBLISHED`, which is a design conflict rather than a numbering one — recorded in `specs/007-admin-dish-approval-pricing/research.md §3.7`.

Spec 007's migration test is named `AdminPricingMigrationTest`, with no version in the name, so a renumbering cannot invalidate it (rule 5).

## Operational note

Flyway runs with `outOfOrder` disabled (the default; no `spring.flyway.out-of-order` is set in any profile). A migration whose version is lower than the highest already applied is therefore **rejected on any database that was not recreated**. Version order must match merge order, which is why a late-merging branch renumbers rather than keeping its claim.
