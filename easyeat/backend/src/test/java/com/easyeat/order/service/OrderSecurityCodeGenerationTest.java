package com.easyeat.order.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSecurityCodeGenerationTest (T028, US4, ENC-2.1.2, RF-012)")
class OrderSecurityCodeGenerationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private DishRepository dishRepository;

    @Test
    @DisplayName("T028: OrderService.generateSecurityCode generates 4-digit numeric PIN between 1000 and 9999")
    void shouldGenerateFourDigitNumericPinInRange() {
        for (int i = 0; i < 100; i++) {
            String code = OrderService.generateSecurityCode();
            assertThat(code)
                    .isNotNull()
                    .hasSize(4)
                    .matches("^[1-9][0-9]{3}$");
            int numeric = Integer.parseInt(code);
            assertThat(numeric).isBetween(1000, 9999);
        }
    }

    @Test
    @DisplayName("T028: OrderService.createOrder assigns a 4-digit securityCode (1000..9999) when persisting Order")
    void shouldAssignFourDigitSecurityCodeWhenCreatingOrder() {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), zone);
        OrderService orderService = new OrderService(orderRepository, establishmentRepository, dishRepository, fixedClock);

        UUID userId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();

        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setTimezone("America/Argentina/Buenos_Aires");
        establishment.setOpeningTime(LocalTime.of(9, 0));
        establishment.setClosingTime(LocalTime.of(22, 0));
        establishment.setMaxCapacityPerSlot(10);
        establishment.setLeadTimeMinutes(15);

        Dish dish = new Dish();
        dish.setId(dishId);
        dish.setName("Milanesa Napolitana");
        dish.setRetailPrice(new BigDecimal("12000.00"));

        CreateOrderItemRequest itemReq = new CreateOrderItemRequest();
        itemReq.setDishId(dishId);
        itemReq.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setEstablishmentId(establishmentId);
        request.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        request.setItems(List.of(itemReq));

        when(orderRepository.findActivePendingOrders(any(), any())).thenReturn(List.of());
        when(orderRepository.hasActivePendingOrder(any(), any())).thenReturn(false);
        when(establishmentRepository.findByIdForUpdate(establishmentId)).thenReturn(Optional.of(establishment));
        when(dishRepository.findByIdIn(List.of(dishId))).thenReturn(List.of(dish));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });

        orderService.createOrder(userId, request);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getSecurityCode())
                .isNotNull()
                .matches("^[1-9][0-9]{3}$");
    }

    @Test
    @DisplayName("T028: Order.onCreate assigns a 4-digit securityCode if missing before persist")
    void shouldAssignFourDigitSecurityCodeOnPrePersistWhenMissing() {
        Order order = new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                FulfillmentMode.IMMEDIATE,
                LocalDate.of(2026, 11, 16),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                OrderStatus.PENDING_PAYMENT,
                new BigDecimal("5000.00"),
                Instant.parse("2026-11-16T15:15:00Z")
        );
        assertThat(order.getSecurityCode()).isNull();

        order.onCreate();

        assertThat(order.getSecurityCode())
                .isNotNull()
                .matches("^[1-9][0-9]{3}$");
    }
}
