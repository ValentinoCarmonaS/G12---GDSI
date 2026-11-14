package com.easyeat.order;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.DinerOrderSummaryResponse;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.exception.ActiveReservationExistsException;
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private DishRepository dishRepository;

    @Mock
    private com.easyeat.operations.repository.OperatingScheduleRepository operatingScheduleRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private TemporaryReservationRepository temporaryReservationRepository;

    private static final ZoneId TEST_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 11).atTime(14, 0).atZone(TEST_ZONE).toInstant(),
            TEST_ZONE
    );

    private OrderService orderService;

    private UUID establishmentId;
    private UUID userId;
    private UUID dishId;
    private Establishment establishment;
    private Dish dish;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                establishmentRepository,
                dishRepository,
                FIXED_CLOCK,
                operatingScheduleRepository
        );

        establishmentId = UUID.randomUUID();
        userId = UUID.randomUUID();
        dishId = UUID.randomUUID();

        establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setName("Restó Central");
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        establishment.setOpeningTime(LocalTime.of(10, 0));
        establishment.setClosingTime(LocalTime.of(23, 0));
        establishment.setLeadTimeMinutes(30);
        establishment.setMaxCapacityPerSlot(10);
        establishment.setOperatingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY");

        dish = new Dish();
        dish.setId(dishId);
        dish.setTenantId(establishmentId);
        dish.setName("Bife de Chorizo");
        dish.setRetailPrice(new BigDecimal("12000.00"));
        dish.setPrice(new BigDecimal("12000.00"));
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setIsActive(true);
    }

    @Nested
    @DisplayName("Fulfillment Mode Exclusivity and Validation")
    class FulfillmentModeTests {

        @Test
        @DisplayName("Rejects order creation when fulfillmentMode is null")
        void rejectsWhenFulfillmentModeIsNull() {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    null,
                    LocalDate.now(FIXED_CLOCK),
                    LocalTime.of(14, 0),
                    LocalTime.of(14, 15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidFulfillmentModeException.class)
                    .hasMessageContaining("modalidad de retiro");

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("Clears / omits time slot fields when fulfillmentMode is IMMEDIATE")
        void clearsSlotFieldsWhenModeIsImmediate() {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    LocalDate.now(FIXED_CLOCK).plusDays(2),
                    LocalTime.of(18, 0),
                    LocalTime.of(18, 15),
                    List.of(new CreateOrderItemRequest(dishId, 2))
            );

            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));
            when(dishRepository.findByIdIn(List.of(dishId))).thenReturn(List.of(dish));
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                o.setId(UUID.randomUUID());
                return o;
            });

            OrderCreatedResponse response = orderService.createOrder(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getFulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            assertThat(savedOrder.getFulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);
            assertThat(savedOrder.getPickupDate()).isEqualTo(LocalDate.now(FIXED_CLOCK));
            assertThat(savedOrder.getPickupDate()).isNotEqualTo(LocalDate.now(FIXED_CLOCK).plusDays(2));
        }

        @Test
        @DisplayName("Requires pickupDate and timeSlotStart when fulfillmentMode is SCHEDULED")
        void requiresSlotWhenModeIsScheduled() {
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidFulfillmentModeException.class);

            verifyNoInteractions(orderRepository);
        }
    }

    @Nested
    @DisplayName("FR-019 / Spec 013: Chef Manual Pause Blocking")
    class ChefPauseTests {

        @Test
        @DisplayName("Blocks checkout with HTTP 422 DISH_PAUSED_BY_CHEF when dish has active pause by chef")
        void blocksWhenDishIsPausedByChef() {
            dish.setDescription("[PAUSED_BY_CHEF] Temporalmente agotado en cocina");

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));
            when(dishRepository.findByIdIn(List.of(dishId))).thenReturn(List.of(dish));

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(DishPausedByChefException.class)
                    .hasMessageContaining("Bife de Chorizo");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("US4: Concurrency, Zero Overbooking and Anti-Acaparamiento")
    class US4CapacityAndReservationTests {

        @Test
        @DisplayName("Rejects order creation when diner already has an active pending reservation (FR-027)")
        void rejectsWhenDinerHasActivePendingReservation() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(true);

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.now(FIXED_CLOCK).plusDays(1),
                    LocalTime.of(13, 0),
                    LocalTime.of(13, 15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(ActiveReservationExistsException.class)
                    .hasMessageContaining("pendiente de confirmación");

            verify(establishmentRepository, never()).findByIdForUpdate(any());
        }

        @Test
        @DisplayName("Rejects order creation with SlotCapacityExceededException when slot capacity is full (Zero Overbooking)")
        void rejectsWhenSlotCapacityIsFull() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate targetDate = LocalDate.now(FIXED_CLOCK).plusDays(1);
            LocalTime slotStart = LocalTime.of(13, 0);

            // Mock that slot already has 10 booked orders (maxCapacity = 10)
            when(orderRepository.countActiveOrdersBySlot(eq(establishmentId), eq(targetDate), eq(slotStart), any()))
                    .thenReturn(10L);

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    targetDate,
                    slotStart,
                    slotStart.plusMinutes(15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(SlotCapacityExceededException.class)
                    .hasMessageContaining("acaba de agotarse");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("C-12 & FR-038: Operating Schedule Validation (Hours & Closed Days)")
    class OperatingScheduleValidationTests {

        @Test
        @DisplayName("Rejects scheduled order when operating schedule is marked closed for that day (FR-038)")
        void rejectsScheduledOrderWhenScheduleIsClosed() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate futureDate = LocalDate.now(FIXED_CLOCK).plusDays(2);
            com.easyeat.operations.domain.EstablishmentOperatingSchedule closedSched =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, futureDate.getDayOfWeek(), LocalTime.of(11, 0), LocalTime.of(15, 0), true
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, futureDate.getDayOfWeek()))
                    .thenReturn(Optional.of(closedSched));

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    futureDate,
                    LocalTime.of(12, 0),
                    LocalTime.of(12, 15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("no se encuentra operativo");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects scheduled order when slot is outside operating schedule opening/closing hours (FR-038)")
        void rejectsScheduledOrderWhenSlotOutsideOperatingHours() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate futureDate = LocalDate.now(FIXED_CLOCK).plusDays(2);
            // Open only 13:00 to 15:00
            com.easyeat.operations.domain.EstablishmentOperatingSchedule schedule =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, futureDate.getDayOfWeek(), LocalTime.of(13, 0), LocalTime.of(15, 0), false
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, futureDate.getDayOfWeek()))
                    .thenReturn(Optional.of(schedule));

            // Attempting 11:30 (before opening 13:00)
            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    futureDate,
                    LocalTime.of(11, 30),
                    LocalTime.of(11, 45),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("fuera del horario de atención");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects immediate order when operating schedule is closed right now (FR-038)")
        void rejectsImmediateOrderWhenScheduleIsClosedNow() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate today = LocalDate.now(FIXED_CLOCK);
            com.easyeat.operations.domain.EstablishmentOperatingSchedule closedSched =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, today.getDayOfWeek(), LocalTime.of(11, 0), LocalTime.of(15, 0), true
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, today.getDayOfWeek()))
                    .thenReturn(Optional.of(closedSched));

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cerrado para pedidos inmediatos");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Allows scheduled order on upcoming MONDAY when establishment is open (C-12)")
        void allowsScheduledOrderOnMondayWhenOpen() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));
            when(dishRepository.findByIdIn(List.of(dishId))).thenReturn(List.of(dish));

            // Next Monday
            LocalDate monday = LocalDate.now(FIXED_CLOCK);
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }
            if (monday.equals(LocalDate.now(FIXED_CLOCK))) {
                monday = monday.plusDays(7);
            }

            com.easyeat.operations.domain.EstablishmentOperatingSchedule mondaySched =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(mondaySched));
            when(orderRepository.countActiveOrdersBySlot(eq(establishmentId), eq(monday), any(), any())).thenReturn(0L);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    monday,
                    LocalTime.of(10, 0),
                    LocalTime.of(10, 15),
                    List.of(new CreateOrderItemRequest(dishId, 2))
            );

            OrderCreatedResponse response = orderService.createOrder(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getFulfillmentMode()).isEqualTo(FulfillmentMode.SCHEDULED);
            assertThat(response.getPickupDate()).isEqualTo(monday);
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Rejects scheduled order on MONDAY when closed in operating schedule (C-12)")
        void rejectsScheduledOrderOnMondayWhenClosed() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate monday = LocalDate.now(FIXED_CLOCK);
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            com.easyeat.operations.domain.EstablishmentOperatingSchedule mondayClosed =
                    new com.easyeat.operations.domain.EstablishmentOperatingSchedule(
                            establishmentId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), true
                    );
            when(operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, DayOfWeek.MONDAY))
                    .thenReturn(Optional.of(mondayClosed));

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    monday,
                    LocalTime.of(10, 0),
                    LocalTime.of(10, 15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(userId, request))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("no se encuentra operativo");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("US4: Order Query and Voluntary Cancellation")
    class US4CancellationAndQueryTests {

        @Test
        @DisplayName("Successfully cancels a PENDING_PAYMENT order voluntarily and releases capacity")
        void cancelsPendingOrderSuccessfully() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order();
            order.setId(orderId);
            order.setUserId(userId);
            order.setEstablishmentId(establishmentId);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setExpiresAt(Instant.now(FIXED_CLOCK).plus(10, ChronoUnit.MINUTES));

            when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderSummaryResponse summary = orderService.cancelOrder(orderId, userId);

            assertThat(summary).isNotNull();
            assertThat(summary.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Rejects voluntary cancellation when order is not in PENDING_PAYMENT state")
        void rejectsCancellationWhenAlreadyConfirmed() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order();
            order.setId(orderId);
            order.setUserId(userId);
            order.setStatus(OrderStatus.CONFIRMED);

            when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("no se encuentra en estado cancelable");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("getOrderById returns order detail and remaining seconds")
        void returnsOrderDetailWithRemainingSeconds() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order();
            order.setId(orderId);
            order.setUserId(userId);
            order.setEstablishmentId(establishmentId);
            order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
            order.setPickupDate(LocalDate.now(FIXED_CLOCK).plusDays(1));
            order.setTimeSlotStart(LocalTime.of(14, 0));
            order.setTimeSlotEnd(LocalTime.of(14, 15));
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setTotalAmount(new BigDecimal("12000.00"));
            order.setExpiresAt(Instant.now(FIXED_CLOCK).plus(600, ChronoUnit.SECONDS));

            when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

            OrderDetailResponse detail = orderService.getOrderById(orderId, userId);

            assertThat(detail).isNotNull();
            assertThat(detail.id()).isEqualTo(orderId);
            assertThat(detail.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(detail.remainingSeconds()).isGreaterThan(0L);
        }
    }

    @Nested
    @DisplayName("Get Diner Orders (US4)")
    class GetDinerOrdersTests {

        private OrderService buildServiceWithAllDeps() {
            return new OrderService(
                    orderRepository,
                    establishmentRepository,
                    dishRepository,
                    FIXED_CLOCK,
                    operatingScheduleRepository,
                    paymentRepository,
                    kdsTicketRepository,
                    temporaryReservationRepository,
                    new MercadoPagoProperties(null, null, null, true, "https://api.mercadopago.com", "http://localhost:3000", "")
            );
        }

        private Order buildOrder(UUID id,
                                 UUID ownerUserId,
                                 OrderStatus status,
                                 Instant createdAt,
                                 Instant expiresAt) {
            Order order = new Order(
                    id,
                    establishmentId,
                    ownerUserId,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.of(2026, 11, 11),
                    LocalTime.of(19, 0),
                    LocalTime.of(19, 15),
                    status,
                    new BigDecimal("24000.00"),
                    expiresAt
            );
            order.setCreatedAt(createdAt);
            order.setUpdatedAt(createdAt);
            order.addItem(new OrderItem(
                    UUID.randomUUID(),
                    order,
                    dishId,
                    "Bife de Chorizo",
                    new BigDecimal("12000.00"),
                    2,
                    new BigDecimal("24000.00")
            ));
            return order;
        }

        @Test
        @DisplayName("Returns user orders in descending repository order with real ticketNumber or null and initPoint priority")
        void returnsOrdersSortedWithTicketNumberAndInitPointPriority() {
            OrderService service = buildServiceWithAllDeps();
            Instant now = FIXED_CLOCK.instant();

            UUID confirmedOrderId = UUID.randomUUID();
            Order confirmedOrder = buildOrder(
                    confirmedOrderId,
                    userId,
                    OrderStatus.CONFIRMED,
                    now.minusSeconds(300),
                    now.plusSeconds(600)
            );

            UUID pendingOrderId = UUID.randomUUID();
            Order pendingOrder = buildOrder(
                    pendingOrderId,
                    userId,
                    OrderStatus.PENDING_PAYMENT,
                    now.minusSeconds(900),
                    now.plusSeconds(300)
            );

            KdsTicket ticket = new KdsTicket(
                    UUID.randomUUID(),
                    confirmedOrderId,
                    establishment,
                    104,
                    KdsTicketStatus.QUEUED,
                    FulfillmentMode.SCHEDULED,
                    LocalDate.of(2026, 11, 11),
                    LocalTime.of(19, 0),
                    LocalTime.of(19, 15),
                    now.minusSeconds(250)
            );

            TemporaryReservation confirmedReservation = new TemporaryReservation(
                    confirmedOrder,
                    "res-confirmed",
                    now.plusSeconds(600)
            );
            Payment confirmedPayment = new Payment(
                    confirmedOrder,
                    confirmedReservation,
                    PaymentMethod.MERCADO_PAGO,
                    confirmedOrderId.toString(),
                    new BigDecimal("24000.00"),
                    "pay-confirmed"
            );
            confirmedPayment.assignGatewayPreference(
                    "pref-confirmed",
                    "https://mp.example.com/init-confirmed",
                    "https://mp.example.com/sandbox-confirmed"
            );

            TemporaryReservation pendingReservation = new TemporaryReservation(
                    pendingOrder,
                    "res-pending",
                    now.plusSeconds(300)
            );
            Payment pendingPayment = new Payment(
                    pendingOrder,
                    pendingReservation,
                    PaymentMethod.MERCADO_PAGO,
                    pendingOrderId.toString(),
                    new BigDecimal("24000.00"),
                    "pay-pending"
            );
            pendingPayment.assignGatewayPreference(
                    "pref-pending",
                    "https://mp.example.com/init-pending",
                    "https://mp.example.com/sandbox-pending"
            );

            when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(confirmedOrder, pendingOrder));
            when(establishmentRepository.findById(establishmentId))
                    .thenReturn(Optional.of(establishment));
            when(kdsTicketRepository.findByOrderId(confirmedOrderId))
                    .thenReturn(Optional.of(ticket));
            when(kdsTicketRepository.findByOrderId(pendingOrderId))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(pendingOrderId))
                    .thenReturn(Optional.of(pendingPayment));

            List<DinerOrderSummaryResponse> results = service.getDinerOrders(userId);

            verify(orderRepository).findByUserIdOrderByCreatedAtDesc(userId);
            assertThat(results).hasSize(2);

            DinerOrderSummaryResponse first = results.get(0);
            assertThat(first.orderId()).isEqualTo(confirmedOrderId);
            assertThat(first.ticketNumber()).isEqualTo(104);
            assertThat(first.establishmentName()).isEqualTo("Restó Central");
            assertThat(first.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(first.redirectUrl()).isNull();
            assertThat(first.trackingUrl()).isEqualTo("/pedidos/" + confirmedOrderId + "/seguimiento");
            assertThat(first.items()).hasSize(1);
            assertThat(first.items().get(0).dishName()).isEqualTo("Bife de Chorizo");
            assertThat(first.items().get(0).quantity()).isEqualTo(2);

            DinerOrderSummaryResponse second = results.get(1);
            assertThat(second.orderId()).isEqualTo(pendingOrderId);
            assertThat(second.ticketNumber()).isNull();
            assertThat(second.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(second.redirectUrl()).isEqualTo("https://mp.example.com/init-pending");
            assertThat(second.trackingUrl()).isEqualTo("/pedidos/" + pendingOrderId + "/seguimiento");
            verify(paymentRepository, never()).findTopByOrderIdOrderByCreatedAtDesc(confirmedOrderId);
        }

        @Test
        @DisplayName("Falls back to sandboxInitPoint or returns null redirectUrl when initPoint is absent")
        void fallsBackToSandboxOrNullRedirectUrlForPendingOrder() {
            OrderService service = buildServiceWithAllDeps();
            Instant now = FIXED_CLOCK.instant();

            UUID sandboxOrderId = UUID.randomUUID();
            Order sandboxOrder = buildOrder(
                    sandboxOrderId,
                    userId,
                    OrderStatus.PENDING_PAYMENT,
                    now.minusSeconds(100),
                    now.plusSeconds(600)
            );
            TemporaryReservation sandboxRes = new TemporaryReservation(sandboxOrder, "res-sbx", now.plusSeconds(600));
            Payment sandboxPayment = new Payment(
                    sandboxOrder,
                    sandboxRes,
                    PaymentMethod.MERCADO_PAGO,
                    sandboxOrderId.toString(),
                    new BigDecimal("24000.00"),
                    "pay-sbx"
            );
            sandboxPayment.assignGatewayPreference("pref-sbx", "   ", "https://sandbox.mercadopago.com.ar/checkout");

            UUID noUrlOrderId = UUID.randomUUID();
            Order noUrlOrder = buildOrder(
                    noUrlOrderId,
                    userId,
                    OrderStatus.PENDING_PAYMENT,
                    now.minusSeconds(200),
                    now.plusSeconds(500)
            );

            when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(sandboxOrder, noUrlOrder));
            when(establishmentRepository.findById(establishmentId))
                    .thenReturn(Optional.of(establishment));
            when(kdsTicketRepository.findByOrderId(any())).thenReturn(Optional.empty());
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(sandboxOrderId))
                    .thenReturn(Optional.of(sandboxPayment));
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(noUrlOrderId))
                    .thenReturn(Optional.empty());

            List<DinerOrderSummaryResponse> results = service.getDinerOrders(userId);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).redirectUrl()).isEqualTo("https://sandbox.mercadopago.com.ar/checkout");
            assertThat(results.get(1).redirectUrl()).isNull();
        }

        @Test
        @DisplayName("Lazily expires PENDING_PAYMENT order, active reservation and IN_PROCESS payment when expiresAt <= now")
        void lazilyExpiresOrderReservationAndPaymentAtOrBeforeNow() {
            OrderService service = buildServiceWithAllDeps();
            Instant now = FIXED_CLOCK.instant();

            UUID expiredBeforeId = UUID.randomUUID();
            Order expiredBefore = buildOrder(
                    expiredBeforeId,
                    userId,
                    OrderStatus.PENDING_PAYMENT,
                    now.minusSeconds(1200),
                    now.minusSeconds(1)
            );
            TemporaryReservation activeResBefore = new TemporaryReservation(
                    expiredBefore,
                    "res-before",
                    now.minusSeconds(1)
            );
            Payment inProcessPayBefore = new Payment(
                    expiredBefore,
                    activeResBefore,
                    PaymentMethod.MERCADO_PAGO,
                    expiredBeforeId.toString(),
                    new BigDecimal("24000.00"),
                    "pay-before"
            );
            inProcessPayBefore.assignGatewayPreference("pref-before", "https://mp.example.com/expired", null);

            UUID expiredExactNowId = UUID.randomUUID();
            Order expiredExactNow = buildOrder(
                    expiredExactNowId,
                    userId,
                    OrderStatus.PENDING_PAYMENT,
                    now.minusSeconds(900),
                    now
            );
            TemporaryReservation activeResNow = new TemporaryReservation(
                    expiredExactNow,
                    "res-now",
                    now
            );
            Payment inProcessPayNow = new Payment(
                    expiredExactNow,
                    activeResNow,
                    PaymentMethod.MERCADO_PAGO,
                    expiredExactNowId.toString(),
                    new BigDecimal("24000.00"),
                    "pay-now"
            );

            when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(expiredBefore, expiredExactNow));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(establishmentRepository.findById(establishmentId))
                    .thenReturn(Optional.of(establishment));
            when(kdsTicketRepository.findByOrderId(any())).thenReturn(Optional.empty());
            when(temporaryReservationRepository.findByOrderId(expiredBeforeId))
                    .thenReturn(Optional.of(activeResBefore));
            when(temporaryReservationRepository.findByOrderId(expiredExactNowId))
                    .thenReturn(Optional.of(activeResNow));
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(expiredBeforeId))
                    .thenReturn(Optional.of(inProcessPayBefore));
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(expiredExactNowId))
                    .thenReturn(Optional.of(inProcessPayNow));

            List<DinerOrderSummaryResponse> results = service.getDinerOrders(userId);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).status()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(results.get(0).redirectUrl()).isNull();
            assertThat(results.get(1).status()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(results.get(1).redirectUrl()).isNull();

            assertThat(activeResBefore.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
            assertThat(activeResBefore.getReleaseReason()).isEqualTo(ReleaseReason.EXPIRED);
            assertThat(activeResNow.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
            assertThat(inProcessPayBefore.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
            assertThat(inProcessPayNow.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

            verify(orderRepository).save(expiredBefore);
            verify(orderRepository).save(expiredExactNow);
            verify(temporaryReservationRepository).save(activeResBefore);
            verify(temporaryReservationRepository).save(activeResNow);
            verify(paymentRepository).save(inProcessPayBefore);
            verify(paymentRepository).save(inProcessPayNow);
        }

        @Test
        @DisplayName("Queries only the requested user's orders preserving isolation")
        void enforcesUserIsolation() {
            OrderService service = buildServiceWithAllDeps();
            UUID otherUserId = UUID.randomUUID();
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(otherUserId))
                    .thenReturn(List.of());

            List<DinerOrderSummaryResponse> results = service.getDinerOrders(otherUserId);

            assertThat(results).isEmpty();
            verify(orderRepository).findByUserIdOrderByCreatedAtDesc(otherUserId);
            verify(orderRepository, never()).findByUserIdOrderByCreatedAtDesc(userId);
        }
    }

    @Nested
    @DisplayName("US3: Conflict orderId, 403 Ownership and Joint Cancellation")
    class ActiveConflictAndCancellationTests {

        private OrderService buildServiceWithAllDeps() {
            return new OrderService(
                    orderRepository,
                    establishmentRepository,
                    dishRepository,
                    FIXED_CLOCK,
                    operatingScheduleRepository,
                    paymentRepository,
                    kdsTicketRepository,
                    temporaryReservationRepository,
                    new MercadoPagoProperties(null, null, null, true, "https://api.mercadopago.com", "http://localhost:3000", "")
            );
        }

        @Test
        @DisplayName("createOrder includes activeOrderId in ActiveReservationExistsException when active pending order exists")
        void createOrderIncludesActiveOrderIdInConflict() {
            OrderService service = buildServiceWithAllDeps();
            UUID activeOrderId = UUID.randomUUID();
            Order activeOrder = new Order();
            activeOrder.setId(activeOrderId);
            activeOrder.setUserId(userId);
            activeOrder.setStatus(OrderStatus.PENDING_PAYMENT);
            activeOrder.setExpiresAt(FIXED_CLOCK.instant().plus(10, ChronoUnit.MINUTES));

            when(orderRepository.findActivePendingOrders(eq(userId), any()))
                    .thenReturn(List.of(activeOrder));

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.IMMEDIATE,
                    null,
                    null,
                    null,
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            assertThatThrownBy(() -> service.createOrder(userId, request))
                    .isInstanceOf(ActiveReservationExistsException.class)
                    .satisfies(ex -> assertThat(((ActiveReservationExistsException) ex).getOrderId())
                            .isEqualTo(activeOrderId));
        }

        @Test
        @DisplayName("cancelOrder throws AccessDeniedException when order belongs to a different user")
        void cancelOrderThrowsAccessDeniedForDifferentUser() {
            OrderService service = buildServiceWithAllDeps();
            UUID orderId = UUID.randomUUID();
            UUID intruderId = UUID.randomUUID();

            Order ownedOrder = new Order();
            ownedOrder.setId(orderId);
            ownedOrder.setUserId(userId);
            ownedOrder.setStatus(OrderStatus.PENDING_PAYMENT);

            when(orderRepository.findByIdAndUserId(orderId, intruderId)).thenReturn(Optional.empty());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(ownedOrder));

            assertThatThrownBy(() -> service.cancelOrder(orderId, intruderId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("cancelOrder releases TemporaryReservation and cancels Payment alongside Order")
        void cancelOrderReleasesReservationAndCancelsPayment() {
            OrderService service = buildServiceWithAllDeps();
            UUID orderId = UUID.randomUUID();
            Order order = new Order();
            order.setId(orderId);
            order.setUserId(userId);
            order.setEstablishmentId(establishmentId);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setExpiresAt(FIXED_CLOCK.instant().plus(10, ChronoUnit.MINUTES));

            TemporaryReservation reservation = new TemporaryReservation(
                    order,
                    "res-cancel",
                    FIXED_CLOCK.instant().plus(10, ChronoUnit.MINUTES)
            );
            Payment payment = new Payment(
                    order,
                    reservation,
                    PaymentMethod.MERCADO_PAGO,
                    orderId.toString(),
                    new BigDecimal("12000.00"),
                    "pay-cancel"
            );

            when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(temporaryReservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
            when(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(payment));

            OrderSummaryResponse response = service.cancelOrder(orderId, userId);

            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
            assertThat(reservation.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED_BY_USER);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(temporaryReservationRepository).save(reservation);
            verify(paymentRepository).save(payment);
        }
    }
}
