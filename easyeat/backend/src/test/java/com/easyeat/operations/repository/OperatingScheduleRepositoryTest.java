package com.easyeat.operations.repository;

import com.easyeat.operations.OperationsPostgresTestBase;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OperatingScheduleRepository queries")
class OperatingScheduleRepositoryTest extends OperationsPostgresTestBase {

    private static final UUID BISTRO_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");

    @Autowired
    private OperatingScheduleRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_operating_schedules WHERE establishment_id IN (?, ?)",
                CAFE_ID, BISTRO_ID);
    }

    @Test
    @DisplayName("finds the week of one establishment only")
    void findsOnlyTheEstablishmentWeek() {
        repository.save(new EstablishmentOperatingSchedule(
                CAFE_ID, DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false));
        repository.save(new EstablishmentOperatingSchedule(
                CAFE_ID, DayOfWeek.TUESDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false));
        repository.save(new EstablishmentOperatingSchedule(
                BISTRO_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), false));

        assertThat(repository.findByEstablishmentId(CAFE_ID))
                .extracting(EstablishmentOperatingSchedule::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }

    @Test
    @DisplayName("finds one day, and returns empty for an unconfigured day (EC-008)")
    void findsOneDayOrEmpty() {
        repository.save(new EstablishmentOperatingSchedule(
                CAFE_ID, DayOfWeek.WEDNESDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false));

        assertThat(repository.findByEstablishmentIdAndDayOfWeek(CAFE_ID, DayOfWeek.WEDNESDAY)).isPresent();
        assertThat(repository.findByEstablishmentIdAndDayOfWeek(CAFE_ID, DayOfWeek.THURSDAY)).isEmpty();
        assertThat(repository.findByEstablishmentIdAndDayOfWeek(BISTRO_ID, DayOfWeek.WEDNESDAY)).isEmpty();
    }
}
