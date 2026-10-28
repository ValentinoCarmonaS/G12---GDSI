package com.easyeat.dish.acceptance;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeResponse;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.InvalidIngredientException;
import com.easyeat.dish.exception.InvalidQuantityException;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.service.DishService;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("User Story 2 Acceptance Test: Recipe Definition & BOM (T037, CHF-2.2.2)")
class DishRecipeAcceptanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_dish_us2_acceptance")
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
    private DishService dishService;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;

    private Ingredient rice;
    private Ingredient eggs;
    private Ingredient discontinuedTruffle;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, role, status, tenant_id, token_version, created_at, updated_at)
                VALUES (?, 'chef.acceptance@easyeat.com', '$2a$12$YN9mdfv/oelL9EQiNRqem.J8cetkck1qL0kA4PRwT5ZdLVKN9e4W2', 'CHEF', 'ACTIVE', ?, 0, NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, chefId, tenantId);

        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();

        rice = new Ingredient(tenantId, "Arroz Carnaroli BOM", IngredientCategory.DRY_GOODS, UnitOfMeasure.G, BigDecimal.valueOf(12.5));
        rice.setStatus(IngredientStatus.ACTIVE);
        rice = ingredientRepository.save(rice);

        eggs = new Ingredient(tenantId, "Huevos de Campo BOM", IngredientCategory.DAIRY, UnitOfMeasure.U, BigDecimal.valueOf(60.0));
        eggs.setStatus(IngredientStatus.ACTIVE);
        eggs = ingredientRepository.save(eggs);

        discontinuedTruffle = new Ingredient(tenantId, "Trufa Descontinuada BOM", IngredientCategory.SPICES, UnitOfMeasure.G, BigDecimal.valueOf(1000.0));
        discontinuedTruffle.setStatus(IngredientStatus.DISCONTINUED);
        discontinuedTruffle = ingredientRepository.save(discontinuedTruffle);
    }

    @AfterEach
    void tearDown() {
        dishRecipeIngredientRepository.deleteAll();
        dishRepository.deleteAll();
        ingredientRepository.deleteAll();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Complete User Story 2 Acceptance Journey: Compose recipe, view BOM in dish detail, and delete recipe line")
    void userStory2_completeAcceptanceJourney() {
        // 1. Given a Chef creates a new dish draft
        DishDetailResponse createdDish = dishService.createDraft(
                new CreateDishRequest("Risotto della Nonna", DishCategory.PLATOS_PRINCIPALES, "Receta tradicional italiana"),
                chefId
        );
        UUID dishId = createdDish.id();
        assertThat(createdDish.status()).isEqualTo(DishStatus.DRAFT);
        assertThat(createdDish.recipe()).isEmpty();

        // 2. When the Chef defines the recipe with continuous and discrete ingredients
        RecipeUpdateRequest updateRequest = new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(rice.getId(), 250.5),
                new RecipeIngredientItem(eggs.getId(), 3.0)
        ));

        RecipeResponse recipeResponse = dishService.updateRecipe(dishId, updateRequest);

        // Then the recipe is saved with enriched 3NF details
        assertThat(recipeResponse).isNotNull();
        assertThat(recipeResponse.dishId()).isEqualTo(dishId);
        assertThat(recipeResponse.ingredients()).hasSize(2);

        RecipeIngredientItem item1 = recipeResponse.ingredients().stream()
                .filter(i -> i.ingredientId().equals(rice.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(item1.ingredientName()).isEqualTo("Arroz Carnaroli BOM");
        assertThat(item1.quantity()).isEqualTo(250.5);
        assertThat(item1.unitOfMeasure()).isEqualTo("g");
        assertThat(item1.isIngredientActive()).isTrue();

        RecipeIngredientItem item2 = recipeResponse.ingredients().stream()
                .filter(i -> i.ingredientId().equals(eggs.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(item2.ingredientName()).isEqualTo("Huevos de Campo BOM");
        assertThat(item2.quantity()).isEqualTo(3.0);
        assertThat(item2.unitOfMeasure()).isEqualTo("u");
        assertThat(item2.isIngredientActive()).isTrue();

        // 3. When querying the dish details, the recipe is populated
        DishDetailResponse detailedDish = dishService.getDishById(dishId);
        assertThat(detailedDish.recipe()).hasSize(2);

        // 4. When removing a single ingredient line
        dishService.removeRecipeIngredient(dishId, eggs.getId());

        // Then the line is unlinked from the recipe
        DishDetailResponse dishAfterRemoval = dishService.getDishById(dishId);
        assertThat(dishAfterRemoval.recipe()).hasSize(1);
        assertThat(dishAfterRemoval.recipe().get(0).ingredientId()).isEqualTo(rice.getId());
    }

    @Test
    @DisplayName("Should enforce discrete unit precision, positive quantities, and active ingredient invariants in acceptance flow")
    void userStory2_invariantsAcceptance() {
        DishDetailResponse createdDish = dishService.createDraft(
                new CreateDishRequest("Pasta al Pesto Genovese", DishCategory.PLATOS_PRINCIPALES, "Pesto clásico"),
                chefId
        );
        UUID dishId = createdDish.id();

        // Discrete unit with decimal quantity rejected
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(eggs.getId(), 1.5)
        )))).isInstanceOf(InvalidQuantityException.class);

        // Zero quantity rejected
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(rice.getId(), 0.0)
        )))).isInstanceOf(InvalidQuantityException.class);

        // Negative quantity rejected
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(rice.getId(), -50.0)
        )))).isInstanceOf(InvalidQuantityException.class);

        // Discontinued ingredient rejected
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(discontinuedTruffle.getId(), 10.0)
        )))).isInstanceOf(InvalidIngredientException.class);

        // Duplicate ingredients rejected
        assertThatThrownBy(() -> dishService.updateRecipe(dishId, new RecipeUpdateRequest(List.of(
                new RecipeIngredientItem(rice.getId(), 100.0),
                new RecipeIngredientItem(rice.getId(), 50.0)
        )))).isInstanceOf(IllegalArgumentException.class);
    }
}
