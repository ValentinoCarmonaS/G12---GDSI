package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeResponse;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.InvalidIngredientException;
import com.easyeat.dish.exception.InvalidQuantityException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DishRecipeServiceTest — Recipe Management Logic Unit Tests")
class DishRecipeServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishPhotoRepository dishPhotoRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private DishService dishService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private final UUID activeContinuousIngredientId = UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    private final UUID activeDiscreteIngredientId = UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
    private final UUID discontinuedIngredientId = UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03");

    private Dish draftDish;
    private Ingredient continuousIngredient;
    private Ingredient discreteIngredient;
    private Ingredient discontinuedIngredient;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        draftDish = new Dish();
        draftDish.setId(dishId);
        draftDish.setTenantId(tenantId);
        draftDish.setName("Risotto de Hongos");
        draftDish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        draftDish.setStatus(DishStatus.DRAFT);
        draftDish.setAllergensConfirmed(false);

        continuousIngredient = new Ingredient(tenantId, "Arroz Carnaroli", IngredientCategory.DRY_GOODS, UnitOfMeasure.G, BigDecimal.valueOf(10));
        continuousIngredient.setId(activeContinuousIngredientId);
        continuousIngredient.setStatus(IngredientStatus.ACTIVE);

        discreteIngredient = new Ingredient(tenantId, "Huevos de Campo", IngredientCategory.DAIRY, UnitOfMeasure.U, BigDecimal.valueOf(50));
        discreteIngredient.setId(activeDiscreteIngredientId);
        discreteIngredient.setStatus(IngredientStatus.ACTIVE);

        discontinuedIngredient = new Ingredient(tenantId, "Trufa Blanca", IngredientCategory.SPICES, UnitOfMeasure.G, BigDecimal.valueOf(500));
        discontinuedIngredient.setId(discontinuedIngredientId);
        discontinuedIngredient.setStatus(IngredientStatus.DISCONTINUED);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Recipe Upsert and Validation Tests")
    class RecipeUpsertTests {

        @Test
        @DisplayName("Should successfully update recipe with continuous decimal and discrete integer quantities")
        void shouldUpdateRecipeSuccessfully() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(ingredientRepository.findByIdAndTenantId(activeContinuousIngredientId, tenantId)).thenReturn(Optional.of(continuousIngredient));
            when(ingredientRepository.findByIdAndTenantId(activeDiscreteIngredientId, tenantId)).thenReturn(Optional.of(discreteIngredient));

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, 150.75),
                    new RecipeIngredientItem(activeDiscreteIngredientId, 2.0)
            ));

            RecipeResponse response = dishService.updateRecipe(dishId, request);

            assertThat(response).isNotNull();
            assertThat(response.dishId()).isEqualTo(dishId);
            assertThat(response.ingredients()).hasSize(2);

            verify(dishRecipeIngredientRepository).deleteByIdDishId(dishId);
            verify(dishRecipeIngredientRepository).saveAll(any());
        }

        @Test
        @DisplayName("Should reject zero or negative quantities with InvalidQuantityException")
        void shouldRejectZeroOrNegativeQuantity() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));

            RecipeUpdateRequest zeroRequest = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, 0.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, zeroRequest))
                    .isInstanceOf(InvalidQuantityException.class)
                    .hasMessageContaining("mayor a cero");

            RecipeUpdateRequest negativeRequest = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, -12.5)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, negativeRequest))
                    .isInstanceOf(InvalidQuantityException.class)
                    .hasMessageContaining("mayor a cero");
        }

        @Test
        @DisplayName("Should reject decimal quantity for discrete unit ('u') with InvalidQuantityException")
        void shouldRejectDecimalQuantityForDiscreteUnit() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(ingredientRepository.findByIdAndTenantId(activeDiscreteIngredientId, tenantId)).thenReturn(Optional.of(discreteIngredient));

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeDiscreteIngredientId, 2.5)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, request))
                    .isInstanceOf(InvalidQuantityException.class)
                    .hasMessageContaining("enteros");
        }

        @Test
        @DisplayName("Should reject duplicate ingredients in recipe request")
        void shouldRejectDuplicateIngredientsInRequest() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));

            RecipeUpdateRequest duplicateRequest = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, 100.0),
                    new RecipeIngredientItem(activeContinuousIngredientId, 50.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, duplicateRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duplicado");
        }

        @Test
        @DisplayName("Should reject discontinued ingredient with InvalidIngredientException")
        void shouldRejectDiscontinuedIngredient() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(ingredientRepository.findByIdAndTenantId(discontinuedIngredientId, tenantId)).thenReturn(Optional.of(discontinuedIngredient));

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(discontinuedIngredientId, 10.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, request))
                    .isInstanceOf(InvalidIngredientException.class)
                    .hasMessageContaining("discontinuado");
        }

        @Test
        @DisplayName("Should reject ingredient not found in tenant catalog")
        void shouldRejectNonExistentIngredient() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            UUID unknownId = UUID.randomUUID();
            when(ingredientRepository.findByIdAndTenantId(unknownId, tenantId)).thenReturn(Optional.empty());

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(unknownId, 10.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, request))
                    .isInstanceOf(InvalidIngredientException.class)
                    .hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("Should reject recipe update when dish is not in DRAFT status")
        void shouldRejectRecipeUpdateWhenDishLocked() {
            draftDish.setStatus(DishStatus.PENDING_PRICE);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, 100.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, request))
                    .isInstanceOf(DishLockedException.class);
        }

        @Test
        @DisplayName("Should reject recipe update when dish does not exist")
        void shouldRejectRecipeUpdateWhenDishNotFound() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            RecipeUpdateRequest request = new RecipeUpdateRequest(List.of(
                    new RecipeIngredientItem(activeContinuousIngredientId, 100.0)
            ));

            assertThatThrownBy(() -> dishService.updateRecipe(dishId, request))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Recipe Line Deletion Tests")
    class RecipeLineDeletionTests {

        @Test
        @DisplayName("Should successfully delete recipe ingredient line from draft dish")
        void shouldDeleteRecipeLineSuccessfully() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dishRecipeIngredientRepository.existsByIdDishIdAndIdIngredientId(dishId, activeContinuousIngredientId)).thenReturn(true);

            dishService.removeRecipeIngredient(dishId, activeContinuousIngredientId);

            verify(dishRecipeIngredientRepository).deleteByIdDishIdAndIdIngredientId(dishId, activeContinuousIngredientId);
        }

        @Test
        @DisplayName("Should reject line deletion when dish is in PENDING_PRICE status")
        void shouldRejectDeleteLineWhenDishLocked() {
            draftDish.setStatus(DishStatus.PENDING_PRICE);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));

            assertThatThrownBy(() -> dishService.removeRecipeIngredient(dishId, activeContinuousIngredientId))
                    .isInstanceOf(DishLockedException.class);

            verify(dishRecipeIngredientRepository, never()).deleteByIdDishIdAndIdIngredientId(any(), any());
        }

        @Test
        @DisplayName("Should reject line deletion when ingredient is not part of recipe")
        void shouldRejectDeleteLineWhenIngredientNotInRecipe() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dishRecipeIngredientRepository.existsByIdDishIdAndIdIngredientId(dishId, activeContinuousIngredientId)).thenReturn(false);

            assertThatThrownBy(() -> dishService.removeRecipeIngredient(dishId, activeContinuousIngredientId))
                    .isInstanceOf(InvalidIngredientException.class)
                    .hasMessageContaining("no forma parte de la receta");

            verify(dishRecipeIngredientRepository, never()).deleteByIdDishIdAndIdIngredientId(any(), any());
        }
    }
}
