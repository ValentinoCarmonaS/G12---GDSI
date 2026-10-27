package com.easyeat.dish.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("V7 Flyway Migration and Dev Seed Integration Test (T017)")
class V7MigrationAndSeedDevTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_v7_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V7 migration should evolve dishes and create dish_photos, dish_recipe_ingredients, and admin_notifications")
    void verifyV7SchemaEvolutionAndTables() {
        // 1. Verify dishes table evolved columns
        List<String> dishColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'dishes'",
                String.class
        );
        assertThat(dishColumns).contains(
                "category",
                "status",
                "allergens_confirmed",
                "created_by",
                "submitted_at",
                "price",
                "name"
        );

        // Verify price column is now nullable
        String priceIsNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'dishes' AND column_name = 'price'",
                String.class
        );
        assertThat(priceIsNullable).isEqualTo("YES");

        // 2. Verify dish_photos table
        List<String> photoColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'dish_photos'",
                String.class
        );
        assertThat(photoColumns).contains(
                "dish_id",
                "file_name",
                "content_type",
                "file_size",
                "data",
                "created_at",
                "updated_at"
        );

        // 3. Verify dish_recipe_ingredients table
        List<String> recipeColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'dish_recipe_ingredients'",
                String.class
        );
        assertThat(recipeColumns).contains(
                "dish_id",
                "ingredient_id",
                "quantity",
                "created_at",
                "updated_at"
        );

        // 4. Verify admin_notifications table
        List<String> notificationColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'admin_notifications'",
                String.class
        );
        assertThat(notificationColumns).contains(
                "id",
                "tenant_id",
                "type",
                "title",
                "message",
                "dish_id",
                "chef_id",
                "is_read",
                "created_at"
        );

        // 5. Verify indexes exist
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class
        );
        assertThat(indexes).contains(
                "uq_dishes_tenant_lower_trimmed_name",
                "idx_dishes_chef_list",
                "idx_dishes_review_inbox_pending",
                "idx_recipe_ingredients_lookup",
                "idx_admin_notifications_tenant_read"
        );
    }

    @Test
    @DisplayName("Dev seed should populate dishes with category, status PENDING_PRICE, allergens_confirmed, and created_by")
    void verifyDevSeedDishesData() {
        UUID tenantId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

        List<Map<String, Object>> seedDishes = jdbcTemplate.queryForList(
                "SELECT name, category, status, allergens_confirmed, created_by FROM dishes WHERE tenant_id = ? ORDER BY name",
                tenantId
        );

        assertThat(seedDishes).hasSizeGreaterThanOrEqualTo(5);

        for (Map<String, Object> dish : seedDishes) {
            assertThat(dish.get("status")).isEqualTo("PENDING_PRICE");
            assertThat(dish.get("allergens_confirmed")).isEqualTo(true);
            assertThat(dish.get("created_by")).isNotNull();
            assertThat((String) dish.get("category")).isIn("Ensaladas", "Platos principales");
        }
    }

    @Test
    @DisplayName("Uniqueness index should prevent duplicate trimmed lower-case names within tenant")
    void verifyNameUniquenessConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID dishId1 = UUID.randomUUID();
        UUID dishId2 = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO dishes (id, tenant_id, name, category, status, allergens_confirmed, is_active) " +
                        "VALUES (?, ?, ?, 'Entradas', 'DRAFT', false, false)",
                dishId1, tenantId, "Tacos de Pollo"
        );

        // Inserting same name with different casing and whitespace padding should fail
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO dishes (id, tenant_id, name, category, status, allergens_confirmed, is_active) " +
                        "VALUES (?, ?, ?, 'Entradas', 'DRAFT', false, false)",
                dishId2, tenantId, "  tacos DE pollo  "
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraint should reject invalid dish category")
    void verifyCategoryCheckConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO dishes (id, tenant_id, name, category, status, allergens_confirmed, is_active) " +
                        "VALUES (?, ?, ?, 'CategoriaInvalida', 'DRAFT', false, false)",
                dishId, tenantId, "Plato Test Categoria"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraint should reject recipe line with zero or negative quantity")
    void verifyRecipeQuantityCheckConstraint() {
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();
        UUID ingredientId = UUID.fromString("a1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");

        jdbcTemplate.update(
                "INSERT INTO dishes (id, tenant_id, name, category, status, allergens_confirmed, is_active) " +
                        "VALUES (?, ?, ?, 'Entradas', 'DRAFT', false, false)",
                dishId, tenantId, "Plato Test Receta"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO dish_recipe_ingredients (dish_id, ingredient_id, quantity) VALUES (?, ?, 0)",
                dishId, ingredientId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO dish_recipe_ingredients (dish_id, ingredient_id, quantity) VALUES (?, ?, -5.5)",
                dishId, ingredientId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraint chk_dishes_status should allow PUBLISHED status in dishes")
    void verifyStatusCheckConstraintAllowsPublished() {
        UUID tenantId = UUID.randomUUID();
        UUID dishId = UUID.randomUUID();

        int rows = jdbcTemplate.update(
                "INSERT INTO dishes (id, tenant_id, name, category, status, allergens_confirmed, is_active, submitted_at) " +
                        "VALUES (?, ?, ?, 'Entradas', 'PUBLISHED', true, true, NOW())",
                dishId, tenantId, "Plato Publicado Test"
        );

        assertThat(rows).isEqualTo(1);
    }
}
