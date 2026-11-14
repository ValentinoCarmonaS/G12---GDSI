package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NeonDataSourceConfigurationYamlTest {

    @Test
    @DisplayName("spring.datasource en application.yml debe incluir conexión parametrizada y HikariCP resiliente")
    void datasourcePropertiesShouldBeProperlyConfigured() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);
            assertThat(data).containsKey("spring");

            @SuppressWarnings("unchecked")
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            assertThat(spring).containsKey("datasource");

            @SuppressWarnings("unchecked")
            Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
            assertThat(datasource.get("url").toString())
                    .isEqualTo("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/easyeat}");
            assertThat(datasource.get("username").toString())
                    .isEqualTo("${SPRING_DATASOURCE_USERNAME:easyeat}");
            assertThat(datasource.get("password").toString())
                    .isEqualTo("${SPRING_DATASOURCE_PASSWORD:easyeat_dev_password}");

            assertThat(datasource).containsKey("hikari");
            @SuppressWarnings("unchecked")
            Map<String, Object> hikari = (Map<String, Object>) datasource.get("hikari");
            assertThat(hikari.get("connection-timeout")).isEqualTo(30000);
            assertThat(hikari.get("validation-timeout")).isEqualTo(5000);
            assertThat(hikari.get("maximum-pool-size")).isEqualTo(5);
        } catch (Exception e) {
            throw new RuntimeException("Error al leer application.yml", e);
        }
    }
}

