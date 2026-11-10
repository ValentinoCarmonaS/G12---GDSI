package com.easyeat.order.controller;

import com.easyeat.order.domain.*;
import com.easyeat.order.dto.OrderPaymentStatusResponse;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderPaymentStatusController {

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public OrderPaymentStatusController(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.paymentRepository = paymentRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    @GetMapping("/{orderId}/payment-status")
    @Transactional
    public ResponseEntity<OrderPaymentStatusResponse> getOrderPaymentStatus(@PathVariable("orderId") UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada: " + orderId));

        Instant now = clock.instant();

        Optional<TemporaryReservation> resOpt = temporaryReservationRepository.findAll().stream()
                .filter(r -> r.getOrder() != null && r.getOrder().getId().equals(orderId))
                .findFirst();

        Optional<Payment> payOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);

        // Expiración perezosa (Lazy expiration): Si expiró y sigue PENDING_PAYMENT, transicionar en el acto
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getExpiresAt() != null && order.getExpiresAt().isBefore(now)) {
            order.setStatus(OrderStatus.EXPIRED);
            order.setUpdatedAt(now);
            orderRepository.save(order);

            if (resOpt.isPresent() && resOpt.get().getStatus() == ReservationStatus.ACTIVE) {
                TemporaryReservation res = resOpt.get();
                res.expire();
                temporaryReservationRepository.save(res);
            }

            if (payOpt.isPresent() && payOpt.get().getStatus() == PaymentStatus.IN_PROCESS) {
                Payment payment = payOpt.get();
                payment.expire();
                paymentRepository.save(payment);
            }
        }

        OrderPaymentStatusResponse.ReservationSummary resSummary = null;
        if (resOpt.isPresent()) {
            TemporaryReservation res = resOpt.get();
            Long remainingSeconds = null;
            if (res.getStatus() == ReservationStatus.ACTIVE && res.getExpiresAt() != null) {
                long diff = Duration.between(now, res.getExpiresAt()).getSeconds();
                remainingSeconds = Math.max(0, diff);
            }
            resSummary = new OrderPaymentStatusResponse.ReservationSummary(
                    res.getId(),
                    res.getStatus(),
                    res.getExpiresAt(),
                    remainingSeconds,
                    res.getReleaseReason()
            );
        }

        OrderPaymentStatusResponse.PaymentSummary paySummary = null;
        if (payOpt.isPresent()) {
            Payment p = payOpt.get();
            String redirect = p.getSandboxInitPoint() != null ? p.getSandboxInitPoint() : p.getInitPoint();
            paySummary = new OrderPaymentStatusResponse.PaymentSummary(
                    p.getId(),
                    p.getStatus(),
                    p.getStatusDetail(),
                    redirect,
                    p.getSandboxInitPoint() != null
            );
        }

        OrderPaymentStatusResponse.FulfillmentSummary fulfillmentSummary = new OrderPaymentStatusResponse.FulfillmentSummary(
                order.getFulfillmentMode(),
                order.getPickupDate(),
                order.getTimeSlotStart(),
                order.getTimeSlotEnd()
        );

        OrderPaymentStatusResponse response = new OrderPaymentStatusResponse(
                order.getId(),
                order.getStatus(),
                resSummary,
                paySummary,
                fulfillmentSummary,
                order.getTotalAmount(),
                "ARS",
                now
        );

        return ResponseEntity.ok(response);
    }
}
