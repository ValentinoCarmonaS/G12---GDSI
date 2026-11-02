---

description: "Task list for Restaurant Operation Configuration (010)"
---

# Tasks: Restaurant Operation Configuration — Business Schedules, Slot Capacity & Online Payment Methods

**Input**: Design documents from `/specs/010-restaurant-operation-config/`
**Prerequisites**: plan.md, spec.md, data-model.md, research.md, contracts/openapi.yaml, contracts/behavioral-contracts.md, quickstart.md
**Tests**: REQUESTED (strict TDD). Within every story the test task precedes its implementation task, and the test MUST be observed failing (RED) before the implementation task starts.
**Organization**: Foundational phase, then one phase per user story in priority order, then a final verification phase.

## Format: `- [ ] T### [P?] [US?] Description with exact file path`

- **[P]**: parallelizable (different files, no dependency on an incomplete task).
- Paths are relative to the repository root. `BE` = `easyeat/backend/src/main/java/com/easyeat/operations`, `BT` = `easyeat/backend/src/test/java/com/easyeat/operations`, `FE` = `easyeat/frontend/src`.
- Build/test commands: use the JDK 21 + Maven invocation recorded in the project toolchain notes (they are not on the PATH); frontend: `npm run test -- src/test/operations/` from `easyeat/frontend`.

## Scope boundary (applies to every phase)

The repo has **no orders / checkout / slot-selector backend**. This feature builds the services, REST endpoints and *standalone* React components (tested with props/mocks). **No orders module, entity, controller or stub is created.** Integration with the future order-approval and checkout flows (A-002..A-004) is a single documented wire-up task (T048).

## Findings from the repo that shape these tasks

- A `Clock` bean **already exists** (`easyeat/backend/src/main/java/com/easyeat/config/ClockConfig.java`, `Clock.systemUTC()`). It is reused, not recreated; the foundational task adds the establishment-timezone resolver on top of it.
- `Establishment` (`easyeat/backend/src/main/java/com/easyeat/catalog/domain/Establishment.java`) already has `timezone`, defaulting to `America/Argentina/Buenos_Aires`.
- Only `V1`–`V9` exist on this branch; `V12` is used as instructed (V10/V11 land via `develop/sprint-3`). Per `.specify/memory/migrations.md` rules 3 and 5, the migration PR must add a registry row, and **no test class name may carry a version number**.
- `GlobalExceptionHandler` lives at `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java`; it has a deliberate comment (≈line 431) about *not* handling `ObjectOptimisticLockingFailureException` globally — read it before T022 and keep the 409 mapping scoped to the operations controllers if that comment requires it.
- Existing admin controllers use `@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")` under `/api/v1/admin/...`; tenant comes from `TenantContextHolder`.

---

## Phase 1: Setup

- [ ] T001 Confirm the module directories listed under "Path Conventions" do not exist yet (`easyeat/backend/src/main/java/com/easyeat/operations`, `easyeat/backend/src/test/java/com/easyeat/operations`); they are created by the first files in Phase 2. No new package-info or placeholder classes.
- [ ] T002 [P] Create `easyeat/frontend/src/test/operations/` and confirm Vitest picks it up via the existing config in `easyeat/frontend/vite.config.ts` (or `vitest.config.ts`); no new dependency allowed.

---

## Phase 2: Foundational (blocks all stories)

**⚠️ No user story starts until this phase is complete.**

- [x] T003 Write RED migration test `easyeat/backend/src/test/java/com/easyeat/operations/repository/OperationConfigMigrationTest.java` (Testcontainers PostgreSQL 16, modeled on `easyeat/backend/src/test/java/com/easyeat/catalog/migration/`): asserts the 3 tables exist; `CHECK (is_closed = TRUE OR opening_time < closing_time)` (INV-7), `CHECK (max_capacity >= 0 AND approved_orders_count >= 0)` (INV-6), UNIQUE `(establishment_id, day_of_week)`, UNIQUE `(establishment_id, slot_date, start_time)`, UNIQUE `(establishment_id, payment_method)`, and that every existing establishment is seeded with `MERCADO_PAGO` and `STRIPE`. Locate the migration by Flyway description, never by hard-coded version. **DONE 2026-11-01** — RED observed: 9 of 9 failing (V12 absent).
- [x] T004 Create `easyeat/backend/src/main/resources/db/migration/V12__restaurant_operation_config.sql` per `data-model.md` §3: tables `establishment_operating_schedules`, `establishment_slot_capacities`, `establishment_payment_methods`, all with `version BIGINT`, FK to `establishments(id)`, the constraints from T003, and the payment-method seed for existing establishments (depends on T003 RED). **DONE 2026-11-01** — GREEN: 9 of 9 passing; DDL taken verbatim from data-model.md §3.
- [ ] T005 [P] Update `.specify/memory/migrations.md`: add the `V12 | V12__restaurant_operation_config.sql | 010` row (rule 3), noting V10/V11 arrive from `develop/sprint-3`.
- [ ] T006 [P] Create enum `easyeat/backend/src/main/java/com/easyeat/operations/domain/PaymentMethod.java` (`MERCADO_PAGO`, `STRIPE`) and value object `easyeat/backend/src/main/java/com/easyeat/operations/domain/TimeSlot.java` (`slotDate`, `startTime`, derived `endTime = startTime + 15 min`, semi-open `[start,end)`).
- [ ] T007 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentOperatingSchedule.java` with `@Version Long version`, `DayOfWeek`, `LocalTime openingTime/closingTime`, `isClosed`.
- [ ] T008 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentSlotCapacity.java` with `@Version`, `maxCapacity`, `approvedOrdersCount`, `slotDate`, `startTime`, `endTime`.
- [ ] T009 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentPaymentMethod.java` with `@Version`, `PaymentMethod` (`@Enumerated(STRING)`), `isEnabled`. No bank/credential columns (A-005).
- [ ] T010 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/OperatingScheduleRepository.java` (find by establishment; find by establishment + day).
- [ ] T011 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/SlotCapacityRepository.java` (find by establishment + date range; find by establishment + date + startTime). The atomic UPDATE is added in T029, not here.
- [ ] T012 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/PaymentMethodRepository.java` (find by establishment; find by establishment + method).
- [ ] T013 Write RED test `easyeat/backend/src/test/java/com/easyeat/operations/service/EstablishmentTimeProviderTest.java`: with a fixed `Clock` and a non-default `Establishment.timezone`, `nowAt(establishmentId)` returns the zoned instant of *that* timezone (EC-014); there is no overload accepting a client-supplied time (EC-018).
- [ ] T014 Create `easyeat/backend/src/main/java/com/easyeat/operations/service/EstablishmentTimeProvider.java`: injects the existing `Clock` bean from `easyeat/backend/src/main/java/com/easyeat/config/ClockConfig.java` and `EstablishmentRepository`, resolves `ZoneId.of(Establishment.timezone)`; the **only** time authority for this module (depends on T013 RED). Do not add a second `Clock` bean.
- [ ] T015 [P] Create frontend types `easyeat/frontend/src/types/operations.ts` (schedule, slot, capacity, payment-method DTOs matching `contracts/openapi.yaml`, incl. `version`).
- [ ] T016 [P] Create typed client `easyeat/frontend/src/api/operationsApi.ts` using `easyeat/frontend/src/api/apiClient.ts` for every path in `contracts/openapi.yaml` (admin schedules / slot-capacities / payment-methods, diner time-slots / payment-methods, validate-payment).

**Checkpoint**: schema, entities, repositories, time authority and client types ready.

---

## Phase 3: User Story 1 — Business Schedules (ADM-2.2.1) (Priority: P1) 🎯 MVP

**Goal**: Admin configures weekly opening/closing; the diner only gets admissible 15-min slots.
**Independent Test**: With a fixed Clock: 15:59:59 admits, 16:00:00 and 16:01 block today, 22:00 admits tomorrow 13:00, unconfigured day is closed; PUT/GET schedules honour RBAC, 400 and 409.

### Tests (RED first)

- [ ] T017 [US1] Create `easyeat/backend/src/test/java/com/easyeat/operations/domain/OperatingInvariantsUnitTest.java` (shared by all stories, one nested class per story to avoid edit conflicts) with the US1 nested class, fixed `Clock`: `shouldBlockAtExactClosingMinute` / `shouldBlockAt1601` (INV-1, EC-001), 15:59:59 admitted, `shouldBlockBeforeOpening`, `shouldAdmitFutureReservationWhenClosedToday` (22:00 → tomorrow 13:00, EC-017), `shouldPartitionDayIntoDeterministic15MinSlots` (12:00–14:00 → 8 slots, INV-5, EC-015), `shouldRejectOpeningEqualOrAfterClosing` (INV-7), `shouldTreatUnconfiguredDayAsClosed` (EC-008), `shouldApplyScheduleRulesToAllFulfillmentTypes` (INV-10), `shouldEvaluateAgainstEstablishmentTimezone` (EC-014), `shouldIgnoreClientClockDrift` (EC-018), `shouldAllowApprovalAfterClosingIfAdmittedBefore` (EC-002). Run and record RED.

### Implementation — services

- [ ] T018 [US1] Implement `easyeat/backend/src/main/java/com/easyeat/operations/service/OperatingScheduleService.java` (upsert week with INV-7 check, unconfigured day ⇒ closed, optimistic version handling) and `easyeat/backend/src/main/java/com/easyeat/operations/service/DinerTimeSlotService.java` (15-min partition over `[opening, closing)`, drops expired slots of today using `EstablishmentTimeProvider`, admits future dates, same rule for pickup and delivery). Turn T017 GREEN.

### Tests — controllers (RED first)

- [ ] T019 [US1] Create `easyeat/backend/src/test/java/com/easyeat/operations/controller/AdminOperationsControllerTest.java` (MockMvc, schedule section; later stories add sections): unauthenticated → 401, non-admin → 403, `shouldSaveSchedule` 200, opening ≥ closing → 400 ProblemDetail "Parámetros de horario inválidos", stale `version` → 409 "Conflicto de concurrencia" (`shouldFailOnOptimisticLockConflict`, EC-012), GET returns the week with unconfigured days closed. Also a diner `GET /time-slots` test (public/diner access, only admissible slots). Run RED.

### Implementation — endpoints

- [ ] T020 [P] [US1] Create DTOs `easyeat/backend/src/main/java/com/easyeat/operations/dto/UpdateScheduleRequest.java`, `OperatingScheduleResponse.java`, `DinerTimeSlotsResponse.java` (Bean Validation + cross-field INV-7 check).
- [ ] T021 [US1] Create `easyeat/backend/src/main/java/com/easyeat/operations/controller/AdminOperatingScheduleController.java` (`PUT`/`GET /api/v1/admin/operating-schedules`, admin-only `@PreAuthorize` like `AdminIngredientController`, tenant from `TenantContextHolder`) and `easyeat/backend/src/main/java/com/easyeat/operations/controller/DinerTimeSlotController.java` (`GET /api/v1/establishments/{tenantId}/time-slots`).
- [ ] T022 [US1] Add RFC 7807 mapping for INV-7 (400) and optimistic-lock (409) for the operations controllers in `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` (respect the comment near line 431; scope by exception type/package if required). Turn T019 GREEN.

### Frontend (tests then components)

- [ ] T023 [P] [US1] Write RED `easyeat/frontend/src/test/operations/WeeklyScheduleManager.test.tsx` (props/mocks only): seven day rows, closed toggle, blocks submit when opening ≥ closing with message, surfaces a 409 "reload" message.
- [ ] T024 [US1] Implement `easyeat/frontend/src/components/operations/WeeklyScheduleManager.tsx` (turns T023 GREEN).
- [ ] T025 [US1] Create `easyeat/frontend/src/pages/AdminOperationsPage.tsx` (hosts WeeklyScheduleManager now; capacity and payment sections added in US2/US3 tasks), register route `/admin/operations` in `easyeat/frontend/src/router/AppRoutes.tsx` under the same `RoleRouteGuard` as the other `/admin/*` routes **before** the `/admin/*` catch-all, and add a navigation `Link` in `easyeat/frontend/src/pages/AdminWorkspacePage.tsx`.
- [ ] T026 [P] [US1] Write `easyeat/frontend/src/test/operations/AdminOperationsPage.test.tsx` (renders schedule section with mocked `operationsApi`, route guarded for ADMIN).

**Checkpoint**: US1 independently deliverable (MVP).

---

## Phase 4: User Story 2 — Slot Capacity (ADM-2.2.2) (Priority: P1)

**Goal**: Per-15-min capacity, atomic consumption, "Agotada" projection, non-retroactive reductions.
**Independent Test**: 10 threads on the last slot → exactly 1 wins, 9 get 409; 50→30 with 40 approved leaves approved untouched and slot sold out.

### Tests (RED first)

- [ ] T027 [US2] Add the US2 nested class to `easyeat/backend/src/test/java/com/easyeat/operations/domain/OperatingInvariantsUnitTest.java`: `shouldAdmitOnlyWhenApprovedBelowCapacity` (INV-2), `shouldMarkSoldOutWhenCapacityReachedOrExceeded` / `shouldMarkSoldOutAtOrder50` (INV-3), `shouldRejectNegativeCapacity` (INV-6), `shouldPreserveApprovedOrdersWhenCapacityReduced` (INV-8), `shouldReopenSlotWhenCapacityIncreased` (EC-004), `shouldTreatZeroCapacityAsPermanentlySoldOut` (EC-006), `shouldDefaultUnconfiguredSlotToZeroCapacity` (EC-007), `shouldPreservePendingOrdersOnScheduleChange` (EC-003). Record RED.
- [ ] T028 [US2] Create `easyeat/backend/src/test/java/com/easyeat/operations/repository/SlotCapacityConcurrencyTest.java` (Testcontainers PostgreSQL 16, modeled on `easyeat/backend/src/test/java/com/easyeat/inventory/concurrency/StockAdjustmentConcurrencyTest.java`): `shouldPreventOvercapacityOnOrder50` (49/50 approved, 10 threads → exactly 1 success, 9 × 409, final count 50, EC-016) and `shouldHandleCapacityReduction` (50→30 with 40 approved → count stays 40, slot sold out, nothing else mutated, EC-005). Record RED.

### Implementation — services

- [ ] T029 [US2] Add the atomic conditional update to `easyeat/backend/src/main/java/com/easyeat/operations/repository/SlotCapacityRepository.java`: `@Modifying @Query("UPDATE … SET approvedOrdersCount = approvedOrdersCount + 1 WHERE establishment = :e AND slotDate = :d AND startTime = :t AND approvedOrdersCount < maxCapacity")` returning affected rows.
- [ ] T030 [US2] Implement `easyeat/backend/src/main/java/com/easyeat/operations/service/SlotCapacityService.java`: list/upsert capacities (INV-6, never touches `approvedOrdersCount`, INV-8), `isSoldOut` projection (unconfigured ⇒ capacity 0), and `consumeSlot(...)` using T029 (0 rows ⇒ slot-exhausted exception). Create `easyeat/backend/src/main/java/com/easyeat/operations/exception/SlotSoldOutException.java`. Turn T027/T028 GREEN. Extend `DinerTimeSlotService` so each slot carries `SOLD_OUT`/`AVAILABLE` status.

### Tests / implementation — endpoints

- [ ] T031 [US2] Add the capacity section to `easyeat/backend/src/test/java/com/easyeat/operations/controller/AdminOperationsControllerTest.java`: 401/403 RBAC, `shouldUpdateCapacity`, negative capacity → 400 "Capacidad inválida", stale version → 409, list upsert accepts many slots in one PUT, diner time-slots show `SOLD_OUT`. Record RED.
- [ ] T032 [P] [US2] Create DTOs `easyeat/backend/src/main/java/com/easyeat/operations/dto/UpdateSlotCapacityRequest.java` (list contract, `@Min(0)`) and `easyeat/backend/src/main/java/com/easyeat/operations/dto/SlotCapacityAdminResponse.java`.
- [ ] T033 [US2] Create `easyeat/backend/src/main/java/com/easyeat/operations/controller/AdminSlotCapacityController.java` (`PUT`/`GET /api/v1/admin/slot-capacities`) and map `SlotSoldOutException` → 409 "Franja horaria agotada" in `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java`. Turn T031 GREEN.

### Frontend (tests then components)

- [ ] T034 [P] [US2] Write RED `easyeat/frontend/src/test/operations/SlotCapacityGrid.test.tsx`: renders the 15-min matrix for a date, edits a cell, rejects negatives, shows approved/max, and a **bulk-apply** control: one value applied to every slot of a date (and a date range) emits **one** `onSave` call carrying the full list (existing PUT list contract, no new endpoint).
- [ ] T035 [P] [US2] Write RED `easyeat/frontend/src/test/operations/DinerSlotSelector.test.tsx`: `shouldRenderSoldOutBadge` ("Agotada"), click on a sold-out slot is disabled/no-op, `shouldRevalidateSlotBeforeOrderCreation` (on submit calls the injected `revalidate` prop and blocks with a message on 409, EC-013).
- [ ] T036 [US2] Implement `easyeat/frontend/src/components/operations/SlotCapacityGrid.tsx` (turns T034 GREEN, includes the bulk-apply action as a single task deliverable; count interactions against SC-005 ≤ 5: pick date → enter value → apply all → save).
- [ ] T037 [US2] Implement `easyeat/frontend/src/components/operations/DinerSlotSelector.tsx` (standalone, props-driven; turns T035 GREEN).
- [ ] T038 [US2] Mount `SlotCapacityGrid` in `easyeat/frontend/src/pages/AdminOperationsPage.tsx` and extend `easyeat/frontend/src/test/operations/AdminOperationsPage.test.tsx` accordingly.

**Checkpoint**: US2 delivers atomic capacity control; integrates with US1 slots.

---

## Phase 5: User Story 3 — Online Payment Method Toggles (ADM-2.2.3) (Priority: P2)

**Goal**: Admin toggles Mercado Pago / Stripe visibility; checkout shows only enabled methods and revalidates at submit. Offline, boolean flag only.
**Independent Test**: disabling STRIPE hides it for the diner; validate-payment returns 409 for a disabled method; all disabled ⇒ message and disabled confirm.

### Tests (RED first)

- [ ] T039 [US3] Add the US3 nested class to `easyeat/backend/src/test/java/com/easyeat/operations/domain/OperatingInvariantsUnitTest.java`: `shouldExposeOnlyEnabledPaymentMethods` / `shouldHideDisabledPaymentMethod` / `shouldShowReenabledPaymentMethod` (INV-4), `shouldRejectConfirmationIfPaymentMethodDisabled` (INV-9, EC-010), `shouldOperateOfflineWithoutExternalGateways` (no gateway SDK or credential class on the classpath of `com.easyeat.operations`, A-005). Record RED.

### Implementation — service

- [ ] T040 [US3] Implement `easyeat/backend/src/main/java/com/easyeat/operations/service/PaymentMethodService.java` (list admin view, toggle with version check, diner list = enabled only, `validateEnabled(method)` throwing `PaymentMethodUnavailableException` in `easyeat/backend/src/main/java/com/easyeat/operations/exception/PaymentMethodUnavailableException.java`). Turn T039 GREEN.

### Tests / implementation — endpoints

- [ ] T041 [US3] Add the payment section to `easyeat/backend/src/test/java/com/easyeat/operations/controller/AdminOperationsControllerTest.java`: 401/403, toggle persists, stale version → 409, diner `GET …/payment-methods` returns only enabled, `POST …/checkout/validate-payment` → 200 when enabled and 409 "Método de pago no disponible" when disabled, unknown method → 400. Record RED.
- [ ] T042 [P] [US3] Create DTOs `easyeat/backend/src/main/java/com/easyeat/operations/dto/TogglePaymentMethodRequest.java`, `PaymentMethodResponse.java`, `DinerPaymentMethodResponse.java`.
- [ ] T043 [US3] Create `easyeat/backend/src/main/java/com/easyeat/operations/controller/AdminPaymentMethodController.java` (`GET /admin/payment-methods`, `PUT /admin/payment-methods/{method}`) and `easyeat/backend/src/main/java/com/easyeat/operations/controller/DinerPaymentMethodController.java` (`GET /establishments/{id}/payment-methods`, `POST /establishments/{id}/checkout/validate-payment`); map `PaymentMethodUnavailableException` → 409 in `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java`. Turn T041 GREEN.

### Frontend (tests then components)

- [ ] T044 [P] [US3] Write RED `easyeat/frontend/src/test/operations/PaymentMethodToggles.test.tsx`: two switches reflect state, toggle calls `onToggle(method, enabled, version)`, shows conflict message on 409.
- [ ] T045 [P] [US3] Write RED `easyeat/frontend/src/test/operations/CheckoutPaymentOptions.test.tsx`: disabled methods not rendered (INV-4), `shouldRenderAllDisabledMessage` with the confirm button disabled (EC-009), `shouldShowNewlyEnabledMethodOnRefresh` (EC-011), revalidation rejection message on submit (INV-9).
- [ ] T046 [US3] Implement `easyeat/frontend/src/components/operations/PaymentMethodToggles.tsx` (T044 GREEN) and `easyeat/frontend/src/components/operations/CheckoutPaymentOptions.tsx` (T045 GREEN; props-driven, no checkout page created).
- [ ] T047 [US3] Mount `PaymentMethodToggles` in `easyeat/frontend/src/pages/AdminOperationsPage.tsx` and extend `easyeat/frontend/src/test/operations/AdminOperationsPage.test.tsx`.

**Checkpoint**: all three stories complete and independently testable.

---

## Phase 6: Wire-up documentation & final verification

- [ ] T048 Write the single wire-up document `specs/010-restaurant-operation-config/wire-up.md` (no code, **no orders module or stub**): exact call sites for the future order-approval flow (`consumeSlot` inside the approval transaction, A-002/A-003), slot-selector revalidation on submit (`DinerSlotSelector` `revalidate` prop → time-slots + `consumeSlot` 409), and checkout (`CheckoutPaymentOptions` + `POST …/validate-payment` before confirming, A-004), including error-handling expectations (409 mapping).
- [ ] T049 Run the full backend suite with the project's JDK 21/Maven invocation and the frontend suite (`npm run test` in `easyeat/frontend`); record results.
- [ ] T050 Execute quickstart Scenarios 1–7 from `specs/010-restaurant-operation-config/quickstart.md` (the quickstart's `-Dtest=*OperationConfig*` filter does not match these class names — use `-Dtest='com.easyeat.operations.**'` and fix the quickstart line if confirmed) and record evidence in `specs/010-restaurant-operation-config/quickstart.md` or a `verification.md` beside it.
- [ ] T051 Produce the traceability check `specs/010-restaurant-operation-config/verification.md`: map INV-1..INV-10, EC-001..EC-018 and SC-001..SC-011 to the passing tests/scenarios above (use plan.md §10); explicitly confirm SC-005 (≤ 5 interactions via bulk apply) and that no gateway SDK/credential and no orders artifact was added.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → Phases 3, 4, 5 → Phase 6.
- Foundational internal order: T003 → T004; T006–T012 [P] after T004; T013 → T014; T015/T016 [P] anytime.
- US1 is the MVP. US2 depends on US1 only for `DinerTimeSlotService` (T018) and the page (T025); US3 is independent of US1/US2 on the backend and only shares `AdminOperationsPage.tsx`, `AdminOperationsControllerTest.java`, `OperatingInvariantsUnitTest.java` and `GlobalExceptionHandler.java` (same-file edits are therefore sequential, not [P]).
- Within each story: test (RED) → service → controller test (RED) → controller → frontend test (RED) → component.

## Parallel Examples

- Foundational: T006–T012 together; T015 + T016 together.
- US1: T023 and T020 in parallel; US2: T034 and T035 in parallel; US3: T042, T044 and T045 in parallel.
- After Foundational, US3 backend (T039–T043) can run in parallel with US1/US2 backend by a different developer, serializing only the shared files above.

## Implementation Strategy

1. Foundational → US1 (MVP: schedules + diner slots) → validate.
2. US2 (capacity, concurrency) → validate.
3. US3 (payments) → validate.
4. Wire-up doc, quickstart and traceability.

Delivery conventions: one atomic commit per task (RED observed before the implementation commit), English artifacts, no co-author trailers, PR to the spec branch.

## Summary

- Total tasks: 51 (Setup 2, Foundational 14, US1 10, US2 12, US3 9, Final 4).
