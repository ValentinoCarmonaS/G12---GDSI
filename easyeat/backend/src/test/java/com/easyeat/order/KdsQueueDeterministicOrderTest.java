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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T005: KDS Deterministic Queue Ordering Test")
class KdsQueueDeterministicOrderTest extends BaseIntegrationTest {

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Establishment testEstablishment;
    private User testDiner;

    @BeforeEach
    void setUp() {
        kdsTicketRepository.deleteAll();
        orderRepository.deleteAll();
        establishmentRepository.deleteAll();
        userRepository.deleteAll();

        Establishment establishment = new Establishment();
        establishment.setName("Restaurante KDS Central");
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(23, 0));
        testEstablishment = establishmentRepository.save(establishment);

        User diner = new User();
        diner.setEmail("diner.kds." + UUID.randomUUID() + "@easyeat.com");
        diner.setPasswordHash("encodedPassword");
        diner.setRole(UserRole.COMENSAL);
        diner.setStatus(UserStatus.ACTIVE);
        testDiner = userRepository.save(diner);
    }

    @Test
    @DisplayName("Debe ordenar determinísticamente: pickupDate ASC, timeSlotStart ASC, queuedAt ASC, id ASC y excluir pickupDate > hoy")
    void shouldOrderActiveQueueTicketsDeterministicallyAndExcludeFutureDates() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // 1. Ayer (turno vencido de día anterior, QUEUED) -> debe ir primero
        KdsTicket t1OverdueDay = createAndSaveTicket(yesterday, LocalTime.of(20, 0), Instant.parse("2026-11-14T23:00:00Z"), KdsTicketStatus.QUEUED, 1);

        // 2. Hoy, franja 12:00, encolado a las 11:50 -> segundo
        KdsTicket t2Today1200Earlier = createAndSaveTicket(today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:50:00Z"), KdsTicketStatus.IN_PREPARATION, 2);

        // 3. Hoy, franja 12:00, encolado a las 11:55 -> tercero
        KdsTicket t3Today1200Later = createAndSaveTicket(today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:55:00Z"), KdsTicketStatus.QUEUED, 3);

        // 4. Hoy, franja 12:15, encolado a las 11:40 (turno posterior, pero encolado antes -> timeSlot manda) -> cuarto
        KdsTicket t4Today1215 = createAndSaveTicket(today, LocalTime.of(12, 15), Instant.parse("2026-11-15T14:40:00Z"), KdsTicketStatus.QUEUED, 4);

        // 5. Mañana (fecha futura) -> debe ser EXCLUIDO de la cola activa
        createAndSaveTicket(tomorrow, LocalTime.of(10, 0), Instant.parse("2026-11-15T15:00:00Z"), KdsTicketStatus.QUEUED, 5);

        // 6. Hoy, pero estado terminal COMPLETED -> debe ser EXCLUIDO
        createAndSaveTicket(today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:00:00Z"), KdsTicketStatus.COMPLETED, 6);

        // 7. Hoy, pero estado terminal READY_FOR_PICKUP -> debe ser EXCLUIDO
        createAndSaveTicket(today, LocalTime.of(12, 0), Instant.parse("2026-11-15T14:05:00Z"), KdsTicketStatus.READY_FOR_PICKUP, 7);

        List<KdsTicket> activeQueue = kdsTicketRepository.findActiveQueueTickets(testEstablishment.getId(), today);

        assertThat(activeQueue).hasSize(4);
        assertThat(activeQueue.get(0).getId()).isEqualTo(t1OverdueDay.getId());
        assertThat(activeQueue.get(1).getId()).isEqualTo(t2Today1200Earlier.getId());
        assertThat(activeQueue.get(2).getId()).isEqualTo(t3Today1200Later.getId());
        assertThat(activeQueue.get(3).getId()).isEqualTo(t4Today1215.getId());
    }

    private KdsTicket createAndSaveTicket(LocalDate pickupDate, LocalTime slotStart, Instant queuedAt, KdsTicketStatus status, int ticketNum) {
        Order order = new Order();
        order.setEstablishmentId(testEstablishment.getId());
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
                testEstablishment,
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
