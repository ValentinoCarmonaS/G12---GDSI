package com.easyeat.operations.domain;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.exception.InvalidCapacityException;
import com.easyeat.operations.exception.InvalidScheduleException;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.exception.PaymentMethodUnavailableException;
import com.easyeat.operations.exception.SlotSoldOutException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.PaymentMethodRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import com.easyeat.operations.service.DinerTimeSlotService;
import com.easyeat.operations.service.DinerTimeSlotService.Availability;
import com.easyeat.operations.service.EstablishmentTimeProvider;
import com.easyeat.operations.service.OperatingScheduleService;
import com.easyeat.operations.service.PaymentMethodService;
import com.easyeat.operations.service.SlotCapacityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the operating invariants INV-1 to INV-10. Shared by the three stories of
 * spec 010, one nested class per story so that each phase edits its own block only.
 */
@DisplayName("Operating invariants (INV-1..INV-10)")
class OperatingInvariantsUnitTest {

    private static final UUID ESTABLISHMENT = UUID.randomUUID();
    private static final String BUENOS_AIRES = "America/Argentina/Buenos_Aires";
    /** Tuesday. */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate WEDNESDAY = TUESDAY.plusDays(1);
    private static final LocalDate THURSDAY = TUESDAY.plusDays(2);

    /** Buenos Aires is UTC-3 all year: local 15:59:59 is 18:59:59Z. */
    private static Instant buenosAires(LocalDate date, LocalTime local) {
        return date.atTime(local).atZone(ZoneId.of(BUENOS_AIRES)).toInstant();
    }

    @Nested
    @DisplayName("US1 - Business schedules (ADM-2.2.1)")
    class BusinessSchedules {

        private final OperatingScheduleRepository scheduleRepository = mock(OperatingScheduleRepository.class);
        private final EstablishmentRepository establishmentRepository = mock(EstablishmentRepository.class);
        private final SlotCapacityRepository capacityRepository = mock(SlotCapacityRepository.class);

        private DinerTimeSlotService serviceAt(Instant now, String timezone) {
            var establishment = new Establishment(ESTABLISHMENT, "Cafe", EstablishmentStatus.ACTIVE,
                    LocalTime.of(11, 0), LocalTime.of(23, 0), timezone);
            when(establishmentRepository.findById(ESTABLISHMENT)).thenReturn(Optional.of(establishment));
            var timeProvider = new EstablishmentTimeProvider(Clock.fixed(now, ZoneOffset.UTC), establishmentRepository);
            return new DinerTimeSlotService(scheduleRepository, capacityRepository, timeProvider);
        }

        private void schedule(DayOfWeek day, int openingHour, int closingHour) {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(ESTABLISHMENT, day)).thenReturn(Optional.of(
                    new EstablishmentOperatingSchedule(ESTABLISHMENT, day,
                            LocalTime.of(openingHour, 0), LocalTime.of(closingHour, 0), false)));
        }

        private void closedDay(DayOfWeek day) {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(ESTABLISHMENT, day)).thenReturn(Optional.of(
                    new EstablishmentOperatingSchedule(ESTABLISHMENT, day,
                            LocalTime.of(0, 0), LocalTime.of(0, 0), true)));
        }

        @Test
        @DisplayName("INV-1 / EC-001: at exactly the closing minute (16:00:00) today is blocked")
        void shouldBlockAtExactClosingMinute() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(16, 0)), BUENOS_AIRES);

            Availability today = service.availabilityFor(ESTABLISHMENT, TUESDAY);

            assertThat(today.closed()).isTrue();
            assertThat(today.slots()).isEmpty();
            assertThat(today.closureReason()).isEqualTo("Cocina cerrada para el día de hoy");
        }

        @Test
        @DisplayName("INV-1 / AC-1.1: at 16:01 with closing at 16:00 today is blocked")
        void shouldBlockAt1601() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(16, 1)), BUENOS_AIRES);

            Availability today = service.availabilityFor(ESTABLISHMENT, TUESDAY);

            assertThat(today.closed()).isTrue();
            assertThat(today.slots()).isEmpty();
        }

        @Test
        @DisplayName("INV-1: at 15:59:59 the last slot 15:45-16:00 is still admitted")
        void shouldAdmitUntilTheLastSecondBeforeClosing() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(15, 59, 59)), BUENOS_AIRES);

            Availability today = service.availabilityFor(ESTABLISHMENT, TUESDAY);

            assertThat(today.closed()).isFalse();
            assertThat(today.slots()).containsExactly(new TimeSlot(TUESDAY, LocalTime.of(15, 45)));
        }

        @Test
        @DisplayName("INV-1: a slot earlier than the opening time is rejected, the opening slot is admitted")
        void shouldBlockBeforeOpening() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(TUESDAY, LocalTime.of(10, 45)))).isFalse();
            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(TUESDAY, LocalTime.of(11, 0)))).isTrue();
            assertThat(service.availabilityFor(ESTABLISHMENT, TUESDAY).slots()).hasSize(20);
        }

        @Test
        @DisplayName("INV-1: the closing instant itself is not an admissible slot start (semi-open)")
        void shouldNotAdmitASlotStartingAtClosing() {
            schedule(DayOfWeek.WEDNESDAY, 12, 14);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(WEDNESDAY, LocalTime.of(13, 45)))).isTrue();
            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(WEDNESDAY, LocalTime.of(14, 0)))).isFalse();
        }

        @Test
        @DisplayName("EC-017: at 22:00 with the kitchen closed today, tomorrow 13:00 is admitted")
        void shouldAdmitFutureReservationWhenClosedToday() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            schedule(DayOfWeek.WEDNESDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(22, 0)), BUENOS_AIRES);

            assertThat(service.availabilityFor(ESTABLISHMENT, TUESDAY).closed()).isTrue();
            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(WEDNESDAY, LocalTime.of(13, 0)))).isTrue();
            assertThat(service.availabilityFor(ESTABLISHMENT, WEDNESDAY).slots())
                    .contains(new TimeSlot(WEDNESDAY, LocalTime.of(13, 0)));
        }

        @Test
        @DisplayName("INV-5 / EC-015: 12:00-14:00 is partitioned into 8 deterministic 15-minute slots")
        void shouldPartitionDayIntoDeterministic15MinSlots() {
            schedule(DayOfWeek.WEDNESDAY, 12, 14);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            List<TimeSlot> first = service.availabilityFor(ESTABLISHMENT, WEDNESDAY).slots();
            List<TimeSlot> second = service.availabilityFor(ESTABLISHMENT, WEDNESDAY).slots();

            assertThat(first).hasSize(8).isEqualTo(second);
            assertThat(first.get(0).startTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(first.get(7).endTime()).isEqualTo(LocalTime.of(14, 0));
            for (int i = 1; i < first.size(); i++) {
                assertThat(first.get(i).startTime()).isEqualTo(first.get(i - 1).endTime());
            }
        }

        @Test
        @DisplayName("INV-7: opening equal to or after closing is rejected")
        void shouldRejectOpeningEqualOrAfterClosing() {
            var service = new OperatingScheduleService(mock(OperatingScheduleRepository.class));
            var equal = new ScheduleChange(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(15, 0), false, null);
            var after = new ScheduleChange(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(15, 0), false, null);

            assertThatThrownBy(() -> service.saveWeek(ESTABLISHMENT, List.of(equal)))
                    .isInstanceOf(InvalidScheduleException.class);
            assertThatThrownBy(() -> service.saveWeek(ESTABLISHMENT, List.of(after)))
                    .isInstanceOf(InvalidScheduleException.class)
                    .hasMessage("La hora de apertura (17:00) debe ser estrictamente anterior a la hora de cierre (15:00).");
        }

        @Test
        @DisplayName("INV-7: a closed day keeps no meaningful times and is not validated")
        void shouldNotValidateTimesOfAClosedDay() {
            var repository = mock(OperatingScheduleRepository.class);
            when(repository.findByEstablishmentIdAndDayOfWeek(eq(ESTABLISHMENT), any())).thenReturn(Optional.empty());
            when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            var service = new OperatingScheduleService(repository);

            var closed = new ScheduleChange(DayOfWeek.SUNDAY, LocalTime.of(0, 0), LocalTime.of(0, 0), true, null);

            assertThat(service.saveWeek(ESTABLISHMENT, List.of(closed))).singleElement()
                    .satisfies(saved -> assertThat(saved.isClosed()).isTrue());
        }

        @Test
        @DisplayName("EC-008: a day with no configuration is closed")
        void shouldTreatUnconfiguredDayAsClosed() {
            when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(ESTABLISHMENT, DayOfWeek.THURSDAY))
                    .thenReturn(Optional.empty());
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            Availability thursday = service.availabilityFor(ESTABLISHMENT, THURSDAY);

            assertThat(thursday.closed()).isTrue();
            assertThat(thursday.slots()).isEmpty();
            assertThat(service.isAdmissible(ESTABLISHMENT, new TimeSlot(THURSDAY, LocalTime.of(13, 0)))).isFalse();
        }

        @Test
        @DisplayName("A day flagged as closed offers no slot even if it keeps times")
        void shouldOfferNothingOnAFlaggedClosedDay() {
            closedDay(DayOfWeek.WEDNESDAY);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            assertThat(service.availabilityFor(ESTABLISHMENT, WEDNESDAY).closed()).isTrue();
        }

        @Test
        @DisplayName("A date before today offers nothing")
        void shouldOfferNothingForAPastDate() {
            schedule(DayOfWeek.MONDAY, 11, 16);
            DinerTimeSlotService service = serviceAt(buenosAires(TUESDAY, LocalTime.of(9, 0)), BUENOS_AIRES);

            Availability yesterday = service.availabilityFor(ESTABLISHMENT, TUESDAY.minusDays(1));

            assertThat(yesterday.closed()).isTrue();
            assertThat(yesterday.slots()).isEmpty();
        }

        @Test
        @DisplayName("INV-10: the rule takes no fulfillment type, so pickup and delivery share it")
        void shouldApplyScheduleRulesToAllFulfillmentTypes() {
            Set<String> parameterTypes = Arrays.stream(DinerTimeSlotService.class.getDeclaredMethods())
                    .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                    .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                    .map(Class::getSimpleName)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(parameterTypes).containsOnly("UUID", "LocalDate", "TimeSlot");
        }

        @Test
        @DisplayName("EC-014: the same instant is judged in the establishment's own timezone")
        void shouldEvaluateAgainstEstablishmentTimezone() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            // 18:30Z is 15:30 in Buenos Aires (kitchen open) but 19:30 in Madrid (past closing)
            Instant instant = Instant.parse("2026-11-10T18:30:00Z");

            Availability buenosAiresToday = serviceAt(instant, BUENOS_AIRES).availabilityFor(ESTABLISHMENT, TUESDAY);
            assertThat(buenosAiresToday.closed()).isFalse();
            assertThat(buenosAiresToday.slots()).extracting(TimeSlot::startTime)
                    .containsExactly(LocalTime.of(15, 30), LocalTime.of(15, 45));

            Availability madridToday = serviceAt(instant, "Europe/Madrid").availabilityFor(ESTABLISHMENT, TUESDAY);
            assertThat(madridToday.timezone()).isEqualTo(ZoneId.of("Europe/Madrid"));
            assertThat(madridToday.closed()).isTrue();
        }

        @Test
        @DisplayName("EC-018: no public method takes a client-supplied instant, so client clock drift is ignored")
        void shouldIgnoreClientClockDrift() {
            boolean acceptsClientTime = Arrays.stream(DinerTimeSlotService.class.getDeclaredMethods())
                    .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                    .map(Method::getParameterTypes)
                    .flatMap(Arrays::stream)
                    .anyMatch(type -> type == Instant.class || type == LocalTime.class
                            || type == java.time.ZonedDateTime.class || type == java.time.LocalDateTime.class
                            || type == Clock.class);

            assertThat(acceptsClientTime).isFalse();
        }

        @Test
        @DisplayName("EC-002: an order admitted at 15:58 is not re-checked on approval after closing; only new entries are blocked")
        void shouldAllowApprovalAfterClosingIfAdmittedBefore() {
            schedule(DayOfWeek.TUESDAY, 11, 16);
            var lastSlot = new TimeSlot(TUESDAY, LocalTime.of(15, 45));

            boolean admittedAt1558 = serviceAt(buenosAires(TUESDAY, LocalTime.of(15, 58)), BUENOS_AIRES)
                    .isAdmissible(ESTABLISHMENT, lastSlot);
            boolean newEntryAt1602 = serviceAt(buenosAires(TUESDAY, LocalTime.of(16, 2)), BUENOS_AIRES)
                    .isAdmissible(ESTABLISHMENT, lastSlot);

            assertThat(admittedAt1558).isTrue();
            assertThat(newEntryAt1602).isFalse();
            // Admission is a point-in-time decision at order entry. The service exposes no approval hook,
            // so an approval at 16:02 never consults the schedule again.
            assertThat(Arrays.stream(DinerTimeSlotService.class.getDeclaredMethods())
                    .map(Method::getName)).noneMatch(name -> name.toLowerCase().contains("approv"));
        }
    }

    @Nested
    @DisplayName("US2 - Slot capacity (ADM-2.2.2)")
    class SlotCapacity {

        private final SlotCapacityRepository capacityRepository = mock(SlotCapacityRepository.class);
        private final OperatingScheduleRepository scheduleRepository = mock(OperatingScheduleRepository.class);
        private final SlotCapacityService service = new SlotCapacityService(capacityRepository, scheduleRepository);

        private final TimeSlot noon = new TimeSlot(WEDNESDAY, LocalTime.of(12, 0));
        private final TimeSlot quarterPast = new TimeSlot(WEDNESDAY, LocalTime.of(12, 15));

        private EstablishmentSlotCapacity row(TimeSlot slot, int max, int approved, long version) {
            var capacity = new EstablishmentSlotCapacity(ESTABLISHMENT, slot, max, approved);
            ReflectionTestUtils.setField(capacity, "version", version);
            return capacity;
        }

        private void stored(EstablishmentSlotCapacity capacity) {
            when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(
                    ESTABLISHMENT, capacity.getSlotDate(), capacity.getStartTime())).thenReturn(Optional.of(capacity));
        }

        @Test
        @DisplayName("INV-2: an order enters only while the approved count is below the capacity")
        void shouldAdmitOnlyWhenApprovedBelowCapacity() {
            when(capacityRepository.incrementIfBelowCapacity(ESTABLISHMENT, WEDNESDAY, LocalTime.of(12, 0))).thenReturn(1);
            service.consumeSlot(ESTABLISHMENT, noon);

            when(capacityRepository.incrementIfBelowCapacity(ESTABLISHMENT, WEDNESDAY, LocalTime.of(12, 15))).thenReturn(0);
            stored(row(quarterPast, 50, 50, 0));
            assertThatThrownBy(() -> service.consumeSlot(ESTABLISHMENT, quarterPast))
                    .isInstanceOf(SlotSoldOutException.class)
                    .hasMessage("La franja seleccionada ha alcanzado su capacidad máxima permitida (50 pedidos).");
        }

        @Test
        @DisplayName("INV-3: sold out as soon as approved reaches or exceeds the capacity")
        void shouldMarkSoldOutWhenCapacityReachedOrExceeded() {
            stored(row(noon, 50, 50, 0));
            stored(row(quarterPast, 30, 45, 0));

            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isTrue();
            assertThat(service.isSoldOut(ESTABLISHMENT, quarterPast)).isTrue();
        }

        @Test
        @DisplayName("US2-AC1: the 49th approval leaves the slot available, the 50th sells it out")
        void shouldMarkSoldOutAtOrder50() {
            stored(row(noon, 50, 49, 0));
            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isFalse();

            stored(row(noon, 50, 50, 0));
            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isTrue();
        }

        @Test
        @DisplayName("INV-6: a negative capacity is rejected and nothing is saved")
        void shouldRejectNegativeCapacity() {
            var negative = new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), -1, null);

            assertThatThrownBy(() -> service.saveCapacities(ESTABLISHMENT, List.of(negative)))
                    .isInstanceOf(InvalidCapacityException.class)
                    .hasMessage("La capacidad máxima por franja debe ser un número entero mayor o igual a 0.");
            verify(capacityRepository, never()).saveAllAndFlush(any());
        }

        @Test
        @DisplayName("INV-5: a start that is not aligned to 15 minutes is rejected")
        void shouldRejectMisalignedSlotStart() {
            var misaligned = new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 7), 10, null);

            assertThatThrownBy(() -> service.saveCapacities(ESTABLISHMENT, List.of(misaligned)))
                    .isInstanceOf(InvalidCapacityException.class)
                    .hasMessageContaining("12:07");
            verify(capacityRepository, never()).saveAllAndFlush(any());
        }

        @Test
        @DisplayName("INV-8 / EC-005: reducing 50 to 30 with 40 approved keeps the 40 and sells the slot out")
        void shouldPreserveApprovedOrdersWhenCapacityReduced() {
            var existing = row(noon, 50, 40, 2);
            stored(existing);
            when(capacityRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<EstablishmentSlotCapacity> saved = service.saveCapacities(ESTABLISHMENT,
                    List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 30, 2L)));

            assertThat(saved).singleElement().satisfies(capacity -> {
                assertThat(capacity.getMaxCapacity()).isEqualTo(30);
                assertThat(capacity.getApprovedOrdersCount()).isEqualTo(40);
                assertThat(capacity.isSoldOut()).isTrue();
            });
        }

        @Test
        @DisplayName("EC-004: raising the capacity of a sold-out slot reopens it")
        void shouldReopenSlotWhenCapacityIncreased() {
            var existing = row(noon, 50, 50, 0);
            stored(existing);
            when(capacityRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.saveCapacities(ESTABLISHMENT, List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 60, 0L)));

            assertThat(existing.isSoldOut()).isFalse();
            assertThat(existing.getApprovedOrdersCount()).isEqualTo(50);
        }

        @Test
        @DisplayName("EC-006: capacity 0 is permanently sold out and never admits an order")
        void shouldTreatZeroCapacityAsPermanentlySoldOut() {
            stored(row(noon, 0, 0, 0));
            when(capacityRepository.incrementIfBelowCapacity(any(), any(), any())).thenReturn(0);

            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isTrue();
            assertThatThrownBy(() -> service.consumeSlot(ESTABLISHMENT, noon))
                    .isInstanceOf(SlotSoldOutException.class)
                    .hasMessageContaining("(0 pedidos)");
        }

        @Test
        @DisplayName("EC-007: a slot without configured capacity behaves as capacity 0")
        void shouldDefaultUnconfiguredSlotToZeroCapacity() {
            when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(capacityRepository.incrementIfBelowCapacity(any(), any(), any())).thenReturn(0);

            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isTrue();
            assertThatThrownBy(() -> service.consumeSlot(ESTABLISHMENT, noon)).isInstanceOf(SlotSoldOutException.class);
        }

        @Test
        @DisplayName("EC-015: adjacent slots are independent of each other")
        void shouldKeepAdjacentSlotsIndependent() {
            stored(row(noon, 50, 50, 0));
            stored(row(quarterPast, 10, 3, 0));

            assertThat(service.isSoldOut(ESTABLISHMENT, noon)).isTrue();
            assertThat(service.isSoldOut(ESTABLISHMENT, quarterPast)).isFalse();
        }

        @Test
        @DisplayName("EC-003 / INV-8: changing the schedule has no access to approved orders at all")
        void shouldPreservePendingOrdersOnScheduleChange() {
            boolean touchesCapacity = Arrays.stream(OperatingScheduleService.class.getDeclaredConstructors())
                    .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                    .anyMatch(SlotCapacityRepository.class::equals);

            assertThat(touchesCapacity).isFalse();
        }
    }

    @Nested
    @DisplayName("US3 - Online payment method toggles (ADM-2.2.3)")
    class PaymentMethodToggles {

        private final PaymentMethodRepository repository = mock(PaymentMethodRepository.class);
        private final PaymentMethodService service = new PaymentMethodService(repository);

        private EstablishmentPaymentMethod row(PaymentMethod method, boolean enabled, long version) {
            var payment = new EstablishmentPaymentMethod(ESTABLISHMENT, method, enabled);
            ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(payment, "version", version);
            return payment;
        }

        private void stored(EstablishmentPaymentMethod... rows) {
            when(repository.findByEstablishmentId(ESTABLISHMENT)).thenReturn(List.of(rows));
            for (EstablishmentPaymentMethod row : rows) {
                when(repository.findByEstablishmentIdAndPaymentMethod(ESTABLISHMENT, row.getPaymentMethod()))
                        .thenReturn(Optional.of(row));
            }
        }

        @Test
        @DisplayName("INV-4: the diner is offered only the enabled methods")
        void shouldExposeOnlyEnabledPaymentMethods() {
            stored(row(PaymentMethod.MERCADO_PAGO, true, 0), row(PaymentMethod.STRIPE, false, 0));

            assertThat(service.getEnabledMethods(ESTABLISHMENT)).containsExactly(PaymentMethod.MERCADO_PAGO);
        }

        @Test
        @DisplayName("INV-4: a disabled method is hidden even though its row exists")
        void shouldHideDisabledPaymentMethod() {
            stored(row(PaymentMethod.MERCADO_PAGO, false, 0), row(PaymentMethod.STRIPE, true, 0));

            assertThat(service.getEnabledMethods(ESTABLISHMENT)).doesNotContain(PaymentMethod.MERCADO_PAGO);
        }

        @Test
        @DisplayName("INV-4 / EC-011: a method that is enabled again is offered again")
        void shouldShowReenabledPaymentMethod() {
            var stripe = row(PaymentMethod.STRIPE, false, 1);
            stored(row(PaymentMethod.MERCADO_PAGO, true, 0), stripe);
            when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            assertThat(service.getEnabledMethods(ESTABLISHMENT)).doesNotContain(PaymentMethod.STRIPE);

            service.toggle(ESTABLISHMENT, PaymentMethod.STRIPE, true, 1L);

            assertThat(service.getEnabledMethods(ESTABLISHMENT)).contains(PaymentMethod.STRIPE);
        }

        @Test
        @DisplayName("A missing row means enabled, so a new establishment is never read as 'all disabled' (EC-009)")
        void shouldTreatMissingPaymentRowAsEnabled() {
            when(repository.findByEstablishmentId(ESTABLISHMENT)).thenReturn(List.of());
            when(repository.findByEstablishmentIdAndPaymentMethod(any(), any())).thenReturn(Optional.empty());

            assertThat(service.getEnabledMethods(ESTABLISHMENT))
                    .containsExactly(PaymentMethod.MERCADO_PAGO, PaymentMethod.STRIPE);
            service.validateEnabled(ESTABLISHMENT, PaymentMethod.STRIPE);
            assertThat(service.getAdminView(ESTABLISHMENT)).hasSize(2).allSatisfy(view -> {
                assertThat(view.enabled()).isTrue();
                assertThat(view.id()).isNull();
                assertThat(view.version()).isNull();
            });
        }

        @Test
        @DisplayName("The first toggle of a method without a row creates the row")
        void shouldCreateTheRowOnTheFirstToggle() {
            when(repository.findByEstablishmentIdAndPaymentMethod(ESTABLISHMENT, PaymentMethod.STRIPE))
                    .thenReturn(Optional.empty());
            when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var view = service.toggle(ESTABLISHMENT, PaymentMethod.STRIPE, false, null);

            assertThat(view.enabled()).isFalse();
            assertThat(view.paymentMethod()).isEqualTo(PaymentMethod.STRIPE);
            assertThat(view.displayName()).isEqualTo("Stripe");
        }

        @Test
        @DisplayName("INV-9 / EC-010: confirming with a method disabled meanwhile is rejected")
        void shouldRejectConfirmationIfPaymentMethodDisabled() {
            stored(row(PaymentMethod.MERCADO_PAGO, true, 0), row(PaymentMethod.STRIPE, false, 2));

            service.validateEnabled(ESTABLISHMENT, PaymentMethod.MERCADO_PAGO);
            assertThatThrownBy(() -> service.validateEnabled(ESTABLISHMENT, PaymentMethod.STRIPE))
                    .isInstanceOf(PaymentMethodUnavailableException.class)
                    .hasMessage("El método de pago 'STRIPE' ha sido deshabilitado por el restaurante. Por favor seleccione otra opción.");
        }

        @Test
        @DisplayName("EC-012: a stale version, or a version for a method without a row, is a conflict")
        void shouldFailOnStaleToggle() {
            stored(row(PaymentMethod.MERCADO_PAGO, true, 4));
            when(repository.findByEstablishmentIdAndPaymentMethod(ESTABLISHMENT, PaymentMethod.STRIPE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.toggle(ESTABLISHMENT, PaymentMethod.MERCADO_PAGO, false, 3L))
                    .isInstanceOf(OperatingConfigConflictException.class);
            assertThatThrownBy(() -> service.toggle(ESTABLISHMENT, PaymentMethod.MERCADO_PAGO, false, null))
                    .isInstanceOf(OperatingConfigConflictException.class);
            assertThatThrownBy(() -> service.toggle(ESTABLISHMENT, PaymentMethod.STRIPE, false, 1L))
                    .isInstanceOf(OperatingConfigConflictException.class);
        }

        @Test
        @DisplayName("A-005: no payment gateway SDK, credential or token is on the classpath of the module")
        void shouldOperateOfflineWithoutExternalGateways() throws Exception {
            for (String gatewayClass : List.of("com.stripe.Stripe", "com.mercadopago.MercadoPagoConfig")) {
                assertThatThrownBy(() -> Class.forName(gatewayClass)).isInstanceOf(ClassNotFoundException.class);
            }

            var root = java.nio.file.Path.of(EstablishmentPaymentMethod.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).resolve("com/easyeat/operations");
            try (var files = java.nio.file.Files.walk(root)) {
                List<String> offending = files.filter(path -> path.toString().endsWith(".class"))
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.matches("(?i).*(credential|apikey|secret|token|gateway|stripeclient).*"))
                        .toList();
                assertThat(offending).isEmpty();
            }

            Set<String> fields = Arrays.stream(EstablishmentPaymentMethod.class.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
            assertThat(fields).containsExactlyInAnyOrder("id", "establishmentId", "paymentMethod", "enabled",
                    "version", "createdAt", "updatedAt");
        }
    }
}
