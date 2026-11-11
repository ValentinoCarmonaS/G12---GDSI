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
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentConfirmationResponse;
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

@DisplayName("T010: IndividualCheckoutIntegrationTest (AS-1, AS-1b, CB-11, C5)")
class IndividualCheckoutIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("AS-1: Confirmación de pago aprobado transiciona a IN_PROGRESS, genera comanda en KDS y descuenta stock")
    void shouldConfirmPaymentAndTransitionToInProgressWithKdsAndStockDeduction() {
        Establishment establishment = createTestEstablishment("Restaurante Checkout Confirm");
        Dish dish = createTestDish(establishment.getId(), "Milanesa con Puré", new BigDecimal("6000.00"));
        User diner = createTestDiner("diner_confirm@test.com");

        Ingredient meat = new Ingredient(establishment.getId(), "Carne de Nalga", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("1000.00"));
        meat.setCurrentStock(new BigDecimal("10.0000"));
        meat.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(meat);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                meat.getId(),
                new BigDecimal("0.5000")
        );
        dishRecipeIngredientRepository.save(recipe);

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(13, 0));
        order.setTimeSlotEnd(LocalTime.of(13, 30));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("6000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice());
        orderItemRepository.save(item);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        PaymentConfirmationRequest request = new PaymentConfirmationRequest(
                order.getId(),
                "mp_pay_123456",
                "approved"
        );

        PaymentConfirmationResponse response = checkoutConfirmationService.confirmPayment(diner.getId(), request);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.orderStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.paymentStatus()).isEqualTo("APPROVED");
        assertThat(response.stockDebited()).isTrue();
        assertThat(response.kdsTicket()).isNotNull();
        assertThat(response.kdsTicket().status()).isEqualTo("QUEUED");
        assertThat(response.idempotentReplay()).isFalse();

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        Optional<KdsTicket> kdsTicket = kdsTicketRepository.findByOrderId(order.getId());
        assertThat(kdsTicket).isPresent();
        assertThat(kdsTicket.get().getStatus()).isEqualTo(KdsTicketStatus.QUEUED);

        // Idempotency check (CB-12, INV-07)
        PaymentConfirmationResponse replayResponse = checkoutConfirmationService.confirmPayment(diner.getId(), request);
        assertThat(replayResponse.idempotentReplay()).isTrue();
        assertThat(replayResponse.orderStatus()).isEqualTo("IN_PROGRESS");
    }
}
