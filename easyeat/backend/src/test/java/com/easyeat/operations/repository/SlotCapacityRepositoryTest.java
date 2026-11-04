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

    private int approved(LocalTime start) {
        return jdbcTemplate.queryForObject(
                "SELECT approved_orders_count FROM establishment_slot_capacities WHERE establishment_id = ? AND slot_date = ? AND start_time = ?",
                Integer.class, CAFE_ID, DAY, start);
    }

    @Test
    @DisplayName("INV-2: the conditional update counts one order while approved is below the capacity")
    void incrementsWhileBelowCapacity() {
        repository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 0)), 2, 1));

        int updated = repository.incrementIfBelowCapacity(CAFE_ID, DAY, LocalTime.of(12, 0));

        assertThat(updated).isEqualTo(1);
        assertThat(approved(LocalTime.of(12, 0))).isEqualTo(2);
    }

    @Test
    @DisplayName("INV-2: at capacity the update touches no row and the count does not move")
    void doesNotIncrementAtCapacity() {
        repository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 0)), 2, 2));

        assertThat(repository.incrementIfBelowCapacity(CAFE_ID, DAY, LocalTime.of(12, 0))).isZero();
        assertThat(approved(LocalTime.of(12, 0))).isEqualTo(2);
    }

    @Test
    @DisplayName("EC-006 / EC-007: capacity 0 and an unconfigured slot both update zero rows")
    void doesNotIncrementWithZeroOrNoCapacity() {
        repository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 0)), 0, 0));

        assertThat(repository.incrementIfBelowCapacity(CAFE_ID, DAY, LocalTime.of(12, 0))).isZero();
        assertThat(repository.incrementIfBelowCapacity(CAFE_ID, DAY, LocalTime.of(12, 15))).isZero();
    }

    @Test
    @DisplayName("EC-015: the update only touches the requested slot")
    void incrementsOnlyTheRequestedSlot() {
        repository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 0)), 5, 0));
        repository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 15)), 5, 0));

        repository.incrementIfBelowCapacity(CAFE_ID, DAY, LocalTime.of(12, 0));

        assertThat(approved(LocalTime.of(12, 0))).isEqualTo(1);
        assertThat(approved(LocalTime.of(12, 15))).isZero();
    }

    private static EstablishmentSlotCapacity slot(UUID establishmentId, LocalDate date, LocalTime start) {
        return new EstablishmentSlotCapacity(establishmentId, new TimeSlot(date, start), 50);
    }
}
