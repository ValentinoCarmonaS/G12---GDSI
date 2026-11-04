package com.easyeat.operations.repository;

import com.easyeat.operations.OperationsPostgresTestBase;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.SlotCapacityChange;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.exception.SlotSoldOutException;
import com.easyeat.operations.service.SlotCapacityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Slot capacity under concurrency, against a real PostgreSQL")
class SlotCapacityConcurrencyTest extends OperationsPostgresTestBase {

    private static final LocalDate DATE = LocalDate.of(2031, 3, 5);
    private static final TimeSlot NOON = new TimeSlot(DATE, LocalTime.of(12, 0));

    @Autowired
    private SlotCapacityService capacityService;

    @Autowired
    private SlotCapacityRepository capacityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM establishment_slot_capacities WHERE establishment_id = ? AND slot_date = ?",
                CAFE_ID, DATE);
    }

    private int approvedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT approved_orders_count FROM establishment_slot_capacities WHERE establishment_id = ? AND slot_date = ? AND start_time = ?",
                Integer.class, CAFE_ID, DATE, LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("EC-016: 10 threads on the last place of the slot, exactly one wins and nine are sold out")
    void shouldPreventOvercapacityOnOrder50() throws Exception {
        capacityRepository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, NOON, 50, 49));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    capacityService.consumeSlot(CAFE_ID, NOON);
                    wins.incrementAndGet();
                } catch (SlotSoldOutException e) {
                    soldOut.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(wins.get()).isEqualTo(1);
        assertThat(soldOut.get()).isEqualTo(9);
        assertThat(approvedCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("EC-005 / INV-8: reducing 50 to 30 with 40 approved keeps 40 and sells the slot out")
    void shouldHandleCapacityReduction() {
        capacityRepository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, NOON, 50, 40));
        Long version = capacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(CAFE_ID, DATE, NOON.startTime())
                .orElseThrow().getVersion();

        capacityService.saveCapacities(CAFE_ID, List.of(new SlotCapacityChange(DATE, NOON.startTime(), 30, version)));

        assertThat(approvedCount()).isEqualTo(40);
        assertThat(capacityService.isSoldOut(CAFE_ID, NOON)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM establishment_slot_capacities WHERE establishment_id = ? AND slot_date = ?",
                Integer.class, CAFE_ID, DATE)).isEqualTo(1);
    }

    @Test
    @DisplayName("INV-8: an admin edit that raced with an approval does not overwrite the approved count")
    void shouldNotOverwriteApprovedCountOnConcurrentCapacityEdit() {
        capacityRepository.saveAndFlush(new EstablishmentSlotCapacity(CAFE_ID, NOON, 50, 10));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EstablishmentSlotCapacity loaded = capacityRepository
                    .findByEstablishmentIdAndSlotDateAndStartTime(CAFE_ID, DATE, NOON.startTime()).orElseThrow();
            // an approval lands between the admin's read and write
            jdbcTemplate.update("UPDATE establishment_slot_capacities SET approved_orders_count = approved_orders_count + 1 "
                    + "WHERE establishment_id = ? AND slot_date = ? AND start_time = ?", CAFE_ID, DATE, NOON.startTime());
            loaded.changeMaxCapacity(80);
            capacityRepository.saveAndFlush(loaded);
        });

        assertThat(approvedCount()).isEqualTo(11);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT max_capacity FROM establishment_slot_capacities WHERE establishment_id = ? AND slot_date = ?",
                Integer.class, CAFE_ID, DATE)).isEqualTo(80);
    }
}
