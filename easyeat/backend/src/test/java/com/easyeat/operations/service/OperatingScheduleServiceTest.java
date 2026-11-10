package com.easyeat.operations.service;

import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.ScheduleChange;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperatingScheduleService: weekly upsert with optimistic versions")
class OperatingScheduleServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private OperatingScheduleRepository repository;

    @Mock
    private EstablishmentRepository establishments;

    @BeforeEach
    void establishmentExists() {
        lenient().when(establishments.existsById(ID)).thenReturn(true);
    }

    @Test
    @DisplayName("saving for an establishment that does not exist is a 404 and nothing is written")
    void rejectsMissingEstablishment() {
        UUID missing = UUID.randomUUID();
        when(establishments.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments)
                .saveWeek(missing, List.of(change(null))))
                .isInstanceOf(EstablishmentNotFoundException.class);
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("EC-012: two admins creating the same day, the second hits the unique constraint")
    void mapsUniqueViolationOnCreate() {
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(repository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_establishment_operating_schedule"));

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments)
                .saveWeek(ID, List.of(change(null))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    private EstablishmentOperatingSchedule existing(long version) {
        var schedule = new EstablishmentOperatingSchedule(ID, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(16, 0), false);
        ReflectionTestUtils.setField(schedule, "version", version);
        return schedule;
    }

    private ScheduleChange change(Long expectedVersion) {
        return new ScheduleChange(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(18, 0), false, expectedVersion);
    }

    @Test
    @DisplayName("updates an existing day when the expected version matches")
    void updatesWhenVersionMatches() {
        var schedule = existing(3L);
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.of(schedule));
        when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(change(3L)));

        assertThat(saved).containsExactly(schedule);
        assertThat(schedule.getOpeningTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(schedule.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("synchronizes establishment opening and closing time when saving today's schedule")
    void synchronizesTodayScheduleOntoEstablishment() {
        DayOfWeek today = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).getDayOfWeek();
        var est = new com.easyeat.catalog.domain.Establishment(ID, "Test", com.easyeat.catalog.domain.EstablishmentStatus.ACTIVE,
                LocalTime.of(8, 0), LocalTime.of(23, 0), "America/Argentina/Buenos_Aires");
        when(establishments.findById(ID)).thenReturn(Optional.of(est));
        when(repository.findByEstablishmentIdAndDayOfWeek(org.mockito.ArgumentMatchers.eq(ID), any())).thenReturn(Optional.empty());
        when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var todayChange = new ScheduleChange(today, LocalTime.of(1, 0), LocalTime.of(23, 30), false, null);
        new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(todayChange));

        assertThat(est.getOpeningTime()).isEqualTo(LocalTime.of(1, 0));
        assertThat(est.getClosingTime()).isEqualTo(LocalTime.of(23, 30));
        verify(establishments).save(est);
    }

    @Test
    @DisplayName("EC-012: a stale expected version is a conflict and nothing is saved")
    void failsOnStaleVersion() {
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.of(existing(4L)));

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(change(3L))))
                .isInstanceOf(OperatingConfigConflictException.class)
                .hasMessageContaining("recargue la pantalla");
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("EC-012: no version for a day somebody else already configured is a conflict")
    void failsWhenDayWasCreatedMeanwhile() {
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.of(existing(0L)));

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(change(null))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("EC-012: a version for a day that no longer exists is a conflict")
    void failsWhenVersionedDayDisappeared() {
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(change(2L))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("EC-012: a lost write race at flush time surfaces as the same conflict")
    void mapsFlushTimeLockFailure() {
        when(repository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.MONDAY)).thenReturn(Optional.empty());
        when(repository.saveAllAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(EstablishmentOperatingSchedule.class, ID));

        assertThatThrownBy(() -> new OperatingScheduleService(repository, establishments).saveWeek(ID, List.of(change(null))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("returns the configured days ordered from Monday to Sunday")
    void returnsWeekOrdered() {
        var sunday = new EstablishmentOperatingSchedule(ID, DayOfWeek.SUNDAY, LocalTime.of(10, 0), LocalTime.of(14, 0), false);
        var monday = existing(0L);
        when(repository.findByEstablishmentId(ID)).thenReturn(List.of(sunday, monday));

        assertThat(new OperatingScheduleService(repository, establishments).getWeek(ID)).containsExactly(monday, sunday);
    }
}
