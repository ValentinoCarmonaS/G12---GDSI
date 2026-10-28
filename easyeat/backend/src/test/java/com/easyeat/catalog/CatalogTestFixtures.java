package com.easyeat.catalog;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base test fixtures, multi-tenant identifiers, and fluent builders for
 * diner catalog test scenarios, establishments, dishes, recipes, and inventory stock.
 */
public final class CatalogTestFixtures {

    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    public static final UUID ANOTHER_TENANT_ID = UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");
    public static final UUID PAUSED_TENANT_ID = UUID.fromString("c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");
    public static final UUID DISABLED_TENANT_ID = UUID.fromString("d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44");

    public static final UUID DEFAULT_DISH_ID = UUID.fromString("e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55");
    public static final UUID DEFAULT_INGREDIENT_ID = UUID.fromString("f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66");

    public static final String DEFAULT_ESTABLISHMENT_NAME = "La Farola de Palermo";
    public static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(11, 0);
    public static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(23, 0);
    public static final String DEFAULT_TIMEZONE = "America/Argentina/Buenos_Aires";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String DEFAULT_DISH_NAME = "Milanesa Napolitana con Papas Fritas";
    public static final String DEFAULT_DISH_DESCRIPTION = "Milanesa de ternera con salsa de tomate casera, jamón cocido, queso mozzarella y orégano.";
    public static final BigDecimal DEFAULT_DISH_PRICE = BigDecimal.valueOf(5500.00);

    private CatalogTestFixtures() {
        // Utility class
    }

    public static Dish createStandardPublishedDish() {
        Dish dish = new Dish();
        dish.setId(DEFAULT_DISH_ID);
        dish.setTenantId(DEFAULT_TENANT_ID);
        dish.setName(DEFAULT_DISH_NAME);
        dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish.setDescription(DEFAULT_DISH_DESCRIPTION);
        dish.setPrice(DEFAULT_DISH_PRICE);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setIsActive(true);
        dish.setAllergensConfirmed(true);
        dish.setCreatedAt(Instant.now());
        dish.setUpdatedAt(Instant.now());
        return dish;
    }

    public static CatalogDishBuilder catalogDishBuilder() {
        return new CatalogDishBuilder();
    }

    public static DishRecipeIngredient createRecipeIngredientLine(UUID dishId, UUID ingredientId, BigDecimal quantity) {
        DishRecipeIngredientId id = new DishRecipeIngredientId(dishId, ingredientId);
        return new DishRecipeIngredient(id, quantity);
    }

    public static Ingredient createInventoryIngredient(UUID tenantId, String name, BigDecimal currentStock, UnitOfMeasure unitOfMeasure) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(UUID.randomUUID());
        ingredient.setTenantId(tenantId);
        ingredient.setName(name);
        ingredient.setCategory(IngredientCategory.DAIRY);
        ingredient.setUnitOfMeasure(unitOfMeasure);
        ingredient.setUnitPurchaseCost(new BigDecimal("100.0000"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredient.setCurrentStock(currentStock != null ? currentStock : BigDecimal.ZERO.setScale(4));
        return ingredient;
    }

    public static final class CatalogDishBuilder {
        private UUID id = UUID.randomUUID();
        private UUID tenantId = DEFAULT_TENANT_ID;
        private String name = DEFAULT_DISH_NAME;
        private DishCategory category = DishCategory.PLATOS_PRINCIPALES;
        private String description = DEFAULT_DISH_DESCRIPTION;
        private BigDecimal price = DEFAULT_DISH_PRICE;
        private DishStatus status = DishStatus.PUBLISHED;
        private Boolean isActive = true;
        private Boolean allergensConfirmed = true;
        private Set<String> restrictions = new HashSet<>();

        public CatalogDishBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public CatalogDishBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public CatalogDishBuilder name(String name) {
            this.name = name;
            return this;
        }

        public CatalogDishBuilder category(DishCategory category) {
            this.category = category;
            return this;
        }

        public CatalogDishBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CatalogDishBuilder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public CatalogDishBuilder status(DishStatus status) {
            this.status = status;
            return this;
        }

        public CatalogDishBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public CatalogDishBuilder allergensConfirmed(Boolean allergensConfirmed) {
            this.allergensConfirmed = allergensConfirmed;
            return this;
        }

        public CatalogDishBuilder restrictions(Set<String> restrictions) {
            this.restrictions = restrictions != null ? new HashSet<>(restrictions) : new HashSet<>();
            return this;
        }

        public Dish build() {
            Dish dish = new Dish();
            dish.setId(this.id);
            dish.setTenantId(this.tenantId);
            dish.setName(this.name);
            dish.setCategory(this.category);
            dish.setDescription(this.description);
            dish.setPrice(this.price);
            dish.setStatus(this.status);
            dish.setIsActive(this.isActive);
            dish.setAllergensConfirmed(this.allergensConfirmed);
            dish.setRestrictionCodes(this.restrictions);
            dish.setCreatedAt(Instant.now());
            dish.setUpdatedAt(Instant.now());
            return dish;
        }
    }
}
