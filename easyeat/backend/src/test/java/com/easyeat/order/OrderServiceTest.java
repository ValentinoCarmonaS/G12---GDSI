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
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
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
import java.time.LocalDate;
import java.time.LocalTime;
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
            // Given request passes slot parameters by mistake with IMMEDIATE mode
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

            // When
            OrderCreatedResponse response = orderService.createOrder(userId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getFulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            assertThat(savedOrder.getFulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);
            // Current day for immediate pickup
            assertThat(savedOrder.getPickupDate()).isEqualTo(LocalDate.now());
            // Immediate slot window bounds are derived from current server time / immediate batch, not the scheduled parameters
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
            // Given dish is paused by chef (simulated via domain property / naming or flag)
            // Even if Dish entity doesn't have paused_by_chef column yet, our domain service must support checking it
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
}
