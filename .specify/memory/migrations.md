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

Repeatable migrations carry no version and are re-applied whenever their content changes: `db/dev/R__seed_dev_users.sql`, `db/dev/R__seed_dev_dishes_and_dietary.sql`.

## In flight — claimed but not yet merged

| Version | Spec | Status |
|---|---|---|
| V8 | **unclaimed** | the next free number as of 2026-10-28 |

Spec 007 deliberately holds no claim: its `plan.md` and `data-model.md` instruct the implementer to take the next free version confirmed against the migration directory, and its migration test is named `AdminPricingMigrationTest` so no renumbering can invalidate it. Spec 008's planning documents still name `V7`, which spec 005 has taken; that is recorded as a note to 008 in `specs/007-admin-dish-approval-pricing/research.md §3.7`.

## Operational note

Flyway runs with `outOfOrder` disabled (the default; no `spring.flyway.out-of-order` is set in any profile). A migration whose version is lower than the highest already applied is therefore **rejected on any database that was not recreated**. Version order must match merge order, which is why a late-merging branch renumbers rather than keeping its claim.
