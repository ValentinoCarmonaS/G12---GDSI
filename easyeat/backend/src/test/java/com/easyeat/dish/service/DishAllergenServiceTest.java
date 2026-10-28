package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.AllergenResponse;
import com.easyeat.dish.dto.AllergenUpdateRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DishAllergenServiceTest — Allergen & Dietary Labeling Unit Tests (T047, CHF-2.2.3)")
class DishAllergenServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishPhotoRepository dishPhotoRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @InjectMocks
    private DishService dishService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private Dish draftDish;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        draftDish = new Dish();
        draftDish.setId(dishId);
        draftDish.setTenantId(tenantId);
        draftDish.setName("Ensalada Mediterránea");
        draftDish.setCategory(DishCategory.ENSALADAS);
        draftDish.setStatus(DishStatus.DRAFT);
        draftDish.setAllergensConfirmed(false);
        draftDish.setRestrictionCodes(new HashSet<>());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Taxonomy Validation and Mapping (FR-013)")
    class TaxonomyValidationTests {

        @Test
        @DisplayName("Should successfully update valid taxonomy codes and persist them on the dish")
        void updateAllergens_shouldPersistValidTaxonomyCodes() {
            List<String> validCodes = List.of("VEGAN", "CELIAC", "DAIRY");
            AllergenUpdateRequest request = new AllergenUpdateRequest(validCodes, true);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dietaryRestrictionRepository.findAllById(validCodes)).thenReturn(List.of(
                    new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Apto vegano"),
                    new DietaryRestriction("CELIAC", "Celíaco", DietaryCategory.DIETARY_LIFESTYLE, "Contiene gluten"),
                    new DietaryRestriction("DAIRY", "Lácteos", DietaryCategory.COMMON_ALLERGEN, "Contiene lácteos")
            ));
            when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AllergenResponse response = dishService.updateAllergens(dishId, request);

            assertThat(response).isNotNull();
            assertThat(response.dishId()).isEqualTo(dishId);
            assertThat(response.allergensConfirmed()).isTrue();
            assertThat(response.restrictionCodes()).containsExactlyInAnyOrder("VEGAN", "CELIAC", "DAIRY");

            ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
            verify(dishRepository).save(dishCaptor.capture());
            Dish saved = dishCaptor.getValue();
            assertThat(saved.getAllergensConfirmed()).isTrue();
            assertThat(saved.getRestrictionCodes()).containsExactlyInAnyOrder("VEGAN", "CELIAC", "DAIRY");
        }

        @Test
        @DisplayName("Should throw DietaryTaxonomyException when unrecognized restriction codes are supplied")
        void updateAllergens_shouldThrowWhenCodeInvalid() {
            List<String> codesWithInvalid = List.of("VEGAN", "INVALID_CODE");
            AllergenUpdateRequest request = new AllergenUpdateRequest(codesWithInvalid, true);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dietaryRestrictionRepository.findAllById(codesWithInvalid)).thenReturn(List.of(
                    new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Apto vegano")
            ));

            assertThatThrownBy(() -> dishService.updateAllergens(dishId, request))
                    .isInstanceOf(DietaryTaxonomyException.class)
                    .hasMessageContaining("INVALID_CODE");

            verify(dishRepository, never()).save(any(Dish.class));
        }
    }

    @Nested
    @DisplayName("Zero-Label Confirmed State (FR-014)")
    class ZeroLabelTests {

        @Test
        @DisplayName("Should allow zero labels as valid state when confirmed explicitly")
        void updateAllergens_shouldAllowZeroLabelsWhenConfirmed() {
            draftDish.setRestrictionCodes(new HashSet<>(Set.of("DAIRY", "PEANUTS")));
            draftDish.setAllergensConfirmed(false);

            AllergenUpdateRequest request = new AllergenUpdateRequest(Collections.emptyList(), true);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AllergenResponse response = dishService.updateAllergens(dishId, request);

            assertThat(response.dishId()).isEqualTo(dishId);
            assertThat(response.allergensConfirmed()).isTrue();
            assertThat(response.restrictionCodes()).isEmpty();

            ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
            verify(dishRepository).save(dishCaptor.capture());
            Dish saved = dishCaptor.getValue();
            assertThat(saved.getAllergensConfirmed()).isTrue();
            assertThat(saved.getRestrictionCodes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Explicit Confirmation Persistence (FR-015)")
    class ConfirmationPersistenceTests {

        @Test
        @DisplayName("Should persist unconfirmed status when confirmed flag is false")
        void updateAllergens_shouldPersistFalseWhenConfirmedIsFalse() {
            List<String> codes = List.of("VEGETARIAN");
            AllergenUpdateRequest request = new AllergenUpdateRequest(codes, false);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));
            when(dietaryRestrictionRepository.findAllById(codes)).thenReturn(List.of(
                    new DietaryRestriction("VEGETARIAN", "Vegetariano", DietaryCategory.DIETARY_LIFESTYLE, "Apto vegetariano")
            ));
            when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AllergenResponse response = dishService.updateAllergens(dishId, request);

            assertThat(response.allergensConfirmed()).isFalse();
            assertThat(response.restrictionCodes()).containsExactly("VEGETARIAN");

            ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
            verify(dishRepository).save(dishCaptor.capture());
            assertThat(dishCaptor.getValue().getAllergensConfirmed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Lock and Immutability Enforcement (FR-020)")
    class LockEnforcementTests {

        @Test
        @DisplayName("Should reject allergen update with DishLockedException when dish status is PENDING_PRICE")
        void updateAllergens_shouldThrowDishLockedException_whenPendingPrice() {
            draftDish.setStatus(DishStatus.PENDING_PRICE);
            AllergenUpdateRequest request = new AllergenUpdateRequest(List.of("CELIAC"), true);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(draftDish));

            assertThatThrownBy(() -> dishService.updateAllergens(dishId, request))
                    .isInstanceOf(DishLockedException.class)
                    .hasMessageContaining("PENDING_PRICE");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when dish does not exist in establishment")
        void updateAllergens_shouldThrowDishNotFoundException_whenNotFound() {
            UUID unknownDishId = UUID.randomUUID();
            AllergenUpdateRequest request = new AllergenUpdateRequest(Collections.emptyList(), true);

            when(dishRepository.findByIdAndTenantId(unknownDishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishService.updateAllergens(unknownDishId, request))
                    .isInstanceOf(DishNotFoundException.class);

            verify(dishRepository, never()).save(any(Dish.class));
        }
    }
}
