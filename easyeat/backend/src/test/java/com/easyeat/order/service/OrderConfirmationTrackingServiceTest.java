package com.easyeat.order.service;

import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T012: OrderConfirmationTrackingService Unit Tests")
class OrderConfirmationTrackingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @InjectMocks
    private OrderConfirmationTrackingService service;

    private UUID orderId;
    private UUID userId;
    private UUID establishmentId;
    private Establishment establishment;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        establishmentId = UUID.randomUUID();

        establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setName("Bistró Real");
    }

    @Test
    @DisplayName("Lee ticketNumber real desde KdsTicketRepository para órdenes en cocina")
    void shouldReadRealTicketNumberFromKdsTicketRepository() {
        Order order = buildOrder(OrderStatus.IN_PROGRESS);
        KdsTicket ticket = new KdsTicket(
                UUID.randomUUID(),
                orderId,
                establishment,
                7,
                KdsTicketStatus.QUEUED,
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 30),
                Instant.now()
        );

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));
        when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        OrderConfirmationTrackingResponse response = service.getConfirmationTracking(orderId, userId);

        assertThat(response.ticketNumber()).isEqualTo(7);
        assertThat(response.approvalStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("PENDING_ACCREDITATION sin comanda devuelve ticketNumber null y approvalStatus PENDING")
    void pendingAccreditationWithoutTicketShouldReturnNullTicketNumberAndPendingStatus() {
        Order order = buildOrder(OrderStatus.PENDING_ACCREDITATION);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));
        when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderConfirmationTrackingResponse response = service.getConfirmationTracking(orderId, userId);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_ACCREDITATION);
        assertThat(response.approvalStatus()).isEqualTo("PENDING");
        assertThat(response.ticketNumber()).isNull();
    }

    @Test
    @DisplayName("Acepta estados PAID y CONFIRMED como APPROVED")
    void shouldAcceptPaidAndConfirmedAsApproved() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.PAID, OrderStatus.CONFIRMED}) {
            Order order = buildOrder(status);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));
            when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            OrderConfirmationTrackingResponse response = service.getConfirmationTracking(orderId, userId);

            assertThat(response.approvalStatus()).isEqualTo("APPROVED");
            assertThat(response.ticketNumber()).isNull();
        }
    }

    @Test
    @DisplayName("Bloquea CANCELLED, EXPIRED y PENDING_PAYMENT con InvalidOrderStateException")
    void shouldBlockCancelledExpiredAndPendingPaymentOrders() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.PENDING_PAYMENT}) {
            Order order = buildOrder(status);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.getConfirmationTracking(orderId, userId))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setEstablishmentId(establishmentId);
        order.setStatus(status);
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 30));
        order.setTotalAmount(new BigDecimal("4200.00"));
        return order;
    }
}

