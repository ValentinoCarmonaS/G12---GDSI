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
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentConfirmationResponse;
import com.easyeat.order.dto.PendingPaymentRequest;
import com.easyeat.order.dto.ZeroAmountOrderRequest;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import com.easyeat.order.service.PaymentGatewayPort;
import com.easyeat.order.service.PaymentGatewayResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
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

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    @DisplayName("Ownership: confirmPayment, registerPendingPayment y confirmZeroAmountOrder lanzan AccessDeniedException si dinerId != order.userId")
    void confirmationMethodsShouldThrowAccessDeniedForForeignDiner() {
        Establishment establishment = createTestEstablishment("Restaurante Ownership");
        Dish dish = createTestDish(establishment.getId(), "Plato Propiedad", new BigDecimal("3500.00"));
        User owner = createTestDiner("owner." + UUID.randomUUID() + "@test.com");
        User attacker = createTestDiner("attacker." + UUID.randomUUID() + "@test.com");

        Ingredient flour = new Ingredient(establishment.getId(), "Harina", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("300.00"));
        flour.setCurrentStock(new BigDecimal("10.0000"));
        flour.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(flour);
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), flour.getId(), new BigDecimal("0.5000")));

        Order order = createOrder(establishment.getId(), owner.getId(), OrderStatus.PENDING_PAYMENT, new BigDecimal("3500.00"), Instant.now().plusSeconds(900));
        orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        Order zeroOrder = createOrder(establishment.getId(), owner.getId(), OrderStatus.PENDING_PAYMENT, BigDecimal.ZERO, Instant.now().plusSeconds(900));

        assertThatThrownBy(() -> checkoutConfirmationService.confirmPayment(
                attacker.getId(),
                new PaymentConfirmationRequest(order.getId(), "mp_hack", "approved")
        )).isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> checkoutConfirmationService.registerPendingPayment(
                attacker.getId(),
                new PendingPaymentRequest(order.getId(), "mp_hack_pending", "pending_contingency")
        )).isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> checkoutConfirmationService.confirmZeroAmountOrder(
                attacker.getId(),
                new ZeroAmountOrderRequest(zeroOrder.getId())
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(ingredientRepository.findById(flour.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("Q3: Orden en CANCELLED o EXPIRED rechaza confirmPayment y registerPendingPayment con InvalidOrderStateException (409) sin debitar stock ni KDS")
    void shouldRejectLateConfirmationOnCancelledAndExpiredOrdersWithoutSideEffects() {
        Establishment establishment = createTestEstablishment("Restaurante Q3");
        Dish dish = createTestDish(establishment.getId(), "Plato Q3", new BigDecimal("4200.00"));
        User diner = createTestDiner("diner.q3." + UUID.randomUUID() + "@test.com");

        Ingredient cheese = new Ingredient(establishment.getId(), "Queso", IngredientCategory.DAIRY, UnitOfMeasure.KG, new BigDecimal("800.00"));
        cheese.setCurrentStock(new BigDecimal("10.0000"));
        cheese.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(cheese);
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), cheese.getId(), new BigDecimal("1.0000")));

        Order cancelledOrder = createOrder(establishment.getId(), diner.getId(), OrderStatus.CANCELLED, new BigDecimal("4200.00"), Instant.now().plusSeconds(600));
        orderItemRepository.save(new OrderItem(cancelledOrder, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        Order expiredOrder = createOrder(establishment.getId(), diner.getId(), OrderStatus.EXPIRED, new BigDecimal("4200.00"), Instant.now().minusSeconds(60));
        orderItemRepository.save(new OrderItem(expiredOrder, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        assertThatThrownBy(() -> checkoutConfirmationService.confirmPayment(
                diner.getId(),
                new PaymentConfirmationRequest(cancelledOrder.getId(), "mp_late_cancelled", "approved")
        )).isInstanceOf(InvalidOrderStateException.class);

        assertThatThrownBy(() -> checkoutConfirmationService.confirmPayment(
                diner.getId(),
                new PaymentConfirmationRequest(expiredOrder.getId(), "mp_late_expired", "approved")
        )).isInstanceOf(InvalidOrderStateException.class);

        assertThatThrownBy(() -> checkoutConfirmationService.registerPendingPayment(
                diner.getId(),
                new PendingPaymentRequest(cancelledOrder.getId(), "mp_late_pending", "in_process")
        )).isInstanceOf(InvalidOrderStateException.class);

        assertThat(ingredientRepository.findById(cheese.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("10.0000");
        assertThat(kdsTicketRepository.findByEstablishmentId(establishment.getId(), Pageable.unpaged()).getContent()).isEmpty();
    }

    @Test
    @DisplayName("Aprobación segura de Payment marca fila existente IN_PROCESS como APPROVED")
    void confirmPaymentShouldApproveExistingPaymentRecord() {
        Establishment establishment = createTestEstablishment("Restaurante Payment Update");
        Dish dish = createTestDish(establishment.getId(), "Plato Payment", new BigDecimal("5100.00"));
        User diner = createTestDiner("diner.payment." + UUID.randomUUID() + "@test.com");

        Order order = createOrder(establishment.getId(), diner.getId(), OrderStatus.PENDING_PAYMENT, new BigDecimal("5100.00"), Instant.now().plusSeconds(900));
        orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        TemporaryReservation reservation = new TemporaryReservation(order, UUID.randomUUID().toString(), Instant.now().plusSeconds(900));
        temporaryReservationRepository.save(reservation);

        Payment payment = new Payment(order, reservation, PaymentMethod.MERCADO_PAGO, order.getId().toString(), order.getTotalAmount(), UUID.randomUUID().toString());
        payment.assignGatewayPreference("pref-update-1", "https://mp/init", "https://mp/sandbox");
        paymentRepository.save(payment);

        PaymentConfirmationResponse response = checkoutConfirmationService.confirmPayment(
                diner.getId(),
                new PaymentConfirmationRequest(order.getId(), "mp_approved_tx_77", "approved")
        );

        assertThat(response.orderStatus()).isEqualTo("IN_PROGRESS");
        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(updatedPayment.getGatewayPaymentId()).isEqualTo("mp_approved_tx_77");
        assertThat(updatedPayment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("Endpoint POST /confirm-payment retorna 409 INSUFFICIENT_STOCK cuando falla stock en receta")
    void confirmPaymentEndpointShouldReturn409InsufficientStockWhenIngredientDepleted() throws Exception {
        Establishment establishment = createTestEstablishment("Restaurante 409 Stock");
        Dish dish = createTestDish(establishment.getId(), "Bife Agotado", new BigDecimal("7000.00"));
        User diner = createTestDiner("diner.stock409." + UUID.randomUUID() + "@test.com");

        Ingredient beef = new Ingredient(establishment.getId(), "Lomo", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("2000.00"));
        beef.setCurrentStock(new BigDecimal("0.2000"));
        beef.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(beef);
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), beef.getId(), new BigDecimal("1.5000")));

        Order order = createOrder(establishment.getId(), diner.getId(), OrderStatus.PENDING_PAYMENT, new BigDecimal("7000.00"), Instant.now().plusSeconds(900));
        orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

        UsernamePasswordAuthenticationToken dinerAuth = new UsernamePasswordAuthenticationToken(
                diner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        String body = """
                {
                  "orderId": "%s",
                  "gatewayPaymentId": "mp_stock_fail_1",
                  "status": "approved"
                }
                """.formatted(order.getId());

        mockMvc.perform(post("/api/v1/checkout/confirm-payment")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(dinerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Nested
    @TestPropertySource(properties = "mercadopago.access-token=TEST_MP_ACCESS_TOKEN")
    @DisplayName("Contraste contra queryPayment cuando accessToken está configurado")
    class GatewayContrastWithAccessTokenTest {

        @Autowired
        private CheckoutConfirmationService checkoutConfirmationService;

        @MockBean
        private PaymentGatewayResolver paymentGatewayResolver;

        @Test
        @DisplayName("Rechaza confirmación si queryPayment retorna REJECTED aunque el navegador informe approved")
        void shouldRejectConfirmationWhenGatewayReportsRejectedDespiteBrowserApproved() {
            Establishment establishment = createTestEstablishment("Restaurante Contrast Reject");
            Dish dish = createTestDish(establishment.getId(), "Plato Contrast", new BigDecimal("3000.00"));
            User diner = createTestDiner("diner.contrast.reject." + UUID.randomUUID() + "@test.com");

            Ingredient salt = new Ingredient(establishment.getId(), "Sal", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("100.00"));
            salt.setCurrentStock(new BigDecimal("5.0000"));
            salt.setStatus(IngredientStatus.ACTIVE);
            ingredientRepository.save(salt);
            dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), salt.getId(), new BigDecimal("0.1000")));

            Order order = createOrder(establishment.getId(), diner.getId(), OrderStatus.PENDING_PAYMENT, new BigDecimal("3000.00"), Instant.now().plusSeconds(900));
            orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

            PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
            when(paymentGatewayResolver.resolve(PaymentMethod.MERCADO_PAGO)).thenReturn(gatewayPort);
            when(gatewayPort.queryPayment("mp_spoof_999")).thenReturn(new PaymentGatewayPort.GatewayPaymentResult(
                    "mp_spoof_999",
                    order.getId().toString(),
                    PaymentStatus.REJECTED,
                    "cc_rejected_insufficient_amount",
                    order.getTotalAmount(),
                    "ARS"
            ));

            assertThatThrownBy(() -> checkoutConfirmationService.confirmPayment(
                    diner.getId(),
                    new PaymentConfirmationRequest(order.getId(), "mp_spoof_999", "approved")
            )).isInstanceOf(InvalidOrderStateException.class);

            verify(gatewayPort).queryPayment("mp_spoof_999");
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(ingredientRepository.findById(salt.getId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("5.0000");
        }

        @Test
        @DisplayName("Confirma cuando queryPayment retorna APPROVED y externalReference coincide con orderId")
        void shouldConfirmWhenGatewayVerifiesApprovedAndMatchingOrderId() {
            Establishment establishment = createTestEstablishment("Restaurante Contrast Ok");
            Dish dish = createTestDish(establishment.getId(), "Plato Ok", new BigDecimal("3000.00"));
            User diner = createTestDiner("diner.contrast.ok." + UUID.randomUUID() + "@test.com");

            Order order = createOrder(establishment.getId(), diner.getId(), OrderStatus.PENDING_PAYMENT, new BigDecimal("3000.00"), Instant.now().plusSeconds(900));
            orderItemRepository.save(new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, dish.getPrice()));

            PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
            when(paymentGatewayResolver.resolve(PaymentMethod.MERCADO_PAGO)).thenReturn(gatewayPort);
            when(gatewayPort.queryPayment("mp_verified_777")).thenReturn(new PaymentGatewayPort.GatewayPaymentResult(
                    "mp_verified_777",
                    order.getId().toString(),
                    PaymentStatus.APPROVED,
                    "accredited",
                    order.getTotalAmount(),
                    "ARS"
            ));

            PaymentConfirmationResponse response = checkoutConfirmationService.confirmPayment(
                    diner.getId(),
                    new PaymentConfirmationRequest(order.getId(), "mp_verified_777", "approved")
            );

            assertThat(response.orderStatus()).isEqualTo("IN_PROGRESS");
            verify(gatewayPort).queryPayment("mp_verified_777");
        }
    }

    private Order createOrder(UUID establishmentId, UUID userId, OrderStatus status, BigDecimal totalAmount, Instant expiresAt) {
        Order order = new Order();
        order.setEstablishmentId(establishmentId);
        order.setUserId(userId);
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(13, 0));
        order.setTimeSlotEnd(LocalTime.of(13, 30));
        order.setStatus(status);
        order.setTotalAmount(totalAmount);
        order.setExpiresAt(expiresAt);
        return orderRepository.save(order);
    }
}
