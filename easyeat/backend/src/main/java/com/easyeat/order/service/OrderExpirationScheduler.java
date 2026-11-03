package com.easyeat.order.service;

import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class OrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Autowired
    public OrderExpirationScheduler(OrderRepository orderRepository,
                                    @Autowired(required = false) Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    public OrderExpirationScheduler(OrderRepository orderRepository) {
        this(orderRepository, Clock.systemDefaultZone());
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sweepExpiredOrders() {
        Instant now = clock.instant();
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(now);

        if (!expiredOrders.isEmpty()) {
            log.info("OrderExpirationScheduler: sweeping {} expired pending orders", expiredOrders.size());
            for (Order order : expiredOrders) {
                order.setStatus(OrderStatus.EXPIRED);
                order.setUpdatedAt(now);
            }
            orderRepository.saveAll(expiredOrders);
        }
    }
}
