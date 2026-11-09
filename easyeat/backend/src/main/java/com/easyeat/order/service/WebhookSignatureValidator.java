package com.easyeat.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Component
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_TIME_DIFF_SECONDS = 300; // 5 minutos (INV-08)

    private final String secretKey;

    public WebhookSignatureValidator(@Value("${mercadopago.webhook-secret:}") String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Valida la firma HMAC-SHA256 de Mercado Pago (INV-08).
     * Formato esperado de x-signature: "ts=[timestamp],v1=[hash]"
     * Formato de manifest según Mercado Pago: "id:[data.id];request-id:[x-request-id];ts:[ts];"
     */
    public boolean isValidSignature(String xSignatureHeader, String xRequestId, String dataId) {
        if (xSignatureHeader == null || xSignatureHeader.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;

        String[] parts = xSignatureHeader.split(",");
        for (String part : parts) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2) {
                if ("ts".equalsIgnoreCase(keyValue[0])) {
                    ts = keyValue[1].trim();
                } else if ("v1".equalsIgnoreCase(keyValue[0])) {
                    v1 = keyValue[1].trim();
                }
            }
        }

        if (ts == null || v1 == null) {
            return false;
        }

        try {
            long timestampSeconds = Long.parseLong(ts);
            // Mercado Pago a veces envía timestamp en milisegundos si es > 10^11
            if (timestampSeconds > 10_000_000_000L) {
                timestampSeconds = timestampSeconds / 1000;
            }

            long currentSeconds = Instant.now().getEpochSecond();
            if (Math.abs(currentSeconds - timestampSeconds) > MAX_TIME_DIFF_SECONDS) {
                log.warn("WebhookSignatureValidator: timestamp fuera de la ventana permitida ({} s de desfasaje)",
                        Math.abs(currentSeconds - timestampSeconds));
                return false;
            }

            String manifest = "id:" + (dataId != null ? dataId : "") +
                    ";request-id:" + (xRequestId != null ? xRequestId : "") +
                    ";ts:" + ts + ";";

            String calculatedHash = calculateHmacSha256(manifest, secretKey);

            return MessageDigest.isEqual(
                    calculatedHash.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NumberFormatException e) {
            log.warn("WebhookSignatureValidator: timestamp inválido en header: {}", ts);
            return false;
        } catch (Exception e) {
            log.error("WebhookSignatureValidator: error calculando firma HMAC", e);
            return false;
        }
    }

    private String calculateHmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(rawHmac.length * 2);
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
