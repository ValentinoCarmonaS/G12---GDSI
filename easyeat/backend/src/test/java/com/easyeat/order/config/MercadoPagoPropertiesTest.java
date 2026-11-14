package com.easyeat.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
        props.put("mercadopago.backend-base-url", "https://api.easyeat.com/");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(props);
        Binder binder = new Binder(source);

        MercadoPagoProperties properties = binder.bind("mercadopago", MercadoPagoProperties.class).get();

        assertThat(properties.accessToken()).isEqualTo("TEST-access-token-123");
        assertThat(properties.publicKey()).isEqualTo("TEST-public-key-456");
        assertThat(properties.webhookSecret()).isEqualTo("secret-xyz");
        assertThat(properties.sandbox()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(properties.frontendBaseUrl()).isEqualTo("http://localhost:5173");
        assertThat(properties.backendBaseUrl()).isEqualTo("https://api.easyeat.com/");
    }

    @Test
    @DisplayName("Defaults baseUrl, frontendBaseUrl, backendBaseUrl and sandbox when optional properties are omitted")
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
        assertThat(properties.frontendBaseUrl()).isEqualTo("http://localhost:3000");
        assertThat(properties.backendBaseUrl()).isEmpty();
        assertThat(properties.sandbox()).isTrue();
    }

    @Test
    @DisplayName("Compatible 6-argument constructor defaults backendBaseUrl to empty string")
    void testSixArgumentCompatibilityConstructor() {
        MercadoPagoProperties properties = new MercadoPagoProperties(
                "token-6",
                "pk-6",
                "secret-6",
                false,
                "https://api.mercadopago.com",
                "https://front.easyeat.com"
        );

        assertThat(properties.accessToken()).isEqualTo("token-6");
        assertThat(properties.publicKey()).isEqualTo("pk-6");
        assertThat(properties.webhookSecret()).isEqualTo("secret-6");
        assertThat(properties.sandbox()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(properties.frontendBaseUrl()).isEqualTo("https://front.easyeat.com");
        assertThat(properties.backendBaseUrl()).isEmpty();
    }

    @Test
    @DisplayName("Resolves backendBaseUrl from RENDER_EXTERNAL_URL fallback and prioritizes MERCADOPAGO_BACKEND_BASE_URL in application.yml")
    void testBackendBaseUrlRenderFallbackAndPrecedenceFromApplicationYml() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> loaded = loader.load("application", new ClassPathResource("application.yml"));

        MockEnvironment fallbackEnv = new MockEnvironment();
        loaded.forEach(ps -> fallbackEnv.getPropertySources().addLast(ps));
        fallbackEnv.setProperty("RENDER_EXTERNAL_URL", "https://easyeat-backend.onrender.com");

        MercadoPagoProperties fallbackProps = Binder.get(fallbackEnv).bind("mercadopago", MercadoPagoProperties.class).get();
        assertThat(fallbackProps.backendBaseUrl()).isEqualTo("https://easyeat-backend.onrender.com");

        MockEnvironment overrideEnv = new MockEnvironment();
        loaded.forEach(ps -> overrideEnv.getPropertySources().addLast(ps));
        overrideEnv.setProperty("RENDER_EXTERNAL_URL", "https://easyeat-backend.onrender.com");
        overrideEnv.setProperty("MERCADOPAGO_BACKEND_BASE_URL", "https://api-custom.easyeat.com");

        MercadoPagoProperties overrideProps = Binder.get(overrideEnv).bind("mercadopago", MercadoPagoProperties.class).get();
        assertThat(overrideProps.backendBaseUrl()).isEqualTo("https://api-custom.easyeat.com");

        MockEnvironment emptyEnv = new MockEnvironment();
        loaded.forEach(ps -> emptyEnv.getPropertySources().addLast(ps));

        MercadoPagoProperties emptyProps = Binder.get(emptyEnv).bind("mercadopago", MercadoPagoProperties.class).get();
        assertThat(emptyProps.backendBaseUrl()).isEmpty();
        assertThat(emptyProps.frontendBaseUrl()).isEqualTo("http://localhost:3000");
    }
}

