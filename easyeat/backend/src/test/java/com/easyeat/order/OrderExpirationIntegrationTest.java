package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.service.OrderExpirationScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T033: TTL Expiration Lifecycle Integration Test")
class OrderExpirationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderExpirationScheduler expirationScheduler;

    @Test
    @DisplayName("Reactive check immediately ignores orders past 15-min TTL, and scheduler sweeps them to EXPIRED")
    void testOrderExpirationLifecycleAndCapacityRelease() {
        Establishment establishment = createTestEstablishment("Restaurante TTL Test");
        establishment.setMaxCapacityPerSlot(2);
        establishmentRepository.save(establishment);

        User diner1 = createTestDiner("ttl_diner1@test.com");
        User diner2 = createTestDiner("ttl_diner2@test.com");

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(14, 0);

        // Order 1: Expired order (expiresAt 1 minute ago)
        Order expiredOrder = new Order();
        expiredOrder.setEstablishmentId(establishment.getId());
        expiredOrder.setUserId(diner1.getId());
        expiredOrder.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        expiredOrder.setPickupDate(pickupDate);
        expiredOrder.setTimeSlotStart(slotStart);
        expiredOrder.setTimeSlotEnd(slotStart.plusMinutes(15));
        expiredOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        expiredOrder.setTotalAmount(new BigDecimal("3000.00"));
        expiredOrder.setCreatedAt(Instant.now().minus(16, ChronoUnit.MINUTES));
        expiredOrder.setUpdatedAt(Instant.now().minus(16, ChronoUnit.MINUTES));
        expiredOrder.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        expiredOrder = orderRepository.save(expiredOrder);

        // Order 2: Active order (expiresAt 10 minutes in the future)
        Order activeOrder = new Order();
        activeOrder.setEstablishmentId(establishment.getId());
        activeOrder.setUserId(diner2.getId());
        activeOrder.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        activeOrder.setPickupDate(pickupDate);
        activeOrder.setTimeSlotStart(slotStart);
        activeOrder.setTimeSlotEnd(slotStart.plusMinutes(15));
        activeOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        activeOrder.setTotalAmount(new BigDecimal("4500.00"));
        activeOrder.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        activeOrder.setUpdatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        activeOrder.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        activeOrder = orderRepository.save(activeOrder);

        // 1. Reactive capacity check: Even BEFORE scheduler runs, expired order should NOT count towards active orders
        long activeCountBeforeSweep = orderRepository.countActiveOrdersBySlot(
                establishment.getId(),
                pickupDate,
                slotStart,
                Instant.now()
        );
        assertThat(activeCountBeforeSweep)
                .as("Only the unexpired order should count against capacity")
                .isEqualTo(1);

        // 2. Trigger scheduled sweep
        expirationScheduler.sweepExpiredOrders();

        // 3. Verify database status after sweep
        Order reloadedExpiredOrder = orderRepository.findById(expiredOrder.getId()).orElseThrow();
        assertThat(reloadedExpiredOrder.getStatus())
                .as("Overdue pending order must transition to EXPIRED")
                .isEqualTo(OrderStatus.EXPIRED);

        Order reloadedActiveOrder = orderRepository.findById(activeOrder.getId()).orElseThrow();
        assertThat(reloadedActiveOrder.getStatus())
                .as("Valid pending order must remain PENDING_PAYMENT")
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
