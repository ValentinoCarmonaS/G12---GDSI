package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.order.controller.OrderConfirmationTrackingController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.service.OrderConfirmationTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller unit tests for GET /api/v1/orders/{orderId}/confirmation-tracking
 * verifying complete confirmation data, green screen payload and error semantics (AS-1, AS-3, INV-001, INV-004).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmationTrackingController Unit Tests")
class OrderConfirmationTrackingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderConfirmationTrackingService trackingService;

    @Mock
    private com.easyeat.order.service.OrderLiveTrackingService liveTrackingService;

    @InjectMocks
    private OrderConfirmationTrackingController controller;

    private User testUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("diner.tracking@test.com");

        auth = new UsernamePasswordAuthenticationToken(
                testUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("AS-1/INV-004: Should return 200 OK with complete confirmation tracking payload")
    void shouldReturnCompleteConfirmationTrackingPayload() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(13, 0);
        LocalTime slotEnd = LocalTime.of(13, 30);
        Instant now = Instant.now();

        List<OrderConfirmationTrackingResponse.OrderTrackingItemDto> items = List.of(
                new OrderConfirmationTrackingResponse.OrderTrackingItemDto(
                        dishId,
                        "Milanesa con Papas",
                        2,
                        new BigDecimal("2100.00"),
                        new BigDecimal("4200.00")
                )
        );

        OrderConfirmationTrackingResponse response = new OrderConfirmationTrackingResponse(
                orderId,
                142,
                OrderStatus.IN_PROGRESS,
                "APPROVED",
                establishmentId,
                "Café Central Palermo",
                FulfillmentMode.SCHEDULED,
                pickupDate,
                slotStart,
                slotEnd,
                new BigDecimal("4200.00"),
                items,
                "/pedidos/" + orderId + "/seguimiento",
                now
        );

        when(trackingService.getConfirmationTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/{orderId}/confirmation-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(142))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.establishmentId").value(establishmentId.toString()))
                .andExpect(jsonPath("$.establishmentName").value("Café Central Palermo"))
                .andExpect(jsonPath("$.fulfillmentMode").value("SCHEDULED"))
                .andExpect(jsonPath("$.pickupDate").value(pickupDate.toString()))
                .andExpect(jsonPath("$.totalAmount").value(4200.00))
                .andExpect(jsonPath("$.items[0].dishName").value("Milanesa con Papas"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.trackingUrl").value("/pedidos/" + orderId + "/seguimiento"));

        verify(trackingService).getConfirmationTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("INV-001: Should return 404 when order does not exist or does not belong to diner")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(trackingService.getConfirmationTracking(eq(orderId), eq(testUser.getId())))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}/confirmation-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(trackingService).getConfirmationTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("INV-001: Should return 409 or 400 when order is in invalid unapproved state")
    void shouldReturnErrorWhenOrderIsInInvalidState() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(trackingService.getConfirmationTracking(eq(orderId), eq(testUser.getId())))
                .thenThrow(new InvalidOrderStateException("La orden no se encuentra en estado aprobado"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/confirmation-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());

        verify(trackingService).getConfirmationTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("AS-5/INV-004: GET /api/v1/orders/{orderId}/live-tracking returns dynamic tracking payload with no-cache headers")
    void shouldReturnLiveTrackingPayloadWithNoCacheHeaders() throws Exception {
        UUID orderId = UUID.randomUUID();
        com.easyeat.order.dto.OrderLiveTrackingResponse liveResponse = new com.easyeat.order.dto.OrderLiveTrackingResponse(
                orderId,
                142,
                OrderStatus.IN_PROGRESS,
                FulfillmentMode.IMMEDIATE,
                true,
                24,
                35,
                false,
                new com.easyeat.order.dto.OrderLiveTrackingResponse.KdsQueueSaturationDto(2, "NORMAL", 0),
                "Preparando tu pedido",
                Instant.now()
        );

        when(liveTrackingService.getLiveTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(liveResponse);

        mockMvc.perform(get("/api/v1/orders/{orderId}/live-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.estimatedMinutesRemaining").value(24))
                .andExpect(jsonPath("$.progressPercentage").value(35))
                .andExpect(jsonPath("$.kdsQueueSaturation.activeOrdersAhead").value(2));

        verify(liveTrackingService).getLiveTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("PENDING_ACCREDITATION serializa ticketNumber null y approvalStatus PENDING sin error")
    void shouldReturnPendingAccreditationWithNullTicketNumber() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        OrderConfirmationTrackingResponse pendingResponse = new OrderConfirmationTrackingResponse(
                orderId,
                null,
                OrderStatus.PENDING_ACCREDITATION,
                "PENDING",
                establishmentId,
                "Café Central Palermo",
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 30),
                new BigDecimal("3500.00"),
                List.of(),
                "/pedidos/" + orderId + "/seguimiento",
                Instant.now()
        );

        when(trackingService.getConfirmationTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(pendingResponse);

        mockMvc.perform(get("/api/v1/orders/{orderId}/confirmation-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ACCREDITATION"))
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.ticketNumber").doesNotExist());
    }
}
