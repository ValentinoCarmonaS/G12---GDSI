package com.easyeat.dish.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migration this feature owns, exercised against PostgreSQL 16.
 *
 * <p>The class name carries no version number on purpose (registry rule 5): an upstream feature
 * that merges first takes the number and this feature renumbers, which must not invalidate the
 * test. For the same reason nothing below hardcodes a version — the migration is located by its
 * <em>description</em>, and the version immediately preceding it is read from Flyway's own
 * metadata.
 *
 * <p>The backfill can only be observed if rows exist <em>before</em> the migration runs, so the
 * schema is built in two steps: migrate up to the version preceding this feature's, insert dishes
 * with the three price shapes that matter, then apply the rest.
 */
@Testcontainers
@DisplayName("Administrative pricing migration")
class AdminPricingMigrationTest {

    private static final String MIGRATION_DESCRIPTION = "admin dish approval pricing";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_admin_pricing_test")
            .withUsername("test")
            .withPassword("test");

    private static final UUID TENANT = UUID.randomUUID();
    private static UUID dishWithPositivePrice;
    private static UUID dishWithZeroPrice;
    private static UUID dishWithNullPrice;
    private static UUID adminId;

    @BeforeAll
    static void buildSchemaInTwoSteps() throws SQLException {
        MigrationVersion previous = versionPrecedingOurs();

        migrateTo(previous);
        seedDishesWithEveryPriceShape();
        migrateTo(MigrationVersion.LATEST);
    }

    // ---------------------------------------------------------------- helpers

    private static Flyway flywayTo(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    /**
     * The version just below this feature's, discovered rather than assumed, so a renumbering
     * upstream cannot break this test.
     */
    private static MigrationVersion versionPrecedingOurs() {
        MigrationInfo[] all = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .info()
                .all();

        MigrationVersion previous = null;
        for (MigrationInfo info : all) {
            if (MIGRATION_DESCRIPTION.equalsIgnoreCase(info.getDescription())) {
                assertThat(previous)
                        .describedAs("this feature's migration cannot be the first in the project")
                        .isNotNull();
                return previous;
            }
            previous = info.getVersion();
        }
        throw new AssertionError(
                "No migration described as '" + MIGRATION_DESCRIPTION + "' was found in "
                        + "db/migration. Found: " + descriptionsOf(all));
    }

    private static List<String> descriptionsOf(MigrationInfo[] all) {
        List<String> out = new ArrayList<>();
        for (MigrationInfo info : all) {
            out.add(info.getVersion() + " -> " + info.getDescription());
        }
        return out;
    }

    private static void migrateTo(MigrationVersion target) {
        flywayTo(target).migrate();
    }

    private static void seedDishesWithEveryPriceShape() throws SQLException {
        dishWithPositivePrice = UUID.randomUUID();
        dishWithZeroPrice = UUID.randomUUID();
        dishWithNullPrice = UUID.randomUUID();
        adminId = UUID.randomUUID();

        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                    INSERT INTO users (id, email, password_hash, full_name, role, tenant_id)
                    VALUES ('%s', 'migration.admin@easyeat.test', 'x', 'Migration Admin',
                            'ADMINISTRADOR', '%s')
                    """.formatted(adminId, TENANT));

            insertDish(s, dishWithPositivePrice, "Dish priced", "4500.00");
            insertDish(s, dishWithZeroPrice, "Dish at zero", "0.00");
            insertDish(s, dishWithNullPrice, "Dish unpriced", null);
        }
    }

    private static void insertDish(Statement s, UUID id, String name, String price)
            throws SQLException {
        s.executeUpdate("""
                INSERT INTO dishes (id, tenant_id, name, price, is_active, status)
                VALUES ('%s', '%s', '%s', %s, TRUE, 'DRAFT')
                """.formatted(id, TENANT, name, price == null ? "NULL" : price));
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<String> column(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static void execute(String sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("applies on top of every migration already present")
    class Applies {

        @Test
        @DisplayName("every migration in db/migration is recorded as successful, in version order")
        void allMigrationsSucceeded() throws SQLException {
            List<String> failed = column(
                    "SELECT version FROM flyway_schema_history WHERE success = FALSE");
            assertThat(failed).isEmpty();

            List<String> versions = column(
                    "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL "
                            + "ORDER BY installed_rank");
            assertThat(versions).isNotEmpty();
            assertThat(scalar(
                    "SELECT description FROM flyway_schema_history "
                            + "WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"))
                    .describedAs("this feature's migration must be the last versioned one applied")
                    .isEqualTo(MIGRATION_DESCRIPTION);
        }

        @Test
        @DisplayName("adds exactly the two columns this feature owns, and no version column")
        void addsOwnedColumnsOnly() throws SQLException {
            List<String> columns = column(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_name = 'dishes'");

            assertThat(columns).contains("retail_price", "published_at");
            assertThat(columns)
                    .describedAs("R-005-4 is withdrawn: FR-025 uses conditional UPDATEs, so no "
                            + "row version exists on a table three specs write through")
                    .doesNotContain("version");
        }

        @Test
        @DisplayName("retail_price is NUMERIC(10,2) and nullable, so 'not yet priced' is sayable")
        void retailPriceShape() throws SQLException {
            assertThat(scalar("SELECT data_type FROM information_schema.columns "
                    + "WHERE table_name = 'dishes' AND column_name = 'retail_price'"))
                    .isEqualTo("numeric");
            assertThat(scalar("SELECT numeric_precision || ',' || numeric_scale "
                    + "FROM information_schema.columns "
                    + "WHERE table_name = 'dishes' AND column_name = 'retail_price'"))
                    .isEqualTo("10,2");
            assertThat(scalar("SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'dishes' AND column_name = 'retail_price'"))
                    .isEqualTo("YES");
        }
    }

    @Nested
    @DisplayName("the backfill copies strictly positive prices and nothing else")
    class Backfill {

        @Test
        @DisplayName("a strictly positive legacy price becomes the retail price")
        void positivePriceIsCopied() throws SQLException {
            assertThat(scalar("SELECT retail_price FROM dishes WHERE id = '"
                    + dishWithPositivePrice + "'"))
                    .isEqualTo("4500.00");
        }

        @Test
        @DisplayName("a legacy price of 0.00 leaves the dish unpriced, not priced at zero")
        void zeroPriceStaysUnpriced() throws SQLException {
            assertThat(scalar("SELECT retail_price FROM dishes WHERE id = '"
                    + dishWithZeroPrice + "'"))
                    .describedAs("0.00 cannot be a retail price: FR-013 requires PVP > 0, and "
                            + "copying it would turn 'free' into 'priced at nothing'")
                    .isNull();
        }

        @Test
        @DisplayName("a NULL legacy price leaves the dish unpriced")
        void nullPriceStaysUnpriced() throws SQLException {
            assertThat(scalar("SELECT retail_price FROM dishes WHERE id = '"
                    + dishWithNullPrice + "'"))
                    .isNull();
        }

        @Test
        @DisplayName("the legacy price column is preserved, only deprecated")
        void legacyPriceIsNotDropped() throws SQLException {
            assertThat(scalar("SELECT price FROM dishes WHERE id = '"
                    + dishWithPositivePrice + "'"))
                    .describedAs("dropping price would break spec 003's tests and the dev seed")
                    .isEqualTo("4500.00");
            assertThat(scalar("SELECT col_description('dishes'::regclass, "
                    + "(SELECT ordinal_position FROM information_schema.columns "
                    + " WHERE table_name = 'dishes' AND column_name = 'price')::int)"))
                    .containsIgnoringCase("deprecated");
        }
    }

    @Nested
    @DisplayName("constraints this feature owns")
    class Constraints {

        @Test
        @DisplayName("a retail price of zero or below is rejected at the database")
        void retailPriceMustBePositive() {
            assertThatThrownBy(() -> execute("UPDATE dishes SET retail_price = 0 WHERE id = '"
                    + dishWithPositivePrice + "'"))
                    .hasMessageContaining("chk_dishes_retail_price_positive");
            assertThatThrownBy(() -> execute("UPDATE dishes SET retail_price = -1 WHERE id = '"
                    + dishWithPositivePrice + "'"))
                    .hasMessageContaining("chk_dishes_retail_price_positive");
        }

        @Test
        @DisplayName("spec 005's status constraint is left exactly as it was")
        void statusConstraintIsUntouched() throws SQLException {
            String definition = scalar(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                            + "WHERE conname = 'chk_dishes_status'");
            assertThat(definition)
                    .describedAs("chk_dishes_status belongs to spec 005's V7; this migration must "
                            + "neither drop nor replace it (D1)")
                    .isNotNull()
                    .contains("DRAFT", "PENDING_PRICE", "PUBLISHED");

            List<String> statusConstraints = column(
                    "SELECT conname FROM pg_constraint WHERE conrelid = 'dishes'::regclass "
                            + "AND conname LIKE '%status%'");
            assertThat(statusConstraints)
                    .describedAs("exactly one owner of the status domain")
                    .containsExactly("chk_dishes_status");
        }
    }

    @Nested
    @DisplayName("the two immutable audit tables")
    class AuditTables {

        @Test
        @DisplayName("both tables exist with the columns data-model.md §1.3 and §1.4 declare")
        void tablesAndColumns() throws SQLException {
            assertThat(column("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name = 'dish_review_history'"))
                    .contains("id", "dish_id", "tenant_id", "cycle_number", "event_type",
                            "admin_id", "comment", "created_at");

            assertThat(column("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name = 'dish_price_history'"))
                    .contains("id", "dish_id", "tenant_id", "admin_id", "previous_price",
                            "new_price", "food_cost_snapshot", "created_at");
        }

        @Test
        @DisplayName("neither audit table has an updated_at: a row that cannot change has no need")
        void noUpdatedAtAnywhere() throws SQLException {
            assertThat(column("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_name IN ('dish_review_history', 'dish_price_history')"))
                    .doesNotContain("updated_at");
        }

        @Test
        @DisplayName("cycle_number is unique per dish and starts at 1")
        void cycleNumberInvariants() throws SQLException {
            execute(insertReview(1, "APPROVED", null));

            assertThatThrownBy(() -> execute(insertReview(1, "REJECTED",
                    "'Ten characters at least, this one is long enough.'")))
                    .describedAs("UNIQUE (dish_id, cycle_number) proves the monotonic invariant")
                    .hasMessageContaining("uq_drh_dish_cycle");

            assertThatThrownBy(() -> execute(insertReview(0, "APPROVED", null)))
                    .hasMessageContaining("chk_drh_cycle_positive");
        }

        @Test
        @DisplayName("event_type admits only APPROVED and REJECTED")
        void eventTypeIsClosed() {
            assertThatThrownBy(() -> execute(insertReview(99, "PUBLISHED", null)))
                    .hasMessageContaining("chk_drh_event_type");
        }

        @Test
        @DisplayName("a rejection without a usable comment is refused by the database too")
        void rejectionCommentIsEnforcedInTheSchema() {
            assertThatThrownBy(() -> execute(insertReview(50, "REJECTED", null)))
                    .hasMessageContaining("chk_drh_rejection_comment");
            assertThatThrownBy(() -> execute(insertReview(51, "REJECTED", "'short'")))
                    .hasMessageContaining("chk_drh_rejection_comment");
            assertThatThrownBy(() -> execute(insertReview(52, "REJECTED",
                    "'          '")))
                    .describedAs("FR-021 measures length after trimming, so whitespace cannot "
                            + "satisfy the minimum")
                    .hasMessageContaining("chk_drh_rejection_comment");
        }

        @Test
        @DisplayName("a price history row needs a positive new price and a non-negative food cost")
        void priceHistoryInvariants() throws SQLException {
            execute(insertPrice("NULL", "1000.00", "0.00"));

            assertThatThrownBy(() -> execute(insertPrice("NULL", "0.00", "10.00")))
                    .hasMessageContaining("chk_dph_new_price_positive");
            assertThatThrownBy(() -> execute(insertPrice("NULL", "10.00", "-0.01")))
                    .hasMessageContaining("chk_dph_food_cost_non_negative");
        }

        @Test
        @DisplayName("deleting a dish cascades its audit rows but never its administrator")
        void cascadeAndRestrict() throws SQLException {
            assertThat(scalar(
                    "SELECT confdeltype FROM pg_constraint WHERE conrelid = "
                            + "'dish_review_history'::regclass AND confrelid = 'dishes'::regclass"))
                    .isEqualTo("c");
            assertThat(scalar(
                    "SELECT confdeltype FROM pg_constraint WHERE conrelid = "
                            + "'dish_review_history'::regclass AND confrelid = 'users'::regclass"))
                    .describedAs("an audit row never loses its actor")
                    .isEqualTo("r");
        }

        private String insertReview(int cycle, String eventType, String comment) {
            return """
                    INSERT INTO dish_review_history
                        (dish_id, tenant_id, cycle_number, event_type, admin_id, comment)
                    VALUES ('%s', '%s', %d, '%s', '%s', %s)
                    """.formatted(dishWithPositivePrice, TENANT, cycle, eventType, adminId,
                    comment == null ? "NULL" : comment);
        }

        private String insertPrice(String previous, String newPrice, String foodCost) {
            return """
                    INSERT INTO dish_price_history
                        (dish_id, tenant_id, admin_id, previous_price, new_price,
                         food_cost_snapshot)
                    VALUES ('%s', '%s', '%s', %s, %s, %s)
                    """.formatted(dishWithPositivePrice, TENANT, adminId, previous, newPrice,
                    foodCost);
        }
    }

    @Nested
    @DisplayName("indexes: the ones this feature owns, and the one it must not re-declare")
    class Indexes {

        @Test
        @DisplayName("the three audit indexes and the published-tab index exist")
        void ownedIndexesExist() throws SQLException {
            List<String> indexes = column("SELECT indexname FROM pg_indexes "
                    + "WHERE tablename IN ('dishes', 'dish_review_history', 'dish_price_history')");

            assertThat(indexes).contains(
                    "idx_drh_dish_created",
                    "idx_drh_tenant_created",
                    "idx_dph_dish_created",
                    "idx_dishes_review_inbox_published");
        }

        @Test
        @DisplayName("the FIFO pending index is consumed from spec 005, declared exactly once")
        void fifoIndexHasOneOwner() throws SQLException {
            assertThat(column("SELECT indexname FROM pg_indexes "
                    + "WHERE indexname = 'idx_dishes_review_inbox_pending'"))
                    .describedAs("two owners of one index object would violate D1")
                    .hasSize(1);

            assertThat(scalar("SELECT indexdef FROM pg_indexes "
                    + "WHERE indexname = 'idx_dishes_review_inbox_pending'"))
                    .contains("tenant_id", "status", "submitted_at");
        }

        @Test
        @DisplayName("the published-tab index orders by published_at descending")
        void publishedIndexOrdering() throws SQLException {
            assertThat(scalar("SELECT indexdef FROM pg_indexes "
                    + "WHERE indexname = 'idx_dishes_review_inbox_published'"))
                    .contains("published_at DESC");
        }
    }
}
