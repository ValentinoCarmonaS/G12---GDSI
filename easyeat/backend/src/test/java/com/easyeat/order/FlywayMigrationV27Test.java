package com.easyeat.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V27 Migration - Specific Delay Minutes & Security Code Test (T005)")
class FlywayMigrationV27Test {

    private static final String MIGRATION_PATH = "db/migration/V27__kds_specific_delay_minutes.sql";

    @Test
    @DisplayName("V27 migration script exists on classpath and defines specific_delay_minutes and security_code columns")
    void v27MigrationScript_definesRequiredColumns() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertThat(resource.exists())
                .as("Flyway migration %s must exist on classpath", MIGRATION_PATH)
                .isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("ALTER TABLE kds_tickets");
            assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS specific_delay_minutes INTEGER NOT NULL DEFAULT 0");
            assertThat(sql).containsIgnoringCase("ADD CONSTRAINT chk_kds_specific_delay_minutes");
            assertThat(sql).containsIgnoringCase("CHECK (specific_delay_minutes >= 0)");
            assertThat(sql).containsIgnoringCase("ALTER TABLE orders");
            assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS security_code VARCHAR(4) NULL");
        }
    }
}
