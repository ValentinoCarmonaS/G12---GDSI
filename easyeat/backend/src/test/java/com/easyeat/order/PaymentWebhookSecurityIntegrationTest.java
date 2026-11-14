package com.easyeat.order;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.easyeat.order.controller.PaymentWebhookController;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.service.MercadoPagoGatewayAdapter;
import com.easyeat.order.service.PaymentGatewayPort;
import com.easyeat.order.service.PaymentGatewayResolver;
import com.easyeat.order.service.PaymentWebhookService;
import com.easyeat.order.service.WebhookProcessingService;
import com.easyeat.order.service.WebhookSignatureValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
@DisplayName("PaymentWebhookSecurityIntegrationTest (T024, T046, T010)")
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

    @MockBean
    private WebhookProcessingService webhookProcessingService;

    @SpyBean
    private MercadoPagoGatewayAdapter mercadoPagoGatewayAdapter;

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

    @Test
    @DisplayName("JSON payment con firma válida delega en WebhookProcessingService")
    void postWebhookJsonPayment_validSignatureDelegatesToWebhookProcessingService() throws Exception {
        String dataId = "998877";
        String orderId = UUID.randomUUID().toString();
        long ts = Instant.now().getEpochSecond();
        String requestId = "req-json-001";
        String signature = validSignature(dataId, requestId, ts);

        doReturn(new PaymentGatewayPort.GatewayPaymentResult(
                dataId,
                orderId,
                PaymentStatus.APPROVED,
                "accredited",
                new BigDecimal("2500.00"),
                "ARS"
        )).when(mercadoPagoGatewayAdapter).queryPayment(dataId);

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .header("x-request-id", requestId)
                        .header("x-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"))
                .andExpect(status().isOk());

        verify(webhookProcessingService).processAsynchronousNotification(
                "mercadopago",
                dataId,
                orderId,
                "approved",
                "accredited"
        );
    }

    @Test
    @DisplayName("IPN query params (topic=payment&id=...) sin body JSON delega en WebhookProcessingService")
    void postWebhookIpnQueryParams_validSignatureDelegatesToWebhookProcessingService() throws Exception {
        String dataId = "554433";
        String orderId = UUID.randomUUID().toString();
        long ts = Instant.now().getEpochSecond();
        String requestId = "req-ipn-001";
        String signature = validSignature(dataId, requestId, ts);

        doReturn(new PaymentGatewayPort.GatewayPaymentResult(
                dataId,
                orderId,
                PaymentStatus.IN_PROCESS,
                "pending_waiting_payment",
                new BigDecimal("2500.00"),
                "ARS"
        )).when(mercadoPagoGatewayAdapter).queryPayment(dataId);

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .queryParam("topic", "payment")
                        .queryParam("id", dataId)
                        .header("x-request-id", requestId)
                        .header("x-signature", signature))
                .andExpect(status().isOk());

        verify(webhookProcessingService).processAsynchronousNotification(
                "mercadopago",
                dataId,
                orderId,
                "in_process",
                "pending_waiting_payment"
        );
    }

    @Test
    @DisplayName("Filtra tópico merchant_order respondiendo 200 OK sin consultar la pasarela ni procesar pago")
    void postWebhookMerchantOrder_returns200WithoutQueryingGateway() throws Exception {
        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .queryParam("topic", "merchant_order")
                        .queryParam("id", "777111"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"merchant_order\",\"data\":{\"id\":\"777112\"}}"))
                .andExpect(status().isOk());

        verify(mercadoPagoGatewayAdapter, never()).queryPayment(any());
        verifyNoInteractions(webhookProcessingService);
    }

    @Test
    @DisplayName("Q3: Ante InvalidOrderStateException (orden CANCELLED/EXPIRED) registra incidencia en logs y responde 200 OK")
    void postWebhookInvalidOrderStateQ3_logsIncidentAndReturns200() throws Exception {
        String dataId = "998811";
        String orderId = UUID.randomUUID().toString();
        long ts = Instant.now().getEpochSecond();
        String requestId = "req-q3-001";
        String signature = validSignature(dataId, requestId, ts);

        doReturn(new PaymentGatewayPort.GatewayPaymentResult(
                dataId,
                orderId,
                PaymentStatus.APPROVED,
                "accredited",
                new BigDecimal("3000.00"),
                "ARS"
        )).when(mercadoPagoGatewayAdapter).queryPayment(dataId);
        doThrow(new InvalidOrderStateException("Orden ya se encuentra EXPIRED"))
                .when(webhookProcessingService)
                .processAsynchronousNotification("mercadopago", dataId, orderId, "approved", "accredited");

        Logger controllerLogger = (Logger) LoggerFactory.getLogger(PaymentWebhookController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        controllerLogger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                            .header("x-request-id", requestId)
                            .header("x-signature", signature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"))
                    .andExpect(status().isOk());

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel().isGreaterOrEqual(Level.WARN)).isTrue();
                assertThat(event.getFormattedMessage()).contains(dataId);
                assertThat(event.getFormattedMessage()).contains(orderId);
            });
        } finally {
            controllerLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("Q4: Ante error transitorio de pasarela o base de datos propaga 5xx para habilitar reintentos de Mercado Pago")
    void postWebhookTransientFailureQ4_returns5xxForRetry() throws Exception {
        String gwFailId = "112233";
        long ts = Instant.now().getEpochSecond();
        String sig1 = validSignature(gwFailId, "req-gw-fail", ts);

        doThrow(new RuntimeException("502 Bad Gateway"))
                .when(mercadoPagoGatewayAdapter).queryPayment(gwFailId);

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .header("x-request-id", "req-gw-fail")
                        .header("x-signature", sig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + gwFailId + "\"}}"))
                .andExpect(status().is5xxServerError());

        String dbFailId = "445566";
        String orderId = UUID.randomUUID().toString();
        String sig2 = validSignature(dbFailId, "req-db-fail", ts);

        doReturn(new PaymentGatewayPort.GatewayPaymentResult(
                dbFailId,
                orderId,
                PaymentStatus.APPROVED,
                "accredited",
                new BigDecimal("1800.00"),
                "ARS"
        )).when(mercadoPagoGatewayAdapter).queryPayment(dbFailId);
        doThrow(new DataAccessResourceFailureException("DB connection pool exhausted"))
                .when(webhookProcessingService)
                .processAsynchronousNotification("mercadopago", dbFailId, orderId, "approved", "accredited");

        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .header("x-request-id", "req-db-fail")
                        .header("x-signature", sig2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dbFailId + "\"}}"))
                .andExpect(status().is5xxServerError());
    }

    private static String validSignature(String dataId, String requestId, long ts) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + calculateHmacSha256(manifest, TEST_WEBHOOK_SECRET);
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
