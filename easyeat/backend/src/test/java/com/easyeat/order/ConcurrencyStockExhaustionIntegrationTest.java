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
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T011: ConcurrencyStockExhaustionIntegrationTest (AS-2, CB-02, INV-05, INV-08)")
class ConcurrencyStockExhaustionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

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

    @Test
    @DisplayName("AS-2: Rechazo inmediato por insumos agotados por concurrencia al iniciar pago")
    void shouldRejectPaymentWhenIngredientsExhaustedByConcurrentTransactions() {
        Establishment establishment = createTestEstablishment("Restaurante Agotamiento Concurrente");
        Dish dish = createTestDish(establishment.getId(), "Hamburguesa Doble", new BigDecimal("7500.00"));
        User diner = createTestDiner("diner_exhausted@test.com");

        Ingredient meat = new Ingredient(establishment.getId(), "Carne Picada", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("800.00"));
        meat.setCurrentStock(new BigDecimal("0.1000")); // Stock insuficiente para la receta que pide 0.3000
        meat.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(meat);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                meat.getId(),
                new BigDecimal("0.3000")
        );
        dishRecipeIngredientRepository.save(recipe);

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                LocalDate.now().plusDays(1),
                LocalTime.of(14, 0),
                LocalTime.of(14, 30),
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1))
        );

        assertThatThrownBy(() -> checkoutPaymentService.initiatePayment(diner.getId(), request))
                .isInstanceOf(InsufficientIngredientStockException.class);
    }
}
