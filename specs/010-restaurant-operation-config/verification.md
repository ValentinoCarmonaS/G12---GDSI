# Verification & Traceability: Restaurant Operation Configuration

**Feature**: `010-restaurant-operation-config` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) §10 | **Quickstart**: [quickstart.md](./quickstart.md) | **Wire-up**: [wire-up.md](./wire-up.md)

This maps every invariant, edge case and success criterion of the spec to the tests that currently pass. Test names are the real ones in the repository; where plan §10 used a different name the real one is listed here.

## 1. Results of the runs

| Run | Command | Result |
|---|---|---|
| Backend, whole suite | `mvn verify -Dapi.version=1.43` (JDK 21, Maven 3.9.9 wrapper, Docker for Testcontainers PostgreSQL 16) | 1190 tests, 0 failures, 0 errors, 0 skipped, coverage checks met |
| Frontend, whole suite | `npm run test` in `easyeat/frontend` | 98 files, 650 tests, 0 failures |
| Frontend types | `npm run lint` (`tsc --noEmit`) | clean |
| Quickstart scenarios 1-7 | `-Dtest=QuickstartScenariosTest` | 7 of 7, full Spring context, real endpoints and security, PostgreSQL 16, movable server clock |

Coverage of `com.easyeat.operations` (JaCoCo, per class): 24 of 25 classes at 100%; the lowest is `EstablishmentSlotCapacity` at 94.8%. Frontend operations components and page: statements 99.3%, branches 96.3%.

Abbreviations: **OIU** = `OperatingInvariantsUnitTest` (nested `BusinessSchedules`, `SlotCapacity`, `PaymentMethodToggles`); **AOC** = `AdminOperationsControllerTest` (nested `Schedules`, `DinerTimeSlots`, `Capacity`, `Payments`); **SCC** = `SlotCapacityConcurrencyTest`; **QS** = `QuickstartScenariosTest`; **SCS** = `SlotCapacityServiceTest`; **OSS** = `OperatingScheduleServiceTest`; **PMS** = `PaymentMethodServiceTest`; **SCR** = `SlotCapacityRepositoryTest`. Frontend tests are under `easyeat/frontend/src/test/operations/`.

## 2. Invariants

| ID | Verified by |
|---|---|
| INV-1 | OIU `shouldBlockAtExactClosingMinute`, `shouldBlockAt1601`, `shouldAdmitUntilTheLastSecondBeforeClosing`, `shouldBlockBeforeOpening`, `shouldNotAdmitASlotStartingAtClosing`, `shouldOfferNothingForAPastDate`; QS scenario 1 |
| INV-2 | OIU `shouldAdmitOnlyWhenApprovedBelowCapacity`; SCR `incrementsWhileBelowCapacity`, `doesNotIncrementAtCapacity`; SCC `shouldPreventOvercapacityOnOrder50`; QS scenarios 2 and 7 |
| INV-3 | OIU `shouldMarkSoldOutWhenCapacityReachedOrExceeded`, `shouldMarkSoldOutAtOrder50`; AOC `shouldShowSoldOutToDiner`; `DinerSlotSelector.test` `shouldRenderSoldOutBadge`; QS scenario 2 |
| INV-4 | OIU `shouldExposeOnlyEnabledPaymentMethods`, `shouldHideDisabledPaymentMethod`, `shouldShowReenabledPaymentMethod`; AOC `shouldReturnOnlyEnabledToDiner`; `CheckoutPaymentOptions.test` "renders only the methods it is given"; QS scenario 4 |
| INV-5 | OIU `shouldPartitionDayIntoDeterministic15MinSlots`, `shouldRejectMisalignedSlotStart`; AOC `shouldRejectMisalignedStart`; `PaymentMethodAndTimeSlotTest` (TimeSlot semi-open, 15 minutes, no wrap) |
| INV-6 | OIU `shouldRejectNegativeCapacity`; AOC `shouldRejectNegativeCapacity`, `shouldRejectNonIntegerCapacity`; `SlotCapacityGrid.test` "rejects a negative or non-integer capacity"; `EstablishmentSlotCapacityTest` (guards) |
| INV-7 | OIU `shouldRejectOpeningEqualOrAfterClosing`, `shouldNotValidateTimesOfAClosedDay`; AOC `shouldRejectOpeningAfterClosing`; `WeeklyScheduleManager.test` "blocks the submit and explains it"; DB `chk_schedule_times` (`OperationConfigMigrationTest`) |
| INV-8 | OIU `shouldPreserveApprovedOrdersWhenCapacityReduced`, `shouldPreservePendingOrdersOnScheduleChange`; SCC `shouldHandleCapacityReduction`, `shouldNotOverwriteApprovedCountOnConcurrentCapacityEdit`; QS scenario 3 |
| INV-9 | OIU `shouldRejectConfirmationIfPaymentMethodDisabled`; AOC `shouldRejectDisabledMethodAtConfirmation`; `CheckoutPaymentOptions.test` "revalidates the chosen method before confirming"; QS scenario 6 |
| INV-10 | OIU `shouldApplyScheduleRulesToAllFulfillmentTypes` (the service takes no fulfillment type) |

## 3. Edge cases

| ID | Verified by |
|---|---|
| EC-001 | OIU `shouldBlockAtExactClosingMinute` |
| EC-002 | OIU `shouldAllowApprovalAfterClosingIfAdmittedBefore` (plan §10 called it `shouldProcessApprovalAfterClosing`); wire-up.md §3 step 3 |
| EC-003 | OIU `shouldPreservePendingOrdersOnScheduleChange` |
| EC-004 | OIU `shouldReopenSlotWhenCapacityIncreased` |
| EC-005 | OIU `shouldPreserveApprovedOrdersWhenCapacityReduced`; SCC `shouldHandleCapacityReduction`; QS scenario 3 |
| EC-006 | OIU `shouldTreatZeroCapacityAsPermanentlySoldOut`; SCR `doesNotIncrementWithZeroOrNoCapacity` |
| EC-007 | OIU `shouldDefaultUnconfiguredSlotToZeroCapacity`; SCS `matrixSynthesizesUnconfiguredSlots`; AOC `shouldReturnDayMatrix`, `shouldShowSoldOutToDiner` |
| EC-008 | OIU `shouldTreatUnconfiguredDayAsClosed`; AOC `shouldReturnClosedForUnconfiguredDay`; `WeeklyScheduleManager.test` "renders the seven days" |
| EC-009 | OIU `shouldTreatMissingPaymentRowAsEnabled` (a missing row is never "all disabled"); AOC `shouldReturnEmptyListWhenAllDisabled`; `CheckoutPaymentOptions.test` `shouldRenderAllDisabledMessage`; QS scenario 5 |
| EC-010 | OIU `shouldRejectConfirmationIfPaymentMethodDisabled`; AOC `shouldRejectDisabledMethodAtConfirmation`; `CheckoutPaymentOptions.test` "a revalidation rejection blocks the order"; QS scenario 6 |
| EC-011 | `CheckoutPaymentOptions.test` `shouldShowNewlyEnabledMethodOnRefresh`; OIU `shouldShowReenabledPaymentMethod` |
| EC-012 | AOC `shouldFailOnOptimisticLockConflict`, Capacity `shouldFailOnStaleVersion`, Payments `shouldFailOnStaleVersion`; OSS `failsOnStaleVersion`; SCS `conflictsOnVersionMismatch`; PMS (write races); the 409 reload message in `WeeklyScheduleManager.test`, `SlotCapacityGrid.test`, `PaymentMethodToggles.test` |
| EC-013 | `DinerSlotSelector.test` `shouldRevalidateSlotBeforeOrderCreation` and "blocks with a message ... sold out meanwhile" |
| EC-014 | OIU `shouldEvaluateAgainstEstablishmentTimezone`; `EstablishmentTimeProviderTest` |
| EC-015 | OIU `shouldKeepAdjacentSlotsIndependent` (plan §10 called it `shouldHandleContiguousSlotsIndependently`); SCR `incrementsOnlyTheRequestedSlot` |
| EC-016 | SCC `shouldPreventOvercapacityOnOrder50` (10 threads, 1 winner, 9 `SlotSoldOutException`); QS scenario 7 (2 threads on place 50) |
| EC-017 | OIU `shouldAdmitFutureReservationWhenClosedToday`; QS scenario 1 |
| EC-018 | OIU `shouldIgnoreClientClockDrift` (no public method takes a client time); `EstablishmentTimeProviderTest` |

## 4. Success criteria

| ID | Verified by | Note |
|---|---|---|
| SC-001 | OIU `shouldBlockAt1601`, `shouldBlockAtExactClosingMinute`; QS scenario 1 | The module makes today unselectable (`isClosed`, no slots). The rejection of the order itself belongs to the future order flow, wire-up.md §2. |
| SC-002 | OIU `shouldBlockBeforeOpening`, `shouldNotAdmitASlotStartingAtClosing` | Same note as SC-001. |
| SC-003 | OIU `shouldMarkSoldOutAtOrder50`; QS scenario 2 (AVAILABLE at 49 of 50, SOLD_OUT at 50) | "Approving" is `consumeSlot`, wire-up.md §3. |
| SC-004 | OIU `shouldHideDisabledPaymentMethod`; AOC `shouldReturnOnlyEnabledToDiner`; QS scenario 4; no gateway is contacted (section 6) | |
| SC-005 | Section 5 | Met: at most 5 interactions. |
| SC-006 | QS scenarios 1, 4, 5, 6 read the new state in the very next request after the save; the diner endpoints compute from the database on every call and the module has no cache | Met by design. A wall-clock measurement of the 2 seconds was **not** taken: no timing test exists. |
| SC-007 | OIU `shouldRejectNegativeCapacity`; AOC `shouldRejectNegativeCapacity`, `shouldRejectNonIntegerCapacity`; `SlotCapacityGrid.test` "rejects a negative or non-integer capacity" | |
| SC-008 | OIU `shouldRejectOpeningEqualOrAfterClosing`; AOC `shouldRejectOpeningAfterClosing`; `WeeklyScheduleManager.test` "blocks the submit and explains it" | |
| SC-009 | AOC Schedules `shouldRejectInvalidTimeFormats` (25:00:00, 11:61:00, text and empty are a 400, nothing saved); `shouldRejectMissingFields`, `shouldRejectBadDate`; time inputs of the UI only produce `HH:mm` | The test was added in T051; no test covered this criterion before. |
| SC-010 | SCC `shouldPreventOvercapacityOnOrder50`, `shouldNotOverwriteApprovedCountOnConcurrentCapacityEdit`; the 409 tests of EC-012; `@Version` on the three entities | |
| SC-011 | SCC `shouldHandleCapacityReduction`; QS scenario 3 (`approvedOrdersCount` stays 40); OIU `shouldPreservePendingOrdersOnScheduleChange` | The order state itself is owned by the future order flow; this module never writes it. |

## 5. SC-005: at most 5 interactions

Counted on the tests that drive the real components (an interaction is one change of a field or one click):

| Task | Interactions | Evidence |
|---|---|---|
| Capacity of a whole day by bulk apply | 4: pick the date, enter the value, "Aplicar a todas", "Guardar capacidad" | `SlotCapacityGrid.test` "bulk apply: one value on every slot of the date is one onSave call with the full list" (value, apply, save) plus `asks for another date through onDateChange`; one `PUT` carries all slots (AOC `shouldAcceptManySlotsInOnePut`) |
| Same over a date range | 5: the above plus the "Hasta" date | `SlotCapacityGrid.test` "bulk apply over a date range loads each day and still sends a single list" |
| Opening hours of a day | 4 at most: uncheck closed, opening, closing, "Guardar horarios" | `WeeklyScheduleManager.test` "opens an unconfigured day with no expectedVersion and only sends the days that changed" |
| A payment method | 1: the switch saves immediately | `AdminOperationsPage.test` "US3: toggling sends the method, the flag and the version" |

## 6. Confirmations

- **No gateway SDK, credential or token**: neither `pom.xml` nor `package.json` mentions Stripe or Mercado Pago; OIU `shouldOperateOfflineWithoutExternalGateways` asserts the SDK classes are not on the classpath, that no class of `com.easyeat.operations` is named after credentials, tokens or gateways, and that the entity holds only the flag fields (A-005). The only occurrences of the names are the display labels and the `PaymentMethod` enum.
- **No orders artifact**: the feature adds no order, cart or approval class, endpoint, table or stub. `CheckoutPaymentOptions` and `DinerSlotSelector` are props-driven components that receive `revalidate`, `onConfirm` and `onRefresh` from the caller; the call sites of the future flow are written down in wire-up.md and nowhere else.
- **Scope of the changes outside the module**: `GlobalExceptionHandler` (a Spec 010 section, nothing existing changed), `AppRoutes` and `AdminWorkspacePage` (route and link), migration `V12`, the migration registry, and one assertion relaxed in spec 007's `AdminPricingMigrationTest`, approved by the user in Phase 2.

## 7. Divergences found and how they were resolved

| Divergence | Resolution |
|---|---|
| Quickstart scenario 1 expected an order to be rejected with a 400 at 16:01; there is no orders module | `quickstart.md` now states what the module returns (200, `isClosed`, no slots); the rejection is the order flow's job, recorded in wire-up.md §2 |
| Quickstart scenarios 4 and 5 sent a `PUT` without `expectedVersion`, which is a 409 on an establishment seeded by V12 | `quickstart.md` sends the version read from the `GET` |
| Quickstart scenario 7 named `SlotCapacityExceededException`; the class is `SlotSoldOutException` | Corrected in `quickstart.md` |
| The quickstart filter `-Dtest=*OperationConfig*` matches no class | Replaced by `com.easyeat.operations.**` and `QuickstartScenariosTest` |
| The global handler answers 422 to a failed Bean Validation of a single body; the contract documents 400 | The two payment request records reject a null in their constructor, so the answer is the contract's 400. The global handler was not changed. |
| OpenAPI `id` / `version` were required and non-null for rows that may not exist yet | Made `nullable` for the capacity matrix and the payment methods (still required) |

`spec.md` and `plan.md` were not modified.
