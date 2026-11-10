package com.easyeat.order.service;

import com.easyeat.order.event.OrderPaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Domain event listener for decoupled processing of approved order payments.
 * Triggers universal, asynchronous receipt email dispatch outside HTTP request cycles.
 */
@Component
public class OrderPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentEventListener.class);

    private final OrderReceiptEmailService receiptEmailService;

    public OrderPaymentEventListener(OrderReceiptEmailService receiptEmailService) {
        this.receiptEmailService = receiptEmailService;
    }

    @Async
    @EventListener
    public void onOrderPaymentApproved(OrderPaymentApprovedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Captured OrderPaymentApprovedEvent for orderId={}. Triggering receipt email dispatch.", event.orderId());
        receiptEmailService.dispatchReceipt(event);
    }
}
