package com.easyeat.order;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("T017: Flyway Full Migration & Idempotency Integration Test")
class FlywayFullMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_flyway_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        // Include both versioned migrations and dev repeatable seed scripts
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Debe ejecutar secuencialmente todas las migraciones V1 a V19 con estado SUCCESS")
    void flyway_shouldExecuteAllVersionedMigrationsSuccessfully() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        assertThat(appliedMigrations)
                .as("Se deben haber aplicado migraciones en la base de datos")
                .isNotEmpty();

        List<String> appliedVersions = Arrays.stream(appliedMigrations)
                .filter(m -> m.getVersion() != null)
                .map(m -> m.getVersion().getVersion())
                .toList();

        // Validar que todas las versiones desde 1 hasta 19 estén aplicadas
        for (int i = 1; i <= 19; i++) {
            String expectedVersion = String.valueOf(i);
            assertThat(appliedVersions)
                    .as("La migración versionada V" + expectedVersion + " debe estar aplicada")
                    .contains(expectedVersion);
        }

        // Validar que cada migración versionada haya finalizado en SUCCESS
        for (MigrationInfo info : appliedMigrations) {
            assertThat(info.getState())
                    .as("La migración " + info.getScript() + " debe tener estado SUCCESS")
                    .isEqualTo(MigrationState.SUCCESS);
        }
    }

    @Test
    @DisplayName("Debe ejecutar los scripts repetibles de semillas R__seed_dev_* exitosamente")
    void flyway_shouldApplyRepeatableDevSeeds() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        List<String> repeatableScripts = Arrays.stream(appliedMigrations)
                .filter(m -> m.getVersion() == null)
                .map(MigrationInfo::getScript)
                .toList();

        assertThat(repeatableScripts)
                .as("Deben ejecutarse los scripts repetibles de desarrollo")
                .contains(
                        "R__seed_dev_users.sql",
                        "R__seed_dev_establishments.sql",
                        "R__seed_dev_dishes_and_dietary.sql"
                );

        // Validar que existan registros sembrados
        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(userCount).isNotNull().isGreaterThan(0);

        Integer establishmentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM establishments", Integer.class);
        assertThat(establishmentCount).isNotNull().isGreaterThan(0);

        Integer dishCount = jdbcTemplate.queryForObject("SELECT count(*) FROM dishes", Integer.class);
        assertThat(dishCount).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("Debe garantizar la idempotencia de los scripts repetibles ante ejecuciones sucesivas (flyway.migrate())")
    void flyway_shouldBeIdempotentOnSubsequentMigrations() {
        // Ejecución repetida de flyway.migrate() (simulando reinicios sucesivos del contenedor en Render)
        int appliedSecondRun = flyway.migrate().migrationsExecuted;

        // No debería re-ejecutar migraciones a menos que cambie el checksum
        assertThat(appliedSecondRun).isZero();

        // Verificar que los datos sigan íntegros y no haya duplicaciones inválidas
        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(userCount).isNotNull().isGreaterThan(0);

        // Verificar que no se hayan lanzado violaciones de clave única
        Integer countOfUniqueEmails = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT email) FROM users", Integer.class);
        assertThat(countOfUniqueEmails).isEqualTo(userCount);
    }
}
