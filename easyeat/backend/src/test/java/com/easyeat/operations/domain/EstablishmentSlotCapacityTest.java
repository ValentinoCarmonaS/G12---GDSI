package com.easyeat.operations.domain;

import com.easyeat.operations.OperationsPostgresTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EstablishmentSlotCapacity entity mapping and sold-out projection")
class EstablishmentSlotCapacityTest extends OperationsPostgresTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_slot_capacities WHERE establishment_id = ?", CAFE_ID);
    }

    @Test
    @DisplayName("persists a slot with its derived end time, zero approved orders and version 0")
    void persistsAndReloads() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(s -> {
            var capacity = new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 0)), 50);
            entityManager.persist(capacity);
            return capacity.getId();
        });

        tx.executeWithoutResult(s -> {
            var loaded = entityManager.find(EstablishmentSlotCapacity.class, id);
            assertThat(loaded.getEstablishmentId()).isEqualTo(CAFE_ID);
            assertThat(loaded.getSlotDate()).isEqualTo(DAY);
            assertThat(loaded.getStartTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(loaded.getEndTime()).isEqualTo(LocalTime.of(12, 15));
            assertThat(loaded.getMaxCapacity()).isEqualTo(50);
            assertThat(loaded.getApprovedOrdersCount()).isZero();
            assertThat(loaded.getVersion()).isZero();
            assertThat(loaded.toTimeSlot()).isEqualTo(new TimeSlot(DAY, LocalTime.of(12, 0)));
        });
    }

    @Test
    @DisplayName("INV-3: sold out exactly when approved orders reach or exceed the capacity")
    void soldOutProjection() {
        var slot = new TimeSlot(DAY, LocalTime.of(12, 15));

        assertThat(new EstablishmentSlotCapacity(CAFE_ID, slot, 50, 49).isSoldOut()).isFalse();
        assertThat(new EstablishmentSlotCapacity(CAFE_ID, slot, 50, 50).isSoldOut()).isTrue();
        assertThat(new EstablishmentSlotCapacity(CAFE_ID, slot, 30, 40).isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("EC-006: zero capacity is permanently sold out")
    void zeroCapacityIsSoldOut() {
        var capacity = new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 30)), 0);

        assertThat(capacity.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("INV-8: changing the capacity never touches the approved orders count")
    void changeCapacityKeepsApprovedCount() {
        var capacity = new EstablishmentSlotCapacity(CAFE_ID, new TimeSlot(DAY, LocalTime.of(12, 45)), 50, 40);

        capacity.changeMaxCapacity(30);

        assertThat(capacity.getMaxCapacity()).isEqualTo(30);
        assertThat(capacity.getApprovedOrdersCount()).isEqualTo(40);
        assertThat(capacity.isSoldOut()).isTrue();

        capacity.changeMaxCapacity(60);
        assertThat(capacity.isSoldOut()).isFalse();
    }

    @Test
    @DisplayName("INV-6: rejects a negative capacity or a negative approved count")
    void rejectsNegativeValues() {
        var slot = new TimeSlot(DAY, LocalTime.of(13, 0));

        assertThatThrownBy(() -> new EstablishmentSlotCapacity(CAFE_ID, slot, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EstablishmentSlotCapacity(CAFE_ID, slot, 5, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EstablishmentSlotCapacity(CAFE_ID, slot, 5).changeMaxCapacity(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
