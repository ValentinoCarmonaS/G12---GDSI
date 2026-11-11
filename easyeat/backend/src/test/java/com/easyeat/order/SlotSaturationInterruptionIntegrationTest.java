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
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.exception.SlotCapacityExceededException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T012: SlotSaturationInterruptionIntegrationTest (AS-3, CB-03, Q1, INV-08)")
class SlotSaturationInterruptionIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("AS-3: Interrupción con HTTP 409 cuando la franja horaria se satura tras superar ventana de 15 minutos")
    void shouldInterruptCheckoutWhenSlotSaturatesAfter15MinutesExpiration() {
        Establishment establishment = createTestEstablishment("Restaurante Saturacion Franja");
        establishment.setMaxCapacityPerSlot(1);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Sandwich Gourmet", new BigDecimal("3500.00"));
        Ingredient bread = new Ingredient(establishment.getId(), "Pan de Molde", IngredientCategory.DRY_GOODS, UnitOfMeasure.U, new BigDecimal("100.00"));
        bread.setCurrentStock(new BigDecimal("50.0000"));
        bread.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(bread);

        DishRecipeIngredient recipe = new DishRecipeIngredient(dish.getId(), bread.getId(), new BigDecimal("2.0000"));
        dishRecipeIngredientRepository.save(recipe);

        User diner = createTestDiner("diner_saturated@test.com");
        User otherDiner = createTestDiner("other_diner@test.com");

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(15, 0);
        LocalTime slotEnd = LocalTime.of(15, 30);

        // Reserva activa de otro usuario que satura la franja (cupo = 1)
        Order otherOrder = new Order();
        otherOrder.setEstablishmentId(establishment.getId());
        otherOrder.setUserId(otherDiner.getId());
        otherOrder.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        otherOrder.setPickupDate(pickupDate);
        otherOrder.setTimeSlotStart(slotStart);
        otherOrder.setTimeSlotEnd(slotEnd);
        otherOrder.setStatus(OrderStatus.IN_PROGRESS);
        otherOrder.setTotalAmount(new BigDecimal("3500.00"));
        otherOrder.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(otherOrder);

        TemporaryReservation otherReservation = new TemporaryReservation(
                otherOrder,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        otherReservation.confirm();
        temporaryReservationRepository.save(otherReservation);

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                com.easyeat.operations.domain.PaymentMethod.MERCADO_PAGO,
                pickupDate,
                slotStart,
                slotEnd,
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1))
        );

        assertThatThrownBy(() -> checkoutPaymentService.initiatePayment(diner.getId(), request))
                .isInstanceOf(SlotCapacityExceededException.class);
    }
}
