package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.repository.RecipeLineWithIngredientProjection;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private RecipeLineWithIngredientProjection mockRecipeLine(
            UUID dishId,
            UUID ingredientId,
            BigDecimal quantity,
            String name,
            IngredientStatus status,
            BigDecimal currentStock) {
        RecipeLineWithIngredientProjection proj = mock(RecipeLineWithIngredientProjection.class);
        org.mockito.Mockito.lenient().when(proj.getDishId()).thenReturn(dishId);
        org.mockito.Mockito.lenient().when(proj.getIngredientId()).thenReturn(ingredientId);
        org.mockito.Mockito.lenient().when(proj.getQuantity()).thenReturn(quantity);
        org.mockito.Mockito.lenient().when(proj.getIngredientName()).thenReturn(name);
        org.mockito.Mockito.lenient().when(proj.getIngredientStatus()).thenReturn(status);
        org.mockito.Mockito.lenient().when(proj.getCurrentStock()).thenReturn(currentStock);
        return proj;
    }

    private String expectedSha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
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

            RecipeLineWithIngredientProjection line = mockRecipeLine(dishId, ingredientId, recipeQuantity, "Carne Molida", IngredientStatus.ACTIVE, currentStock);
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isTrue();
            verify(dishRecipeIngredientRepository, never()).findByIdDishId(any());
            verify(ingredientRepository, never()).findByIdAndTenantId(any(), any());
        }

        @Test
        @DisplayName("Should evaluate available as false when there is a fractional shortfall in stock")
        void shouldBeUnavailableWhenFractionalShortfall() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("0.2500");
            BigDecimal currentStock = new BigDecimal("0.2499"); // 0.0001 shortfall

            RecipeLineWithIngredientProjection line = mockRecipeLine(dishId, ingredientId, recipeQuantity, "Carne Molida", IngredientStatus.ACTIVE, currentStock);
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
            verify(dishRecipeIngredientRepository, never()).findByIdDishId(any());
            verify(ingredientRepository, never()).findByIdAndTenantId(any(), any());
        }

        @Test
        @DisplayName("Should evaluate available as false when inventory stock is negative, without crashing")
        void shouldHandleNegativeStockSafely() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("1.0000");
            BigDecimal currentStock = new BigDecimal("-1.5000"); // Operational discrepancy

            RecipeLineWithIngredientProjection line = mockRecipeLine(dishId, ingredientId, recipeQuantity, "Aceite", IngredientStatus.ACTIVE, currentStock);
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when dish has no recipe lines (empty recipe guard)")
        void shouldTreatEmptyRecipeAsUnavailable() {
            UUID dishId = UUID.randomUUID();

            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(Collections.emptyList());

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

            RecipeLineWithIngredientProjection line = mockRecipeLine(dishId, ingredientId, recipeQuantity, "Sal", IngredientStatus.DISCONTINUED, currentStock);
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should evaluate available as false when an ingredient is not found in tenant inventory")
        void shouldBeUnavailableWhenIngredientNotFound() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            BigDecimal recipeQuantity = new BigDecimal("1.0000");

            RecipeLineWithIngredientProjection line = mockRecipeLine(dishId, ingredientId, recipeQuantity, null, null, null);
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line));

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

            RecipeLineWithIngredientProjection lineA = mockRecipeLine(dishAId, sharedIngId, new BigDecimal("1.0000"), "Queso Cheddar", IngredientStatus.ACTIVE, new BigDecimal("1.0000"));
            RecipeLineWithIngredientProjection lineB = mockRecipeLine(dishBId, sharedIngId, new BigDecimal("2.0000"), "Queso Cheddar", IngredientStatus.ACTIVE, new BigDecimal("1.0000"));

            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(eq(List.of(dishAId)), eq(tenantId)))
                    .thenReturn(List.of(lineA));
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(eq(List.of(dishBId)), eq(tenantId)))
                    .thenReturn(List.of(lineB));

            assertThat(availabilityEngine.isDishAvailable(dishAId, tenantId)).isTrue();
            assertThat(availabilityEngine.isDishAvailable(dishBId, tenantId)).isFalse();
        }

        @Test
        @DisplayName("Should simultaneously invalidate all dependent dishes when shared ingredient drops to 0")
        void shouldSimultaneouslyInvalidateWhenSharedStockDepleted() {
            UUID dishAId = UUID.randomUUID();
            UUID dishBId = UUID.randomUUID();
            UUID sharedIngId = UUID.randomUUID();

            RecipeLineWithIngredientProjection lineA = mockRecipeLine(dishAId, sharedIngId, new BigDecimal("1.0000"), "Carne Picada", IngredientStatus.ACTIVE, BigDecimal.ZERO);
            RecipeLineWithIngredientProjection lineB = mockRecipeLine(dishBId, sharedIngId, new BigDecimal("1.0000"), "Carne Picada", IngredientStatus.ACTIVE, BigDecimal.ZERO);

            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(eq(List.of(dishAId)), eq(tenantId)))
                    .thenReturn(List.of(lineA));
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(eq(List.of(dishBId)), eq(tenantId)))
                    .thenReturn(List.of(lineB));

            assertThat(availabilityEngine.isDishAvailable(dishAId, tenantId)).isFalse();
            assertThat(availabilityEngine.isDishAvailable(dishBId, tenantId)).isFalse();
        }

        @Test
        @DisplayName("INV-6, FR-009, SC-011, EC-008: testCriticalStockDoesNotBlockSale - Critical stock status of ingredient does not block dish availability when currentStock > 0")
        void testCriticalStockDoesNotBlockSale() {
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();

            RecipeLineWithIngredientProjection recipeLine = mockRecipeLine(dishId, ingredientId, new BigDecimal("2.0000"), "Harina Especial", IngredientStatus.ACTIVE, new BigDecimal("5.0000"));
            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(recipeLine));

            boolean available = availabilityEngine.isDishAvailable(dishId, tenantId);

            assertThat(available)
                    .as("Dish must remain available when ingredient is in critical stock but current stock satisfies recipe requirement (INV-6, FR-009, SC-011, EC-008)")
                    .isTrue();
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
        @DisplayName("Should return availability map for published active dishes in single batch without N+1")
        void shouldReturnAvailabilityMapForPublishedDishes() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(establishment));

            Dish dish1 = createDish(UUID.randomUUID(), tenantId, "Plato 1");
            Dish dish2 = createDish(UUID.randomUUID(), tenantId, "Plato 2");

            when(dishRepository.findPublishedActiveDishesWithRestrictions(tenantId))
                    .thenReturn(List.of(dish1, dish2));

            UUID ing1Id = UUID.randomUUID();
            RecipeLineWithIngredientProjection line1 = mockRecipeLine(dish1.getId(), ing1Id, new BigDecimal("1.0000"), "Ing 1", IngredientStatus.ACTIVE, new BigDecimal("5.0000"));

            when(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(anyCollection(), eq(tenantId)))
                    .thenReturn(List.of(line1));

            Map<UUID, Boolean> map = availabilityEngine.computeAvailabilityMap(tenantId);

            assertThat(map).hasSize(2);
            assertThat(map.get(dish1.getId())).isTrue();
            assertThat(map.get(dish2.getId())).isFalse();
            verify(dishRecipeIngredientRepository, never()).findByIdDishId(any());
            verify(ingredientRepository, never()).findByIdAndTenantId(any(), any());
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
        void shouldGenerateStableHashForEmptyMap() throws Exception {
            String etag = availabilityEngine.computeETag(Collections.emptyMap());
            assertThat(etag).isEqualTo(expectedSha256("empty"));
        }

        @Test
        @DisplayName("Should compute ETag bit-for-bit deterministically including pausedByChef without findById in loop")
        void shouldComputeETagDeterministicallyWithoutFindByIdInLoop() throws Exception {
            UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
            Dish d1 = createDish(id1, tenantId, "Plato Agotado");
            d1.setPausedByChef(true);

            when(dishRepository.findByIdIn(anyCollection())).thenReturn(List.of(d1));

            String etagPaused = availabilityEngine.computeETag(Map.of(id1, false));
            assertThat(etagPaused).isEqualTo(expectedSha256(id1 + "=false:paused=true;"));

            d1.setPausedByChef(false);
            String etagUnpaused = availabilityEngine.computeETag(Map.of(id1, false));
            assertThat(etagUnpaused).isEqualTo(expectedSha256(id1 + "=false:paused=false;"));
            assertThat(etagPaused).isNotEqualTo(etagUnpaused);

            verify(dishRepository, never()).findById(any());
        }
    }
}
