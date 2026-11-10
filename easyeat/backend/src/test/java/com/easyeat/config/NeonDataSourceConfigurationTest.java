package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T018: Neon DataSource Configuration Test")
class NeonDataSourceConfigurationTest {

    @Configuration
    static class TestContextConfig {
    }

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    @DisplayName("Debe definir las expresiones esperadas para DataSource y HikariCP en application.yml")
    void applicationYaml_shouldDeclareDataSourceAndHikariProperties() throws IOException {
        List<PropertySource<?>> propertySources = loader.load(
                "application.yml",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources).isNotEmpty();
        PropertySource<?> source = propertySources.get(0);

        // DataSource properties
        assertThat(source.getProperty("spring.datasource.url"))
                .isEqualTo("${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/easyeat}");
        assertThat(source.getProperty("spring.datasource.username"))
                .isEqualTo("${SPRING_DATASOURCE_USERNAME:easyeat}");
        assertThat(source.getProperty("spring.datasource.password"))
                .isEqualTo("${SPRING_DATASOURCE_PASSWORD:easyeat_dev_password}");
        assertThat(source.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");

        // HikariCP properties for tolerating Neon scale-to-zero
        assertThat(source.getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo(30000);
        assertThat(source.getProperty("spring.datasource.hikari.validation-timeout"))
                .isEqualTo(5000);
        assertThat(source.getProperty("spring.datasource.hikari.maximum-pool-size"))
                .isEqualTo(5);
        assertThat(source.getProperty("spring.datasource.hikari.minimum-idle"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Debe resolver valores por defecto de DataSource cuando no hay variables de entorno definidas")
    void dataSourceProperties_whenEnvNotProvided_shouldResolveDefaults() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(TestContextConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=classpath:application.yml")
                .run()) {

            assertThat(ctx.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/easyeat");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.username"))
                    .isEqualTo("easyeat");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo("easyeat_dev_password");
        }
    }

    @Test
    @DisplayName("Debe resolver la URL con sslmode=require y credenciales de Neon inyectadas por variables de entorno")
    void dataSourceProperties_whenNeonEnvProvided_shouldResolveNeonUrlAndCredentials() {
        String neonUrl = "jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require";
        String neonUser = "neondb_owner";
        String neonPass = "n30n_p4ssw0rd_s3cur3";

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(TestContextConfig.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.location=classpath:application.yml",
                        "SPRING_DATASOURCE_URL=" + neonUrl,
                        "SPRING_DATASOURCE_USERNAME=" + neonUser,
                        "SPRING_DATASOURCE_PASSWORD=" + neonPass
                )
                .run()) {

            String resolvedUrl = ctx.getEnvironment().getProperty("spring.datasource.url");
            String resolvedUser = ctx.getEnvironment().getProperty("spring.datasource.username");
            String resolvedPass = ctx.getEnvironment().getProperty("spring.datasource.password");

            assertThat(resolvedUrl).isEqualTo(neonUrl);
            assertThat(resolvedUrl).contains("?sslmode=require");
            assertThat(resolvedUser).isEqualTo(neonUser);
            assertThat(resolvedPass).isEqualTo(neonPass);

            // HikariCP settings remain active and properly typed
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.hikari.connection-timeout", Integer.class))
                    .isEqualTo(30000);
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.hikari.validation-timeout", Integer.class))
                    .isEqualTo(5000);
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                    .isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Debe configurar las ubicaciones de Flyway incluyendo db/migration y db/dev")
    void flywayLocations_shouldIncludeMigrationAndDev() throws IOException {
        List<PropertySource<?>> propertySources = loader.load(
                "application.yml",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources).isNotEmpty();
        PropertySource<?> source = propertySources.get(0);

        Object loc0 = source.getProperty("spring.flyway.locations[0]");
        Object loc1 = source.getProperty("spring.flyway.locations[1]");
        assertThat(loc0).isEqualTo("classpath:db/migration");
        assertThat(loc1).isEqualTo("classpath:db/dev");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
                .sources(TestContextConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=classpath:application.yml")
                .run()) {

            String locProp0 = ctx.getEnvironment().getProperty("spring.flyway.locations[0]");
            String locProp1 = ctx.getEnvironment().getProperty("spring.flyway.locations[1]");
            assertThat(locProp0).isEqualTo("classpath:db/migration");
            assertThat(locProp1).isEqualTo("classpath:db/dev");
        }
    }
}
