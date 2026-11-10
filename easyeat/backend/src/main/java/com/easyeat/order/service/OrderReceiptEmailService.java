package com.easyeat.order.service;

import com.easyeat.order.domain.OrderReceipt;
import com.easyeat.order.domain.OrderReceiptStatus;
import com.easyeat.order.event.OrderPaymentApprovedEvent;
import com.easyeat.order.repository.OrderReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for universal, asynchronous and idempotent order receipt email dispatch.
 */
@Service
public class OrderReceiptEmailService {

    private final JavaMailSender mailSender;
    private final OrderReceiptRepository orderReceiptRepository;
    private final String fromEmail;

    public OrderReceiptEmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            OrderReceiptRepository orderReceiptRepository,
            @Value("${app.mail.from:${spring.mail.username:noreply@easyeat.com}}") String fromEmail
    ) {
        this.mailSender = mailSender;
        this.orderReceiptRepository = orderReceiptRepository;
        this.fromEmail = fromEmail != null && !fromEmail.isBlank() ? fromEmail.trim() : "noreply@easyeat.com";
    }

    @Async
    public CompletableFuture<OrderReceipt> dispatchReceipt(OrderPaymentApprovedEvent event) {
        throw new UnsupportedOperationException("T009: OrderReceiptEmailService.dispatchReceipt not implemented yet");
    }
}
