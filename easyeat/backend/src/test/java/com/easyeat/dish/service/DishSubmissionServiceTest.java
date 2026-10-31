package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.IncompleteDishException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.repository.AdminNotificationRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DishSubmissionServiceTest — Submission for Review & Immutability Unit Tests (T057, CHF-2.2.4)")
class DishSubmissionServiceTest {

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

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @InjectMocks
    private DishService dishService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;
    private final UUID chefId = UUID.randomUUID();
    private final UUID ingredientId1 = UUID.randomUUID();
    private final UUID ingredientId2 = UUID.randomUUID();

    private Dish completeDraft;
    private Ingredient activeIngredient1;
    private Ingredient activeIngredient2;
    private List<DishRecipeIngredient> recipeLines;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);

        completeDraft = new Dish();
        completeDraft.setId(dishId);
        completeDraft.setTenantId(tenantId);
        completeDraft.setName("Risotto al Tartufo");
        completeDraft.setCategory(DishCategory.PLATOS_PRINCIPALES);
        completeDraft.setDescription("Exquisito risotto.");
        completeDraft.setStatus(DishStatus.DRAFT);
        completeDraft.setAllergensConfirmed(true);
        completeDraft.setCreatedBy(chefId);
        completeDraft.setRestrictionCodes(new HashSet<>(List.of("VEGETARIAN", "DAIRY")));

        activeIngredient1 = new Ingredient(tenantId, "Arroz Carnaroli", com.easyeat.inventory.dto.IngredientCategory.DRY_GOODS, UnitOfMeasure.G, new BigDecimal("100.00"));
        activeIngredient1.setId(ingredientId1);
        activeIngredient1.setStatus(IngredientStatus.ACTIVE);

        activeIngredient2 = new Ingredient(tenantId, "Trufa Negra", com.easyeat.inventory.dto.IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("20.00"));
        activeIngredient2.setId(ingredientId2);
        activeIngredient2.setStatus(IngredientStatus.ACTIVE);

        recipeLines = List.of(
                new DishRecipeIngredient(dishId, ingredientId1, new BigDecimal("200.00")),
                new DishRecipeIngredient(dishId, ingredientId2, new BigDecimal("15.00"))
        );
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Completeness Gate Verification")
    class CompletenessGateTests {

        @Test
        @DisplayName("Should throw DishNotFoundException when dish does not exist for tenant")
        void submitForReview_shouldThrowNotFound_whenDishDoesNotExist() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(DishNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw DishLockedException when dish is already in PENDING_PRICE")
        void submitForReview_shouldThrowLocked_whenAlreadyPendingPrice() {
            completeDraft.setStatus(DishStatus.PENDING_PRICE);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(DishLockedException.class);
        }

        @Test
        @DisplayName("Should throw IncompleteDishException when name or category is missing")
        void submitForReview_shouldThrowIncomplete_whenNameOrCategoryMissing() {
            completeDraft.setName("   ");
            completeDraft.setCategory(null);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(recipeLines);
            when(ingredientRepository.findByIdAndTenantId(ingredientId1, tenantId)).thenReturn(Optional.of(activeIngredient1));
            when(ingredientRepository.findByIdAndTenantId(ingredientId2, tenantId)).thenReturn(Optional.of(activeIngredient2));

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("nombre"));
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("categoría") || c.toLowerCase().contains("categoria"));
                    });
        }

        @Test
        @DisplayName("Should throw IncompleteDishException when recipe has zero ingredients")
        void submitForReview_shouldThrowIncomplete_whenRecipeIsEmpty() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("receta"));
                    });
        }

        @Test
        @DisplayName("Should throw IncompleteDishException when recipe contains ingredient with non-positive quantity")
        void submitForReview_shouldThrowIncomplete_whenRecipeHasNonPositiveQuantity() {
            List<DishRecipeIngredient> invalidLines = List.of(
                    new DishRecipeIngredient(dishId, ingredientId1, BigDecimal.ZERO)
            );
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(invalidLines);
            when(ingredientRepository.findByIdAndTenantId(ingredientId1, tenantId)).thenReturn(Optional.of(activeIngredient1));

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("cantidad"));
                    });
        }

        @Test
        @DisplayName("Should throw IncompleteDishException when recipe contains an inactive or discontinued ingredient")
        void submitForReview_shouldThrowIncomplete_whenIngredientInactive() {
            activeIngredient2.setStatus(IngredientStatus.DISCONTINUED);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(recipeLines);
            when(ingredientRepository.findByIdAndTenantId(ingredientId1, tenantId)).thenReturn(Optional.of(activeIngredient1));
            when(ingredientRepository.findByIdAndTenantId(ingredientId2, tenantId)).thenReturn(Optional.of(activeIngredient2));

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("activo") || c.toLowerCase().contains("discontinuado"));
                    });
        }

        @Test
        @DisplayName("Should throw IncompleteDishException when allergen section is not confirmed")
        void submitForReview_shouldThrowIncomplete_whenAllergensNotConfirmed() {
            completeDraft.setAllergensConfirmed(false);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(recipeLines);
            when(ingredientRepository.findByIdAndTenantId(ingredientId1, tenantId)).thenReturn(Optional.of(activeIngredient1));
            when(ingredientRepository.findByIdAndTenantId(ingredientId2, tenantId)).thenReturn(Optional.of(activeIngredient2));

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions()).anyMatch(c -> c.toLowerCase().contains("alérgeno") || c.toLowerCase().contains("alergeno") || c.toLowerCase().contains("confirm"));
                    });
        }

        @Test
        @DisplayName("Should collect multiple unmet completeness conditions simultaneously")
        void submitForReview_shouldCollectAllUnmetConditions() {
            completeDraft.setName("");
            completeDraft.setAllergensConfirmed(false);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> dishService.submitForReview(dishId))
                    .isInstanceOf(IncompleteDishException.class)
                    .satisfies(ex -> {
                        IncompleteDishException ide = (IncompleteDishException) ex;
                        assertThat(ide.getMissingConditions().size()).isGreaterThanOrEqualTo(3);
                    });
        }
    }

    @Nested
    @DisplayName("Successful Submission & Transition to PENDING_PRICE")
    class SuccessfulSubmissionTests {

        @Test
        @DisplayName("Should atomically transition dish to PENDING_PRICE, set submittedAt, and dispatch admin notification")
        void submitForReview_shouldTransitionAndNotify_whenAllConditionsPass() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
            when(dishRecipeIngredientRepository.findByIdDishId(dishId)).thenReturn(recipeLines);
            when(ingredientRepository.findByIdAndTenantId(ingredientId1, tenantId)).thenReturn(Optional.of(activeIngredient1));
            when(ingredientRepository.findByIdAndTenantId(ingredientId2, tenantId)).thenReturn(Optional.of(activeIngredient2));
            when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> invocation.getArgument(0));

            DishDetailResponse response = dishService.submitForReview(dishId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(dishId);
            assertThat(response.status()).isEqualTo(DishStatus.PENDING_PRICE);

            // Verify dish entity transition
            ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
            verify(dishRepository).save(dishCaptor.capture());
            Dish savedDish = dishCaptor.getValue();
            assertThat(savedDish.getStatus()).isEqualTo(DishStatus.PENDING_PRICE);
            assertThat(savedDish.getSubmittedAt()).isNotNull();

            // Verify admin notification generation
            ArgumentCaptor<AdminNotification> notifCaptor = ArgumentCaptor.forClass(AdminNotification.class);
            verify(adminNotificationRepository).save(notifCaptor.capture());
            AdminNotification notification = notifCaptor.getValue();
            assertThat(notification.getTenantId()).isEqualTo(tenantId);
            assertThat(notification.getType()).isEqualTo(NotificationType.DISH_SUBMITTED_FOR_REVIEW);
            assertThat(notification.getTitle()).contains("revisión");
            assertThat(notification.getMessage()).contains("Risotto al Tartufo");
            assertThat(notification.getDishId()).isEqualTo(dishId);
            assertThat(notification.getChefId()).isEqualTo(chefId);
            assertThat(notification.isRead()).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability & Mutation Lock Verification on Locked Dish")
    class ImmutabilityLockTests {

        @BeforeEach
        void lockDish() {
            completeDraft.setStatus(DishStatus.PENDING_PRICE);
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(completeDraft));
        }

        @Test
        @DisplayName("Should reject basic info update when dish is locked")
        void updateBasicInfo_shouldThrowLocked_whenPendingPrice() {
            assertThatThrownBy(() -> dishService.updateBasicInfo(dishId, new com.easyeat.dish.dto.UpdateDishRequest("Nuevo Nombre", DishCategory.POSTRES, "Desc")))
                    .isInstanceOf(DishLockedException.class);
        }

        @Test
        @DisplayName("Should reject draft deletion when dish is locked")
        void deleteDraft_shouldThrowLocked_whenPendingPrice() {
            assertThatThrownBy(() -> dishService.deleteDraft(dishId))
                    .isInstanceOf(DishLockedException.class);
        }

        @Test
        @DisplayName("Should reject recipe update when dish is locked")
        void updateRecipe_shouldThrowLocked_whenPendingPrice() {
            assertThatThrownBy(() -> dishService.updateRecipe(dishId, new com.easyeat.dish.dto.RecipeUpdateRequest(List.of())))
                    .isInstanceOf(DishLockedException.class);
        }

        @Test
        @DisplayName("Should reject allergen update when dish is locked")
        void updateAllergens_shouldThrowLocked_whenPendingPrice() {
            assertThatThrownBy(() -> dishService.updateAllergens(dishId, new com.easyeat.dish.dto.AllergenUpdateRequest(List.of("CELIAC"), true)))
                    .isInstanceOf(DishLockedException.class);
        }
    }
}
