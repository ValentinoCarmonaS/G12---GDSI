package com.easyeat.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MercadoPagoProperties Binding and Defaults Unit Test")
class MercadoPagoPropertiesTest {

    @Test
    @DisplayName("Binds all properties correctly from prefix mercadopago")
    void testPropertiesBinding() {
        Map<String, Object> props = new HashMap<>();
        props.put("mercadopago.access-token", "TEST-access-token-123");
        props.put("mercadopago.public-key", "TEST-public-key-456");
        props.put("mercadopago.webhook-secret", "secret-xyz");
        props.put("mercadopago.sandbox", "true");
        props.put("mercadopago.base-url", "https://api.mercadopago.com");
        props.put("mercadopago.frontend-base-url", "http://localhost:5173");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(props);
        Binder binder = new Binder(source);

        MercadoPagoProperties properties = binder.bind("mercadopago", MercadoPagoProperties.class).get();

        assertThat(properties.accessToken()).isEqualTo("TEST-access-token-123");
        assertThat(properties.publicKey()).isEqualTo("TEST-public-key-456");
        assertThat(properties.webhookSecret()).isEqualTo("secret-xyz");
        assertThat(properties.sandbox()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(properties.frontendBaseUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("Defaults baseUrl and sandbox when optional properties are omitted")
    void testPropertiesDefaults() {
        Map<String, Object> props = new HashMap<>();
        props.put("mercadopago.access-token", "token");
        props.put("mercadopago.public-key", "key");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(props);
        Binder binder = new Binder(source);

        MercadoPagoProperties properties = binder.bind("mercadopago", MercadoPagoProperties.class).get();

        assertThat(properties.accessToken()).isEqualTo("token");
        assertThat(properties.publicKey()).isEqualTo("key");
        assertThat(properties.baseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(properties.sandbox()).isTrue();
    }
}

