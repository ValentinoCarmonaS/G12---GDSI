package com.easyeat.order.service;

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
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.dto.PaymentInitiationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 022, T010 (US1, ENC-3.2.1): every checkout path persists a non-null PIN in [1000, 9999]
 * (RF-001, AC-1, data-model invariant 1) that stays identical once the payment is confirmed (RF-002, invariant 2).
 * The stored value is read through JDBC, so the assertion is on the database row, not on the entity.
 */
@DisplayName("OrderPinCreationIntegrationTest (T010, spec 022, US1, RF-001, RF-002)")
class OrderPinCreationIntegrationTest extends BaseIntegrationTest {

    private static final String PIN_FORMAT = "^[1-9][0-9]{3}$";

    @Autowired
    private OrderService orderService;

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @Autowired
    private CheckoutConfirmationService checkoutConfirmationService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("OrderService.createOrder persists a non-null PIN in [1000, 9999]")
    void createOrderPersistsPin() {
        Establishment establishment = establishment();
        Dish dish = createTestDish(establishment.getId(), "T010 Dish " + UUID.randomUUID(), new BigDecimal("5500.00"));
        User diner = createTestDiner("t010-create-" + UUID.randomUUID() + "@easyeat.test");

        OrderCreatedResponse response = orderService.createOrder(diner.getId(), new CreateOrderRequest(
                establishment.getId(), FulfillmentMode.SCHEDULED, LocalDate.now().plusDays(1),
                LocalTime.of(13, 0), LocalTime.of(13, 15), List.of(new CreateOrderItemRequest(dish.getId(), 1))));

        assertThat(storedSecurityCode(response.getId())).isNotNull().matches(PIN_FORMAT);
    }

    @Test
    @DisplayName("CheckoutPaymentService.initiatePayment persists a non-null PIN in [1000, 9999]")
    void initiatePaymentPersistsPin() {
        Establishment establishment = establishment();
        User diner = createTestDiner("t010-initiate-" + UUID.randomUUID() + "@easyeat.test");

        PaymentInitiationResponse response = checkoutPaymentService.initiatePayment(diner.getId(),
                paymentRequest(establishment, stockedDish(establishment)));

        assertThat(storedSecurityCode(response.orderId())).isNotNull().matches(PIN_FORMAT);
    }

    @Test
    @DisplayName("confirming the payment keeps the PIN generated at checkout unchanged (RF-002)")
    void confirmPaymentKeepsPin() {
        Establishment establishment = establishment();
        User diner = createTestDiner("t010-confirm-" + UUID.randomUUID() + "@easyeat.test");
        PaymentInitiationResponse initiated = checkoutPaymentService.initiatePayment(diner.getId(),
                paymentRequest(establishment, stockedDish(establishment)));
        String pinAtCheckout = storedSecurityCode(initiated.orderId());

        checkoutConfirmationService.confirmPayment(diner.getId(),
                new PaymentConfirmationRequest(initiated.orderId(), "mp_pay_t010", "approved"));

        assertThat(pinAtCheckout).matches(PIN_FORMAT);
        assertThat(storedSecurityCode(initiated.orderId())).isEqualTo(pinAtCheckout);
    }

    private Establishment establishment() {
        Establishment establishment = createTestEstablishment("T010 PIN Bistro " + UUID.randomUUID());
        establishment.setMaxCapacityPerSlot(10);
        return establishmentRepository.save(establishment);
    }

    private Dish stockedDish(Establishment establishment) {
        Dish dish = createTestDish(establishment.getId(), "T010 Pasta " + UUID.randomUUID(), new BigDecimal("4500.00"));
        Ingredient flour = new Ingredient(establishment.getId(), "T010 Harina", IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG, new BigDecimal("100.00"));
        flour.setCurrentStock(new BigDecimal("10.00"));
        flour.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(flour);
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), flour.getId(), new BigDecimal("0.50")));
        return dish;
    }

    private PaymentInitiationRequest paymentRequest(Establishment establishment, Dish dish) {
        return new PaymentInitiationRequest(establishment.getId(), FulfillmentMode.SCHEDULED, PaymentMethod.MERCADO_PAGO,
                LocalDate.now().plusDays(1), LocalTime.of(13, 0), LocalTime.of(13, 15),
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1)));
    }

    private String storedSecurityCode(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT security_code FROM orders WHERE id = ?", String.class, orderId);
    }
}
