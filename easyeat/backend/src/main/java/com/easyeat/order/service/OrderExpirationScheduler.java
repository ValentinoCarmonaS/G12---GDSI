package com.easyeat.order.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class OrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Autowired
    public OrderExpirationScheduler(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository,
            @Autowired(required = false) Clock clock) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.paymentRepository = paymentRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    public OrderExpirationScheduler(OrderRepository orderRepository) {
        this(orderRepository, null, null, Clock.systemDefaultZone());
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sweepExpiredOrders() {
        Instant now = clock.instant();

        // 1. Barrido de órdenes pendientes vencidas
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(now);
        if (!expiredOrders.isEmpty()) {
            log.info("OrderExpirationScheduler: sweeping {} expired pending orders", expiredOrders.size());
            for (Order order : expiredOrders) {
                order.setStatus(OrderStatus.EXPIRED);
                order.setUpdatedAt(now);
            }
            orderRepository.saveAll(expiredOrders);
        }

        // 2. Barrido coordinado de reservas temporales vencidas (T028, AS-4b, INV-04)
        if (temporaryReservationRepository != null) {
            List<TemporaryReservation> expiredReservations = temporaryReservationRepository.findExpiredActiveReservations(now);
            if (!expiredReservations.isEmpty()) {
                log.info("OrderExpirationScheduler: sweeping {} expired active reservations", expiredReservations.size());
                for (TemporaryReservation res : expiredReservations) {
                    res.expire();

                    if (paymentRepository != null && res.getOrder() != null) {
                        Optional<Payment> paymentOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(res.getOrder().getId());
                        if (paymentOpt.isPresent()) {
                            Payment payment = paymentOpt.get();
                            if (payment.getStatus() == PaymentStatus.IN_PROCESS) {
                                payment.expire();
                                paymentRepository.save(payment);
                            }
                        }
                    }
                }
                temporaryReservationRepository.saveAll(expiredReservations);
            }
        }
    }
}
