package com.easyeat.order.service;

import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PendingPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
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
        String normalizedStatus = status != null ? status.trim().toLowerCase(Locale.ROOT) : "";
        log.info("Processing webhook for provider={}, gatewayPaymentId={}, orderId={}, status={}",
                provider, gatewayPaymentId, orderIdStr, normalizedStatus);

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (Exception e) {
            log.warn("Invalid orderId: {}", orderIdStr);
            return;
        }

        if ("approved".equals(normalizedStatus)) {
            checkoutConfirmationService.confirmPayment(null, new PaymentConfirmationRequest(orderId, gatewayPaymentId, normalizedStatus));
        } else if ("pending".equals(normalizedStatus) || "in_process".equals(normalizedStatus)) {
            checkoutConfirmationService.registerPendingPayment(null, new PendingPaymentRequest(orderId, gatewayPaymentId, statusDetail));
        } else {
            paymentWebhookService.processPaymentUpdate(provider, gatewayPaymentId, orderIdStr, normalizedStatus, statusDetail);
        }
    }
}
