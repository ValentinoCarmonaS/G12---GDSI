package com.easyeat.order.repository;

import com.easyeat.order.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 023, T006: verifies V29 Flyway migration applied on real PostgreSQL (Testcontainers):
 * <ul>
 *   <li>{@code orders.paid_at} exists as nullable {@code timestamp with time zone}.</li>
 *   <li>Partial composite index {@code idx_orders_analytics} exists on {@code orders(establishment_id, paid_at)}.</li>
 * </ul>
 */
@DisplayName("OrderPaidAtSchemaIntegrationTest (T006, spec 023): V29 schema and partial index")
class OrderPaidAtSchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("orders.paid_at exists as a nullable timestamptz (V29)")
    void paidAtIsNullableTimestamptz() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT data_type, is_nullable
                FROM information_schema.columns
                WHERE table_name = 'orders' AND column_name = 'paid_at'
                """);

        assertThat(column.get("data_type")).isEqualTo("timestamp with time zone");
        assertThat(column.get("is_nullable")).isEqualTo("YES");
    }

    @Test
    @DisplayName("partial composite index idx_orders_analytics exists on orders(establishment_id, paid_at)")
    void partialIndexIdxOrdersAnalyticsExists() {
        List<String> indexDefs = jdbcTemplate.queryForList("""
                SELECT indexdef
                FROM pg_indexes
                WHERE tablename = 'orders' AND indexname = 'idx_orders_analytics'
                """, String.class);

        assertThat(indexDefs).hasSize(1);
        String def = indexDefs.get(0);
        assertThat(def).contains("establishment_id");
        assertThat(def).contains("paid_at");
        assertThat(def).contains("PAID");
        assertThat(def).contains("IN_PROGRESS");
        assertThat(def).contains("READY_FOR_PICKUP");
        assertThat(def).contains("COMPLETED");
    }
}
