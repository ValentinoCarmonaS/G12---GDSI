package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.PendingPaymentRequest;
import com.easyeat.order.dto.PendingPaymentResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
