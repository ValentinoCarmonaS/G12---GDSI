package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.PendingPaymentRequest;
import com.easyeat.order.dto.PendingPaymentResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("T014: PendingPaymentResolutionIntegrationTest (AS-5, CB-05, FR-006, INV-03, INV-04)")
class PendingPaymentResolutionIntegrationTest extends BaseIntegrationTest {

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
    private KdsTicketRepository kdsTicketRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("AS-5: Estado pendiente congela reserva temporal, NO debita stock real y NO envía a KDS")
    void shouldRegisterPendingPaymentFreezingReservationWithoutDebitingStockOrQueuingInKds() {
        Establishment establishment = createTestEstablishment("Restaurante Pago Pendiente");
        Dish dish = createTestDish(establishment.getId(), "Pasta Pendiente", new BigDecimal("4200.00"));
        User diner = createTestDiner("diner_pending@test.com");

        Ingredient ingredient = new Ingredient(establishment.getId(), "Semolín", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("300.00"));
        ingredient.setCurrentStock(new BigDecimal("15.0000"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.now().plusDays(1));
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 30));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("4200.00"));
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

        PendingPaymentRequest request = new PendingPaymentRequest(
                order.getId(),
                "mp_pay_999000",
                "pending_contingency"
        );

        PendingPaymentResponse response = checkoutConfirmationService.registerPendingPayment(diner.getId(), request);

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.orderStatus()).isEqualTo("PENDING_ACCREDITATION");
        assertThat(response.paymentStatus()).isEqualTo("PENDING");
        assertThat(response.stockDebited()).isFalse(); // INV-03
        assertThat(response.kdsQueued()).isFalse(); // INV-04
        assertThat(response.reservationFrozen()).isTrue();

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_ACCREDITATION);

        // Verificar que NO hay ticket en KDS
        assertThat(kdsTicketRepository.findByOrderId(order.getId())).isEmpty();

        // Verificar que el stock físico no varió
        Ingredient untouchedIngredient = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        assertThat(untouchedIngredient.getCurrentStock()).isEqualByComparingTo(new BigDecimal("15.0000"));
    }

    @Test
    @DisplayName("T036 US3: 409 ACTIVE_RESERVATION_EXISTS incluye orderId en /orders y /checkout/pay, y cancelación ajena responde 403")
    void shouldReturnActiveOrderIdOn409AndRejectForeignCancellationWith403() throws Exception {
        Establishment establishment = createTestEstablishment("Restaurante Conflicto US3");
        Dish dish = createTestDish(establishment.getId(), "Plato US3", new BigDecimal("6000.00"));
        User owner = createTestDiner("diner.us3.owner." + UUID.randomUUID() + "@test.com");
        User intruder = createTestDiner("diner.us3.intruder." + UUID.randomUUID() + "@test.com");

        Order activeOrder = new Order();
        activeOrder.setEstablishmentId(establishment.getId());
        activeOrder.setUserId(owner.getId());
        activeOrder.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        activeOrder.setPickupDate(LocalDate.now().plusDays(1));
        activeOrder.setTimeSlotStart(LocalTime.of(13, 0));
        activeOrder.setTimeSlotEnd(LocalTime.of(13, 15));
        activeOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        activeOrder.setTotalAmount(new BigDecimal("6000.00"));
        activeOrder.setExpiresAt(Instant.now().plusSeconds(900));
        activeOrder = orderRepository.save(activeOrder);

        TemporaryReservation reservation = temporaryReservationRepository.save(new TemporaryReservation(
                activeOrder,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        ));
        Payment payment = paymentRepository.save(new Payment(
                activeOrder,
                reservation,
                PaymentMethod.MERCADO_PAGO,
                activeOrder.getId().toString(),
                new BigDecimal("6000.00"),
                UUID.randomUUID().toString()
        ));

        UsernamePasswordAuthenticationToken ownerAuth = new UsernamePasswordAuthenticationToken(
                owner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
        UsernamePasswordAuthenticationToken intruderAuth = new UsernamePasswordAuthenticationToken(
                intruder,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );

        String payBody = """
                {
                  "establishmentId": "%s",
                  "fulfillmentMode": "SCHEDULED",
                  "pickupDate": "%s",
                  "timeSlotStart": "14:00:00",
                  "timeSlotEnd": "14:15:00",
                  "items": [{"dishId": "%s", "quantity": 1}]
                }
                """.formatted(establishment.getId(), LocalDate.now().plusDays(1), dish.getId());

        mockMvc.perform(post("/api/v1/checkout/pay")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATION_EXISTS"))
                .andExpect(jsonPath("$.orderId").value(activeOrder.getId().toString()));

        String orderBody = """
                {
                  "establishmentId": "%s",
                  "fulfillmentMode": "SCHEDULED",
                  "pickupDate": "%s",
                  "timeSlotStart": "14:00:00",
                  "timeSlotEnd": "14:15:00",
                  "items": [{"dishId": "%s", "quantity": 1}]
                }
                """.formatted(establishment.getId(), LocalDate.now().plusDays(1), dish.getId());

        mockMvc.perform(post("/api/v1/orders")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESERVATION_EXISTS"))
                .andExpect(jsonPath("$.orderId").value(activeOrder.getId().toString()));

        String cancelPayBody = """
                {
                  "orderId": "%s"
                }
                """.formatted(activeOrder.getId());

        mockMvc.perform(post("/api/v1/checkout/cancel-payment")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelPayBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", activeOrder.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(intruderAuth)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", activeOrder.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        TemporaryReservation released = temporaryReservationRepository.findById(reservation.getId()).orElseThrow();
        Payment cancelledPay = paymentRepository.findById(payment.getId()).orElseThrow();
        Order cancelledOrder = orderRepository.findById(activeOrder.getId()).orElseThrow();

        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(released.getReleaseReason()).isEqualTo(ReleaseReason.CANCELLED_BY_USER);
        assertThat(cancelledPay.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
