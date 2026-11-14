package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private static final Set<OrderStatus> ALREADY_CONFIRMED_STATUSES = Set.of(
            OrderStatus.PAID,
            OrderStatus.CONFIRMED,
            OrderStatus.IN_PROGRESS,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.COMPLETED
    );

    private static final Set<OrderStatus> PROTECTED_FROM_NEGATIVE_WEBHOOK = Set.of(
            OrderStatus.IN_PROGRESS,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.COMPLETED,
            OrderStatus.PAID,
            OrderStatus.CONFIRMED,
            OrderStatus.CANCELLED,
            OrderStatus.EXPIRED
    );

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final PaymentRepository paymentRepository;

    public PaymentWebhookService(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Procesa la actualización fehaciente de pago recibida vía webhook (AS-2b, AS-3, INV-02, INV-06).
     * Garantiza estricta idempotencia ante llamadas duplicadas.
     */
    @Transactional
    public void processPaymentUpdate(String provider, String gatewayPaymentId, String externalReference, String status, String statusDetail) {
        log.info("PaymentWebhookService: processing provider={}, gatewayPaymentId={}, externalReference={}, status={}",
                provider, gatewayPaymentId, externalReference, status);

        UUID orderId;
        try {
            orderId = UUID.fromString(externalReference);
        } catch (Exception e) {
            log.warn("PaymentWebhookService: invalid externalReference (not a valid UUID): {}", externalReference);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("PaymentWebhookService: order not found for externalReference={}", externalReference);
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId());
        Payment payment = paymentOpt.orElse(null);

        Optional<TemporaryReservation> resOpt = temporaryReservationRepository.findActiveByOrderId(order.getId());
        TemporaryReservation reservation = resOpt.orElse(null);

        String normalizedStatus = status != null ? status.trim().toLowerCase(Locale.ROOT) : "";

        if ("approved".equals(normalizedStatus)) {
            if (ALREADY_CONFIRMED_STATUSES.contains(order.getStatus())) {
                log.info("PaymentWebhookService: order {} already in confirmed status {}. Idempotent skip.",
                        orderId, order.getStatus());
                return;
            }
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
                log.warn("PaymentWebhookService: late approved webhook blocked for order {} in status {}",
                        orderId, order.getStatus());
                throw new InvalidOrderStateException(
                        "No se puede aprobar una orden que ya se encuentra en estado " + order.getStatus()
                );
            }

            order.setStatus(OrderStatus.PAID);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            if (reservation != null) {
                reservation.confirm();
                temporaryReservationRepository.save(reservation);
            }

            if (payment != null) {
                payment.approve(gatewayPaymentId, statusDetail != null ? statusDetail : "accredited");
                paymentRepository.save(payment);
            }
        } else if ("rejected".equals(normalizedStatus)
                || "cancelled".equals(normalizedStatus)
                || "failed".equals(normalizedStatus)
                || "expired".equals(normalizedStatus)) {
            if (PROTECTED_FROM_NEGATIVE_WEBHOOK.contains(order.getStatus())) {
                log.warn("PaymentWebhookService: ignoring negative webhook status={} for order {} already in status={}",
                        normalizedStatus, orderId, order.getStatus());
                return;
            }

            if ("expired".equals(normalizedStatus)) {
                order.setStatus(OrderStatus.EXPIRED);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);

                if (reservation != null) {
                    reservation.release(ReleaseReason.EXPIRED);
                    temporaryReservationRepository.save(reservation);
                }

                if (payment != null) {
                    if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
                        payment.setGatewayPaymentId(gatewayPaymentId);
                    }
                    payment.expire();
                    paymentRepository.save(payment);
                }
                return;
            }

            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            if ("cancelled".equals(normalizedStatus)) {
                if (reservation != null) {
                    reservation.release(ReleaseReason.CANCELLED_BY_USER);
                    temporaryReservationRepository.save(reservation);
                }
                if (payment != null) {
                    if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
                        payment.setGatewayPaymentId(gatewayPaymentId);
                    }
                    if (statusDetail != null && !statusDetail.isBlank()) {
                        payment.setStatusDetail(statusDetail);
                    }
                    payment.cancel();
                    paymentRepository.save(payment);
                }
            } else if ("rejected".equals(normalizedStatus)) {
                if (reservation != null) {
                    reservation.release(ReleaseReason.REJECTED);
                    temporaryReservationRepository.save(reservation);
                }
                if (payment != null) {
                    payment.reject(gatewayPaymentId, statusDetail);
                    paymentRepository.save(payment);
                }
            } else {
                if (reservation != null) {
                    reservation.release(ReleaseReason.GATEWAY_ERROR);
                    temporaryReservationRepository.save(reservation);
                }
                if (payment != null) {
                    if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
                        payment.setGatewayPaymentId(gatewayPaymentId);
                    }
                    payment.fail(statusDetail);
                    paymentRepository.save(payment);
                }
            }
        }
    }
}
