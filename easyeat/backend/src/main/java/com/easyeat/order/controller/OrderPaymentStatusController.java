package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.OrderPaymentStatusResponse;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderPaymentStatusController {

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    private final MercadoPagoProperties mercadoPagoProperties;

    @Autowired
    public OrderPaymentStatusController(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository,
            @Autowired(required = false) Clock clock,
            @Autowired(required = false) MercadoPagoProperties mercadoPagoProperties) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.paymentRepository = paymentRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    public OrderPaymentStatusController(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository,
            Clock clock) {
        this(orderRepository, temporaryReservationRepository, paymentRepository, clock, null);
    }

    @GetMapping("/{orderId}/payment-status")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    @Transactional
    public ResponseEntity<OrderPaymentStatusResponse> getOrderPaymentStatus(
            @PathVariable("orderId") UUID orderId,
            @AuthenticationPrincipal User authenticatedUser,
            Authentication authentication) {
        UUID currentUserId = resolveUserId(authenticatedUser, authentication);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getUserId() == null || !order.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("No tenés autorización para consultar el estado de pago de esta orden");
        }

        Instant now = clock.instant();

        Optional<TemporaryReservation> resOpt = temporaryReservationRepository.findByOrderId(orderId);
        Optional<Payment> payOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);

        // Expiración perezosa (Lazy expiration): Si expiró (<= now) y sigue PENDING_PAYMENT, transicionar en el acto
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getExpiresAt() != null
                && !order.getExpiresAt().isAfter(now)) {
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
            String initPoint = normalizeBlankToNull(p.getInitPoint());
            String sandboxInitPoint = normalizeBlankToNull(p.getSandboxInitPoint());
            String redirect = initPoint != null ? initPoint : sandboxInitPoint;
            boolean sandbox = mercadoPagoProperties == null || mercadoPagoProperties.sandbox();
            paySummary = new OrderPaymentStatusResponse.PaymentSummary(
                    p.getId(),
                    p.getStatus(),
                    p.getStatusDetail(),
                    redirect,
                    sandbox
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

    private UUID resolveUserId(User user, Authentication authentication) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User u && u.getId() != null) {
                return u.getId();
            }
            if (principal instanceof UUID uid) {
                return uid;
            }
            if (principal instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new AccessDeniedException("No authenticated diner found in security context");
    }

    private static String normalizeBlankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
