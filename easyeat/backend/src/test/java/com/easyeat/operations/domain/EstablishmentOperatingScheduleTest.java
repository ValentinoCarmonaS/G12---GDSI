package com.easyeat.operations.domain;

import com.easyeat.operations.OperationsPostgresTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EstablishmentOperatingSchedule entity mapping and optimistic locking")
class EstablishmentOperatingScheduleTest extends OperationsPostgresTestBase {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_operating_schedules WHERE establishment_id = ?", CAFE_ID);
    }

    @Test
    @DisplayName("persists and reloads every field, starting at version 0")
    void persistsAndReloads() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(s -> {
            var schedule = new EstablishmentOperatingSchedule(
                    CAFE_ID, DayOfWeek.FRIDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false);
            entityManager.persist(schedule);
            return schedule.getId();
        });

        tx.executeWithoutResult(s -> {
            var loaded = entityManager.find(EstablishmentOperatingSchedule.class, id);
            assertThat(loaded.getEstablishmentId()).isEqualTo(CAFE_ID);
            assertThat(loaded.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
            assertThat(loaded.getOpeningTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(loaded.getClosingTime()).isEqualTo(LocalTime.of(16, 0));
            assertThat(loaded.isClosed()).isFalse();
            assertThat(loaded.getVersion()).isZero();
            assertThat(loaded.getCreatedAt()).isNotNull();
            assertThat(loaded.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("EC-012: a stale concurrent update fails with an optimistic lock conflict")
    void staleUpdateFails() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(s -> {
            var schedule = new EstablishmentOperatingSchedule(
                    CAFE_ID, DayOfWeek.SATURDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false);
            entityManager.persist(schedule);
            return schedule.getId();
        });

        var stale = tx.execute(s -> {
            var loaded = entityManager.find(EstablishmentOperatingSchedule.class, id);
            entityManager.detach(loaded);
            return loaded;
        });

        tx.executeWithoutResult(s -> entityManager.find(EstablishmentOperatingSchedule.class, id)
                .update(LocalTime.of(11, 0), LocalTime.of(17, 0), false));

        stale.update(LocalTime.of(10, 0), LocalTime.of(18, 0), false);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> entityManager.merge(stale)))
                .isInstanceOfAny(ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class);

        tx.executeWithoutResult(s -> {
            var loaded = entityManager.find(EstablishmentOperatingSchedule.class, id);
            assertThat(loaded.getVersion()).isEqualTo(1L);
            assertThat(loaded.getOpeningTime()).isEqualTo(LocalTime.of(11, 0));
        });
    }

    @Test
    @DisplayName("update replaces the times and the closed flag")
    void updateReplacesFields() {
        var schedule = new EstablishmentOperatingSchedule(
                CAFE_ID, DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(16, 0), false);

        schedule.update(LocalTime.of(9, 0), LocalTime.of(10, 0), true);

        assertThat(schedule.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(schedule.getClosingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(schedule.isClosed()).isTrue();
    }
}
