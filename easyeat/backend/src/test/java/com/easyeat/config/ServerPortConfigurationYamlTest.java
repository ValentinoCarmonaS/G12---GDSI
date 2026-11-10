package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPortConfigurationYamlTest {

    @Test
    @DisplayName("server.port en application.yml debe estar configurado como ${PORT:8080}")
    void serverPortShouldBeConfiguredWithDynamicPortAndFallback() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);
            assertThat(data).containsKey("server");

            @SuppressWarnings("unchecked")
            Map<String, Object> server = (Map<String, Object>) data.get("server");
            assertThat(server).containsKey("port");
            assertThat(server.get("port").toString()).isEqualTo("${PORT:8080}");
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar application.yml", e);
        }
    }
}

