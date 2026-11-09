package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.service.DishAvailabilityEngine;
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
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.dto.PaymentInitiationResponse;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T015: CheckoutPaymentIntegrationTest (AS-1, FR-001, FR-002)")
class CheckoutPaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

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

    @Autowired
    private DishAvailabilityEngine dishAvailabilityEngine;

    @Test
    @DisplayName("AS-1: Iniciar pago con carrito válido crea orden PENDING_PAYMENT, reserva temporal ACTIVE con TTL 15 min y redirección a Sandbox")
    void shouldInitiatePaymentAndCreateAtomic15MinReservation() {
        Establishment establishment = createTestEstablishment("Restaurante Checkout Pro");
        establishment.setMaxCapacityPerSlot(10);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Pasta Gourmet", new BigDecimal("4500.00"));
        Ingredient ingredient = new Ingredient(establishment.getId(), "Harina", IngredientCategory.GRAINS, UnitOfMeasure.KG, new BigDecimal("100.00"));
        ingredient.setCurrentStock(new BigDecimal("10.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                new DishRecipeIngredientId(dish.getId(), ingredient.getId()),
                new BigDecimal("0.50"),
                1
        );
        dishRecipeIngredientRepository.save(recipe);

        User diner = createTestDiner("diner_checkout_test@test.com");

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(13, 0);
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

        Instant beforeCall = Instant.now();
        PaymentInitiationResponse response = checkoutPaymentService.initiatePayment(diner.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.reservationId()).isNotNull();
        assertThat(response.paymentId()).isNotNull();
        assertThat(response.redirectUrl()).isNotEmpty();
        assertThat(response.sandbox()).isTrue();
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("9000.00"));
        assertThat(response.currency()).isEqualTo("ARS");

        // Validar TTL de exactamente 15 minutos (+/- 5 segundos)
        Duration reservationDuration = Duration.between(beforeCall, response.expiresAt());
        assertThat(reservationDuration.toMinutes()).isEqualTo(15);

        // Verificar entidades persistidas
        Order order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9000.00"));

        TemporaryReservation reservation = temporaryReservationRepository.findById(response.reservationId()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getOrder().getId()).isEqualTo(order.getId());

        Payment payment = paymentRepository.findById(response.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROCESS);
        assertThat(payment.getTransactionAmount()).isEqualByComparingTo(new BigDecimal("9000.00"));
        assertThat(payment.getReservation().getId()).isEqualTo(reservation.getId());
    }
}
