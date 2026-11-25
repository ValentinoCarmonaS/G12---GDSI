package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 028 T003: verifies the Flyway migration for composite performance indexes
 * (FR-009, D-10) located by description per rule 5 of .specify/memory/migrations.md.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spec 028 - Performance composite indexes migration (T003)")
class PerformanceCompositeIndexesMigrationTest {

    private static final String DESCRIPTION_SUFFIX = "__add_performance_composite_indexes.sql";
    private static final String MIGRATIONS = "classpath*:db/migration/V*.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("exactly one versioned migration carries the add_performance_composite_indexes description and unique version")
    void migrationExistsOnceWithUniqueVersion() throws IOException {
        List<Resource> all = Arrays.asList(new PathMatchingResourcePatternResolver().getResources(MIGRATIONS));
        List<Resource> matching = all.stream()
                .filter(r -> Objects.requireNonNull(r.getFilename()).endsWith(DESCRIPTION_SUFFIX))
                .toList();

        assertThat(matching)
                .as("migration *%s must exist exactly once on classpath", DESCRIPTION_SUFFIX)
                .hasSize(1);

        String version = versionOf(matching.get(0));
        long sameVersionCount = all.stream()
                .map(PerformanceCompositeIndexesMigrationTest::versionOf)
                .filter(version::equals)
                .count();

        assertThat(sameVersionCount)
                .as("Flyway version %s must belong to a single migration file", version)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("pg_indexes contains idx_dishes_public_catalog, idx_order_items_dish_order and idx_temp_reservations_active_expires_order")
    void pgIndexesShouldContainExpectedCompositeIndexes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'idx_dishes_public_catalog',
                      'idx_order_items_dish_order',
                      'idx_temp_reservations_active_expires_order'
                  )
                """
        );

        assertThat(rows).hasSize(3);

        Map<String, String> defsByName = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> String.valueOf(r.get("indexname")),
                        r -> String.valueOf(r.get("indexdef"))
                ));

        assertThat(defsByName.get("idx_dishes_public_catalog"))
                .contains("ON public.dishes")
                .contains("tenant_id")
                .contains("category")
                .contains("WHERE");

        assertThat(defsByName.get("idx_order_items_dish_order"))
                .contains("ON public.order_items")
                .contains("dish_id")
                .contains("order_id");

        assertThat(defsByName.get("idx_temp_reservations_active_expires_order"))
                .contains("ON public.temporary_reservations")
                .contains("expires_at")
                .contains("order_id")
                .contains("WHERE");
    }

    private static String versionOf(Resource resource) {
        String filename = Objects.requireNonNull(resource.getFilename());
        return filename.substring(0, filename.indexOf("__"));
    }
}
