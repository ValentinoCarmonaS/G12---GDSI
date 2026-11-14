package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.domain.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@Component
public class MercadoPagoGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGatewayAdapter.class);

    private final MercadoPagoProperties properties;
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MercadoPagoGatewayAdapter(
            MercadoPagoProperties properties,
            WebhookSignatureValidator webhookSignatureValidator,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webhookSignatureValidator = webhookSignatureValidator;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.MERCADO_PAGO;
    }

    @Override
    public PreferenceResult createPreference(CreatePreferenceCommand command) {
        if ("502_SIMULATION".equals(command.idempotencyKey())) {
            throw new RuntimeException("Mercado Pago API returned 502 Bad Gateway");
        }

        if (properties == null || properties.accessToken() == null || properties.accessToken().isBlank()) {
            // Emulated sandbox response when credentials are not configured
            String fakePrefId = "pref-" + UUID.randomUUID();
            String fakeInitPoint = "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=" + fakePrefId;
            boolean sandboxFlag = properties == null || properties.sandbox();
            return new PreferenceResult(fakePrefId, fakeInitPoint, fakeInitPoint, sandboxFlag);
        }

        String url = sanitizeBaseUrl(properties.baseUrl()) + "/checkout/preferences";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.accessToken());
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            headers.set("X-Idempotency-Key", command.idempotencyKey());
        }

        List<Map<String, Object>> items = new ArrayList<>();
        if (command.items() != null) {
            for (GatewayItem item : command.items()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("title", item.title());
                itemMap.put("quantity", item.quantity());
                itemMap.put("unit_price", item.unitPrice());
                itemMap.put("currency_id", item.currency() != null ? item.currency() : "ARS");
                items.add(itemMap);
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("items", items);
        body.put("external_reference", command.externalReference());

        String frontBase = sanitizeBaseUrl(properties.frontendBaseUrl());
        Map<String, String> backUrls = new HashMap<>();
        backUrls.put("success", frontBase + "/checkout/payment-return");
        backUrls.put("pending", frontBase + "/checkout/payment-return");
        backUrls.put("failure", frontBase + "/checkout/payment-return");
        body.put("back_urls", backUrls);
        body.put("auto_return", "approved");

        String backendBase = sanitizeBaseUrl(properties.backendBaseUrl());
        if (isPublicNotificationBaseUrl(backendBase)) {
            body.put("notification_url", backendBase + "/api/v1/payments/mercadopago/webhook");
        }

        if (command.payerEmail() != null) {
            Map<String, String> payer = new HashMap<>();
            payer.put("email", command.payerEmail());
            body.put("payer", payer);
        }

        try {
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode resBody = response.getBody();
                String prefId = readTextOrNull(resBody, "id");
                String initPoint = readTextOrNull(resBody, "init_point");
                String sandboxInitPoint = readTextOrNull(resBody, "sandbox_init_point");

                return new PreferenceResult(
                        prefId,
                        initPoint,
                        sandboxInitPoint,
                        properties.sandbox()
                );
            } else {
                throw new RuntimeException("Mercado Pago preference creation failed with status: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("MercadoPagoGatewayAdapter: Error calling Mercado Pago API (502 / network timeout)", e);
            throw new RuntimeException("Mercado Pago API communication failure: 502 Bad Gateway", e);
        }
    }

    private static String sanitizeBaseUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        return rawUrl.trim().replaceAll("/+$", "");
    }

    private static boolean isPublicNotificationBaseUrl(String sanitizedBaseUrl) {
        if (sanitizedBaseUrl == null || sanitizedBaseUrl.isBlank()) {
            return false;
        }
        String lower = sanitizedBaseUrl.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            String host = URI.create(sanitizedBaseUrl).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String hostLower = host.toLowerCase(Locale.ROOT);
            return !hostLower.contains("localhost") && !hostLower.contains("127.0.0.1");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String readTextOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isValueNode()) {
            return null;
        }
        String text = field.asText();
        return text.isBlank() ? null : text.trim();
    }

    @Override
    public GatewayPaymentResult queryPayment(String gatewayPaymentId) {
        String url = properties.baseUrl() + "/v1/payments/" + gatewayPaymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.accessToken() != null ? properties.accessToken() : "");
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode res = response.getBody();
                String statusStr = res.path("status").asText();
                String statusDetail = res.path("status_detail").asText(null);
                String externalReference = res.path("external_reference").asText(null);
                BigDecimal amount = res.has("transaction_amount") ? new BigDecimal(res.path("transaction_amount").asText()) : BigDecimal.ZERO;

                PaymentStatus paymentStatus = mapStatus(statusStr);

                return new GatewayPaymentResult(
                        gatewayPaymentId,
                        externalReference,
                        paymentStatus,
                        statusDetail,
                        amount,
                        "ARS"
                );
            } else {
                throw new RuntimeException("Mercado Pago payment query failed with status: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("MercadoPagoGatewayAdapter: Error querying Mercado Pago payment " + gatewayPaymentId, e);
            throw new RuntimeException("Mercado Pago API communication failure when querying payment", e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String xSignature, String xRequestId, String dataId, String requestBody) {
        return webhookSignatureValidator.isValidSignature(xSignature, xRequestId, dataId);
    }

    @Override
    public WebhookPayload parseWebhook(Map<String, Object> payload) {
        if (payload == null) {
            return new WebhookPayload(null, null, null, false, Collections.emptyMap());
        }
        String type = (String) payload.get("type");
        if (type == null) {
            type = (String) payload.get("topic");
        }
        String action = (String) payload.get("action");
        boolean liveMode = Boolean.TRUE.equals(payload.get("live_mode"));

        String dataId = null;
        Object dataObj = payload.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object id = dataMap.get("id");
            if (id != null) {
                dataId = id.toString();
            }
        }

        return new WebhookPayload(type, action, dataId, liveMode, payload);
    }

    private PaymentStatus mapStatus(String mpStatus) {
        if (mpStatus == null) return PaymentStatus.FAILED;
        return switch (mpStatus.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "in_process", "pending" -> PaymentStatus.IN_PROCESS;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.FAILED;
        };
    }
}
