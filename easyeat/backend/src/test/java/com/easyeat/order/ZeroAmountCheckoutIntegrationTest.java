package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.ZeroAmountOrderRequest;
import com.easyeat.order.dto.ZeroAmountOrderResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T013: ZeroAmountCheckoutIntegrationTest (AS-4, AS-6b, CB-04, FR-005, INV-06)")
class ZeroAmountCheckoutIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutConfirmationService checkoutConfirmationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Test
    @DisplayName("AS-4: Confirmación directa de orden bonificada 100% (monto 0) descuenta stock, envía a KDS y omite pasarela")
    void shouldConfirmZeroAmountOrderBypassingGatewayAndDebitingStock() {
        Establishment establishment = createTestEstablishment("Restaurante Promocional");
        Dish dish = createTestDish(establishment.getId(), "Menú Degustación Bonificado", BigDecimal.ZERO);
        User diner = createTestDiner("diner_zero@test.com");

        Ingredient vegetable = new Ingredient(establishment.getId(), "Verduras Frescas", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("400.00"));
        vegetable.setCurrentStock(new BigDecimal("5.0000"));
        vegetable.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(vegetable);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                vegetable.getId(),
                new BigDecimal("0.2500")
        );
        dishRecipeIngredientRepository.save(recipe);

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.now());
        order.setTimeSlotEnd(LocalTime.now().plusMinutes(15));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, dish.getId(), dish.getName(), BigDecimal.ZERO, 1, BigDecimal.ZERO);
        orderItemRepository.save(item);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        ZeroAmountOrderRequest request = new ZeroAmountOrderRequest(order.getId());
        ZeroAmountOrderResponse response = checkoutConfirmationService.confirmZeroAmountOrder(diner.getId(), request);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.orderStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.gatewayBypassed()).isTrue();
        assertThat(response.stockDebited()).isTrue();
        assertThat(response.kdsTicket()).isNotNull();
        assertThat(response.kdsTicket().status()).isEqualTo("QUEUED");

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        Optional<KdsTicket> ticket = kdsTicketRepository.findByOrderId(order.getId());
        assertThat(ticket).isPresent();
        assertThat(ticket.get().getStatus()).isEqualTo(KdsTicketStatus.QUEUED);
    }
}
