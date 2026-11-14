package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorHealthEndpointConfigurationYamlTest {

    @Test
    @DisplayName("application.yml debe configurar management.endpoints.web.exposure.include y management.endpoint.health.show-details")
    void actuatorHealthPropertiesShouldBeConfigured() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);
            assertThat(data).containsKey("management");

            @SuppressWarnings("unchecked")
            Map<String, Object> management = (Map<String, Object>) data.get("management");
            assertThat(management).containsKey("endpoints");

            @SuppressWarnings("unchecked")
            Map<String, Object> endpoints = (Map<String, Object>) management.get("endpoints");
            @SuppressWarnings("unchecked")
            Map<String, Object> web = (Map<String, Object>) endpoints.get("web");
            @SuppressWarnings("unchecked")
            Map<String, Object> exposure = (Map<String, Object>) web.get("exposure");
            assertThat(exposure.get("include").toString()).isEqualTo("health");

            assertThat(management).containsKey("endpoint");
            @SuppressWarnings("unchecked")
            Map<String, Object> endpoint = (Map<String, Object>) management.get("endpoint");
            @SuppressWarnings("unchecked")
            Map<String, Object> health = (Map<String, Object>) endpoint.get("health");
            assertThat(health.get("show-details").toString()).isEqualTo("when-authorized");
        } catch (Exception e) {
            throw new RuntimeException("Error al leer application.yml", e);
        }
    }
}

