package com.easyeat.order;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
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

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveTrackingScheduledCountdownTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private EstablishmentKdsSettingsRepository kdsSettingsRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private OrderLiveTrackingService liveTrackingService;

    private UUID orderId;
    private UUID userId;
    private UUID establishmentId;
    private Order order;
    private Establishment establishment;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        establishmentId = UUID.randomUUID();

        establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setTimezone("America/Argentina/Buenos_Aires");

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setEstablishmentId(establishmentId);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setFulfillmentMode(FulfillmentMode.SCHEDULED);
        order.setPickupDate(LocalDate.of(2026, 11, 9));
        order.setTimeSlotStart(LocalTime.of(14, 0));
        order.setTimeSlotEnd(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("AS-6/INV-006/CB-005: static phase (>15 min) disables countdown and returns null estimatedMinutes")
    void shouldKeepStaticPhaseWhenMoreThan15MinutesBeforeSlot() {
        // Current time: 12:30 (90 min before slot start 14:00)
        Instant fixedInstant = ZonedDateTime.of(
                LocalDate.of(2026, 11, 9),
                LocalTime.of(12, 30),
                ZoneId.of("America/Argentina/Buenos_Aires")
        ).toInstant();

        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.isCountdownActive()).isFalse();
        assertThat(response.estimatedMinutesRemaining()).isNull();
        assertThat(response.isReadyForPickup()).isFalse();
        assertThat(response.message()).contains("14:00");
    }

    @Test
    @DisplayName("AS-6/Q1/A1/CB-006: proximity phase (<=15 min) activates countdown and calculates exact remaining minutes")
    void shouldActivateCountdownWhenWithin15MinutesOfSlot() {
        // Current time: 13:46 (14 min before slot start 14:00)
        Instant fixedInstant = ZonedDateTime.of(
                LocalDate.of(2026, 11, 9),
                LocalTime.of(13, 46),
                ZoneId.of("America/Argentina/Buenos_Aires")
        ).toInstant();

        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.isCountdownActive()).isTrue();
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(14);
        assertThat(response.isReadyForPickup()).isFalse();
    }

    @Test
    @DisplayName("AS-8/INV-008/CB-008: READY_FOR_PICKUP state irreversibly suppresses countdown and flags isReadyForPickup true")
    void shouldSuppressCountdownAndFlagReadyWhenOrderIsReadyForPickup() {
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        Instant fixedInstant = ZonedDateTime.of(
                LocalDate.of(2026, 11, 9),
                LocalTime.of(14, 15),
                ZoneId.of("America/Argentina/Buenos_Aires")
        ).toInstant();

        when(clock.instant()).thenReturn(fixedInstant);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(response.isCountdownActive()).isFalse();
        assertThat(response.estimatedMinutesRemaining()).isNull();
        assertThat(response.progressPercentage()).isEqualTo(100);
        assertThat(response.isReadyForPickup()).isTrue();
        assertThat(response.message()).containsIgnoringCase("mostrador");
    }

    @Test
    @DisplayName("AS-6/CB-005: correctly computes local establishment time when server clock is UTC")
    void shouldCorrectlyComputeMinutesUntilSlotWhenServerClockIsUTC() {
        // Current local time: 12:39 in America/Argentina/Buenos_Aires = 15:39 UTC.
        // Slot start: 13:15 local time (36 minutes in the future).
        order.setPickupDate(LocalDate.of(2026, 11, 10));
        order.setTimeSlotStart(LocalTime.of(13, 15));
        order.setTimeSlotEnd(LocalTime.of(13, 30));

        Instant fixedInstant = ZonedDateTime.of(
                LocalDate.of(2026, 11, 10),
                LocalTime.of(12, 39),
                ZoneId.of("America/Argentina/Buenos_Aires")
        ).toInstant();

        // Server clock returns UTC
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.isCountdownActive()).isFalse();
        assertThat(response.estimatedMinutesRemaining()).isNull();
        assertThat(response.message()).contains("13:15");
    }

    @Test
    @DisplayName("AS-6/CB-006: transitions to in-slot last touches state when slot start time is reached without dispatch")
    void shouldTransitionToInSlotLastTouchesWhenSlotTimeReached() {
        // Slot start: 14:00. Current time: 14:05 (5 minutes into the slot, slot started).
        Instant fixedInstant = ZonedDateTime.of(
                LocalDate.of(2026, 11, 9),
                LocalTime.of(14, 5),
                ZoneId.of("America/Argentina/Buenos_Aires")
        ).toInstant();

        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.isCountdownActive()).isTrue();
        assertThat(response.estimatedMinutesRemaining()).isEqualTo(0);
        assertThat(response.progressPercentage()).isEqualTo(92);
        assertThat(response.isReadyForPickup()).isFalse();
        assertThat(response.message()).containsIgnoringCase("Tu turno ha comenzado");
    }
}
