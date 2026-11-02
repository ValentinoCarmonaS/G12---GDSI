package com.easyeat.operations.domain;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.exception.InvalidScheduleException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.service.DinerTimeSlotService;
import com.easyeat.operations.service.DinerTimeSlotService.Availability;
import com.easyeat.operations.service.EstablishmentTimeProvider;
import com.easyeat.operations.service.OperatingScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

        private DinerTimeSlotService serviceAt(Instant now, String timezone) {
            var establishment = new Establishment(ESTABLISHMENT, "Cafe", EstablishmentStatus.ACTIVE,
                    LocalTime.of(11, 0), LocalTime.of(23, 0), timezone);
            when(establishmentRepository.findById(ESTABLISHMENT)).thenReturn(Optional.of(establishment));
            var timeProvider = new EstablishmentTimeProvider(Clock.fixed(now, ZoneOffset.UTC), establishmentRepository);
            return new DinerTimeSlotService(scheduleRepository, timeProvider);
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
            when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
}
