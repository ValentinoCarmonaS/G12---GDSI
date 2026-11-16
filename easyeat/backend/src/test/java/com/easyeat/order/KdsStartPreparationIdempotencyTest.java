package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsStartPreparationResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.KdsQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T015: KdsStartPreparationIdempotencyTest - Concurrency and Idempotency Test")
class KdsStartPreparationIdempotencyTest extends BaseIntegrationTest {

    @Autowired
    private KdsQueueService kdsQueueService;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Establishment testEstablishment;
    private Order testOrder;
    private KdsTicket testTicket;

    @BeforeEach
    void setUp() {
        kdsTicketRepository.deleteAll();
        orderRepository.deleteAll();
        establishmentRepository.deleteAll();
        userRepository.deleteAll();

        Establishment establishment = new Establishment();
        establishment.setName("Restaurante KDS Idempotencia");
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(23, 0));
        testEstablishment = establishmentRepository.save(establishment);

        User diner = new User();
        diner.setEmail("diner.idempotency@easyeat.com");
        diner.setPasswordHash("encoded_pwd");
        diner.setRole(UserRole.COMENSAL);
        diner.setStatus(UserStatus.ACTIVE);
        diner.setTenantId(testEstablishment.getId());
        User savedDiner = userRepository.save(diner);

        Order order = new Order();
        order.setEstablishmentId(testEstablishment.getId());
        order.setUserId(savedDiner.getId());
        order.setTotalAmount(new BigDecimal("1800.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));
        testOrder = orderRepository.save(order);

        KdsTicket ticket = new KdsTicket(
                null,
                testOrder.getId(),
                testEstablishment,
                101,
                KdsTicketStatus.QUEUED,
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                Instant.now()
        );
        testTicket = kdsTicketRepository.save(ticket);
    }

    @Test
    @DisplayName("Peticiones concurrentes deben ejecutar un único cambio de estado y preservar startedAt original")
    void shouldHandleConcurrentStartPreparationIdempotently() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<KdsStartPreparationResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    KdsStartPreparationResponse resp = kdsQueueService.startTicketPreparation(
                            testTicket.getId(),
                            testEstablishment.getId()
                    );
                    responses.add(resp);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(responses).hasSize(threadCount);

        Instant firstStartedAt = responses.get(0).startedAt();
        assertThat(firstStartedAt).isNotNull();

        for (KdsStartPreparationResponse resp : responses) {
            assertThat(resp.status()).isEqualTo(KdsTicketStatus.IN_PREPARATION);
            assertThat(resp.startedAt()).isEqualTo(firstStartedAt);
            assertThat(resp.ticketId()).isEqualTo(testTicket.getId());
        }

        KdsTicket updatedTicket = kdsTicketRepository.findById(testTicket.getId()).orElseThrow();
        assertThat(updatedTicket.getStatus()).isEqualTo(KdsTicketStatus.IN_PREPARATION);
        assertThat(updatedTicket.getStartedAt()).isEqualTo(firstStartedAt);

        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }
}
