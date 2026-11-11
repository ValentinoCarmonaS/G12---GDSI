package com.easyeat.order;

import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.easyeat.order.controller.PaymentWebhookController;
import com.easyeat.order.service.MercadoPagoGatewayAdapter;
import com.easyeat.order.service.PaymentGatewayResolver;
import com.easyeat.order.service.PaymentWebhookService;
import com.easyeat.order.service.WebhookSignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentWebhookController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        PaymentGatewayResolver.class,
        MercadoPagoGatewayAdapter.class,
        WebhookSignatureValidator.class,
        RestTemplateAutoConfiguration.class
})
@TestPropertySource(properties = {
        "mercadopago.webhook-secret=" + PaymentWebhookSecurityIntegrationTest.TEST_WEBHOOK_SECRET
})
@DisplayName("PaymentWebhookSecurityIntegrationTest (T024, T046)")
class PaymentWebhookSecurityIntegrationTest {

    static final String TEST_WEBHOOK_SECRET = "test-webhook-secret-017";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PaymentWebhookService paymentWebhookService;

    @Test
    @DisplayName("POST /api/v1/payments/mercadopago/webhook sin firma resuelve el adaptador y responde 200 OK (nunca 404)")
    void postWebhookMercadoPago_whenAnonymousWithoutSignature_shouldResolveAdapterAndReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/payments/mercadopago/webhook con firma HMAC válida responde 200 OK")
    void postWebhookMercadoPago_whenValidSignature_shouldReturnOk() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String requestId = "req-valid-001";
        String manifest = "id:;request-id:" + requestId + ";ts:" + ts + ";";
        String signature = "ts=" + ts + ",v1=" + calculateHmacSha256(manifest, TEST_WEBHOOK_SECRET);

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"payment.updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/payments/mercadopago/webhook con firma inválida alcanza el adaptador y rechaza con 401 del controlador")
    void postWebhookMercadoPago_whenInvalidSignature_shouldReturnControllerUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .header("x-request-id", "req-invalid-001")
                        .header("x-signature", "ts=" + Instant.now().getEpochSecond() + ",v1=invalid_hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"12345\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("POST /api/v1/payments/mercado-pago/webhook resuelve el adaptador de MERCADO_PAGO (200 OK o 401 ante firma inválida)")
    void postWebhookHyphenatedMercadoPago_shouldResolveAdapter() throws Exception {
        mockMvc.perform(post("/api/v1/payments/mercado-pago/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/mercado-pago/webhook")
                        .header("x-request-id", "req-invalid-002")
                        .header("x-signature", "ts=" + Instant.now().getEpochSecond() + ",v1=invalid_hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"12345\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("POST /api/v1/payments/stripe/webhook retorna 404 Not Found cuando el método no tiene adaptador registrado")
    void postWebhookMethodWithoutAdapter_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/payments/custom-provider/webhook retorna 404 Not Found ante proveedor desconocido")
    void postWebhookUnknownProvider_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/payments/custom-provider/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private static String calculateHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
