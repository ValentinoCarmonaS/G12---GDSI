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
- [x] T005 [P] Update `.specify/memory/migrations.md`: add the `V12 | V12__restaurant_operation_config.sql | 010` row (rule 3), noting V10/V11 arrive from `develop/sprint-3`. **DONE 2026-11-01** — V12 row added; V10/V11 noted as living on `develop/sprint-3`.
- [x] T006 [P] Create enum `easyeat/backend/src/main/java/com/easyeat/operations/domain/PaymentMethod.java` (`MERCADO_PAGO`, `STRIPE`) and value object `easyeat/backend/src/main/java/com/easyeat/operations/domain/TimeSlot.java` (`slotDate`, `startTime`, derived `endTime = startTime + 15 min`, semi-open `[start,end)`). **DONE 2026-11-01** — Test added in the same commit (PaymentMethodAndTimeSlotTest); RED: compilation failure, then GREEN 6 of 6. TimeSlot also rejects a start that would wrap past midnight, mirroring the CHECK start_time < end_time.
- [x] T007 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentOperatingSchedule.java` with `@Version Long version`, `DayOfWeek`, `LocalTime openingTime/closingTime`, `isClosed`. **DONE 2026-11-01** — Test EstablishmentOperatingScheduleTest added in the same commit (RED: compilation failure; GREEN 3 of 3) together with the shared OperationsPostgresTestBase (one container for the module's persistence tests). Entity is a plain mapped holder following Establishment's style; INV-7 stays in DTO + DDL per plan §7.
- [x] T008 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentSlotCapacity.java` with `@Version`, `maxCapacity`, `approvedOrdersCount`, `slotDate`, `startTime`, `endTime`. **DONE 2026-11-01** — Test EstablishmentSlotCapacityTest added in the same commit (RED: compilation failure; GREEN 5 of 5). Entity guards INV-6 locally and exposes the INV-3 sold-out projection; the atomic increment stays in the repository (T029).
- [x] T009 [P] Create JPA entity `easyeat/backend/src/main/java/com/easyeat/operations/domain/EstablishmentPaymentMethod.java` with `@Version`, `PaymentMethod` (`@Enumerated(STRING)`), `isEnabled`. No bank/credential columns (A-005). **DONE 2026-11-01** — Test EstablishmentPaymentMethodTest added in the same commit (RED: compilation failure; GREEN 3 of 3). Flag only, no credential columns (A-005).
- [x] T010 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/OperatingScheduleRepository.java` (find by establishment; find by establishment + day). **DONE 2026-11-01** — Test OperatingScheduleRepositoryTest added in the same commit (RED: compilation failure; GREEN 2 of 2).
- [x] T011 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/SlotCapacityRepository.java` (find by establishment + date range; find by establishment + date + startTime). The atomic UPDATE is added in T029, not here. **DONE 2026-11-01** — Test SlotCapacityRepositoryTest added in the same commit (RED: compilation failure; GREEN 2 of 2). The atomic UPDATE is intentionally left to T029.
- [x] T012 [P] Create repository `easyeat/backend/src/main/java/com/easyeat/operations/repository/PaymentMethodRepository.java` (find by establishment; find by establishment + method). **DONE 2026-11-01** — Test PaymentMethodRepositoryTest added in the same commit (RED: compilation failure; GREEN 2 of 2).
- [x] T013 Write RED test `easyeat/backend/src/test/java/com/easyeat/operations/service/EstablishmentTimeProviderTest.java`: with a fixed `Clock` and a non-default `Establishment.timezone`, `nowAt(establishmentId)` returns the zoned instant of *that* timezone (EC-014); there is no overload accepting a client-supplied time (EC-018). **DONE 2026-11-01** — RED observed: test did not compile (EstablishmentTimeProvider absent); 5 tests written.
- [x] T014 Create `easyeat/backend/src/main/java/com/easyeat/operations/service/EstablishmentTimeProvider.java`: injects the existing `Clock` bean from `easyeat/backend/src/main/java/com/easyeat/config/ClockConfig.java` and `EstablishmentRepository`, resolves `ZoneId.of(Establishment.timezone)`; the **only** time authority for this module (depends on T013 RED). Do not add a second `Clock` bean. **DONE 2026-11-01** — GREEN: 5 of 5 passing. Reuses the existing Clock bean from ClockConfig (Clock.systemUTC()); no second Clock bean was created.
- [x] T015 [P] Create frontend types `easyeat/frontend/src/types/operations.ts` (schedule, slot, capacity, payment-method DTOs matching `contracts/openapi.yaml`, incl. `version`). **DONE 2026-11-01** — Types mirror contracts/openapi.yaml. No unit test: a type-only module has no runtime behavior (excluded from coverage by src/types/**); verified with `npm run lint` (tsc --noEmit) and exercised by the T016 client test.
- [x] T016 [P] Create typed client `easyeat/frontend/src/api/operationsApi.ts` using `easyeat/frontend/src/api/apiClient.ts` for every path in `contracts/openapi.yaml` (admin schedules / slot-capacities / payment-methods, diner time-slots / payment-methods, validate-payment). **DONE 2026-11-01** — Test added in the same commit at easyeat/frontend/tests/api/operationsApi.test.ts (the repo's location for API client tests, rather than src/test/operations/); RED: import unresolved, GREEN 10 of 10; tsc clean.

**Checkpoint**: schema, entities, repositories, time authority and client types ready.

---

## Phase 3: User Story 1 — Business Schedules (ADM-2.2.1) (Priority: P1) 🎯 MVP

**Goal**: Admin configures weekly opening/closing; the diner only gets admissible 15-min slots.
**Independent Test**: With a fixed Clock: 15:59:59 admits, 16:00:00 and 16:01 block today, 22:00 admits tomorrow 13:00, unconfigured day is closed; PUT/GET schedules honour RBAC, 400 and 409.

### Tests (RED first)

- [x] T017 [US1] Create `easyeat/backend/src/test/java/com/easyeat/operations/domain/OperatingInvariantsUnitTest.java` (shared by all stories, one nested class per story to avoid edit conflicts) with the US1 nested class, fixed `Clock`: `shouldBlockAtExactClosingMinute` / `shouldBlockAt1601` (INV-1, EC-001), 15:59:59 admitted, `shouldBlockBeforeOpening`, `shouldAdmitFutureReservationWhenClosedToday` (22:00 → tomorrow 13:00, EC-017), `shouldPartitionDayIntoDeterministic15MinSlots` (12:00–14:00 → 8 slots, INV-5, EC-015), `shouldRejectOpeningEqualOrAfterClosing` (INV-7), `shouldTreatUnconfiguredDayAsClosed` (EC-008), `shouldApplyScheduleRulesToAllFulfillmentTypes` (INV-10), `shouldEvaluateAgainstEstablishmentTimezone` (EC-014), `shouldIgnoreClientClockDrift` (EC-018), `shouldAllowApprovalAfterClosingIfAdmittedBefore` (EC-002). Run and record RED.
  - **DONE 2026-11-02**: RED observed as a compilation failure (DinerTimeSlotService, OperatingScheduleService, ScheduleChange and InvalidScheduleException do not exist yet; 14 tests written). Decisions: a slot of today expires when it ends (`now < start + 15 min`), so 15:59:59 still admits 15:45-16:00 and 16:00:00 blocks; `DinerTimeSlotService` returns an `Availability` value (the DTO mapping belongs to T020); a closed day is not INV-7-validated, matching `chk_schedule_times`. EC-002 and INV-10 are asserted structurally (no orders module exists, so the service exposes no approval hook and no fulfillment parameter).

### Implementation — services

- [x] T018 [US1] Implement `easyeat/backend/src/main/java/com/easyeat/operations/service/OperatingScheduleService.java` (upsert week with INV-7 check, unconfigured day ⇒ closed, optimistic version handling) and `easyeat/backend/src/main/java/com/easyeat/operations/service/DinerTimeSlotService.java` (15-min partition over `[opening, closing)`, drops expired slots of today using `EstablishmentTimeProvider`, admits future dates, same rule for pickup and delivery). Turn T017 GREEN.
  - **DONE 2026-11-02**: T017 GREEN (16 of 16) plus `OperatingScheduleServiceTest` (6 of 6). Per-class coverage: DinerTimeSlotService 100%, OperatingScheduleService 100%, InvalidScheduleException 100%, OperatingConfigConflictException 100%. Decisions: only configured days are stored and returned (the contract makes `id` and `version` required, so an unconfigured day is not synthesised; consumers read a missing day as closed, EC-008); a stale or missing `expectedVersion` raises `OperatingConfigConflictException` (mapped to 409 in T022) and a write race at flush is mapped to the same exception; the slot rule is `start >= opening and start < closing`, as in FR-004.

### Tests — controllers (RED first)

- [x] T019 [US1] Create `easyeat/backend/src/test/java/com/easyeat/operations/controller/AdminOperationsControllerTest.java` (MockMvc, schedule section; later stories add sections): unauthenticated → 401, non-admin → 403, `shouldSaveSchedule` 200, opening ≥ closing → 400 ProblemDetail "Parámetros de horario inválidos", stale `version` → 409 "Conflicto de concurrencia" (`shouldFailOnOptimisticLockConflict`, EC-012), GET returns the week with unconfigured days closed. Also a diner `GET /time-slots` test (public/diner access, only admissible slots). Run RED.
  - **DONE 2026-11-02**: RED observed as a compilation failure (AdminOperatingScheduleController and DinerTimeSlotController do not exist; 14 tests written). The web test imports the real services over mocked repositories and `EstablishmentTimeProvider`, so the 400 and 409 are produced end to end. Corrections: `GET /time-slots` requires a JWT of any role (401 without one), following the OpenAPI contract instead of the word "public" in this task; the JSON keys `isClosed` are asserted exactly, with `closed` absent; GET /operating-schedules returns only configured days because the contract makes `id` and `version` required, a missing day is closed (EC-008) and the form fills it in.

### Implementation — endpoints

- [x] T020 [P] [US1] Create DTOs `easyeat/backend/src/main/java/com/easyeat/operations/dto/UpdateScheduleRequest.java`, `OperatingScheduleResponse.java`, `DinerTimeSlotsResponse.java` (Bean Validation + cross-field INV-7 check).
  - **DONE 2026-11-02**: compiles; exercised by T019, which turns GREEN in T022. Correction: the cross-field INV-7 check is not duplicated in the DTO, `OperatingScheduleService` is the only place that applies it because it also knows whether the day is closed (a closed day keeps no meaningful times, as `chk_schedule_times` allows). `isClosed` is a record component so the JSON key is exactly the contract name; times are written as `HH:mm:ss`.
- [x] T021 [US1] Create `easyeat/backend/src/main/java/com/easyeat/operations/controller/AdminOperatingScheduleController.java` (`PUT`/`GET /api/v1/admin/operating-schedules`, admin-only `@PreAuthorize` like `AdminIngredientController`, tenant from `TenantContextHolder`) and `easyeat/backend/src/main/java/com/easyeat/operations/controller/DinerTimeSlotController.java` (`GET /api/v1/establishments/{tenantId}/time-slots`).
  - **DONE 2026-11-02**: T019 now 11 of 13 passing; the two remaining (400 INV-7 and 409 EC-012) need the handlers of T022. Admin controller is gated by `@PreAuthorize` like `AdminIngredientController` and takes the tenant from `TenantContextHolder.getRequiredTenantId()`; the diner controller has no `@PreAuthorize`, `SecurityConfig` already requires authentication for any role (contract).
- [x] T022 [US1] Add RFC 7807 mapping for INV-7 (400) and optimistic-lock (409) for the operations controllers in `easyeat/backend/src/main/java/com/easyeat/auth/controller/GlobalExceptionHandler.java` (respect the comment near line 431; scope by exception type/package if required). Turn T019 GREEN.
  - **DONE 2026-11-02**: T019 GREEN (13 of 13, plus 16 of 16 and 6 of 6 from earlier tasks). Per-class coverage: AdminOperatingScheduleController 100%, DinerTimeSlotController 100%, services and exceptions 100%. The 409 is not a global `ObjectOptimisticLockingFailureException` handler: the service translates it into `OperatingConfigConflictException` and `GlobalExceptionHandler` gets a dedicated "Spec 010" section with two handlers (400 "Parámetros de horario inválidos", 409 "Conflicto de concurrencia"), leaving the spec 007 comment valid. DTO records are excluded from the JaCoCo report, they are covered through the controller test.

### Frontend (tests then components)

- [x] T023 [P] [US1] Write RED `easyeat/frontend/src/test/operations/WeeklyScheduleManager.test.tsx` (props/mocks only): seven day rows, closed toggle, blocks submit when opening ≥ closing with message, surfaces a 409 "reload" message.
  - **DONE 2026-11-02**: RED observed, the suite fails to load (0 of 7 tests run, `WeeklyScheduleManager` does not exist). Contract the test fixes: props `schedules`, `onSave(changes)`, optional `onReload`; only changed days are sent, an unconfigured day goes with `expectedVersion: null`; times are shown as `HH:mm` and sent as `HH:mm:ss`.
- [x] T024 [US1] Implement `easyeat/frontend/src/components/operations/WeeklyScheduleManager.tsx` (turns T023 GREEN).
  - **DONE 2026-11-02**: T023 GREEN (7 of 7); `tsc --noEmit` clean. Coverage of `WeeklyScheduleManager.tsx`: statements 100%, branches 97.5%, functions 100%, lines 100%. An unconfigured day starts closed with 09:00-17:00 as the values it takes if the admin opens it; the save button stays disabled until a day changes.
- [x] T025 [US1] Create `easyeat/frontend/src/pages/AdminOperationsPage.tsx` (hosts WeeklyScheduleManager now; capacity and payment sections added in US2/US3 tasks), register route `/admin/operations` in `easyeat/frontend/src/router/AppRoutes.tsx` under the same `RoleRouteGuard` as the other `/admin/*` routes **before** the `/admin/*` catch-all, and add a navigation `Link` in `easyeat/frontend/src/pages/AdminWorkspacePage.tsx`.
  - **DONE 2026-11-02**: T026 GREEN (7 of 7) and WeeklyScheduleManager 7 of 7; `tsc --noEmit` clean; existing `AdminWorkspacePage` and router suites still pass (85 of 85). Coverage: AdminOperationsPage.tsx and WeeklyScheduleManager.tsx statements 100%, branches 98%. The route is declared before the `/admin/*` catch-all under `RoleRouteGuard allowedRoles={[ADMINISTRADOR]}`, and the hub gets a card linking to it.
- [x] T026 [P] [US1] Write `easyeat/frontend/src/test/operations/AdminOperationsPage.test.tsx` (renders schedule section with mocked `operationsApi`, route guarded for ADMIN).
  - **DONE 2026-11-02**: written and committed before T025 so that TDD holds (the page is the unit under test); RED observed, the suite fails to load (0 of 8 tests run, `AdminOperationsPage` does not exist; the COMENSAL case was dropped afterwards, 7 remain). Covers the page with a mocked `operationsApi`, the hub link, the `/admin/operations` route for ADMINISTRADOR (and that the `/admin/*` hub does not answer first) and its refusal for CHEF and ENCARGADO (COMENSAL would land on the catalog, which needs a TenantProvider unrelated to this feature).

**Checkpoint**: US1 independently deliverable (MVP).

---

## Phase 4: User Story 2 — Slot Capacity (ADM-2.2.2) (Priority: P1)

**Goal**: Per-15-min capacity, atomic consumption, "Agotada" projection, non-retroactive reductions.
**Independent Test**: 10 threads on the last slot → exactly 1 wins, 9 get 409; 50→30 with 40 approved leaves approved untouched and slot sold out.

### Tests (RED first)

- [x] T027 [US2] Add the US2 nested class to `easyeat/backend/src/test/java/com/easyeat/operations/domain/OperatingInvariantsUnitTest.java`: `shouldAdmitOnlyWhenApprovedBelowCapacity` (INV-2), `shouldMarkSoldOutWhenCapacityReachedOrExceeded` / `shouldMarkSoldOutAtOrder50` (INV-3), `shouldRejectNegativeCapacity` (INV-6), `shouldPreserveApprovedOrdersWhenCapacityReduced` (INV-8), `shouldReopenSlotWhenCapacityIncreased` (EC-004), `shouldTreatZeroCapacityAsPermanentlySoldOut` (EC-006), `shouldDefaultUnconfiguredSlotToZeroCapacity` (EC-007), `shouldPreservePendingOrdersOnScheduleChange` (EC-003). Record RED.
  - **DONE 2026-11-02**: RED observed as a compilation failure (SlotCapacityService, SlotCapacityChange, InvalidCapacityException, SlotSoldOutException and the atomic repository method do not exist; 11 tests written). Decisions: admission by capacity (INV-2) is `consumeSlot` over one conditional UPDATE, so the unit test fixes its contract with a mocked repository and the real atomicity is proven in T028; a start not aligned to 15 minutes is rejected with the same "Capacidad inválida" family (INV-5); EC-003 is asserted structurally (`OperatingScheduleService` has no `SlotCapacityRepository` dependency, there is no orders module).
- [x] T028 [US2] Create `easyeat/backend/src/test/java/com/easyeat/operations/repository/SlotCapacityConcurrencyTest.java` (Testcontainers PostgreSQL 16, modeled on `easyeat/backend/src/test/java/com/easyeat/inventory/concurrency/StockAdjustmentConcurrencyTest.java`): `shouldPreventOvercapacityOnOrder50` (49/50 approved, 10 threads → exactly 1 success, 9 × 409, final count 50, EC-016) and `shouldHandleCapacityReduction` (50→30 with 40 approved → count stays 40, slot sold out, nothing else mutated, EC-005). Record RED.
  - **DONE 2026-11-02**: RED observed as a compilation failure (SlotCapacityService, SlotCapacityChange and SlotSoldOutException do not exist; 3 tests written). It extends `OperationsPostgresTestBase` (the module shared PostgreSQL 16 container) instead of declaring its own container as `StockAdjustmentConcurrencyTest` does, to stay consistent with the other operations persistence tests. A third test was added beyond the task text: `shouldNotOverwriteApprovedCountOnConcurrentCapacityEdit`, because an admin edit that races with an approval would write back a stale `approved_orders_count` unless the entity updates only the changed columns (`@DynamicUpdate`, delivered with T030).

### Implementation — services

- [x] T029 [US2] Add the atomic conditional update to `easyeat/backend/src/main/java/com/easyeat/operations/repository/SlotCapacityRepository.java`: `@Modifying @Query("UPDATE … SET approvedOrdersCount = approvedOrdersCount + 1 WHERE establishment = :e AND slotDate = :d AND startTime = :t AND approvedOrdersCount < maxCapacity")` returning affected rows.
  - **DONE 2026-11-02**: `SlotCapacityRepositoryTest` gained 4 tests for the conditional update (below capacity, at capacity, capacity 0 or no row, only the requested slot). The first run with the query in place failed 4 of 4 with "no transaction" for a `@Modifying` call, fixed with `@Transactional` on the method; final 6 of 6. The statement does not bump `version`: the version guards the admin edits, and an approval must not make an admin save fail. The T027/T028 classes were set aside while running it because they still do not compile until T030.
- [x] T030 [US2] Implement `easyeat/backend/src/main/java/com/easyeat/operations/service/SlotCapacityService.java`: list/upsert capacities (INV-6, never touches `approvedOrdersCount`, INV-8), `isSoldOut` projection (unconfigured ⇒ capacity 0), and `consumeSlot(...)` using T029 (0 rows ⇒ slot-exhausted exception). Create `easyeat/backend/src/main/java/com/easyeat/operations/exception/SlotSoldOutException.java`. Turn T027/T028 GREEN. Extend `DinerTimeSlotService` so each slot carries `SOLD_OUT`/`AVAILABLE` status.
  - **DONE 2026-11-02**: T027 GREEN (11 of 11), T028 GREEN (3 of 3 against PostgreSQL 16, exactly 1 winner and 9 `SlotSoldOutException` out of 10 threads), plus `SlotCapacityServiceTest` (6 of 6). Per-class coverage: SlotCapacityService 100%, DinerTimeSlotService 99.5%, SlotSoldOutException and InvalidCapacityException 100%. Corrections: `EstablishmentSlotCapacity` got `@DynamicUpdate`, without it an admin edit rewrites `approved_orders_count` from a stale read and loses a concurrent approval (INV-8; proven by the third T028 test); `DinerTimeSlotService.Availability` now carries the sold-out set and a slot without a capacity row is `SOLD_OUT` (EC-007), so the US1 unit and controller tests were updated to supply capacity rows and the new constructor argument; `GET` matrix rows for unconfigured slots have `id` and `version` null, as decided in the interview (openapi amended in T032).

### Tests / implementation — endpoints

- [x] T031 [US2] Add the capacity section to `easyeat/backend/src/test/java/com/easyeat/operations/controller/AdminOperationsControllerTest.java`: 401/403 RBAC, `shouldUpdateCapacity`, negative capacity → 400 "Capacidad inválida", stale version → 409, list upsert accepts many slots in one PUT, diner time-slots show `SOLD_OUT`. Record RED.
  - **DONE 2026-11-02**: RED observed as a compilation failure (AdminSlotCapacityController and GlobalExceptionHandler.handleSlotSoldOut do not exist; 13 tests written in a new `Capacity` nested class). Beyond the task text: the day matrix GET, non-integer capacity (FR-007), a start not on a 15-minute boundary (INV-5), a missing field, and a direct test of the `SlotSoldOutException` mapping because no endpoint consumes a slot (there is no orders module).
- [x] T032 [P] [US2] Create DTOs `easyeat/backend/src/main/java/com/easyeat/operations/dto/UpdateSlotCapacityRequest.java` (list contract, `@Min(0)`) and `easyeat/backend/src/main/java/com/easyeat/operations/dto/SlotCapacityAdminResponse.java`.
  - **DONE 2026-11-02**: compiles; exercised by T031, which turns GREEN in T033. Corrections: no `@Min(0)` on the request, a negative capacity must reach the service so it answers with the contract problem "Capacidad inválida" (a Bean Validation failure would carry the generic validation title); the strict `@JsonFormat(lenient = FALSE)` makes a non-integer a 400 (FR-007). `contracts/openapi.yaml` amended: `SlotCapacityAdminResponse.id` and `.version` stay required but are `nullable`, as decided in the interview, so the admin matrix can list unconfigured slots.
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
