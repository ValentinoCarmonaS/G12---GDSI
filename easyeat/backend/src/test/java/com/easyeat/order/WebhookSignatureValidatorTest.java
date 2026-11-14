package com.easyeat.order;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.easyeat.order.service.WebhookSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WebhookSignatureValidatorTest (T013, INV-08)")
class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;
    private final String secret = "test_mercadopago_secret_key_12345";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator(secret);
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Valida exitosamente una firma válida de Mercado Pago dentro de la ventana de 5 minutos")
    void shouldValidateValidSignatureWithinWindow() {
        long currentTimestamp = Instant.now().getEpochSecond();
        String dataId = "123456789";
        // Mercado Pago template: id:[data.id_url_param];request-id:[x-request-id];ts:[ts];
        String manifest = "id:" + dataId + ";request-id:req-999;ts:" + currentTimestamp + ";";
        String expectedHash = calculateHmacSha256(manifest, secret);

        String xSignatureHeader = "ts=" + currentTimestamp + ",v1=" + expectedHash;

        boolean isValid = validator.isValidSignature(xSignatureHeader, "req-999", dataId);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Rechaza firma si el timestamp tiene un desfasaje superior a 5 minutos (INV-08)")
    void shouldRejectSignatureWhenTimestampIsOlderThanFiveMinutes() {
        long oldTimestamp = Instant.now().minusSeconds(301).getEpochSecond();
        String dataId = "123456789";
        String manifest = "id:" + dataId + ";request-id:req-999;ts:" + oldTimestamp + ";";
        String hash = calculateHmacSha256(manifest, secret);

        String xSignatureHeader = "ts=" + oldTimestamp + ",v1=" + hash;

        boolean isValid = validator.isValidSignature(xSignatureHeader, "req-999", dataId);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Rechaza firma si el hash provisto no coincide (firma adulterada)")
    void shouldRejectTamperedSignature() {
        long currentTimestamp = Instant.now().getEpochSecond();
        String dataId = "123456789";
        String xSignatureHeader = "ts=" + currentTimestamp + ",v1=invalid_tampered_hash_value";

        boolean isValid = validator.isValidSignature(xSignatureHeader, "req-999", dataId);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Rechaza firma si el header está malformado o faltan partes")
    void shouldRejectMalformedHeader() {
        assertThat(validator.isValidSignature(null, "req-999", "12345")).isFalse();
        assertThat(validator.isValidSignature("", "req-999", "12345")).isFalse();
        assertThat(validator.isValidSignature("invalid_header_without_ts_or_v1", "req-999", "12345")).isFalse();
        assertThat(validator.isValidSignature("ts=123456", "req-999", "12345")).isFalse();
        assertThat(validator.isValidSignature("v1=abcdef", "req-999", "12345")).isFalse();
    }

    @Test
    @DisplayName("Q4: Rechaza fail-closed con log ERROR explícito cuando llega x-signature sin MERCADOPAGO_WEBHOOK_SECRET configurado")
    void shouldFailClosedWithErrorLogWhenWebhookSecretMissing() {
        Logger validatorLogger = (Logger) LoggerFactory.getLogger(WebhookSignatureValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        validatorLogger.addAppender(appender);
        try {
            WebhookSignatureValidator unconfiguredValidator = new WebhookSignatureValidator("");
            long currentTimestamp = Instant.now().getEpochSecond();
            String xSignatureHeader = "ts=" + currentTimestamp + ",v1=0123456789abcdef";

            boolean isValid = unconfiguredValidator.isValidSignature(xSignatureHeader, "req-missing-secret", "12345");

            assertThat(isValid).isFalse();
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getFormattedMessage()).contains("MERCADOPAGO_WEBHOOK_SECRET");
                    });
        } finally {
            validatorLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
