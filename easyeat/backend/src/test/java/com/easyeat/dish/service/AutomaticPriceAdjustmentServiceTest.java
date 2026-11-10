package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutomaticPriceAdjustmentService (C-09) Unit Tests")
class AutomaticPriceAdjustmentServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private DishPriceHistoryRepository priceHistoryRepository;

    private FoodCostAuditService foodCostAuditService;
    private AutomaticPriceAdjustmentService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DISH_ID = UUID.randomUUID();
    private static final UUID INGREDIENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Real FoodCostEngine and real FoodCostAuditService to avoid Mockito class mock on JDK 27
        FoodCostEngine foodCostEngine = new FoodCostEngine();
        foodCostAuditService = new FoodCostAuditService(
                dishRepository,
                dishRecipeIngredientRepository,
                ingredientRepository,
                foodCostEngine
        );

        service = new AutomaticPriceAdjustmentServiceImpl(
                dishRepository,
                foodCostAuditService,
                priceHistoryRepository
        );
    }

    @Nested
    @DisplayName("C-09 Criteria A: Cost Increase (Alza de costos, e.g. Leche $650 -> $1750)")
    class CostIncreaseTests {

        @Test
        @DisplayName("Increases Food Cost and recalculates PVP upward preserving margin percentage and records audit trace")
        void costIncrease_recalculatesPvpUpward_preservesMarginPercentage() {
            // Given: Published dish with retail price $2000.00, recipe 1 unit of milk
            // Initial milk cost: 650.00 -> Food Cost $650.00 -> PVP $2000.00 -> margin = ((2000 - 650) / 2000) * 100 = 67.5%
            Dish dish = new Dish(TENANT_ID, "Café con Leche", DishCategory.BEBIDAS, "Café", new BigDecimal("2000.00"), true, ACTOR_ID);
            dish.setId(DISH_ID);
            dish.setStatus(DishStatus.PUBLISHED);
            dish.setRetailPrice(new BigDecimal("2000.00"));
            dish.setMarginPercentage(new BigDecimal("67.5000"));

            when(dishRepository.findPublishedDishesByIngredientId(TENANT_ID, INGREDIENT_ID))
                    .thenReturn(List.of(dish));

            DishRecipeIngredient recipeLine = new DishRecipeIngredient(DISH_ID, INGREDIENT_ID, new BigDecimal("1.0000"));
            when(dishRecipeIngredientRepository.findByIdDishId(DISH_ID))
                    .thenReturn(List.of(recipeLine));

            // In inventory, ingredient unit_purchase_cost is now 1750.00
            Ingredient milk = new Ingredient(TENANT_ID, "Leche Entera", IngredientCategory.DAIRY, UnitOfMeasure.L, new BigDecimal("1750.0000"));
            milk.setId(INGREDIENT_ID);
            when(ingredientRepository.findAllById(List.of(INGREDIENT_ID)))
                    .thenReturn(List.of(milk));

            // When: stock addition increases milk cost from 650.00 to 1750.00
            service.processIngredientCostChange(
                    TENANT_ID,
                    INGREDIENT_ID,
                    new BigDecimal("650.0000"),
                    new BigDecimal("1750.0000"),
                    ACTOR_ID
            );

            // Then:
            // Food_Cost_nuevo = 1750.00
            // PVP_nuevo = 1750.00 / (1 - (67.5 / 100)) = 1750.00 / 0.325 = 5384.62
            assertThat(dish.getRetailPrice()).isEqualByComparingTo("5384.62");
            verify(dishRepository).save(dish);

            // Verify audit trace in dish_price_history
            ArgumentCaptor<DishPriceHistory> captor = ArgumentCaptor.forClass(DishPriceHistory.class);
            verify(priceHistoryRepository).save(captor.capture());

            DishPriceHistory savedHistory = captor.getValue();
            assertThat(savedHistory.getDishId()).isEqualTo(dish.getId());
            assertThat(savedHistory.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(savedHistory.getAdminId()).isEqualTo(ACTOR_ID);
            assertThat(savedHistory.getPreviousPrice()).isEqualByComparingTo("2000.00");
            assertThat(savedHistory.getNewPrice()).isEqualByComparingTo("5384.62");
            assertThat(savedHistory.getPreviousFoodCost()).isEqualByComparingTo("650.00");
            assertThat(savedHistory.getFoodCostSnapshot()).isEqualByComparingTo("1750.00");
            assertThat(savedHistory.getTriggeringIngredientId()).isEqualTo(INGREDIENT_ID);
            assertThat(savedHistory.getIngredientPreviousCost()).isEqualByComparingTo("650.0000");
            assertThat(savedHistory.getIngredientNewCost()).isEqualByComparingTo("1750.0000");
        }
    }

    @Nested
    @DisplayName("C-09 Criteria B: Cost Decrease (Baja de costos)")
    class CostDecreaseTests {

        @Test
        @DisplayName("Food Cost decreases: internal cost is updated, but public PVP is unchanged (PVP_nuevo = PVP_anterior)")
        void costDecrease_keepsPvpUnchanged_noPriceHistoryGenerated() {
            // Given: Published dish with retail price $2000.00, recipe with 1 unit of ingredient
            // Previous cost: $1000.00 (margin 50.00%)
            Dish dish = new Dish(TENANT_ID, "Café con Leche", DishCategory.BEBIDAS, "Café", new BigDecimal("2000.00"), true, ACTOR_ID);
            dish.setId(DISH_ID);
            dish.setStatus(DishStatus.PUBLISHED);
            dish.setRetailPrice(new BigDecimal("2000.00"));
            dish.setMarginPercentage(new BigDecimal("50.0000"));

            when(dishRepository.findPublishedDishesByIngredientId(TENANT_ID, INGREDIENT_ID))
                    .thenReturn(List.of(dish));

            DishRecipeIngredient recipeLine = new DishRecipeIngredient(DISH_ID, INGREDIENT_ID, new BigDecimal("1.0000"));
            when(dishRecipeIngredientRepository.findByIdDishId(DISH_ID))
                    .thenReturn(List.of(recipeLine));

            // In inventory, ingredient unit_purchase_cost decreased to 600.00 (Food Cost = 600.00 < 1000.00)
            Ingredient ing = new Ingredient(TENANT_ID, "Café", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("600.0000"));
            ing.setId(INGREDIENT_ID);
            when(ingredientRepository.findAllById(List.of(INGREDIENT_ID)))
                    .thenReturn(List.of(ing));

            // When: stock addition with decreased unit cost
            service.processIngredientCostChange(
                    TENANT_ID,
                    INGREDIENT_ID,
                    new BigDecimal("1000.0000"),
                    new BigDecimal("600.0000"),
                    ACTOR_ID
            );

            // Then: PVP stays intact at $2000.00
            assertThat(dish.getRetailPrice()).isEqualByComparingTo("2000.00");
            verify(dishRepository, never()).save(dish);
            verify(priceHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("C-09 Criteria C: Dishes not in PUBLISHED status (e.g. PENDING_PRICE)")
    class UnpublishedDishesTests {

        @Test
        @DisplayName("Only dishes in PUBLISHED status are re-priced by automatic price adjustment")
        void dishesInPendingPrice_areNotReprisedOrPublished() {
            // Given: No published dishes returned for ingredient
            when(dishRepository.findPublishedDishesByIngredientId(TENANT_ID, INGREDIENT_ID))
                    .thenReturn(Collections.emptyList());

            // Execute
            service.processIngredientCostChange(
                    TENANT_ID,
                    INGREDIENT_ID,
                    new BigDecimal("500.00"),
                    new BigDecimal("800.00"),
                    ACTOR_ID
            );

            // Then: zero interactions with price modification or history
            verify(dishRepository, never()).save(any());
            verify(priceHistoryRepository, never()).save(any());
        }
    }
}
