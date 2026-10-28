package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DishAvailabilityEngine Unit Tests")
class DishAvailabilityEngineTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    private DishAvailabilityEngine availabilityEngine;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private Establishment establishment;

    @BeforeEach
    void setUp() {
        availabilityEngine = new DishAvailabilityEngine(
                establishmentRepository,
                dishRepository,
                dishRecipeIngredientRepository,
                ingredientRepository
        );

        establishment = new Establishment(tenantId, "Café Central Palermo", EstablishmentStatus.ACTIVE, null, null, "America/Argentina/Buenos_Aires");
    }

    private Ingredient createIngredient(UUID id, UUID tId, String name, BigDecimal stock, IngredientStatus status) {
        Ingredient ing = new Ingredient(tId, name, IngredientCategory.MEAT, UnitOfMeasure.KG, BigDecimal.valueOf(100));
        ing.setId(id);
        ing.setCurrentStock(stock);
        ing.setStatus(status);
        return ing;
    }

    private Dish createDish(UUID id, UUID tId, String name) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setTenantId(tId);
        dish.setName(name);
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setIsActive(true);
        dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        return dish;
    }

    @Nested
    @DisplayName("Single Dish Availability Calculation")
    class SingleDishAvailabilityTests {

        @Test
        @DisplayName("Should evaluate available as true when currentStock exactly equals recipe quantity (decimal boundary)")
        void shouldBeAvailableWhenStockMatchesRecipeQuantityExactly() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("0.2500");
            BigDecimal currentStock = new BigDecimal("0.2500");

            DishRecipeIngredient line = new DishRecipeIngredient(new DishRecipeIngredientId(dishId, ingredientId), recipeQuantity);
            Ingredient ing = createIngredient(ingredientId, tenantId, "Carne Molida", currentStock, IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
            when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ing));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Should evaluate available as false when there is a fractional shortfall in stock")
        void shouldBeUnavailableWhenFractionalShortfall() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("0.2500");
            BigDecimal currentStock = new BigDecimal("0.2499"); // 0.0001 shortfall

            DishRecipeIngredient line = new DishRecipeIngredient(new DishRecipeIngredientId(dishId, ingredientId), recipeQuantity);
            Ingredient ing = createIngredient(ingredientId, tenantId, "Carne Molida", currentStock, IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
            when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ing));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when inventory stock is negative, without crashing")
        void shouldHandleNegativeStockSafely() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("1.0000");
            BigDecimal currentStock = new BigDecimal("-1.5000"); // Operational discrepancy

            DishRecipeIngredient line = new DishRecipeIngredient(new DishRecipeIngredientId(dishId, ingredientId), recipeQuantity);
            Ingredient ing = createIngredient(ingredientId, tenantId, "Aceite", currentStock, IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
            when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ing));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when dish has no recipe lines (empty recipe guard)")
        void shouldTreatEmptyRecipeAsUnavailable() {
            UUID dishId = UUID.randomUUID();

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(Collections.emptyList());

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when an ingredient is deactivated or discontinued")
        void shouldBeUnavailableWhenIngredientDeactivated() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("1.0000");
            BigDecimal currentStock = new BigDecimal("10.0000");

            DishRecipeIngredient line = new DishRecipeIngredient(new DishRecipeIngredientId(dishId, ingredientId), recipeQuantity);
            Ingredient ing = createIngredient(ingredientId, tenantId, "Sal", currentStock, IngredientStatus.DISCONTINUED);

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
            when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ing));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when an ingredient is not found in tenant inventory")
        void shouldBeUnavailableWhenIngredientNotFound() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("1.0000");

            DishRecipeIngredient line = new DishRecipeIngredient(new DishRecipeIngredientId(dishId, ingredientId), recipeQuantity);

            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(List.of(line));
            when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.empty());

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }
    }

    @Nested
    @DisplayName("Shared Ingredient N:M Matrix & Simultaneous Invalidation")
    class SharedIngredientMatrixTests {

        @Test
        @DisplayName("Should evaluate Dish A available and Dish B unavailable when shared ingredient only satisfies Dish A")
        void shouldEvaluateSharedIngredientNM() {
            UUID dishAId = UUID.randomUUID();
            UUID dishBId = UUID.randomUUID();
            UUID sharedIngId = UUID.randomUUID();

            // Dish A requires 1 unit; Dish B requires 2 units
            DishRecipeIngredient lineA = new DishRecipeIngredient(new DishRecipeIngredientId(dishAId, sharedIngId), new BigDecimal("1.0000"));
            DishRecipeIngredient lineB = new DishRecipeIngredient(new DishRecipeIngredientId(dishBId, sharedIngId), new BigDecimal("2.0000"));

            Ingredient sharedIng = createIngredient(sharedIngId, tenantId, "Queso Cheddar", new BigDecimal("1.0000"), IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dishAId)).thenReturn(List.of(lineA));
            when(dishRecipeIngredientRepository.findByIdDishId(dishBId)).thenReturn(List.of(lineB));
            when(ingredientRepository.findByIdAndTenantId(sharedIngId, tenantId)).thenReturn(Optional.of(sharedIng));

            assertThat(availabilityEngine.isDishAvailable(dishAId, tenantId)).isTrue();
            assertThat(availabilityEngine.isDishAvailable(dishBId, tenantId)).isFalse();
        }

        @Test
        @DisplayName("Should simultaneously invalidate all dependent dishes when shared ingredient drops to 0")
        void shouldSimultaneouslyInvalidateWhenSharedStockDepleted() {
            UUID dishAId = UUID.randomUUID();
            UUID dishBId = UUID.randomUUID();
            UUID sharedIngId = UUID.randomUUID();

            DishRecipeIngredient lineA = new DishRecipeIngredient(new DishRecipeIngredientId(dishAId, sharedIngId), new BigDecimal("1.0000"));
            DishRecipeIngredient lineB = new DishRecipeIngredient(new DishRecipeIngredientId(dishBId, sharedIngId), new BigDecimal("1.0000"));

            Ingredient depletedIng = createIngredient(sharedIngId, tenantId, "Carne Picada", BigDecimal.ZERO, IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dishAId)).thenReturn(List.of(lineA));
            when(dishRecipeIngredientRepository.findByIdDishId(dishBId)).thenReturn(List.of(lineB));
            when(ingredientRepository.findByIdAndTenantId(sharedIngId, tenantId)).thenReturn(Optional.of(depletedIng));

            assertThat(availabilityEngine.isDishAvailable(dishAId, tenantId)).isFalse();
            assertThat(availabilityEngine.isDishAvailable(dishBId, tenantId)).isFalse();
        }
    }

    @Nested
    @DisplayName("Compute Availability Map for Establishment")
    class ComputeAvailabilityMapTests {

        @Test
        @DisplayName("Should throw EstablishmentNotFoundException when establishment does not exist")
        void shouldThrowWhenEstablishmentNotFound() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> availabilityEngine.computeAvailabilityMap(tenantId))
                    .isInstanceOf(EstablishmentNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw EstablishmentInactiveException when establishment is PAUSED or DISABLED")
        void shouldThrowWhenEstablishmentInactive() {
            Establishment paused = new Establishment(tenantId, "Café Pausado", EstablishmentStatus.PAUSED, null, null, null);
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(paused));

            assertThatThrownBy(() -> availabilityEngine.computeAvailabilityMap(tenantId))
                    .isInstanceOf(EstablishmentInactiveException.class);
        }

        @Test
        @DisplayName("Should return availability map for published active dishes")
        void shouldReturnAvailabilityMapForPublishedDishes() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(establishment));

            Dish dish1 = createDish(UUID.randomUUID(), tenantId, "Plato 1");
            Dish dish2 = createDish(UUID.randomUUID(), tenantId, "Plato 2");

            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of(dish1, dish2));

            UUID ing1Id = UUID.randomUUID();
            DishRecipeIngredient line1 = new DishRecipeIngredient(new DishRecipeIngredientId(dish1.getId(), ing1Id), new BigDecimal("1.0000"));
            Ingredient ing1 = createIngredient(ing1Id, tenantId, "Ing 1", new BigDecimal("5.0000"), IngredientStatus.ACTIVE);

            when(dishRecipeIngredientRepository.findByIdDishId(dish1.getId())).thenReturn(List.of(line1));
            when(ingredientRepository.findByIdAndTenantId(ing1Id, tenantId)).thenReturn(Optional.of(ing1));

            // dish2 has empty recipe lines
            when(dishRecipeIngredientRepository.findByIdDishId(dish2.getId())).thenReturn(Collections.emptyList());

            Map<UUID, Boolean> map = availabilityEngine.computeAvailabilityMap(tenantId);

            assertThat(map).hasSize(2);
            assertThat(map.get(dish1.getId())).isTrue();
            assertThat(map.get(dish2.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("Deterministic ETag Generation")
    class ETagGenerationTests {

        @Test
        @DisplayName("Should generate deterministic SHA-256 ETag irrespective of map iteration order")
        void shouldGenerateDeterministicETag() {
            UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

            Map<UUID, Boolean> map1 = new LinkedHashMap<>();
            map1.put(id1, true);
            map1.put(id2, false);

            Map<UUID, Boolean> map2 = new LinkedHashMap<>();
            map2.put(id2, false);
            map2.put(id1, true);

            String etag1 = availabilityEngine.computeETag(map1);
            String etag2 = availabilityEngine.computeETag(map2);

            assertThat(etag1).isNotNull();
            assertThat(etag1).isEqualTo(etag2);
            assertThat(etag1).matches("^[a-f0-9]{64}$");
        }

        @Test
        @DisplayName("Should generate different ETags when availability values change")
        void shouldGenerateDifferentETagsWhenValuesChange() {
            UUID id1 = UUID.randomUUID();

            Map<UUID, Boolean> mapBefore = Map.of(id1, true);
            Map<UUID, Boolean> mapAfter = Map.of(id1, false);

            String etagBefore = availabilityEngine.computeETag(mapBefore);
            String etagAfter = availabilityEngine.computeETag(mapAfter);

            assertThat(etagBefore).isNotEqualTo(etagAfter);
        }

        @Test
        @DisplayName("Should generate stable hash for empty map")
        void shouldGenerateStableHashForEmptyMap() {
            String etag = availabilityEngine.computeETag(Collections.emptyMap());
            assertThat(etag).isNotNull();
            assertThat(etag).matches("^[a-f0-9]{64}$");
        }
    }
}
