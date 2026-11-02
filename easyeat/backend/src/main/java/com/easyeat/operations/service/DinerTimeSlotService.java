package com.easyeat.operations.service;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Diner side of the weekly schedule: partitions an open day into 15-minute slots over
 * {@code [opening, closing)} (INV-5) and drops the slots of today that have ended (INV-1). The
 * clock is the server's, read in the establishment timezone (D-001, EC-018). The rule is the same
 * for pickup and delivery (INV-10), so it takes no fulfillment type.
 */
@Service
public class DinerTimeSlotService {

    /** Result of evaluating one calendar day. */
    public record Availability(LocalDate date, boolean closed, String closureReason,
                               ZoneId timezone, List<TimeSlot> slots, Set<TimeSlot> soldOut) {
    }

    private static final String CLOSED_TODAY = "Cocina cerrada para el día de hoy";
    private static final String CLOSED_DAY = "Cocina cerrada para este día";
    private static final String PAST_DATE = "La fecha seleccionada ya pasó";

    private final OperatingScheduleRepository scheduleRepository;
    private final SlotCapacityRepository capacityRepository;
    private final EstablishmentTimeProvider timeProvider;

    public DinerTimeSlotService(OperatingScheduleRepository scheduleRepository,
                                SlotCapacityRepository capacityRepository,
                                EstablishmentTimeProvider timeProvider) {
        this.scheduleRepository = scheduleRepository;
        this.capacityRepository = capacityRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional(readOnly = true)
    public Availability availabilityFor(UUID establishmentId, LocalDate date) {
        ZonedDateTime now = timeProvider.nowAt(establishmentId);
        ZoneId zone = now.getZone();
        LocalDate today = now.toLocalDate();

        if (date.isBefore(today)) {
            return new Availability(date, true, PAST_DATE, zone, List.of(), Set.of());
        }
        Optional<EstablishmentOperatingSchedule> schedule =
                scheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek());
        if (schedule.isEmpty() || schedule.get().isClosed()) {
            return new Availability(date, true, CLOSED_DAY, zone, List.of(), Set.of());
        }

        List<TimeSlot> slots = partition(date, schedule.get());
        if (date.equals(today)) {
            LocalTime nowTime = now.toLocalTime();
            slots = slots.stream().filter(slot -> slot.endTime().isAfter(nowTime)).toList();
            if (slots.isEmpty()) {
                return new Availability(date, true, CLOSED_TODAY, zone, List.of(), Set.of());
            }
        }
        return new Availability(date, false, null, zone, slots, soldOutAmong(establishmentId, date, slots));
    }

    /** Whether a new order may enter for this slot right now (INV-1). */
    @Transactional(readOnly = true)
    public boolean isAdmissible(UUID establishmentId, TimeSlot slot) {
        return availabilityFor(establishmentId, slot.slotDate()).slots().contains(slot);
    }

    /** EC-007: a slot is sold out unless its row exists and still has room (INV-3). */
    private Set<TimeSlot> soldOutAmong(UUID establishmentId, LocalDate date, List<TimeSlot> slots) {
        Set<LocalTime> open = new java.util.HashSet<>();
        capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(establishmentId, date, date)
                .stream().filter(capacity -> !capacity.isSoldOut()).map(EstablishmentSlotCapacity::getStartTime).forEach(open::add);
        return slots.stream().filter(slot -> !open.contains(slot.startTime())).collect(java.util.stream.Collectors.toSet());
    }

    static List<TimeSlot> partition(LocalDate date, EstablishmentOperatingSchedule schedule) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime start = schedule.getOpeningTime();
        // FR-004: a slot belongs to the day when opening <= start < closing.
        // A slot that would run past midnight cannot be built (TimeSlot), so the day ends there.
        while (start.isBefore(schedule.getClosingTime())
                && !start.plusMinutes(TimeSlot.DURATION_MINUTES).isBefore(start)) {
            slots.add(new TimeSlot(date, start));
            start = start.plusMinutes(TimeSlot.DURATION_MINUTES);
        }
        return slots;
    }
}
