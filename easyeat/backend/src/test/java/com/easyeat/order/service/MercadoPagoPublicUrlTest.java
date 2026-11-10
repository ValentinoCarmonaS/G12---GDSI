package com.easyeat.order.service;

import com.easyeat.order.config.MercadoPagoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MercadoPagoPublicUrlTest (T025)")
class MercadoPagoPublicUrlTest {

    @Test
    @DisplayName("createPreference debe construir back_urls usando MERCADOPAGO_FRONTEND_BASE_URL sin referencias a localhost")
    @SuppressWarnings("unchecked")
    void createPreference_shouldUseFrontendBaseUrlForBackUrlsWithoutLocalhost() {
        // Given
        String publicFrontendBaseUrl = "https://easyeat-frontend.onrender.com";
        MercadoPagoProperties properties = new MercadoPagoProperties(
                "TEST-ACCESS-TOKEN-XYZ",
                "TEST-PUBLIC-KEY-XYZ",
                "webhook-secret-123",
                true,
                "https://api.mercadopago.com",
                publicFrontendBaseUrl
        );

        WebhookSignatureValidator webhookValidator = mock(WebhookSignatureValidator.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode responseJson = JsonNodeFactory.instance.objectNode();
        responseJson.put("id", "pref-public-12345");
        responseJson.put("init_point", "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-public-12345");
        responseJson.put("sandbox_init_point", "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-public-12345");

        when(restTemplate.postForEntity(eq("https://api.mercadopago.com/checkout/preferences"), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.CREATED));

        MercadoPagoGatewayAdapter adapter = new MercadoPagoGatewayAdapter(
                properties,
                webhookValidator,
                restTemplateBuilder,
                objectMapper
        );

        PaymentGatewayPort.CreatePreferenceCommand command = new PaymentGatewayPort.CreatePreferenceCommand(
                "ORD-TEST-001",
                BigDecimal.valueOf(1500.00),
                "ARS",
                "diner@example.com",
                List.of(new PaymentGatewayPort.GatewayItem("Burger", 1, BigDecimal.valueOf(1500.00), "ARS")),
                "idempotency-key-001"
        );

        // When
        PaymentGatewayPort.PreferenceResult result = adapter.createPreference(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.preferenceId()).isEqualTo("pref-public-12345");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.mercadopago.com/checkout/preferences"), entityCaptor.capture(), eq(com.fasterxml.jackson.databind.JsonNode.class));

        Map<String, Object> requestBody = entityCaptor.getValue().getBody();
        assertThat(requestBody).isNotNull();
        assertThat(requestBody).containsKey("back_urls");

        Map<String, String> backUrls = (Map<String, String>) requestBody.get("back_urls");
        assertThat(backUrls).isNotNull();

        String expectedReturnUrl = "https://easyeat-frontend.onrender.com/checkout/payment-return";
        assertThat(backUrls.get("success")).isEqualTo(expectedReturnUrl);
        assertThat(backUrls.get("pending")).isEqualTo(expectedReturnUrl);
        assertThat(backUrls.get("failure")).isEqualTo(expectedReturnUrl);

        // Verificación estricta de cero referencias a localhost en las URLs de retorno
        for (String url : backUrls.values()) {
            assertThat(url).doesNotContain("localhost");
            assertThat(url).doesNotContain("127.0.0.1");
            assertThat(url).startsWith("https://");
        }
    }
}
