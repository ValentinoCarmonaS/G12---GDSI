package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.repository.EstablishmentKdsSettingsRepository;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.OrderLiveTrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KdsReadyForPickupIdempotencyTest (T009, US1, RF-002, RF-003, Invariante 1 y 2)")
class KdsReadyForPickupIdempotencyTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentKdsSettingsRepository kdsSettingsRepository;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Test
    @DisplayName("T009: Reintentos secuenciales sobre comanda ya en READY_FOR_PICKUP preservan el ready_at original sin volver a mutar")
    void sequentialRetriesShouldPreserveOriginalReadyAtAndAvoidDuplicateMutations() {
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        Establishment est = new Establishment();
        est.setId(UUID.randomUUID());

        KdsTicket ticket = new KdsTicket(
                ticketId, orderId, est, 104,
                KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 15), LocalTime.of(13, 0), LocalTime.of(13, 15),
                Instant.parse("2026-11-15T19:15:00Z")
        );
        Order order = new Order();
        order.setId(orderId);
        order.setEstablishmentId(est.getId());
        order.setStatus(OrderStatus.IN_PROGRESS);

        Instant firstInstant = Instant.parse("2026-11-15T19:30:00.000Z");
        Instant secondInstant = Instant.parse("2026-11-15T19:35:00.000Z");
        AtomicInteger callCount = new AtomicInteger(0);

        Clock advancingClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return callCount.getAndIncrement() == 0 ? firstInstant : secondInstant;
            }
        };

        when(kdsTicketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        lenient().when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        OrderLiveTrackingService service = new OrderLiveTrackingService(
                orderRepository,
                kdsSettingsRepository,
                null,
                advancingClock,
                kdsTicketRepository
        );

        // 1. Primera llamada: transiciona a READY_FOR_PICKUP con firstInstant
        KdsTicketResponse firstResponse = service.markTicketReadyForPickup(ticketId, chefId);
        assertThat(firstResponse.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(firstResponse.readyAt()).isEqualTo(firstInstant);
        assertThat(ticket.getReadyAt()).isEqualTo(firstInstant);
        assertThat(order.getReadyAt()).isEqualTo(firstInstant);

        // 2. Segunda llamada (reintento 5 minutos después): debe ser idempotente y devolver firstInstant
        KdsTicketResponse retryResponse = service.markTicketReadyForPickup(ticketId, chefId);
        assertThat(retryResponse.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(retryResponse.readyAt()).isEqualTo(firstInstant);
        assertThat(ticket.getReadyAt()).isEqualTo(firstInstant);
        assertThat(order.getReadyAt()).isEqualTo(firstInstant);

        // Solo se debió persistir en la primera transición
        verify(kdsTicketRepository, times(1)).save(ticket);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("T009: Peticiones concurrentes bajo bloqueo pesimista ejecutan un único cambio de estado y retornan idéntico ready_at")
    void concurrentRequestsUnderPessimisticLockShouldExecuteSingleTransitionAndPreserveReadyAt() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID chefId = UUID.randomUUID();
        Establishment est = new Establishment();
        est.setId(UUID.randomUUID());

        KdsTicket ticket = new KdsTicket(
                ticketId, orderId, est, 108,
                KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 15), LocalTime.of(13, 0), LocalTime.of(13, 15),
                Instant.parse("2026-11-15T19:20:00Z")
        );
        Order order = new Order();
        order.setId(orderId);
        order.setEstablishmentId(est.getId());
        order.setStatus(OrderStatus.IN_PROGRESS);

        Instant baseInstant = Instant.parse("2026-11-15T19:30:00.000Z");
        AtomicInteger clockTick = new AtomicInteger(0);

        Clock tickingClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return baseInstant.plusSeconds(clockTick.getAndIncrement() * 10L);
            }
        };

        // Simular el bloqueo exclusivo de fila PESSIMISTIC_WRITE de PostgreSQL durante cada transacción
        ReentrantLock rowLock = new ReentrantLock();
        AtomicInteger ticketSaves = new AtomicInteger(0);
        AtomicInteger orderSaves = new AtomicInteger(0);

        when(kdsTicketRepository.findByIdForUpdate(ticketId)).thenAnswer(inv -> {
            rowLock.lock();
            return Optional.of(ticket);
        });
        lenient().when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        lenient().when(kdsTicketRepository.save(ticket)).thenAnswer(inv -> {
            ticketSaves.incrementAndGet();
            return ticket;
        });
        lenient().when(orderRepository.save(order)).thenAnswer(inv -> {
            orderSaves.incrementAndGet();
            return order;
        });

        OrderLiveTrackingService service = new OrderLiveTrackingService(
                orderRepository,
                kdsSettingsRepository,
                null,
                tickingClock,
                kdsTicketRepository
        );

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<KdsTicketResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    return service.markTicketReadyForPickup(ticketId, chefId);
                } finally {
                    if (rowLock.isHeldByCurrentThread()) {
                        rowLock.unlock();
                    }
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        List<KdsTicketResponse> responses = new ArrayList<>();
        for (Future<KdsTicketResponse> future : futures) {
            responses.add(future.get());
        }

        assertThat(responses).hasSize(threadCount);
        Instant winningReadyAt = ticket.getReadyAt();
        assertThat(winningReadyAt).isNotNull();
        assertThat(order.getReadyAt()).isEqualTo(winningReadyAt);

        assertThat(responses)
                .allSatisfy(resp -> {
                    assertThat(resp.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
                    assertThat(resp.readyAt()).isEqualTo(winningReadyAt);
                    assertThat(resp.ticketNumber()).isEqualTo(108);
                });

        // Exactamente 1 transición efectiva persistida entre las 4 hebras concurrentes
        assertThat(ticketSaves.get()).isEqualTo(1);
        assertThat(orderSaves.get()).isEqualTo(1);
    }
}
