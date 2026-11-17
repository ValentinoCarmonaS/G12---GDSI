package com.easyeat.order;

import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T019: LiveTrackingDelayCalculationTest - Dynamic Delay Calculation Tests (US2 - COM-7.3.2 BIS)")
class LiveTrackingDelayCalculationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KdsTicketRepository kdsTicketRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private OrderLiveTrackingService liveTrackingService;

    private UUID orderId;
    private UUID userId;
    private Order order;
    private KdsTicket ticket;
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        fixedNow = Instant.parse("2026-11-15T12:00:00Z");

        when(clock.instant()).thenReturn(fixedNow);

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setEstablishmentId(UUID.randomUUID());
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 15));

        ticket = new KdsTicket(
                UUID.randomUUID(),
                orderId,
                null,
                101,
                KdsTicketStatus.IN_PREPARATION,
                FulfillmentMode.IMMEDIATE,
                LocalDate.now(),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                fixedNow.minus(5, ChronoUnit.MINUTES)
        );
        ticket.setStartedAt(fixedNow.minus(5, ChronoUnit.MINUTES)); // 5 min elapsed
        ticket.setSpecificDelayMinutes(0);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(kdsTicketRepository.findByOrderId(orderId)).thenReturn(Optional.of(ticket));
    }

    @Test
    @DisplayName("Sin demora (+0): 5 min transcurridos -> 15 min restantes de 20 min totales")
    void calculatesStandardRemainingMinutesWithoutDelay() {
        ticket.setSpecificDelayMinutes(0);

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response.estimatedTotalMinutes()).isEqualTo(20);
        assertThat(response.elapsedMinutes()).isEqualTo(5);
        assertThat(response.remainingMinutes()).isEqualTo(15);
        assertThat(response.specificDelayMinutes()).isEqualTo(0);
        assertThat(response.progressPercent()).isBetween(15, 92);
    }

    @Test
    @DisplayName("Con incremento granular (+5 min): 5 min transcurridos -> 20 min restantes de 25 min totales")
    void recalculatesEstimatedTotalAndRemainingMinutesWithPlusFiveDelay() {
        ticket.setSpecificDelayMinutes(5);

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response.estimatedTotalMinutes()).isEqualTo(25);
        assertThat(response.elapsedMinutes()).isEqualTo(5);
        assertThat(response.remainingMinutes()).isEqualTo(20);
        assertThat(response.specificDelayMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("Con adiciones sucesivas (+15 min): 5 min transcurridos -> 30 min restantes de 35 min totales")
    void recalculatesEstimatedTotalWithSuccessiveAdditions() {
        ticket.setSpecificDelayMinutes(15);

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response.estimatedTotalMinutes()).isEqualTo(35);
        assertThat(response.elapsedMinutes()).isEqualTo(5);
        assertThat(response.remainingMinutes()).isEqualTo(30);
        assertThat(response.specificDelayMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("Cota de piso y techo: cuando el tiempo transcurrido supera el estimado, fija piso en 1 min y techo en 92%")
    void appliesFloorAtOneMinuteAndCeilingAtNinetyTwoPercentWhenExceeded() {
        // 40 min transcurridos con 20 min de estimado (excedido por 20 min)
        ticket.setStartedAt(fixedNow.minus(40, ChronoUnit.MINUTES));
        ticket.setSpecificDelayMinutes(0);

        OrderLiveTrackingResponse response = liveTrackingService.getLiveTracking(orderId, userId);

        assertThat(response.estimatedTotalMinutes()).isEqualTo(20);
        assertThat(response.elapsedMinutes()).isEqualTo(40);
        assertThat(response.remainingMinutes()).isEqualTo(1); // Cota de piso estricta
        assertThat(response.progressPercent()).isEqualTo(92); // Cota de techo estricta
        assertThat(response.message()).isEqualTo("Dando los últimos toques a tu pedido");
    }
}
