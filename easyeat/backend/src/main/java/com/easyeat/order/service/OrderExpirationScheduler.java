package com.easyeat.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationScheduler {

    @Scheduled(fixedRate = 60000)
    public void sweepExpiredOrders() {
        // Stub: to be implemented in T037
    }
}
