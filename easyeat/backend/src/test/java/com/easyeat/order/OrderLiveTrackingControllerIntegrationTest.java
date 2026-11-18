package com.easyeat.order;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.order.controller.OrderConfirmationTrackingController;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.service.OrderConfirmationTrackingService;
import com.easyeat.order.service.OrderLiveTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("T010: OrderLiveTrackingControllerIntegrationTest (US1 - COM-8.1.1)")
class OrderLiveTrackingControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private OrderConfirmationTrackingService trackingService;

    @Mock
    private OrderLiveTrackingService liveTrackingService;

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
        testUser.setEmail("diner.live@test.com");

        auth = new UsernamePasswordAuthenticationToken(
                testUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMENSAL"))
        );
    }

    @Test
    @DisplayName("Estado QUEUED: debe retornar positionAhead (N pedidos adelante), minutero y barra nulos, y no-cache headers")
    void shouldReturnQueuedStateWithPositionAheadAndNullMetrics() throws Exception {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        OrderLiveTrackingResponse queuedResponse = new OrderLiveTrackingResponse(
                orderId,
                105,
                OrderStatus.IN_PROGRESS,
                FulfillmentMode.IMMEDIATE,
                false,
                null,
                null,
                false,
                null,
                "En cola en cocina",
                now,
                3, // positionAhead = 3
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new OrderLiveTrackingResponse.LiveTrackingItemDto("Hamburguesa Completa", 2))
        );

        when(liveTrackingService.getLiveTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(queuedResponse);

        mockMvc.perform(get("/api/v1/orders/{orderId}/live-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value(105))
                .andExpect(jsonPath("$.positionAhead").value(3))
                .andExpect(jsonPath("$.isCountdownActive").value(false))
                .andExpect(jsonPath("$.remainingMinutes").doesNotExist())
                .andExpect(jsonPath("$.progressPercent").doesNotExist())
                .andExpect(jsonPath("$.securityCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].dishName").value("Hamburguesa Completa"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(liveTrackingService).getLiveTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("Estado IN_PREPARATION: debe retornar minutero activo, barra de progreso y positionAhead ausente")
    void shouldReturnInPreparationStateWithActiveTimerAndNullPosition() throws Exception {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant startedAt = now.minusSeconds(300);

        OrderLiveTrackingResponse prepResponse = new OrderLiveTrackingResponse(
                orderId,
                105,
                OrderStatus.IN_PROGRESS,
                FulfillmentMode.IMMEDIATE,
                true,
                15,
                35,
                false,
                null,
                "Cocinando / En preparación",
                now,
                null, // positionAhead = null
                20,
                5,
                15,
                35,
                0,
                null,
                startedAt,
                null,
                List.of(new OrderLiveTrackingResponse.LiveTrackingItemDto("Pizza Muzzarella", 1))
        );

        when(liveTrackingService.getLiveTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(prepResponse);

        mockMvc.perform(get("/api/v1/orders/{orderId}/live-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.positionAhead").doesNotExist())
                .andExpect(jsonPath("$.isCountdownActive").value(true))
                .andExpect(jsonPath("$.estimatedTotalMinutes").value(20))
                .andExpect(jsonPath("$.elapsedMinutes").value(5))
                .andExpect(jsonPath("$.remainingMinutes").value(15))
                .andExpect(jsonPath("$.progressPercent").value(35))
                .andExpect(jsonPath("$.specificDelayMinutes").value(0))
                .andExpect(jsonPath("$.securityCode").doesNotExist());

        verify(liveTrackingService).getLiveTracking(eq(orderId), eq(testUser.getId()));
    }

    @Test
    @DisplayName("Estado READY_FOR_PICKUP: debe retornar panel de éxito con securityCode (4 dígitos), platos y métricas purgadas")
    void shouldReturnReadyForPickupWithSecurityCodeAndPurgedMetrics() throws Exception {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant readyAt = now.minusSeconds(60);

        OrderLiveTrackingResponse readyResponse = new OrderLiveTrackingResponse(
                orderId,
                105,
                OrderStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE,
                false,
                null,
                null,
                true,
                null,
                "¡Tu pedido está listo para retirar!",
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                "4821", // securityCode
                null,
                readyAt,
                List.of(new OrderLiveTrackingResponse.LiveTrackingItemDto("Empanadas", 6))
        );

        when(liveTrackingService.getLiveTracking(eq(orderId), eq(testUser.getId())))
                .thenReturn(readyResponse);

        mockMvc.perform(get("/api/v1/orders/{orderId}/live-tracking", orderId)
                        .principal(auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate"))
                .andExpect(jsonPath("$.isReadyForPickup").value(true))
                .andExpect(jsonPath("$.securityCode").value("4821"))
                .andExpect(jsonPath("$.positionAhead").doesNotExist())
                .andExpect(jsonPath("$.remainingMinutes").doesNotExist())
                .andExpect(jsonPath("$.progressPercent").doesNotExist())
                .andExpect(jsonPath("$.items[0].dishName").value("Empanadas"))
                .andExpect(jsonPath("$.items[0].quantity").value(6));

        verify(liveTrackingService).getLiveTracking(eq(orderId), eq(testUser.getId()));
    }
}
