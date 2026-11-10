package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayAndCorsConfigurationYamlTest {

    @Test
    @DisplayName("application.yml debe configurar spring.flyway y app.cors.allowed-origins")
    void flywayAndCorsPropertiesShouldBeConfigured() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);
            assertThat(data).containsKey("spring");

            @SuppressWarnings("unchecked")
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            assertThat(spring).containsKey("flyway");

            @SuppressWarnings("unchecked")
            Map<String, Object> flyway = (Map<String, Object>) spring.get("flyway");
            assertThat(flyway.get("enabled")).isEqualTo(true);

            Object locations = flyway.get("locations");
            assertThat(locations).isNotNull();
            if (locations instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> locList = (List<String>) locations;
                assertThat(locList).contains("classpath:db/migration", "classpath:db/dev");
            } else {
                assertThat(locations.toString()).contains("classpath:db/migration").contains("classpath:db/dev");
            }

            assertThat(data).containsKey("app");
            @SuppressWarnings("unchecked")
            Map<String, Object> app = (Map<String, Object>) data.get("app");
            assertThat(app).containsKey("cors");
            @SuppressWarnings("unchecked")
            Map<String, Object> cors = (Map<String, Object>) app.get("cors");
            assertThat(cors.get("allowed-origins").toString())
                    .isEqualTo("${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}");
        } catch (Exception e) {
            throw new RuntimeException("Error al leer application.yml", e);
        }
    }
}
