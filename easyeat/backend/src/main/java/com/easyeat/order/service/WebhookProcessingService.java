package com.easyeat.order.service;

import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PendingPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final WebhookSignatureValidator webhookSignatureValidator;
    private final CheckoutConfirmationService checkoutConfirmationService;
    private final PaymentWebhookService paymentWebhookService;

    public WebhookProcessingService(
            WebhookSignatureValidator webhookSignatureValidator,
            CheckoutConfirmationService checkoutConfirmationService,
            PaymentWebhookService paymentWebhookService
    ) {
        this.webhookSignatureValidator = webhookSignatureValidator;
        this.checkoutConfirmationService = checkoutConfirmationService;
        this.paymentWebhookService = paymentWebhookService;
    }

    public boolean validateSignature(String xSignature, String xRequestId, String dataId) {
        return webhookSignatureValidator.isValidSignature(xSignature, xRequestId, dataId);
    }

    public void processAsynchronousNotification(String provider, String gatewayPaymentId, String orderIdStr, String status, String statusDetail) {
        log.info("Processing webhook for provider={}, gatewayPaymentId={}, orderId={}, status={}",
                provider, gatewayPaymentId, orderIdStr, status);

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (Exception e) {
            log.warn("Invalid orderId: {}", orderIdStr);
            return;
        }

        if ("approved".equalsIgnoreCase(status)) {
            checkoutConfirmationService.confirmPayment(null, new PaymentConfirmationRequest(orderId, gatewayPaymentId, status));
        } else if ("pending".equalsIgnoreCase(status) || "in_process".equalsIgnoreCase(status)) {
            checkoutConfirmationService.registerPendingPayment(null, new PendingPaymentRequest(orderId, gatewayPaymentId, statusDetail));
        } else {
            paymentWebhookService.processPaymentUpdate(provider, gatewayPaymentId, orderIdStr, status, statusDetail);
        }
    }
}
