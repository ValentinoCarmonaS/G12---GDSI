package com.easyeat.catalog;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTestFixturesTest {

    @Test
    @DisplayName("Should provide consistent multi-tenant UUID identifiers and status constants")
    void defaultIdentifiersAndConstants() {
        assertThat(CatalogTestFixtures.DEFAULT_TENANT_ID).isNotNull();
        assertThat(CatalogTestFixtures.ANOTHER_TENANT_ID).isNotNull();
        assertThat(CatalogTestFixtures.PAUSED_TENANT_ID).isNotNull();
        assertThat(CatalogTestFixtures.DISABLED_TENANT_ID).isNotNull();
        assertThat(CatalogTestFixtures.DEFAULT_DISH_ID).isNotNull();
        assertThat(CatalogTestFixtures.DEFAULT_INGREDIENT_ID).isNotNull();

        assertThat(CatalogTestFixtures.DEFAULT_TENANT_ID)
                .isNotEqualTo(CatalogTestFixtures.ANOTHER_TENANT_ID)
                .isNotEqualTo(CatalogTestFixtures.PAUSED_TENANT_ID)
                .isNotEqualTo(CatalogTestFixtures.DISABLED_TENANT_ID);

        assertThat(CatalogTestFixtures.DEFAULT_ESTABLISHMENT_NAME).isEqualTo("La Farola de Palermo");
        assertThat(CatalogTestFixtures.DEFAULT_OPENING_TIME).isEqualTo(LocalTime.of(11, 0));
        assertThat(CatalogTestFixtures.DEFAULT_CLOSING_TIME).isEqualTo(LocalTime.of(23, 0));
        assertThat(CatalogTestFixtures.DEFAULT_TIMEZONE).isEqualTo("America/Argentina/Buenos_Aires");

        assertThat(CatalogTestFixtures.STATUS_ACTIVE).isEqualTo("ACTIVE");
        assertThat(CatalogTestFixtures.STATUS_PAUSED).isEqualTo("PAUSED");
        assertThat(CatalogTestFixtures.STATUS_DISABLED).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("Should create standard published dish for diner catalog")
    void createStandardPublishedDish() {
        Dish dish = CatalogTestFixtures.createStandardPublishedDish();

        assertThat(dish).isNotNull();
        assertThat(dish.getId()).isEqualTo(CatalogTestFixtures.DEFAULT_DISH_ID);
        assertThat(dish.getTenantId()).isEqualTo(CatalogTestFixtures.DEFAULT_TENANT_ID);
        assertThat(dish.getName()).isEqualTo(CatalogTestFixtures.DEFAULT_DISH_NAME);
        assertThat(dish.getCategory()).isEqualTo(DishCategory.PLATOS_PRINCIPALES);
        assertThat(dish.getDescription()).isEqualTo(CatalogTestFixtures.DEFAULT_DISH_DESCRIPTION);
        assertThat(dish.getPrice()).isEqualTo(CatalogTestFixtures.DEFAULT_DISH_PRICE);
        assertThat(dish.getStatus()).isEqualTo(DishStatus.PUBLISHED);
        assertThat(dish.getIsActive()).isTrue();
        assertThat(dish.getAllergensConfirmed()).isTrue();
    }

    @Test
    @DisplayName("Should build dish via fluent CatalogDishBuilder with custom overrides")
    void catalogDishBuilder() {
        UUID customTenant = UUID.randomUUID();
        Dish dish = CatalogTestFixtures.catalogDishBuilder()
                .tenantId(customTenant)
                .name("Flan Mixto Casero")
                .category(DishCategory.POSTRES)
                .description("Flan de vainilla casero con dulce de leche y crema")
                .price(BigDecimal.valueOf(2500.00))
                .status(DishStatus.PUBLISHED)
                .isActive(true)
                .allergensConfirmed(true)
                .restrictions(Set.of("CELIAC"))
                .build();

        assertThat(dish.getTenantId()).isEqualTo(customTenant);
        assertThat(dish.getName()).isEqualTo("Flan Mixto Casero");
        assertThat(dish.getCategory()).isEqualTo(DishCategory.POSTRES);
        assertThat(dish.getPrice()).isEqualTo(BigDecimal.valueOf(2500.00));
        assertThat(dish.getStatus()).isEqualTo(DishStatus.PUBLISHED);
        assertThat(dish.getRestrictionCodes()).containsExactly("CELIAC");
    }

    @Test
    @DisplayName("Should create recipe ingredient line with valid composite id and quantity")
    void createRecipeIngredientLine() {
        UUID dishId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        BigDecimal qty = new BigDecimal("250.0000");

        DishRecipeIngredient line = CatalogTestFixtures.createRecipeIngredientLine(dishId, ingredientId, qty);

        assertThat(line).isNotNull();
        assertThat(line.getId().getDishId()).isEqualTo(dishId);
        assertThat(line.getId().getIngredientId()).isEqualTo(ingredientId);
        assertThat(line.getQuantity()).isEqualTo(qty);
    }

    @Test
    @DisplayName("Should create inventory ingredient with specified stock and unit of measure")
    void createInventoryIngredient() {
        UUID tenantId = UUID.randomUUID();
        Ingredient ingredient = CatalogTestFixtures.createInventoryIngredient(
                tenantId,
                "Queso Mozzarella",
                new BigDecimal("5000.0000"),
                UnitOfMeasure.G
        );

        assertThat(ingredient).isNotNull();
        assertThat(ingredient.getTenantId()).isEqualTo(tenantId);
        assertThat(ingredient.getName()).isEqualTo("Queso Mozzarella");
        assertThat(ingredient.getCurrentStock()).isEqualTo(new BigDecimal("5000.0000"));
        assertThat(ingredient.getUnitOfMeasure()).isEqualTo(UnitOfMeasure.G);
        assertThat(ingredient.getStatus()).isEqualTo(IngredientStatus.ACTIVE);
    }
}

