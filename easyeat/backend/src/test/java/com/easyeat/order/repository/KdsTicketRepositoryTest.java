package com.easyeat.order.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T003: KdsTicketRepository Lock Test")
class KdsTicketRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private KdsTicket createAndPersistTicket() {
        Establishment establishment = new Establishment();
        establishment.setName("Restaurante KDS Lock " + UUID.randomUUID());
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(23, 0));
        establishment = establishmentRepository.save(establishment);

        User user = new User();
        user.setEmail("user-kds-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash123");
        user.setRole(UserRole.COMENSAL);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(user.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setTotalAmount(new BigDecimal("5000.00"));
        order = orderRepository.save(order);

        KdsTicket ticket = new KdsTicket();
        ticket.setOrderId(order.getId());
        ticket.setEstablishment(establishment);
        ticket.setTicketNumber(101);
        ticket.setStatus(KdsTicketStatus.QUEUED);
        ticket.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        ticket.setPickupDate(LocalDate.now());
        ticket.setTimeSlotStart(LocalTime.of(12, 0));
        ticket.setTimeSlotEnd(LocalTime.of(12, 15));
        ticket.setQueuedAt(Instant.now());

        return kdsTicketRepository.save(ticket);
    }

    @Test
    @Transactional
    @DisplayName("findByIdForUpdate should retrieve ticket successfully with pessimistic lock")
    void testFindByIdForUpdateRetrievesTicket() {
        KdsTicket ticket = createAndPersistTicket();

        Optional<KdsTicket> lockedTicket = kdsTicketRepository.findByIdForUpdate(ticket.getId());

        assertThat(lockedTicket).isPresent();
        assertThat(lockedTicket.get().getId()).isEqualTo(ticket.getId());
    }

    @Test
    @DisplayName("findByIdForUpdate should apply exclusive write lock blocking concurrent write")
    void testFindByIdForUpdateBlocksConcurrentAccess() throws Exception {
        KdsTicket ticket = createAndPersistTicket();
        UUID ticketId = ticket.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);
        AtomicBoolean secondTxCompleted = new AtomicBoolean(false);

        Future<?> firstTxFuture = executor.submit(() -> {
            txTemplate.executeWithoutResult(status -> {
                Optional<KdsTicket> locked = kdsTicketRepository.findByIdForUpdate(ticketId);
                assertThat(locked).isPresent();
                lockAcquiredLatch.countDown();
                try {
                    boolean released = releaseLockLatch.await(5, TimeUnit.SECONDS);
                    assertThat(released).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        assertThat(lockAcquiredLatch.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> secondTxFuture = executor.submit(() -> {
            txTemplate.executeWithoutResult(status -> {
                Optional<KdsTicket> locked = kdsTicketRepository.findByIdForUpdate(ticketId);
                assertThat(locked).isPresent();
                secondTxCompleted.set(true);
            });
        });

        // La segunda transacción no debe completarse de inmediato mientras la primera sostiene el lock
        Thread.sleep(500);
        assertThat(secondTxCompleted.get()).isFalse();

        // Liberamos el lock de la primera transacción
        releaseLockLatch.countDown();

        firstTxFuture.get(5, TimeUnit.SECONDS);
        secondTxFuture.get(5, TimeUnit.SECONDS);

        assertThat(secondTxCompleted.get()).isTrue();

        executor.shutdown();
    }
}
