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

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentGatewayResolver paymentGatewayResolver;
    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(
            PaymentGatewayResolver paymentGatewayResolver,
            PaymentWebhookService paymentWebhookService) {
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/{provider}/webhook")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        log.info("PaymentWebhookController: received webhook for provider={}", provider);

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(provider.replace("-", "_").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("PaymentWebhookController: unknown provider: {}", provider);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PaymentGatewayPort gatewayPort = paymentGatewayResolver.resolve(method);

        PaymentGatewayPort.WebhookPayload parsed = gatewayPort.parseWebhook(payload);
        String dataId = parsed.dataId() != null ? parsed.dataId() : dataIdParam;

        // Validar firma HMAC (INV-08)
        if (xSignature != null && !xSignature.isBlank()) {
            boolean valid = gatewayPort.verifyWebhookSignature(xSignature, xRequestId, dataId, "");
            if (!valid) {
                log.warn("PaymentWebhookController: invalid webhook signature for provider={}", provider);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        // Si tenemos un dataId de pago, reconciliar contra la API de la pasarela como única autoridad (INV-08)
        if (dataId != null && !dataId.isBlank()) {
            try {
                PaymentGatewayPort.GatewayPaymentResult result = gatewayPort.queryPayment(dataId);
                paymentWebhookService.processPaymentUpdate(
                        provider,
                        result.gatewayPaymentId(),
                        result.externalReference(),
                        result.status() != null ? result.status().name().toLowerCase() : "unknown",
                        result.statusDetail()
                );
            } catch (Exception e) {
                log.error("PaymentWebhookController: error querying payment from gateway for dataId={}", dataId, e);
                // Si la consulta falla temporalmente o es sandbox, retornamos 200 para evitar bombardeos si es reintento
            }
        }

        return ResponseEntity.ok().build();
    }
}
