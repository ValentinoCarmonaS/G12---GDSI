package com.easyeat.operations.service;

import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.SlotCapacityChange;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import com.easyeat.operations.service.SlotCapacityService.Row;
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
import java.time.LocalDate;
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
@DisplayName("SlotCapacityService: admin matrix and optimistic versions")
class SlotCapacityServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);

    @Mock
    private SlotCapacityRepository capacityRepository;

    @Mock
    private OperatingScheduleRepository scheduleRepository;

    @Mock
    private EstablishmentRepository establishments;

    @BeforeEach
    void establishmentExists() {
        lenient().when(establishments.existsById(ID)).thenReturn(true);
    }

    private SlotCapacityService service() {
        return new SlotCapacityService(capacityRepository, scheduleRepository, establishments);
    }

    @Test
    @DisplayName("saving for an establishment that does not exist is a 404 and nothing is written")
    void rejectsMissingEstablishment() {
        UUID missing = UUID.randomUUID();
        when(establishments.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> service().saveCapacities(missing,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 25, null))))
                .isInstanceOf(EstablishmentNotFoundException.class);
        verify(capacityRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("EC-012: two admins creating the same slot, the second hits the unique constraint")
    void mapsUniqueViolationOnCreate() {
        when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(ID, WEDNESDAY, LocalTime.of(12, 0)))
                .thenReturn(Optional.empty());
        when(capacityRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_establishment_slot_capacity"));

        assertThatThrownBy(() -> service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 25, null))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    private void openWednesday(int openingHour, int closingHour, boolean closed) {
        when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.WEDNESDAY)).thenReturn(Optional.of(
                new EstablishmentOperatingSchedule(ID, DayOfWeek.WEDNESDAY,
                        LocalTime.of(openingHour, 0), LocalTime.of(closingHour, 0), closed)));
    }

    private EstablishmentSlotCapacity stored(LocalTime start, int max, int approved, long version) {
        var capacity = new EstablishmentSlotCapacity(ID, new TimeSlot(WEDNESDAY, start), max, approved);
        ReflectionTestUtils.setField(capacity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(capacity, "version", version);
        return capacity;
    }

    @Test
    @DisplayName("the matrix lists every slot of the schedule; unconfigured ones are capacity 0, sold out, without id or version (EC-007)")
    void matrixSynthesizesUnconfiguredSlots() {
        openWednesday(12, 13, false);
        when(capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(ID, WEDNESDAY, WEDNESDAY))
                .thenReturn(List.of(stored(LocalTime.of(12, 15), 20, 5, 3)));

        List<Row> rows = service().getDayMatrix(ID, WEDNESDAY);

        assertThat(rows).extracting(row -> row.slot().startTime())
                .containsExactly(LocalTime.of(12, 0), LocalTime.of(12, 15), LocalTime.of(12, 30), LocalTime.of(12, 45));
        assertThat(rows.get(0)).satisfies(row -> {
            assertThat(row.id()).isNull();
            assertThat(row.version()).isNull();
            assertThat(row.maxCapacity()).isZero();
            assertThat(row.soldOut()).isTrue();
        });
        assertThat(rows.get(1)).satisfies(row -> {
            assertThat(row.id()).isNotNull();
            assertThat(row.version()).isEqualTo(3L);
            assertThat(row.maxCapacity()).isEqualTo(20);
            assertThat(row.approvedOrdersCount()).isEqualTo(5);
            assertThat(row.soldOut()).isFalse();
        });
    }

    @Test
    @DisplayName("a configured slot outside the schedule still appears, and a closed or unconfigured day lists only configured rows")
    void matrixKeepsConfiguredSlotsOutsideTheSchedule() {
        openWednesday(12, 13, true);
        when(capacityRepository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(ID, WEDNESDAY, WEDNESDAY))
                .thenReturn(List.of(stored(LocalTime.of(20, 0), 10, 0, 0)));

        assertThat(service().getDayMatrix(ID, WEDNESDAY)).singleElement()
                .satisfies(row -> assertThat(row.slot().startTime()).isEqualTo(LocalTime.of(20, 0)));

        when(scheduleRepository.findByEstablishmentIdAndDayOfWeek(ID, DayOfWeek.WEDNESDAY)).thenReturn(Optional.empty());
        assertThat(service().getDayMatrix(ID, WEDNESDAY)).hasSize(1);
    }

    @Test
    @DisplayName("creates a row for a slot that has none when no version is sent")
    void createsNewRow() {
        when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(ID, WEDNESDAY, LocalTime.of(12, 0)))
                .thenReturn(Optional.empty());
        when(capacityRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<EstablishmentSlotCapacity> saved = service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 25, null)));

        assertThat(saved).singleElement().satisfies(capacity -> {
            assertThat(capacity.getMaxCapacity()).isEqualTo(25);
            assertThat(capacity.getApprovedOrdersCount()).isZero();
        });
    }

    @Test
    @DisplayName("EC-012: a stale version, a missing version for an existing slot and a version for a missing slot are conflicts")
    void conflictsOnVersionMismatch() {
        when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(ID, WEDNESDAY, LocalTime.of(12, 0)))
                .thenReturn(Optional.of(stored(LocalTime.of(12, 0), 10, 0, 4)));
        when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(ID, WEDNESDAY, LocalTime.of(12, 15)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 5, 3L))))
                .isInstanceOf(OperatingConfigConflictException.class);
        assertThatThrownBy(() -> service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 5, null))))
                .isInstanceOf(OperatingConfigConflictException.class);
        assertThatThrownBy(() -> service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 15), 5, 2L))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("EC-012: a lost write race at flush time surfaces as the same conflict")
    void mapsFlushTimeLockFailure() {
        when(capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(any(), any(), any())).thenReturn(Optional.empty());
        when(capacityRepository.saveAllAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(EstablishmentSlotCapacity.class, ID));

        assertThatThrownBy(() -> service().saveCapacities(ID,
                List.of(new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 5, null))))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("a bulk request with one invalid slot saves none of them")
    void bulkIsAllOrNothing() {
        assertThatThrownBy(() -> service().saveCapacities(ID, List.of(
                new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 0), 5, null),
                new SlotCapacityChange(WEDNESDAY, LocalTime.of(12, 15), -3, null))))
                .isInstanceOf(com.easyeat.operations.exception.InvalidCapacityException.class);
    }
}
