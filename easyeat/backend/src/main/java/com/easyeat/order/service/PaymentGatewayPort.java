package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Universal payment gateway port (Hexagonal Architecture / SOLID DIP).
 * Decouples business logic from specific payment providers (Mercado Pago, Stripe, etc.).
 */
public interface PaymentGatewayPort {

    PaymentMethod getPaymentMethod();

    PreferenceResult createPreference(CreatePreferenceCommand command);

    GatewayPaymentResult queryPayment(String gatewayPaymentId);

    boolean verifyWebhookSignature(String xSignature, String xRequestId, String dataId, String requestBody);

    WebhookPayload parseWebhook(Map<String, Object> payload);

    record CreatePreferenceCommand(
            String externalReference,
            BigDecimal totalAmount,
            String currency,
            String payerEmail,
            List<GatewayItem> items,
            String idempotencyKey
    ) {}

    record GatewayItem(
            String title,
            int quantity,
            BigDecimal unitPrice,
            String currency
    ) {}

    record PreferenceResult(
            String preferenceId,
            String initPoint,
            String sandboxInitPoint,
            boolean sandbox
    ) {}

    record GatewayPaymentResult(
            String gatewayPaymentId,
            String externalReference,
            PaymentStatus status,
            String statusDetail,
            BigDecimal transactionAmount,
            String currency
    ) {}

    record WebhookPayload(
            String type,
            String action,
            String dataId,
            boolean liveMode,
            Map<String, Object> rawData
    ) {}
}

