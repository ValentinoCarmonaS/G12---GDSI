package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPortConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    @DisplayName("Debe definir la expresión de server.port como ${PORT:8080} en application.yml")
    void serverPort_inApplicationYaml_shouldBeConfiguredWithDynamicPortAndFallback() throws IOException {
        List<PropertySource<?>> propertySources = loader.load(
                "application.yml",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources).isNotEmpty();
        Object serverPort = propertySources.get(0).getProperty("server.port");
        assertThat(serverPort).isEqualTo("${PORT:8080}");
    }

    @Test
    @DisplayName("Debe resolver el puerto por defecto 8080 cuando no se define la variable PORT")
    void serverPort_whenPortNotProvided_shouldResolveTo8080() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=classpath:application.yml")
                .run()) {
            String resolvedPort = ctx.getEnvironment().getProperty("server.port");
            assertThat(resolvedPort).isEqualTo("8080");
        }
    }

    @Test
    @DisplayName("Debe resolver el puerto dinámicamente cuando se inyecta la variable PORT")
    void serverPort_whenPortProvided_shouldResolveCustomPort() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(EmptyConfig.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.location=classpath:application.yml",
                        "PORT=10000"
                )
                .run()) {
            String resolvedPort = ctx.getEnvironment().getProperty("server.port");
            assertThat(resolvedPort).isEqualTo("10000");
        }
    }

    @org.springframework.context.annotation.Configuration
    static class EmptyConfig {
    }
}
