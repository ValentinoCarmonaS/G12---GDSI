package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.EstablishmentKdsSettings;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.repository.EstablishmentKdsSettingsRepository;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.OrderLiveTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveTrackingSaturationCalculationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentKdsSettingsRepository kdsSettingsRepository;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @InjectMocks
    private OrderLiveTrackingService liveTrackingService;

    private UUID orderId;
    private UUID userId;
    private UUID establishmentId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        establishmentId = UUID.randomUUID();

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setEstablishmentId(establishmentId);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setCreatedAt(Instant.now().minus(2, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("AS-5/INV-005: calculates dynamic estimated time and progress for IMMEDIATE order based on queue saturation and elapsed time")
    void shouldCalculateEstimatedTimeBasedOnQueueSaturation() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // 2 orders ahead in queue
        when(orderRepository.countActiveOrdersAhead(eq(establishmentId), any(Instant.class))).thenReturn(2L);
        // 0 delay
        when(kdsSettingsRepository.findById(establishmentId))
                .thenReturn(Optional.of(new EstablishmentKdsSettings(establishmentId, 0, null)));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(response.fulfillmentMode()).isEqualTo(FulfillmentMode.IMMEDIATE);
        assertThat(response.isCountdownActive()).isTrue();
        assertThat(response.isReadyForPickup()).isFalse();

        // Base total: 20 min + (2 * 2 min) + 0 delay = 24 min.
        // Elapsed: 2 min -> remaining: 22 min.
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(22);
        // Progress: 15 + round((2 / 24) * 77) = 15 + 6 = 21%
        assertThat(response.progressPercentage()).isEqualTo(21);
        assertThat(response.message()).isEqualTo("Preparando tu pedido");
        assertThat(response.kdsQueueSaturation()).isNotNull();
        assertThat(response.kdsQueueSaturation().activeOrdersAhead()).isEqualTo(2);
        assertThat(response.kdsQueueSaturation().kitchenStatus()).isEqualTo("NORMAL");
        assertThat(response.kdsQueueSaturation().globalDelayAppliedMinutes()).isEqualTo(0);
    }

    @Test
    @DisplayName("AS-7/INV-007: adjusts estimated time dynamically when global kitchen delay is configured")
    void shouldAdjustEstimatedTimeWhenGlobalDelayIsConfigured() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // 1 order ahead
        when(orderRepository.countActiveOrdersAhead(eq(establishmentId), any(Instant.class))).thenReturn(1L);
        // +10 min global delay
        when(kdsSettingsRepository.findById(establishmentId))
                .thenReturn(Optional.of(new EstablishmentKdsSettings(establishmentId, 10, null)));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        // Base total: 20 min + (1 * 2 min) + 10 delay = 32 min.
        // Elapsed: 2 min -> remaining: 30 min.
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(30);
        assertThat(response.kdsQueueSaturation().activeOrdersAhead()).isEqualTo(1);
        assertThat(response.kdsQueueSaturation().kitchenStatus()).isEqualTo("DELAYED");
        assertThat(response.kdsQueueSaturation().globalDelayAppliedMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("AS-5/INV-005: caps at 92% and 1 minute with last touches message when elapsed time exceeds estimate")
    void shouldCapAt92PercentAnd1MinuteWhenElapsedExceedsTotalEstimate() {
        // Order created 30 minutes ago, while total estimate will be 24 min
        order.setCreatedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.countActiveOrdersAhead(eq(establishmentId), any(Instant.class))).thenReturn(2L);
        when(kdsSettingsRepository.findById(establishmentId))
                .thenReturn(Optional.of(new EstablishmentKdsSettings(establishmentId, 0, null)));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(1);
        assertThat(response.progressPercentage()).isEqualTo(92);
        assertThat(response.message()).isEqualTo("Dando los últimos toques a tu pedido");
        assertThat(response.isReadyForPickup()).isFalse();
    }

    @Test
    @DisplayName("Obtiene ticketNumber real desde KdsTicketRepository y null si no existe comanda")
    void shouldUseKdsTicketNumberOrNullWithoutFakeHashCode() {
        Establishment est = new Establishment();
        est.setId(establishmentId);
        KdsTicket ticket = new KdsTicket(
                UUID.randomUUID(), orderId, est, 88,
                KdsTicketStatus.IN_PREPARATION, FulfillmentMode.IMMEDIATE,
                java.time.LocalDate.now(), java.time.LocalTime.of(12, 0), java.time.LocalTime.of(12, 30), Instant.now()
        );
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.countActiveOrdersAhead(eq(establishmentId), any(Instant.class))).thenReturn(0L);
        when(kdsSettingsRepository.findById(establishmentId)).thenReturn(Optional.empty());
        when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));

        OrderLiveTrackingResponse withTicket = liveTrackingService.getLiveTracking(orderId, userId);
        assertThat(withTicket.ticketNumber()).isEqualTo(88);

        when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        OrderLiveTrackingResponse withoutTicket = liveTrackingService.getLiveTracking(orderId, userId);
        assertThat(withoutTicket.ticketNumber()).isNull();
    }
}
