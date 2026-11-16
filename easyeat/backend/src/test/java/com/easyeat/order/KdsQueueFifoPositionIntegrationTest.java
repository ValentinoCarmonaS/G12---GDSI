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
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T009: KDS Queue FIFO Position Integration Test (US1 - COM-8.1.1)")
class KdsQueueFifoPositionIntegrationTest extends BaseIntegrationTest {

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
    private User testDiner;

    @BeforeEach
    void setUp() {
        kdsTicketRepository.deleteAll();
        orderRepository.deleteAll();
        establishmentRepository.deleteAll();
        userRepository.deleteAll();

        Establishment estA = new Establishment();
        estA.setName("Local A FIFO");
        estA.setStatus(EstablishmentStatus.ACTIVE);
        estA.setOpeningTime(LocalTime.of(10, 0));
        estA.setClosingTime(LocalTime.of(23, 0));
        establishmentA = establishmentRepository.save(estA);

        Establishment estB = new Establishment();
        estB.setName("Local B FIFO");
        estB.setStatus(EstablishmentStatus.ACTIVE);
        estB.setOpeningTime(LocalTime.of(10, 0));
        estB.setClosingTime(LocalTime.of(23, 0));
        establishmentB = establishmentRepository.save(estB);

        User diner = new User();
        diner.setEmail("diner.fifo." + UUID.randomUUID() + "@easyeat.com");
        diner.setPasswordHash("encodedPassword");
        diner.setRole(UserRole.COMENSAL);
        diner.setStatus(UserStatus.ACTIVE);
        testDiner = userRepository.save(diner);
    }

    @Test
    @DisplayName("Debe computar determinísticamente N bajo regla FIFO (pickup_date, time_slot_start, queued_at, id), excluir tickets en IN_PREPARATION y dar N=0 al tope")
    void shouldCountQueuedTicketsAheadDeterministically() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // 1. Ayer vencido en cola en Local A (debe ir primero, N = 0)
        KdsTicket t1Yesterday = createTicket(establishmentA, yesterday, LocalTime.of(20, 0), Instant.parse("2026-11-14T23:00:00Z"), KdsTicketStatus.QUEUED, 1);

        // 2. Hoy 12:00 en Local A pero en IN_PREPARATION (NO debe contar como cola previa para tickets posteriores)
        KdsTicket t2Cooking = createTicket(establishmentA, today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:50:00Z"), KdsTicketStatus.IN_PREPARATION, 2);

        // 3. Hoy 12:00 en Local A en QUEUED encolado 14:55
        KdsTicket t3QueuedFirst = createTicket(establishmentA, today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:55:00Z"), KdsTicketStatus.QUEUED, 3);

        // 4. Hoy 12:15 en Local A en QUEUED encolado 14:40 (turno posterior)
        KdsTicket t4QueuedSecond = createTicket(establishmentA, today, LocalTime.of(12, 15), Instant.parse("2026-11-15T14:40:00Z"), KdsTicketStatus.QUEUED, 4);

        // 5. Mañana fecha futura en Local A (debe ser EXCLUIDO de cola activa)
        KdsTicket t5Future = createTicket(establishmentA, tomorrow, LocalTime.of(10, 0), Instant.parse("2026-11-15T15:00:00Z"), KdsTicketStatus.QUEUED, 5);

        // 6. Ticket en Local B (aislamiento multi-tenant, no debe afectar a Local A)
        KdsTicket t6OtherEstablishment = createTicket(establishmentB, today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:30:00Z"), KdsTicketStatus.QUEUED, 6);

        // Verificación caso frontera: t1 es el primero en cola -> N = 0
        long aheadT1 = kdsTicketRepository.countQueuedTicketsAhead(
                establishmentA.getId(),
                t1Yesterday.getPickupDate(),
                t1Yesterday.getTimeSlotStart(),
                t1Yesterday.getQueuedAt(),
                t1Yesterday.getId(),
                today
        );
        assertThat(aheadT1).isEqualTo(0L);

        // Verificación t3: solo t1 está antes en QUEUED (t2 está en IN_PREPARATION por lo que se excluye) -> N = 1
        long aheadT3 = kdsTicketRepository.countQueuedTicketsAhead(
                establishmentA.getId(),
                t3QueuedFirst.getPickupDate(),
                t3QueuedFirst.getTimeSlotStart(),
                t3QueuedFirst.getQueuedAt(),
                t3QueuedFirst.getId(),
                today
        );
        assertThat(aheadT3).isEqualTo(1L);

        // Verificación t4: t1 y t3 están antes en QUEUED -> N = 2
        long aheadT4 = kdsTicketRepository.countQueuedTicketsAhead(
                establishmentA.getId(),
                t4QueuedSecond.getPickupDate(),
                t4QueuedSecond.getTimeSlotStart(),
                t4QueuedSecond.getQueuedAt(),
                t4QueuedSecond.getId(),
                today
        );
        assertThat(aheadT4).isEqualTo(2L);

        // Verificación Local B: t6 es el único en Local B -> N = 0
        long aheadT6 = kdsTicketRepository.countQueuedTicketsAhead(
                establishmentB.getId(),
                t6OtherEstablishment.getPickupDate(),
                t6OtherEstablishment.getTimeSlotStart(),
                t6OtherEstablishment.getQueuedAt(),
                t6OtherEstablishment.getId(),
                today
        );
        assertThat(aheadT6).isEqualTo(0L);
    }

    private KdsTicket createTicket(Establishment establishment, LocalDate pickupDate, LocalTime slotStart, Instant queuedAt, KdsTicketStatus status, int ticketNum) {
        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(testDiner.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(pickupDate);
        order.setTimeSlotStart(slotStart);
        order.setTimeSlotEnd(slotStart.plusMinutes(15));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("1500.00"));
        Order savedOrder = orderRepository.save(order);

        KdsTicket ticket = new KdsTicket(
                null,
                savedOrder.getId(),
                establishment,
                ticketNum,
                status,
                FulfillmentMode.IMMEDIATE,
                pickupDate,
                slotStart,
                slotStart.plusMinutes(15),
                queuedAt
        );
        return kdsTicketRepository.save(ticket);
    }
}
