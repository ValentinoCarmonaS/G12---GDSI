package com.easyeat.operations.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.ScheduleChange;
import com.easyeat.operations.exception.InvalidScheduleException;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin side of the weekly schedule (ADM-2.2.1). A day with no row is closed (EC-008), so only
 * configured days are stored and returned.
 */
@Service
public class OperatingScheduleService {

    private final OperatingScheduleRepository scheduleRepository;
    private final EstablishmentRepository establishmentRepository;

    public OperatingScheduleService(OperatingScheduleRepository scheduleRepository,
                                    EstablishmentRepository establishmentRepository) {
        this.scheduleRepository = scheduleRepository;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional(readOnly = true)
    public List<EstablishmentOperatingSchedule> getWeek(UUID establishmentId) {
        return scheduleRepository.findByEstablishmentId(establishmentId).stream()
                .sorted(java.util.Comparator.comparing(EstablishmentOperatingSchedule::getDayOfWeek))
                .toList();
    }

    /**
     * Upserts the given days atomically: if any day is invalid or stale, none is saved.
     * Synchronizes today's operating hours onto the base establishment entity.
     */
    @Transactional
    public List<EstablishmentOperatingSchedule> saveWeek(UUID establishmentId, List<ScheduleChange> changes) {
        if (!establishmentRepository.existsById(establishmentId)) {
            throw new EstablishmentNotFoundException(establishmentId);
        }

        List<EstablishmentOperatingSchedule> toSave = new ArrayList<>();
        for (ScheduleChange change : changes) {
            if (!change.closed() && !change.openingTime().isBefore(change.closingTime())) {
                throw new InvalidScheduleException(change.openingTime(), change.closingTime());
            }
            Optional<EstablishmentOperatingSchedule> existing =
                    scheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, change.dayOfWeek());
            if (existing.isPresent()) {
                EstablishmentOperatingSchedule schedule = existing.get();
                if (!schedule.getVersion().equals(change.expectedVersion())) {
                    throw new OperatingConfigConflictException();
                }
                schedule.update(change.openingTime(), change.closingTime(), change.closed());
                toSave.add(schedule);
            } else {
                if (change.expectedVersion() != null) {
                    throw new OperatingConfigConflictException();
                }
                toSave.add(new EstablishmentOperatingSchedule(establishmentId, change.dayOfWeek(),
                        change.openingTime(), change.closingTime(), change.closed()));
            }
        }

        // Sincronizar el horario del día de hoy en la entidad base establishment
        establishmentRepository.findById(establishmentId).ifPresent(establishment -> {
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(establishment.getTimezone() != null ? establishment.getTimezone() : "America/Argentina/Buenos_Aires");
            } catch (Exception e) {
                zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
            }
            DayOfWeek currentDay = LocalDate.now(zoneId).getDayOfWeek();
            for (ScheduleChange change : changes) {
                if (change.dayOfWeek() == currentDay && !change.closed()) {
                    establishment.setOpeningTime(change.openingTime());
                    establishment.setClosingTime(change.closingTime());
                    establishmentRepository.save(establishment);
                    break;
                }
            }
        });

        try {
            return scheduleRepository.saveAllAndFlush(toSave);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new OperatingConfigConflictException();
        }
    }
}
