package com.easyeat.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 022 T001 (RF-011, Invariante 8): the delivery-completion timestamp on {@code orders}.
 *
 * <p>The migration is located by its Flyway description, never by its version number, so an
 * upstream renumbering cannot invalidate this test (rule 5 of {@code .specify/memory/migrations.md}).
 * Its application against PostgreSQL is verified separately by T006/T007.
 */
@DisplayName("Spec 022 - orders.completed_at migration script (T001)")
class OrderCompletedAtMigrationTest {

    private static final String DESCRIPTION_SUFFIX = "__add_completed_at_to_orders.sql";
    private static final String MIGRATIONS = "classpath*:db/migration/V*.sql";

    @Test
    @DisplayName("exactly one versioned migration carries the add_completed_at_to_orders description")
    void migrationExistsOnceByDescription() throws IOException {
        assertThat(completedAtMigrations())
                .as("migration *%s must exist exactly once on the classpath", DESCRIPTION_SUFFIX)
                .hasSize(1);
    }

    @Test
    @DisplayName("adds a nullable TIMESTAMPTZ completed_at column to orders, idempotently")
    void addsNullableCompletedAtToOrders() throws IOException {
        String sql = normalizedSql(singleMigration());

        assertThat(sql).contains("ALTER TABLE ORDERS");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS COMPLETED_AT TIMESTAMPTZ NULL");
        assertThat(sql).doesNotContain("NOT NULL");
    }

    @Test
    @DisplayName("does not touch security_code (owned by spec 020 V26) nor add an unjustified index (A-02, A-03)")
    void limitsItselfToCompletedAt() throws IOException {
        String sql = normalizedSql(singleMigration());

        assertThat(sql).doesNotContain("SECURITY_CODE");
        assertThat(sql).doesNotContain("CREATE INDEX");
        assertThat(sql).doesNotContain("KDS_TICKETS");
    }

    @Test
    @DisplayName("its version number is not shared with any other migration")
    void versionIsUnique() throws IOException {
        String version = versionOf(singleMigration());

        long sameVersion = allMigrations().stream()
                .map(OrderCompletedAtMigrationTest::versionOf)
                .filter(version::equals)
                .count();

        assertThat(sameVersion)
                .as("Flyway version %s must belong to a single migration file", version)
                .isEqualTo(1);
    }

    private static List<Resource> allMigrations() throws IOException {
        return Arrays.asList(new PathMatchingResourcePatternResolver().getResources(MIGRATIONS));
    }

    private static List<Resource> completedAtMigrations() throws IOException {
        return allMigrations().stream()
                .filter(r -> Objects.requireNonNull(r.getFilename()).endsWith(DESCRIPTION_SUFFIX))
                .toList();
    }

    private static Resource singleMigration() throws IOException {
        List<Resource> found = completedAtMigrations();
        assertThat(found).as("migration *%s", DESCRIPTION_SUFFIX).hasSize(1);
        return found.get(0);
    }

    private static String versionOf(Resource resource) {
        String filename = Objects.requireNonNull(resource.getFilename());
        return filename.substring(0, filename.indexOf("__"));
    }

    /** Strips SQL line comments, collapses whitespace and upper-cases, so assertions ignore formatting. */
    private static String normalizedSql(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return raw.replaceAll("--[^\\n]*", " ")
                    .replaceAll("\\s+", " ")
                    .toUpperCase();
        }
    }
}
