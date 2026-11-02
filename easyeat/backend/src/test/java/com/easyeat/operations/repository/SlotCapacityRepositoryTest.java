package com.easyeat.operations.repository;

import com.easyeat.operations.OperationsPostgresTestBase;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.TimeSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlotCapacityRepository queries")
class SlotCapacityRepositoryTest extends OperationsPostgresTestBase {

    private static final UUID BISTRO_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    @Autowired
    private SlotCapacityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_slot_capacities WHERE establishment_id IN (?, ?)",
                CAFE_ID, BISTRO_ID);
    }

    @Test
    @DisplayName("lists a date range of one establishment ordered by date then start time")
    void listsRangeOrdered() {
        repository.save(slot(CAFE_ID, DAY.plusDays(1), LocalTime.of(12, 0)));
        repository.save(slot(CAFE_ID, DAY, LocalTime.of(12, 15)));
        repository.save(slot(CAFE_ID, DAY, LocalTime.of(12, 0)));
        repository.save(slot(CAFE_ID, DAY.plusDays(5), LocalTime.of(12, 0)));
        repository.save(slot(BISTRO_ID, DAY, LocalTime.of(12, 0)));

        var result = repository.findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                CAFE_ID, DAY, DAY.plusDays(1));

        assertThat(result)
                .extracting(EstablishmentSlotCapacity::getSlotDate, EstablishmentSlotCapacity::getStartTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DAY, LocalTime.of(12, 0)),
                        org.assertj.core.groups.Tuple.tuple(DAY, LocalTime.of(12, 15)),
                        org.assertj.core.groups.Tuple.tuple(DAY.plusDays(1), LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("finds one slot by establishment, date and start, or empty when unconfigured (EC-007)")
    void findsOneSlotOrEmpty() {
        repository.save(slot(CAFE_ID, DAY, LocalTime.of(13, 0)));

        assertThat(repository.findByEstablishmentIdAndSlotDateAndStartTime(CAFE_ID, DAY, LocalTime.of(13, 0)))
                .isPresent();
        assertThat(repository.findByEstablishmentIdAndSlotDateAndStartTime(CAFE_ID, DAY, LocalTime.of(13, 15)))
                .isEmpty();
        assertThat(repository.findByEstablishmentIdAndSlotDateAndStartTime(BISTRO_ID, DAY, LocalTime.of(13, 0)))
                .isEmpty();
    }

    private static EstablishmentSlotCapacity slot(UUID establishmentId, LocalDate date, LocalTime start) {
        return new EstablishmentSlotCapacity(establishmentId, new TimeSlot(date, start), 50);
    }
}
