package com.easyeat.operations;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/**
 * Shares one PostgreSQL 16 container (and therefore one Spring context) across the
 * persistence tests of the operations module, so each class does not pay a cold start.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class OperationsPostgresTestBase {

    /** Development establishment seeded by the establishments migration. */
    protected static final UUID CAFE_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_operations_test")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
