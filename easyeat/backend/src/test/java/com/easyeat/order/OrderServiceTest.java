package com.easyeat.order;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.repository.SlotCapacityRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.exception.ActiveReservationExistsException;
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
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
    private SlotCapacityRepository slotCapacityRepository;

    @InjectMocks
    private OrderService orderService;

    private UUID establishmentId;
    private UUID userId;
    private UUID dishId;
    private Establishment establishment;
    private Dish dish;

    @BeforeEach
    void setUp() {
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
                    LocalDate.now(),
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
                    LocalDate.now().plusDays(2),
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
            assertThat(savedOrder.getPickupDate()).isEqualTo(LocalDate.now());
            assertThat(savedOrder.getPickupDate()).isNotEqualTo(LocalDate.now().plusDays(2));
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
                    LocalDate.now().plusDays(1),
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

            LocalDate targetDate = LocalDate.now().plusDays(1);
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

        @Test
        @DisplayName("Intento de compra sobre slot con maxCapacity = 0 arroja SlotCapacityExceededException")
        void rejectsWhenSlotCapacityIsConfiguredZero() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate targetDate = LocalDate.now().plusDays(1);
            LocalTime slotStart = LocalTime.of(13, 0);

            when(slotCapacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, targetDate, slotStart))
                    .thenReturn(Optional.of(new EstablishmentSlotCapacity(establishmentId, new TimeSlot(targetDate, slotStart), 0)));

            when(orderRepository.countActiveOrdersBySlot(eq(establishmentId), eq(targetDate), eq(slotStart), any()))
                    .thenReturn(0L);

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

        @Test
        @DisplayName("Allows order creation when configured maxCapacity is 5 and 4 orders booked")
        void allowsOrderWhenCapacityFiveAndFourBooked() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate targetDate = LocalDate.now().plusDays(1);
            LocalTime slotStart = LocalTime.of(13, 0);

            when(slotCapacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, targetDate, slotStart))
                    .thenReturn(Optional.of(new EstablishmentSlotCapacity(establishmentId, new TimeSlot(targetDate, slotStart), 5)));

            when(orderRepository.countActiveOrdersBySlot(eq(establishmentId), eq(targetDate), eq(slotStart), any()))
                    .thenReturn(4L);

            when(dishRepository.findByIdIn(any())).thenReturn(List.of(dish));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order o = invocation.getArgument(0);
                o.setId(UUID.randomUUID());
                return o;
            });

            CreateOrderRequest request = new CreateOrderRequest(
                    establishmentId,
                    FulfillmentMode.SCHEDULED,
                    targetDate,
                    slotStart,
                    slotStart.plusMinutes(15),
                    List.of(new CreateOrderItemRequest(dishId, 1))
            );

            OrderCreatedResponse response = orderService.createOrder(userId, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Uses fallback capacity of 10 when no SlotCapacity record exists")
        void usesFallbackCapacityWhenNoRecordExists() {
            when(orderRepository.hasActivePendingOrder(eq(userId), any())).thenReturn(false);
            when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));

            LocalDate targetDate = LocalDate.now().plusDays(1);
            LocalTime slotStart = LocalTime.of(13, 0);

            when(slotCapacityRepository.findByEstablishmentIdAndSlotDateAndStartTime(establishmentId, targetDate, slotStart))
                    .thenReturn(Optional.empty());

            // 10 booked orders out of fallback 10 capacity
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
            order.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

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
            order.setPickupDate(LocalDate.now().plusDays(1));
            order.setTimeSlotStart(LocalTime.of(14, 0));
            order.setTimeSlotEnd(LocalTime.of(14, 15));
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setTotalAmount(new BigDecimal("12000.00"));
            order.setExpiresAt(Instant.now().plus(600, ChronoUnit.SECONDS));

            when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

            OrderDetailResponse detail = orderService.getOrderById(orderId, userId);

            assertThat(detail).isNotNull();
            assertThat(detail.id()).isEqualTo(orderId);
            assertThat(detail.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(detail.remainingSeconds()).isGreaterThan(0L);
        }
    }
}
