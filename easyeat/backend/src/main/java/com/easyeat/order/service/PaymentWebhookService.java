package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

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

        if ("approved".equalsIgnoreCase(status)) {
            // Idempotencia: Si la orden ya está pagada y confirmada, no hacer nada
            if (order.getStatus() == OrderStatus.PAID) {
                log.info("PaymentWebhookService: order {} already in PAID state. Idempotent skip.", orderId);
                return;
            }

            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            if (reservation != null) {
                reservation.confirm();
                temporaryReservationRepository.save(reservation);
            }

            if (payment != null) {
                payment.approve(gatewayPaymentId, statusDetail != null ? statusDetail : "accredited");
                paymentRepository.save(payment);
            }
        } else if ("rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            // Rechazo o cancelación fehaciente: liberar recursos inmediatamente (AS-3, INV-03)
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.info("PaymentWebhookService: order {} already CANCELLED. Idempotent skip.", orderId);
                return;
            }

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            if (reservation != null) {
                reservation.release(ReleaseReason.REJECTED);
                temporaryReservationRepository.save(reservation);
            }

            if (payment != null) {
                payment.reject(gatewayPaymentId, statusDetail);
                paymentRepository.save(payment);
            }
        }
    }
}
