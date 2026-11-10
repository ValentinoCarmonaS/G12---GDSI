package com.easyeat.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        @DefaultValue("http://localhost:5173") String frontendBaseUrl
) {}

