package com.easyeat.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades tipadas para la integración de pagos con Mercado Pago Checkout Pro.
 */
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
        String accessToken,
        String publicKey,
        String webhookSecret,
        @DefaultValue("true") boolean sandbox,
        @DefaultValue("https://api.mercadopago.com") String baseUrl,
        @DefaultValue("http://localhost:3000") String frontendBaseUrl,
        @DefaultValue("") String backendBaseUrl
) {

    @ConstructorBinding
    public MercadoPagoProperties {
    }

    /**
     * Constructor secundario de compatibilidad para llamadas y pruebas existentes de 6 argumentos.
     */
    public MercadoPagoProperties(
            String accessToken,
            String publicKey,
            String webhookSecret,
            boolean sandbox,
            String baseUrl,
            String frontendBaseUrl
    ) {
        this(accessToken, publicKey, webhookSecret, sandbox, baseUrl, frontendBaseUrl, "");
    }
}

