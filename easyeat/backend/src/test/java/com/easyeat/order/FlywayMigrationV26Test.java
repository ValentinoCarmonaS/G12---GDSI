package com.easyeat.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flyway V26 Migration - Counter Dispatch, Coincident Timestamps & Security Code Test (T004)")
class FlywayMigrationV26Test {

    private static final String MIGRATION_PATH = "db/migration/V26__counter_dispatch_and_security_code.sql";

    @Test
    @DisplayName("V26 migration script exists on classpath and defines orders.ready_at and orders.security_code columns")
    void v26MigrationScript_definesReadyAtAndSecurityCodeColumns() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertThat(resource.exists())
                .as("Flyway migration %s must exist on classpath", MIGRATION_PATH)
                .isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("ALTER TABLE orders");
            assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS ready_at TIMESTAMPTZ NULL");
            assertThat(sql).containsIgnoringCase("ADD COLUMN IF NOT EXISTS security_code VARCHAR(4) NULL");
        }
    }

    @Test
    @DisplayName("V26 migration script enforces chk_orders_security_code constraint for 4-digit codes in 1000..9999")
    void v26MigrationScript_enforcesSecurityCodeCheckConstraint() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("DROP CONSTRAINT IF EXISTS chk_orders_security_code");
            assertThat(sql).containsIgnoringCase("ADD CONSTRAINT chk_orders_security_code");
            assertThat(sql).contains("CHECK (security_code IS NULL OR security_code ~ '^[1-9][0-9]{3}$')");
        }

        Pattern securityCodeRegex = Pattern.compile("^[1-9][0-9]{3}$");
        assertThat(securityCodeRegex.matcher("1000").matches()).isTrue();
        assertThat(securityCodeRegex.matcher("4821").matches()).isTrue();
        assertThat(securityCodeRegex.matcher("9999").matches()).isTrue();
        assertThat(securityCodeRegex.matcher("0123").matches()).isFalse();
        assertThat(securityCodeRegex.matcher("999").matches()).isFalse();
        assertThat(securityCodeRegex.matcher("10000").matches()).isFalse();
        assertThat(securityCodeRegex.matcher("12A4").matches()).isFalse();
    }

    @Test
    @DisplayName("V26 migration script includes safe backfill for security_code and ready_at synchronization")
    void v26MigrationScript_includesBackfillStatements() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("UPDATE orders");
            assertThat(sql).containsIgnoringCase("SET security_code = LPAD(FLOOR(1000 + RANDOM() * 9000)::TEXT, 4, '0')");
            assertThat(sql).containsIgnoringCase("WHERE security_code IS NULL");

            assertThat(sql).containsIgnoringCase("UPDATE orders o");
            assertThat(sql).containsIgnoringCase("SET ready_at = k.ready_at");
            assertThat(sql).containsIgnoringCase("FROM kds_tickets k");
            assertThat(sql).containsIgnoringCase("WHERE k.order_id = o.id");
            assertThat(sql).containsIgnoringCase("AND o.status = 'READY_FOR_PICKUP'");
            assertThat(sql).containsIgnoringCase("AND o.ready_at IS NULL");
            assertThat(sql).containsIgnoringCase("AND k.ready_at IS NOT NULL");
        }
    }

    @Test
    @DisplayName("V26 migration script creates composite index idx_kds_tickets_counter_ready on kds_tickets")
    void v26MigrationScript_createsCounterReadyCompositeIndex() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertThat(resource.exists()).isTrue();

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).containsIgnoringCase("CREATE INDEX IF NOT EXISTS idx_kds_tickets_counter_ready");
            assertThat(sql).containsIgnoringCase("ON kds_tickets (establishment_id, status, pickup_date, ready_at ASC, ticket_number ASC)");
        }
    }
}
