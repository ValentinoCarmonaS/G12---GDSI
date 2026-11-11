package com.easyeat.order.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Phase2FoundationalDtosTest {

    @Test
    @DisplayName("Should correctly instantiate and read immutable DTOs for checkout and KDS")
    void shouldInstantiateAndReadDtos() {
        UUID orderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Instant now = Instant.now();

        KdsTicketSummaryDto.EstimatedPickup pickup = new KdsTicketSummaryDto.EstimatedPickup(
                LocalDate.of(2026, 11, 10),
                LocalTime.of(13, 0),
                LocalTime.of(13, 30)
        );

        KdsTicketSummaryDto ticketSummary = new KdsTicketSummaryDto(
                ticketId,
                42,
                "QUEUED",
                now,
                pickup
        );

        assertThat(ticketSummary.ticketId()).isEqualTo(ticketId);
        assertThat(ticketSummary.ticketNumber()).isEqualTo(42);
        assertThat(ticketSummary.status()).isEqualTo("QUEUED");
        assertThat(ticketSummary.estimatedPickup().slotStart()).isEqualTo(LocalTime.of(13, 0));

        PaymentConfirmationRequest confirmReq = new PaymentConfirmationRequest(orderId, "mp-12345", "approved");
        assertThat(confirmReq.orderId()).isEqualTo(orderId);
        assertThat(confirmReq.gatewayPaymentId()).isEqualTo("mp-12345");
        assertThat(confirmReq.status()).isEqualTo("approved");

        PaymentConfirmationResponse confirmRes = new PaymentConfirmationResponse(
                orderId,
                "IN_PROGRESS",
                "APPROVED",
                true,
                ticketSummary,
                false
        );
        assertThat(confirmRes.orderId()).isEqualTo(orderId);
        assertThat(confirmRes.stockDebited()).isTrue();
        assertThat(confirmRes.kdsTicket()).isEqualTo(ticketSummary);
        assertThat(confirmRes.idempotentReplay()).isFalse();

        PendingPaymentRequest pendingReq = new PendingPaymentRequest(orderId, "mp-999", "pending_contingency");
        assertThat(pendingReq.orderId()).isEqualTo(orderId);
        assertThat(pendingReq.statusDetail()).isEqualTo("pending_contingency");

        PendingPaymentResponse pendingRes = new PendingPaymentResponse(
                orderId,
                "PENDING_ACCREDITATION",
                "PENDING",
                false,
                false,
                true,
                "Pago pendiente"
        );
        assertThat(pendingRes.stockDebited()).isFalse();
        assertThat(pendingRes.kdsQueued()).isFalse();
        assertThat(pendingRes.reservationFrozen()).isTrue();

        ZeroAmountOrderRequest zeroReq = new ZeroAmountOrderRequest(orderId);
        assertThat(zeroReq.orderId()).isEqualTo(orderId);

        ZeroAmountOrderResponse zeroRes = new ZeroAmountOrderResponse(
                orderId,
                "IN_PROGRESS",
                BigDecimal.ZERO,
                true,
                true,
                ticketSummary,
                "Orden confirmada"
        );
        assertThat(zeroRes.gatewayBypassed()).isTrue();
        assertThat(zeroRes.stockDebited()).isTrue();

        KdsDashboardSummaryResponse dashboard = new KdsDashboardSummaryResponse(5, 3, 2, 20);
        assertThat(dashboard.queuedOrdersCount()).isEqualTo(5);
        assertThat(dashboard.inPreparationOrdersCount()).isEqualTo(3);
        assertThat(dashboard.readyForPickupOrdersCount()).isEqualTo(2);
        assertThat(dashboard.completedTodayCount()).isEqualTo(20);
    }
}
