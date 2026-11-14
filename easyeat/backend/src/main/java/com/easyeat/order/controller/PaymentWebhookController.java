package com.easyeat.order.controller;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.service.PaymentGatewayPort;
import com.easyeat.order.service.PaymentGatewayResolver;
import com.easyeat.order.service.PaymentWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentGatewayResolver paymentGatewayResolver;
    private final PaymentWebhookService paymentWebhookService;
    private final com.easyeat.order.service.WebhookProcessingService webhookProcessingService;

    public PaymentWebhookController(
            PaymentGatewayResolver paymentGatewayResolver,
            PaymentWebhookService paymentWebhookService) {
        this(paymentGatewayResolver, paymentWebhookService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentWebhookController(
            PaymentGatewayResolver paymentGatewayResolver,
            PaymentWebhookService paymentWebhookService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.easyeat.order.service.WebhookProcessingService webhookProcessingService) {
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.paymentWebhookService = paymentWebhookService;
        this.webhookProcessingService = webhookProcessingService;
    }

    @PostMapping("/{provider}/webhook")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam,
            @RequestParam(value = "id", required = false) String idParam,
            @RequestParam(value = "topic", required = false) String topicParam,
            @RequestParam(value = "type", required = false) String typeParam,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        log.info("PaymentWebhookController: received webhook for provider={}", provider);

        Optional<PaymentMethod> methodOpt = PaymentMethod.fromWebhookSlug(provider);
        if (methodOpt.isEmpty()) {
            log.warn("PaymentWebhookController: unknown provider: {}", provider);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Optional<PaymentGatewayPort> gatewayPortOpt = paymentGatewayResolver.findAdapter(methodOpt.get());
        if (gatewayPortOpt.isEmpty()) {
            log.warn("PaymentWebhookController: no adapter registered for provider={} (method={})", provider, methodOpt.get());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PaymentGatewayPort gatewayPort = gatewayPortOpt.get();

        PaymentGatewayPort.WebhookPayload parsed = gatewayPort.parseWebhook(payload);
        String resolvedType = firstNonBlank(parsed.type(), typeParam, topicParam);
        String dataId = firstNonBlank(parsed.dataId(), dataIdParam, idParam);

        // Validar firma HMAC fail-closed (INV-08)
        if (xSignature != null && !xSignature.isBlank()) {
            boolean valid = gatewayPort.verifyWebhookSignature(xSignature, xRequestId, dataId != null ? dataId : "", "");
            if (!valid) {
                log.warn("PaymentWebhookController: invalid webhook signature for provider={}", provider);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        if (resolvedType != null && !"payment".equalsIgnoreCase(resolvedType.trim())) {
            log.info("PaymentWebhookController: ignoring non-payment webhook topic/type={} for provider={}",
                    resolvedType, provider);
            return ResponseEntity.ok().build();
        }

        // Reconciliar pagos reales contra la API de la pasarela como única autoridad (INV-08)
        if (dataId != null && !dataId.isBlank()) {
            PaymentGatewayPort.GatewayPaymentResult result = gatewayPort.queryPayment(dataId);
            String normalizedStatus = result.status() != null
                    ? result.status().name().toLowerCase(java.util.Locale.ROOT)
                    : "unknown";
            try {
                if (webhookProcessingService != null) {
                    webhookProcessingService.processAsynchronousNotification(
                            provider,
                            result.gatewayPaymentId(),
                            result.externalReference(),
                            normalizedStatus,
                            result.statusDetail()
                    );
                } else {
                    paymentWebhookService.processPaymentUpdate(
                            provider,
                            result.gatewayPaymentId(),
                            result.externalReference(),
                            normalizedStatus,
                            result.statusDetail()
                    );
                }
            } catch (com.easyeat.order.exception.InvalidOrderStateException ex) {
                log.error("PaymentWebhookController: late webhook reconciliation alert (manual refund review) for provider={}, gatewayPaymentId={}, orderId={}, status={}: {}",
                        provider, result.gatewayPaymentId(), result.externalReference(), normalizedStatus, ex.getMessage());
                return ResponseEntity.ok().build();
            }
        }

        return ResponseEntity.ok().build();
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }
}
