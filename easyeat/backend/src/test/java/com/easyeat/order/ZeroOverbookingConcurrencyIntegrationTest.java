package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T032: Concurrency Stress Integration Test - Zero Overbooking FCFS")
class ZeroOverbookingConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("20 concurrent threads competing for final slot: exactly 1 succeeds and 19 receive SlotCapacityExceededException")
    void testZeroOverbookingWith20ConcurrentThreads() throws InterruptedException {
        // Given an establishment with max capacity per slot = 1
        Establishment establishment = createTestEstablishment("Restaurante Alta Demanda");
        establishment.setMaxCapacityPerSlot(1);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Milanesa Napolitana", new BigDecimal("5500.00"));

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(13, 0);

        int threadCount = 20;
        List<User> diners = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            diners.add(createTestDiner("concurrency_diner_" + i + "@test.com"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorsCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            User diner = diners.get(i);
            CreateOrderRequest request = new CreateOrderRequest(
                    establishment.getId(),
                    FulfillmentMode.SCHEDULED,
                    pickupDate,
                    slotStart,
                    slotStart.plusMinutes(15),
                    List.of(new CreateOrderItemRequest(dish.getId(), 1))
            );

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderService.createOrder(diner.getId(), request);
                    successCount.incrementAndGet();
                } catch (SlotCapacityExceededException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrorsCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all threads to be ready, then trigger simultaneously
        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();

        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("All concurrent threads finished within 30 seconds").isTrue();
        assertThat(otherErrorsCount.get()).as("No unexpected errors").isZero();
        assertThat(successCount.get()).as("Exactly 1 order succeeds under FCFS zero overbooking").isEqualTo(1);
        assertThat(conflictCount.get()).as("19 orders are rejected with SlotCapacityExceededException").isEqualTo(19);

        long activeOrdersInDb = orderRepository.countActiveOrdersBySlot(
                establishment.getId(),
                pickupDate,
                slotStart,
                Instant.now()
        );
        assertThat(activeOrdersInDb).as("Database has exactly 1 active order for the saturated slot").isEqualTo(1);
    }
}
