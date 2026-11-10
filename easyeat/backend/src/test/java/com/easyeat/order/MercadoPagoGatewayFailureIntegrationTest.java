package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutPaymentService;
import com.easyeat.order.service.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("T019: MercadoPagoGatewayFailureIntegrationTest (AS-6, FR-011, CB-06)")
class MercadoPagoGatewayFailureIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @MockBean
    private PaymentGatewayPort paymentGatewayPort;

    @Autowired
    private com.easyeat.order.service.PaymentGatewayResolver paymentGatewayResolver;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Test
    @DisplayName("AS-6: Fallo 5xx o timeout de Mercado Pago provoca rollback atómico completo sin dejar orden, reserva ni pago colgado")
    void shouldRollbackAtomicallyWhenGatewayFailsOnInitiation() {
        Establishment establishment = createTestEstablishment("Restaurante Gateway Failure");
        establishment.setMaxCapacityPerSlot(10);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Plato Fallo Red", new BigDecimal("2500.00"));
        Ingredient ingredient = new Ingredient(establishment.getId(), "Arroz", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("50.00"));
        ingredient.setCurrentStock(new BigDecimal("10.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                ingredient.getId(),
                new BigDecimal("0.25")
        );
        dishRecipeIngredientRepository.save(recipe);

        User diner = createTestDiner("diner_gateway_fail@test.com");

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(16, 0);
        LocalTime slotEnd = slotStart.plusMinutes(15);

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                PaymentMethod.MERCADO_PAGO,
                pickupDate,
                slotStart,
                slotEnd,
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 2))
        );

        when(paymentGatewayPort.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);
        when(paymentGatewayPort.createPreference(any()))
                .thenThrow(new RuntimeException("Mercado Pago API returned 502 Bad Gateway"));
        paymentGatewayResolver.registerAdapter(paymentGatewayPort);

        long ordersBefore = orderRepository.count();
        long reservationsBefore = temporaryReservationRepository.count();
        long paymentsBefore = paymentRepository.count();

        assertThatThrownBy(() -> checkoutPaymentService.initiatePayment(diner.getId(), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("502");

        // Rollback completo: ninguna entidad debe quedar persistida (INV-01)
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(temporaryReservationRepository.count()).isEqualTo(reservationsBefore);
        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
    }
}
