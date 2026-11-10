package com.easyeat.order;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.EstablishmentKdsSettings;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.repository.EstablishmentKdsSettingsRepository;
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
    @DisplayName("AS-5/INV-005: calculates dynamic estimated time for IMMEDIATE order based on queue saturation")
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

        // Base: 20 min + (2 * 2 min) + 0 delay = 24 min
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(24);
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
        // Base: 20 min + (1 * 2 min) + 10 delay = 32 min
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(32);
        assertThat(response.kdsQueueSaturation().activeOrdersAhead()).isEqualTo(1);
        assertThat(response.kdsQueueSaturation().kitchenStatus()).isEqualTo("DELAYED");
        assertThat(response.kdsQueueSaturation().globalDelayAppliedMinutes()).isEqualTo(10);
    }
}
