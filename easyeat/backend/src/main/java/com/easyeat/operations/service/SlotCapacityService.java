package com.easyeat.operations.service;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.SlotCapacityChange;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.exception.InvalidCapacityException;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.exception.SlotSoldOutException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Capacity per 15-minute slot (ADM-2.2.2). Saving a capacity never touches the approved count
 * (INV-8); that count only moves through {@link #consumeSlot}, one conditional statement (INV-2).
 * A slot with no row has capacity 0 and is sold out (EC-007).
 */
@Service
public class SlotCapacityService {

    /** A slot of the admin matrix; {@code id} and {@code version} are null for an unconfigured slot. */
    public record Row(UUID id, TimeSlot slot, int maxCapacity, int approvedOrdersCount,
                      boolean soldOut, Long version) {
    }

    private final SlotCapacityRepository capacityRepository;
    private final OperatingScheduleRepository scheduleRepository;

    public SlotCapacityService(SlotCapacityRepository capacityRepository,
                               OperatingScheduleRepository scheduleRepository) {
        this.capacityRepository = capacityRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Every slot the schedule defines for the date plus any configured slot outside it, in time
     * order, so the admin can configure slots that have no capacity yet.
     */
    @Transactional(readOnly = true)
    public List<Row> getDayMatrix(UUID establishmentId, LocalDate date) {
        Map<LocalTime, Row> byStart = new HashMap<>();
        Optional<EstablishmentOperatingSchedule> schedule =
                scheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek());
        if (schedule.isPresent() && !schedule.get().isClosed()) {
            DinerTimeSlotService.partition(date, schedule.get())
                    .forEach(slot -> byStart.put(slot.startTime(), new Row(null, slot, 0, 0, true, null)));
        }
        capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                        establishmentId, date, date)
                .forEach(capacity -> byStart.put(capacity.getStartTime(), toRow(capacity)));
        return byStart.values().stream().sorted(Comparator.comparing(row -> row.slot().startTime())).toList();
    }

    /** Upserts the given slots atomically: if any is invalid or stale, none is saved. */
    @Transactional
    public List<EstablishmentSlotCapacity> saveCapacities(UUID establishmentId, List<SlotCapacityChange> changes) {
        for (SlotCapacityChange change : changes) {
            if (change.maxCapacity() < 0) {
                throw InvalidCapacityException.negative();
            }
            LocalTime start = change.startTime();
            if (start.getMinute() % TimeSlot.DURATION_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
                throw InvalidCapacityException.misaligned(start);
            }
        }
        List<EstablishmentSlotCapacity> toSave = new ArrayList<>();
        for (SlotCapacityChange change : changes) {
            Optional<EstablishmentSlotCapacity> existing = capacityRepository
                    .findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, change.slotDate(), change.startTime());
            if (existing.isPresent()) {
                EstablishmentSlotCapacity capacity = existing.get();
                if (!capacity.getVersion().equals(change.expectedVersion())) {
                    throw new OperatingConfigConflictException();
                }
                capacity.changeMaxCapacity(change.maxCapacity());
                toSave.add(capacity);
            } else {
                if (change.expectedVersion() != null) {
                    throw new OperatingConfigConflictException();
                }
                toSave.add(new EstablishmentSlotCapacity(establishmentId,
                        new TimeSlot(change.slotDate(), change.startTime()), change.maxCapacity()));
            }
        }
        try {
            return capacityRepository.saveAllAndFlush(toSave);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OperatingConfigConflictException();
        }
    }

    /** INV-3 / EC-007: sold out when approved reaches the capacity, or when there is no capacity row. */
    @Transactional(readOnly = true)
    public boolean isSoldOut(UUID establishmentId, TimeSlot slot) {
        return capacityRepository
                .findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, slot.slotDate(), slot.startTime())
                .map(EstablishmentSlotCapacity::isSoldOut)
                .orElse(true);
    }

    /**
     * Counts one approved order against the slot. Either the single conditional UPDATE changes the
     * row or the slot is sold out; there is no read-then-write window (EC-016).
     */
    @Transactional
    public void consumeSlot(UUID establishmentId, TimeSlot slot) {
        int updated = capacityRepository.incrementIfBelowCapacity(establishmentId, slot.slotDate(), slot.startTime());
        if (updated == 0) {
            int maxCapacity = capacityRepository
                    .findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, slot.slotDate(), slot.startTime())
                    .map(EstablishmentSlotCapacity::getMaxCapacity)
                    .orElse(0);
            throw new SlotSoldOutException(maxCapacity);
        }
    }

    private static Row toRow(EstablishmentSlotCapacity capacity) {
        return new Row(capacity.getId(), capacity.toTimeSlot(), capacity.getMaxCapacity(),
                capacity.getApprovedOrdersCount(), capacity.isSoldOut(), capacity.getVersion());
    }
}
