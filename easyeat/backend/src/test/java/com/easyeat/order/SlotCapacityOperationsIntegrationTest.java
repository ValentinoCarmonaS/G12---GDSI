package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Spec 010 and 012 Integration: Slot Capacity Hierarchy and Overbooking Protection")
class SlotCapacityOperationsIntegrationTest extends BaseIntegrationTest {

    private Establishment establishment;
    private User diner;
    private Dish dish;
    private LocalDate tomorrow;

    private RequestPostProcessor dinerAuth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))));
    }

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        slotCapacityRepository.deleteAll();

        establishment = createTestEstablishment("Restaurante Operaciones Integration " + UUID.randomUUID());
        diner = createTestDiner("diner_cap_" + UUID.randomUUID() + "@easyeat.test");
        dish = createTestDish(establishment.getId(), "Milanesa de la Casa", new BigDecimal("6000.00"));
        tomorrow = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
    }

    @Test
    @DisplayName("Franja con maxCapacity = 0 en SlotCapacity -> verifica SOLD_OUT y no seleccionable")
    void slotWithZeroCapacityConfiguredReturnsSoldOutAndNotSelectable() throws Exception {
        LocalTime zeroSlotTime = LocalTime.of(14, 0);
        EstablishmentSlotCapacity zeroCap = new EstablishmentSlotCapacity(
                establishment.getId(),
                new TimeSlot(tomorrow, zeroSlotTime),
                0
        );
        slotCapacityRepository.save(zeroCap);

        mockMvc.perform(get("/api/v1/establishments/{id}/pickup-slots", establishment.getId())
                        .param("date", tomorrow.toString())
                        .with(dinerAuth(diner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:00:00')].maxCapacity", contains(0)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:00:00')].status", contains("SOLD_OUT")))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:00:00')].isSelectable", contains(false)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:00:00')].occupancyPercentage", contains(100.0)));
    }

    @Test
    @DisplayName("Franja con maxCapacity = 5 y 4 pedidos en orders -> verifica badge HIGH_DEMAND (>=80%)")
    void slotWithCapacityFiveAndFourOrdersReturnsHighDemand() throws Exception {
        LocalTime slotTime = LocalTime.of(14, 15);
        EstablishmentSlotCapacity capacityRecord = new EstablishmentSlotCapacity(
                establishment.getId(),
                new TimeSlot(tomorrow, slotTime),
                5
        );
        slotCapacityRepository.save(capacityRecord);

        // Create 4 orders
        for (int i = 0; i < 4; i++) {
            User orderDiner = createTestDiner("diner_sub_" + i + "_" + UUID.randomUUID() + "@test.com");
            Order order = new Order();
            order.setEstablishmentId(establishment.getId());
            order.setUserId(orderDiner.getId());
            order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
            order.setPickupDate(tomorrow);
            order.setTimeSlotStart(slotTime);
            order.setTimeSlotEnd(slotTime.plusMinutes(15));
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setTotalAmount(new BigDecimal("6000.00"));
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            order.setExpiresAt(Instant.now().plusSeconds(900));
            orderRepository.save(order);
        }

        mockMvc.perform(get("/api/v1/establishments/{id}/pickup-slots", establishment.getId())
                        .param("date", tomorrow.toString())
                        .with(dinerAuth(diner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:15:00')].maxCapacity", contains(5)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:15:00')].bookedCount", contains(4)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:15:00')].occupancyPercentage", contains(80.0)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:15:00')].status", contains("HIGH_DEMAND")))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:15:00')].isSelectable", contains(true)));
    }

    @Test
    @DisplayName("Franja sin registro en SlotCapacity -> verifica fallback a 10 cupos")
    void slotWithoutCapacityRecordFallsBackToEstablishmentDefault() throws Exception {
        // No SlotCapacity saved for 14:30
        mockMvc.perform(get("/api/v1/establishments/{id}/pickup-slots", establishment.getId())
                        .param("date", tomorrow.toString())
                        .with(dinerAuth(diner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:30:00')].maxCapacity", contains(10)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:30:00')].bookedCount", contains(0)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:30:00')].occupancyPercentage", contains(0.0)))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:30:00')].status", contains("AVAILABLE")))
                .andExpect(jsonPath("$.slots[?(@.startTime == '14:30:00')].isSelectable", contains(true)));
    }

    @Test
    @DisplayName("Intento de compra sobre slot con maxCapacity = 0 -> HTTP 409")
    void orderAttemptOnZeroCapacitySlotReturnsHttp409() throws Exception {
        LocalTime zeroSlotTime = LocalTime.of(15, 0);
        EstablishmentSlotCapacity zeroCap = new EstablishmentSlotCapacity(
                establishment.getId(),
                new TimeSlot(tomorrow, zeroSlotTime),
                0
        );
        slotCapacityRepository.save(zeroCap);

        CreateOrderRequest request = new CreateOrderRequest(
                establishment.getId(),
                FulfillmentMode.SCHEDULED,
                tomorrow,
                zeroSlotTime,
                zeroSlotTime.plusMinutes(15),
                List.of(new CreateOrderItemRequest(dish.getId(), 1))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(dinerAuth(diner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_SOLD_OUT"));
    }
}

