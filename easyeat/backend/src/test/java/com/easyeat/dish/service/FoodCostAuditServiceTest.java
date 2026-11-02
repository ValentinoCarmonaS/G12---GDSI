package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FoodCostAuditService Unit Tests - Business Rules and Lifecycle")
class FoodCostAuditServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    private FoodCostAuditService auditService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID dishId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // A real FoodCostEngine instance is used instead of a Mockito @Spy: inline class mocking is
        // not supported on JDK 27, and the engine is a pure function that needs no stubbing here.
        auditService = new FoodCostAuditService(
                dishRepository,
                dishRecipeIngredientRepository,
                ingredientRepository,
                new FoodCostEngine());
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private Dish createDish(String name, DishCategory category) {
        Dish dish = new Dish(tenantId, name, category, "Desc", new BigDecimal("100.00"), true, UUID.randomUUID());
        dish.setId(dishId);
        return dish;
    }

    @Test
    @DisplayName("Throws DishNotFoundException when dish does not exist for tenant")
    void shouldThrowDishNotFound_whenDishDoesNotExist() {
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.auditDishCost(dishId))
                .isInstanceOf(DishNotFoundException.class);
    }

    @Test
    @DisplayName("Successfully computes food cost for a dish in DRAFT status without throwing state lock (status-agnostic service unblocking spec 007)")
    void shouldComputeFoodCostAudit_whenDishIsDraft() {
        Dish dish = createDish("Milanesa", DishCategory.PLATOS_PRINCIPALES);
        dish.setStatus(DishStatus.DRAFT);

        UUID meatId = UUID.randomUUID();
        DishRecipeIngredient line = new DishRecipeIngredient(dishId, meatId, new BigDecimal("0.5000"));

        Ingredient meat = new Ingredient(tenantId, "Carne", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("5000.00"));
        meat.setId(meatId);

        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
        when(ingredientRepository.findAllById(List.of(meatId))).thenReturn(List.of(meat));

        FoodCostAuditResponse response = auditService.auditDishCost(dishId);

        assertThat(response).isNotNull();
        assertThat(response.totalFoodCost()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    @DisplayName("Throws EmptyRecipeException when dish has no ingredients")
    void shouldThrowEmptyRecipe_whenNoIngredients() {
        Dish dish = createDish("Milanesa", DishCategory.PLATOS_PRINCIPALES);
        dish.setStatus(DishStatus.PENDING_PRICE);
        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> auditService.auditDishCost(dishId))
                .isInstanceOf(EmptyRecipeException.class);
    }

    @Test
    @DisplayName("Successfully computes food cost audit with declared dietary tags and itemized lines")
    void shouldComputeFoodCostAudit_whenValidPendingPriceDish() {
        Dish dish = createDish("Pizza Napolitana", DishCategory.PLATOS_PRINCIPALES);
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setRestrictionCodes(Set.of("CELIAC", "DAIRY"));

        UUID flourId = UUID.randomUUID();
        UUID cheeseId = UUID.randomUUID();

        DishRecipeIngredient line1 = new DishRecipeIngredient(dishId, flourId, new BigDecimal("0.3000"));
        DishRecipeIngredient line2 = new DishRecipeIngredient(dishId, cheeseId, new BigDecimal("0.2000"));

        Ingredient flour = new Ingredient(tenantId, "Harina Sin Gluten", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("1500.00"));
        flour.setId(flourId);

        Ingredient cheese = new Ingredient(tenantId, "Mozzarella", IngredientCategory.DAIRY, UnitOfMeasure.KG, new BigDecimal("8000.00"));
        cheese.setId(cheeseId);

        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line1, line2));
        when(ingredientRepository.findAllById(List.of(flourId, cheeseId))).thenReturn(List.of(flour, cheese));

        FoodCostAuditResponse response = auditService.auditDishCost(dishId);

        assertThat(response).isNotNull();
        assertThat(response.dishName()).isEqualTo("Pizza Napolitana");
        // 0.3 * 1500 = 450.00 ; 0.2 * 8000 = 1600.00 -> total = 2050.00
        assertThat(response.totalFoodCost()).isEqualByComparingTo(new BigDecimal("2050.00"));
        assertThat(response.hasUnpricedIngredients()).isFalse();
        assertThat(response.warningNotes()).isEmpty();
        assertThat(response.requiredDietaryRestrictions()).containsExactlyInAnyOrder("CELIAC", "DAIRY");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).lineSubtotal()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(response.items().get(1).lineSubtotal()).isEqualByComparingTo(new BigDecimal("1600.00"));
    }

    @Test
    @DisplayName("Flags unpriced ingredients and includes warning note when ingredient has 0.00 or null cost")
    void shouldFlagUnpricedIngredient_whenUnitCostZero() {
        Dish dish = createDish("Sopa", DishCategory.ENTRADAS);
        dish.setStatus(DishStatus.PENDING_PRICE);

        UUID waterId = UUID.randomUUID();
        DishRecipeIngredient line = new DishRecipeIngredient(dishId, waterId, new BigDecimal("1.0000"));

        Ingredient water = new Ingredient(tenantId, "Agua Filtrada", IngredientCategory.DRY_GOODS, UnitOfMeasure.L, BigDecimal.ZERO);
        water.setId(waterId);

        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
        when(ingredientRepository.findAllById(List.of(waterId))).thenReturn(List.of(water));

        FoodCostAuditResponse response = auditService.auditDishCost(dishId);

        assertThat(response.hasUnpricedIngredients()).isTrue();
        assertThat(response.totalFoodCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.warningNotes()).hasSize(1);
        assertThat(response.warningNotes().get(0)).contains("Agua Filtrada");
        assertThat(response.items().get(0).unpricedWarning()).isTrue();
    }

    @Test
    @DisplayName("Applies purchase-presentation conversion to the audit unit cost and line subtotal")
    void shouldApplyPackageConversion_whenIngredientHasPurchasePresentation() {
        Dish dish = createDish("Revuelto de huevos", DishCategory.PLATOS_PRINCIPALES);
        dish.setStatus(DishStatus.PENDING_PRICE);

        UUID eggsId = UUID.randomUUID();
        DishRecipeIngredient line = new DishRecipeIngredient(dishId, eggsId, new BigDecimal("2.0000"));

        Ingredient eggs = new Ingredient(
                tenantId, "Huevos", IngredientCategory.DAIRY, UnitOfMeasure.U, new BigDecimal("3600.0000"));
        eggs.setId(eggsId);
        eggs.setPackageCost(new BigDecimal("3600.0000"));
        eggs.setUnitsPerPackage(12);

        when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
        when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
        when(ingredientRepository.findAllById(List.of(eggsId))).thenReturn(List.of(eggs));

        FoodCostAuditResponse response = auditService.auditDishCost(dishId);

        assertThat(response.items()).hasSize(1);
        // 3,600 / 12 = 300 per base unit; 2 units -> 600.00. The package total never leaks.
        assertThat(response.items().get(0).unitPurchaseCost()).isEqualByComparingTo(new BigDecimal("300.0000"));
        assertThat(response.items().get(0).lineSubtotal()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(response.totalFoodCost()).isEqualByComparingTo(new BigDecimal("600.00"));
    }
}
