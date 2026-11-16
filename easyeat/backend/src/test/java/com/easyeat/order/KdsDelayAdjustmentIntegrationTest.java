package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsDelayIncrementResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
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
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T018: KdsDelayAdjustmentIntegrationTest - Atomic Granular Delay (+5 min) (US2 - COM-7.3.2 BIS)")
class KdsDelayAdjustmentIntegrationTest extends BaseIntegrationTest {

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

    private Establishment establishmentA;
    private Establishment establishmentB;
    private Order testOrder;
    private KdsTicket inPrepTicket;

    @BeforeEach
    void setUp() {
        kdsTicketRepository.deleteAll();
        orderRepository.deleteAll();
        establishmentRepository.deleteAll();
        userRepository.deleteAll();

        Establishment estA = new Establishment();
        estA.setName("Local Cocina Central");
        estA.setStatus(EstablishmentStatus.ACTIVE);
        estA.setOpeningTime(LocalTime.of(8, 0));
        estA.setClosingTime(LocalTime.of(23, 0));
        establishmentA = establishmentRepository.save(estA);

        Establishment estB = new Establishment();
        estB.setName("Local Sucursal Norte");
        estB.setStatus(EstablishmentStatus.ACTIVE);
        estB.setOpeningTime(LocalTime.of(8, 0));
        estB.setClosingTime(LocalTime.of(23, 0));
        establishmentB = establishmentRepository.save(estB);

        User chef = new User();
        chef.setEmail("chef.central@easyeat.com");
        chef.setPasswordHash("hashed_pass");
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.ACTIVE);
        chef.setTenantId(establishmentA.getId());
        userRepository.save(chef);

        testOrder = new Order();
        testOrder.setEstablishmentId(establishmentA.getId());
        testOrder.setUserId(chef.getId());
        testOrder.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        testOrder.setPickupDate(LocalDate.now());
        testOrder.setTimeSlotStart(LocalTime.of(12, 0));
        testOrder.setTimeSlotEnd(LocalTime.of(12, 15));
        testOrder.setStatus(OrderStatus.IN_PROGRESS);
        testOrder.setTotalAmount(new BigDecimal("5000.00"));
        testOrder = orderRepository.save(testOrder);

        inPrepTicket = new KdsTicket(
                null,
                testOrder.getId(),
                establishmentA,
                101,
                KdsTicketStatus.IN_PREPARATION,
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                Instant.now()
        );
        inPrepTicket.setStartedAt(Instant.now());
        inPrepTicket.setSpecificDelayMinutes(0);
        inPrepTicket = kdsTicketRepository.save(inPrepTicket);
    }

    @Test
    @DisplayName("Debe incrementar atómicamente la demora en 15 min ante 3 pulsaciones rápidas concurrentes (+5 cada una)")
    void shouldIncrementDelayAtomicallyUnderConcurrentInvocations() throws Exception {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<KdsDelayIncrementResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    KdsDelayIncrementResponse response = kdsQueueService.addTicketDelay(
                            inPrepTicket.getId(),
                            establishmentA.getId()
                    );
                    responses.add(response);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(responses).hasSize(3);

        // Verificamos que la demora persistida final sea exactamente +15 min acumulados
        KdsTicket reloaded = kdsTicketRepository.findById(inPrepTicket.getId()).orElseThrow();
        assertThat(reloaded.getSpecificDelayMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("Debe lanzar NoSuchElementException (404) si el ticketId no existe")
    void shouldThrowNotFoundWhenTicketDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        assertThatThrownBy(() -> kdsQueueService.addTicketDelay(nonExistentId, establishmentA.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Debe lanzar TenantViolationException (403) si el ticket pertenece a otro establecimiento")
    void shouldThrowTenantViolationWhenTicketBelongsToOtherEstablishment() {
        assertThatThrownBy(() -> kdsQueueService.addTicketDelay(inPrepTicket.getId(), establishmentB.getId()))
                .isInstanceOf(TenantViolationException.class);
    }

    @Test
    @DisplayName("Debe lanzar InvalidOrderStateException (409) si el ticket no está en IN_PREPARATION (ej. QUEUED)")
    void shouldThrowConflictWhenTicketNotInPreparation() {
        inPrepTicket.setStatus(KdsTicketStatus.QUEUED);
        kdsTicketRepository.save(inPrepTicket);

        assertThatThrownBy(() -> kdsQueueService.addTicketDelay(inPrepTicket.getId(), establishmentA.getId()))
                .isInstanceOf(InvalidOrderStateException.class);
    }
}
