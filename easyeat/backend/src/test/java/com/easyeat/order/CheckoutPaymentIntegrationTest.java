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
import com.easyeat.order.service.PaymentGatewayPort;
import com.easyeat.order.service.PaymentGatewayResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("T015: CheckoutPaymentIntegrationTest (AS-1, FR-001, FR-002)")
class CheckoutPaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @SpyBean
    private PaymentGatewayResolver paymentGatewayResolver;

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
        Ingredient ingredient = new Ingredient(establishment.getId(), "Harina", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("100.00"));
        ingredient.setCurrentStock(new BigDecimal("10.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                ingredient.getId(),
                new BigDecimal("0.50")
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

    @Test
    @DisplayName("AS-1: Iniciar pago con plato que tiene retailPrice (PVP) y price nulo no produce NullPointerException y calcula correctamente el monto total")
    void shouldInitiatePaymentSuccessfullyWhenDishHasRetailPriceAndNullPrice() {
        Establishment establishment = createTestEstablishment("Restaurante Retail Price Checkout");
        establishment.setMaxCapacityPerSlot(10);
        establishmentRepository.save(establishment);

        Dish dish = new Dish();
        dish.setTenantId(establishment.getId());
        dish.setName("Milanesa a caballo");
        dish.setPrice(null); // Explicitamente nulo como en platos con PVP de spec 007
        dish.setRetailPrice(new BigDecimal("136.67"));
        dish.setStatus(com.easyeat.dish.domain.DishStatus.PUBLISHED);
        dish = dishRepository.save(dish);

        Ingredient ingredient = new Ingredient(establishment.getId(), "Manzanas", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("100.00"));
        ingredient.setCurrentStock(new BigDecimal("19.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                dish.getId(),
                ingredient.getId(),
                new BigDecimal("1.00")
        );
        dishRecipeIngredientRepository.save(recipe);

        User diner = createTestDiner("diner_retail_price_test@test.com");

        LocalDate pickupDate = LocalDate.now().plusDays(2);
        LocalTime slotStart = LocalTime.of(14, 30);
        LocalTime slotEnd = slotStart.plusMinutes(15);

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                PaymentMethod.MERCADO_PAGO,
                pickupDate,
                slotStart,
                slotEnd,
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1))
        );

        PaymentInitiationResponse response = checkoutPaymentService.initiatePayment(diner.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("136.67"));
        assertThat(response.redirectUrl()).isNotEmpty();

        Order order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("136.67"));

        var items = orderItemRepository.findByOrderId(order.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("136.67"));
        assertThat(items.get(0).getSubtotal()).isEqualByComparingTo(new BigDecimal("136.67"));
    }

    @Test
    @DisplayName("FR-001: redirectUrl prioriza init_point sobre sandbox_init_point incluso cuando sandbox = true")
    void shouldPrioritizeInitPointOverSandboxInitPointEvenWhenSandboxTrue() {
        Establishment establishment = createTestEstablishment("Restaurante InitPoint Priority");
        establishment.setMaxCapacityPerSlot(10);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Empanada Salteña", new BigDecimal("2500.00"));
        Ingredient ingredient = new Ingredient(establishment.getId(), "Carne", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("50.00"));
        ingredient.setCurrentStock(new BigDecimal("20.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), ingredient.getId(), new BigDecimal("0.25")));

        User diner = createTestDiner("diner_initpoint_priority@test.com");

        String officialInitPoint = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-priority-1";
        String sandboxInitPoint = "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-priority-1";

        PaymentGatewayPort stubGateway = mock(PaymentGatewayPort.class);
        when(stubGateway.createPreference(any())).thenReturn(
                new PaymentGatewayPort.PreferenceResult("pref-priority-1", officialInitPoint, sandboxInitPoint, true)
        );
        doReturn(stubGateway).when(paymentGatewayResolver).resolve(PaymentMethod.MERCADO_PAGO);

        PaymentInitiationRequest request = new PaymentInitiationRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                PaymentMethod.MERCADO_PAGO,
                LocalDate.now().plusDays(1),
                LocalTime.of(15, 0),
                LocalTime.of(15, 15),
                List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1))
        );

        PaymentInitiationResponse response = checkoutPaymentService.initiatePayment(diner.getId(), request);

        assertThat(response.redirectUrl()).isEqualTo(officialInitPoint);
        assertThat(response.sandbox()).isTrue();
    }
}
