package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.dto.DishReviewDetailResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishPublicationGateRepository;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FR-006 (the complete culinary file with the itemized audit), FR-012 (the margin recomputed on
 * every consultation), FR-015 (the gate reported as status rather than enforced), and FR-037 (a
 * dish with no recipe).
 *
 * <p>Two things this suite is careful about. The audit comes from spec 006's service, embedded
 * verbatim and never recomputed here, so the tests assert the embedding rather than the
 * arithmetic — that belongs to spec 006's own suite. And the margin is asserted to be derived on
 * each call rather than read from anywhere, which is the difference FR-012 insists on.
 */
@DisplayName("DishReviewDetailService")
class DishReviewDetailServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final BigDecimal FOOD_COST = new BigDecimal("4500.00");

    private DishAccessGuard accessGuard;
    private FoodCostAuditService foodCostAuditService;
    private DishReviewHistoryRepository reviewHistoryRepository;
    private DishPublicationGateRepository gateRepository;
    private DishPhotoRepository photoRepository;
    private UserRepository userRepository;
    private DishReviewDetailService service;

    private Dish dish;

    @BeforeEach
    void setUp() {
        accessGuard = Mockito.mock(DishAccessGuard.class);
        foodCostAuditService = Mockito.mock(FoodCostAuditService.class);
        reviewHistoryRepository = Mockito.mock(DishReviewHistoryRepository.class);
        gateRepository = Mockito.mock(DishPublicationGateRepository.class);
        photoRepository = Mockito.mock(DishPhotoRepository.class);
        userRepository = Mockito.mock(UserRepository.class);

        service = new DishReviewDetailService(
                foodCostAuditService, reviewHistoryRepository,
                gateRepository, photoRepository, userRepository,
                new RetailPricingCalculator(), accessGuard);

        TenantContextHolder.setTenantId(TENANT);

        dish = new Dish();
        dish.setId(UUID.randomUUID());
        dish.setTenantId(TENANT);
        dish.setName("Risotto de Hongos");
        dish.setDescription("Arborio, portobello y parmesano.");
        dish.setCategory(DishCategory.PLATOS_PRINCIPALES);
        dish.setStatus(DishStatus.PENDING_PRICE);
        dish.setAllergensConfirmed(true);
        dish.setSubmittedAt(Instant.parse("2026-10-01T10:00:00Z"));

        when(accessGuard.requireOwnedByTenant(any(), any())).thenReturn(dish);
        when(foodCostAuditService.auditDishCost(any(Dish.class))).thenReturn(audit(FOOD_COST, false));
        when(reviewHistoryRepository.countByDishIdAndTenantId(any(), any())).thenReturn(0L);
        when(gateRepository.countActiveRecipeLines(any(), any())).thenReturn(3L);
        when(photoRepository.findByDishId(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private FoodCostAuditResponse audit(BigDecimal total, boolean unpriced) {
        return new FoodCostAuditResponse(dish.getId(), dish.getName(), total, unpriced,
                unpriced ? List.of("Insumo sin precio: Parmesano") : List.of(),
                List.of("CELIAC"), List.of());
    }

    private DishReviewDetailResponse detail() {
        return service.getDetail(dish.getId());
    }

    @Nested
    @DisplayName("the culinary file (FR-006)")
    class CulinaryFile {

        @Test
        @DisplayName("identity, status and labels come through, and the audit is embedded verbatim")
        void identityAndEmbeddedAudit() {
            FoodCostAuditResponse stubbed = audit(FOOD_COST, false);
            when(foodCostAuditService.auditDishCost(any(Dish.class))).thenReturn(stubbed);

            DishReviewDetailResponse response = detail();

            assertThat(response.dishId()).isEqualTo(dish.getId());
            assertThat(response.name()).isEqualTo("Risotto de Hongos");
            assertThat(response.description()).isEqualTo("Arborio, portobello y parmesano.");
            assertThat(response.category()).isEqualTo(DishCategory.PLATOS_PRINCIPALES);
            assertThat(response.status()).isEqualTo(DishStatus.PENDING_PRICE);
            assertThat(response.allergensConfirmed()).isTrue();
            assertThat(response.submittedAt()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));

            assertThat(response.foodCostAudit())
                    .as("spec 006 owns the itemized shape; this feature embeds the very object "
                            + "its service returned rather than reshaping it, so a change there "
                            + "needs no change here")
                    .isSameAs(stubbed);
            assertThat(response.foodCostAudit().totalFoodCost()).isEqualByComparingTo(FOOD_COST);
            assertThat(response.foodCostAudit().requiredDietaryRestrictions())
                    .containsExactly("CELIAC");
        }

        @ParameterizedTest
        @EnumSource(value = DishStatus.class, names = {"PENDING_PRICE", "PUBLISHED"})
        @DisplayName("the audit is requested for any status, not only PENDING_PRICE (R-006-1)")
        void auditWorksForEveryReviewableStatus(DishStatus status) {
            dish.setStatus(status);

            detail();

            Mockito.verify(foodCostAuditService).auditDishCost(dish);
        }

        @Test
        @DisplayName("a photo is reported as the path of spec 005's existing endpoint")
        void photoUrlWhenPresent() {
            when(photoRepository.findByDishId(dish.getId()))
                    .thenReturn(Optional.of(new DishPhoto()));

            assertThat(detail().photoUrl())
                    .as("this feature serves no image bytes of its own; it points at the "
                            + "endpoint spec 005 already publishes")
                    .isEqualTo("/api/v1/dishes/" + dish.getId() + "/photo");
        }

        @Test
        @DisplayName("no photo reports null rather than a path that would 404")
        void photoUrlWhenAbsent() {
            assertThat(detail().photoUrl()).isNull();
        }

        @Test
        @DisplayName("an unknown dish is not found")
        void unknownDish() {
            when(accessGuard.requireOwnedByTenant(any(), any()))
                    .thenThrow(new DishNotFoundException(dish.getId()));

            assertThatThrownBy(() -> detail()).isInstanceOf(DishNotFoundException.class);
        }

        @Test
        @DisplayName("a dish belonging to another establishment is forbidden, not merely absent "
                + "(FR-001, SC-003) — spec.md's Edge Cases rule out 404 as an alternative here")
        void crossTenantDishIsForbidden() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new TenantViolationException(TENANT, UUID.randomUUID()));

            assertThatThrownBy(() -> detail()).isInstanceOf(TenantViolationException.class);
        }
    }

    @Nested
    @DisplayName("the margin, recomputed on every consultation (FR-012, FR-008)")
    class Margin {

        @Test
        @DisplayName("a priced dish reports the margin derived from the live Food Cost")
        void marginFromLiveFoodCost() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            var margin = detail().margin();

            assertThat(margin.foodCost()).isEqualByComparingTo(FOOD_COST);
            assertThat(margin.retailPrice()).isEqualByComparingTo("11250.00");
            assertThat(margin.marginPct())
                    .as("(11250 - 4500) / 11250 * 100 at scale 4")
                    .isEqualByComparingTo("60.0000");
        }

        @Test
        @DisplayName("the margin is derived on each call, never carried on the dish")
        void marginIsNeverStored() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            detail();

            when(foodCostAuditService.auditDishCost(any(Dish.class)))
                    .thenReturn(audit(new BigDecimal("5625.00"), false));

            assertThat(detail().margin().marginPct())
                    .as("FR-012: the same price against a moved Food Cost must read differently")
                    .isEqualByComparingTo("50.0000");
        }

        @Test
        @DisplayName("an unpriced dish reports a null margin, not a zero one")
        void unpricedDishHasNoMargin() {
            var margin = detail().margin();

            assertThat(margin.retailPrice()).isNull();
            assertThat(margin.marginPct())
                    .as("zero would claim the dish breaks even, which is a different statement "
                            + "from 'not yet priced'")
                    .isNull();
            assertThat(margin.marginTolerancePp()).isNull();
            assertThat(margin.toleranceThresholdPrice()).isNull();
        }

        @Test
        @DisplayName("the disclosure values of FR-008 ride along when a price exists")
        void toleranceDisclosure() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            var margin = detail().margin();

            assertThat(margin.toleranceThresholdPrice())
                    .as("50 * (1 - 60/100)")
                    .isEqualByComparingTo("20.00");
            assertThat(margin.marginTolerancePp())
                    .as("0.5 * 4500 / (11250 * 11249.995), effectively nil at this price")
                    .isNotNull();
        }

        @ParameterizedTest
        @CsvSource({"4500.00, true", "0.00, false"})
        @DisplayName("marginDerivable is false exactly when the Food Cost is zero (FR-007)")
        void marginDerivableFlag(String foodCost, boolean derivable) {
            when(foodCostAuditService.auditDishCost(any(Dish.class)))
                    .thenReturn(audit(new BigDecimal(foodCost), false));

            assertThat(detail().margin().marginDerivable()).isEqualTo(derivable);
        }

        @Test
        @DisplayName("a price below the Food Cost reports a negative margin rather than hiding it")
        void negativeMarginIsReported() {
            dish.setRetailPrice(new BigDecimal("4000.00"));

            assertThat(detail().margin().marginPct()).isNegative();
        }

        @Test
        @DisplayName("the unpriced-ingredient flag is carried from the audit, not recomputed")
        void unpricedFlagComesFromTheAudit() {
            when(foodCostAuditService.auditDishCost(any(Dish.class)))
                    .thenReturn(audit(FOOD_COST, true));

            assertThat(detail().margin().hasUnpricedIngredients()).isTrue();
        }
    }

    @Nested
    @DisplayName("the publication gate, reported as status (FR-015)")
    class Gate {

        @Test
        @DisplayName("all three conditions met reports satisfied with no unmet condition")
        void gateSatisfied() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            var gate = detail().publicationGate();

            assertThat(gate.satisfied()).isTrue();
            assertThat(gate.unmetConditions()).isEmpty();
        }

        @Test
        @DisplayName("a missing price is reported as RETAIL_PRICE_MISSING")
        void missingPrice() {
            var gate = detail().publicationGate();

            assertThat(gate.satisfied()).isFalse();
            assertThat(gate.unmetConditions())
                    .contains(PublicationGateCondition.RETAIL_PRICE_MISSING);
        }

        @Test
        @DisplayName("unconfirmed allergens are reported as ALLERGENS_UNCONFIRMED")
        void unconfirmedAllergens() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            dish.setAllergensConfirmed(false);

            assertThat(detail().publicationGate().unmetConditions())
                    .contains(PublicationGateCondition.ALLERGENS_UNCONFIRMED);
        }

        @Test
        @DisplayName("condition 2 counts ACTIVE recipe lines, not lines in the audit payload")
        void condition2UsesTheActiveLineCount() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(0L);

            assertThat(detail().publicationGate().unmetConditions())
                    .as("FR-015 condition 2 asks whether the dish can be produced today. The "
                            + "audit payload carries no ingredient lifecycle state and includes "
                            + "discontinued lines in its total, so the two cannot be derived "
                            + "from one another")
                    .contains(PublicationGateCondition.EMPTY_RECIPE);
        }

        @Test
        @DisplayName("a recipe of only discontinued ingredients fails the gate with a real cost")
        void discontinuedOnlyRecipeStillHasACost() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(0L);

            DishReviewDetailResponse response = detail();

            assertThat(response.publicationGate().unmetConditions())
                    .contains(PublicationGateCondition.EMPTY_RECIPE);
            assertThat(response.foodCostAudit().totalFoodCost())
                    .as("the cost projects the recipe as written, which is why it is non-zero "
                            + "while the gate is unmet")
                    .isEqualByComparingTo(FOOD_COST);
        }

        @Test
        @DisplayName("every unmet condition is reported at once, not the first one found")
        void allUnmetConditionsAtOnce() {
            dish.setAllergensConfirmed(false);
            when(gateRepository.countActiveRecipeLines(any(), any())).thenReturn(0L);

            assertThat(detail().publicationGate().unmetConditions())
                    .containsExactlyInAnyOrder(
                            PublicationGateCondition.RETAIL_PRICE_MISSING,
                            PublicationGateCondition.EMPTY_RECIPE,
                            PublicationGateCondition.ALLERGENS_UNCONFIRMED);
        }

        @Test
        @DisplayName("unpriced ingredients require an acknowledgement, without blocking the gate")
        void unpricedIngredientsRequireAcknowledgement() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            when(foodCostAuditService.auditDishCost(any(Dish.class)))
                    .thenReturn(audit(FOOD_COST, true));

            var gate = detail().publicationGate();

            assertThat(gate.satisfied())
                    .as("FR-017: an advisory is an additional precondition, never a gate failure")
                    .isTrue();
            assertThat(gate.requiredAcknowledgements())
                    .contains(AcknowledgementKind.UNPRICED_INGREDIENTS);
        }

        @Test
        @DisplayName("a negative margin requires an acknowledgement, also without blocking")
        void negativeMarginRequiresAcknowledgement() {
            dish.setRetailPrice(new BigDecimal("4000.00"));

            var gate = detail().publicationGate();

            assertThat(gate.satisfied()).isTrue();
            assertThat(gate.requiredAcknowledgements())
                    .contains(AcknowledgementKind.NEGATIVE_MARGIN);
        }

        @Test
        @DisplayName("a healthy priced dish needs no acknowledgement at all")
        void noAcknowledgementsWhenNothingIsAdvisory() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            assertThat(detail().publicationGate().requiredAcknowledgements()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a dish with no recipe lines (FR-037)")
    class EmptyRecipe {

        @BeforeEach
        void recipeIsEmpty() {
            when(foodCostAuditService.auditDishCost(any(Dish.class)))
                    .thenThrow(new EmptyRecipeException(dish.getId()));
            when(gateRepository.countActiveRecipeLines(any(), any())).thenReturn(0L);
        }

        @Test
        @DisplayName("the consultation succeeds: an absent recipe is a fact, not a fault")
        void consultationDoesNotFail() {
            DishReviewDetailResponse response = detail();

            assertThat(response.dishId()).isEqualTo(dish.getId());
            assertThat(response.name()).isEqualTo("Risotto de Hongos");
            assertThat(response.status()).isEqualTo(DishStatus.PENDING_PRICE);
        }

        @Test
        @DisplayName("no itemization is offered, and the flag says why rather than leaving a gap")
        void noItemization() {
            DishReviewDetailResponse response = detail();

            assertThat(response.foodCostAudit()).isNull();
            assertThat(response.recipeAbsent())
                    .as("a null audit alone would be indistinguishable from a failed lookup")
                    .isTrue();
        }

        @Test
        @DisplayName("no margin is claimed, because there is no Food Cost to derive one from")
        void noMargin() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            var margin = detail().margin();

            assertThat(margin.foodCost()).isNull();
            assertThat(margin.marginPct()).isNull();
            assertThat(margin.marginDerivable()).isFalse();
            assertThat(margin.retailPrice())
                    .as("the assigned price is still reported: it is a fact about the dish")
                    .isEqualByComparingTo("11250.00");
        }

        @Test
        @DisplayName("the gate reports condition 2 unmet, which is what blocks publication")
        void gateReportsEmptyRecipe() {
            dish.setRetailPrice(new BigDecimal("11250.00"));

            assertThat(detail().publicationGate().unmetConditions())
                    .contains(PublicationGateCondition.EMPTY_RECIPE);
            assertThat(detail().publicationGate().satisfied()).isFalse();
        }

        @Test
        @DisplayName("identity, author and review history are all still rendered")
        void everythingElseStillRenders() {
            UUID authorId = UUID.randomUUID();
            dish.setCreatedBy(authorId);
            User author = new User();
            author.setId(authorId);
            author.setFullName("Camila Duarte");
            author.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
            when(reviewHistoryRepository.countByDishIdAndTenantId(any(), any())).thenReturn(2L);

            DishReviewDetailResponse response = detail();

            assertThat(response.authorName()).isEqualTo("Camila Duarte");
            assertThat(response.reviewCycleCount()).isEqualTo(2);
            assertThat(response.isResubmission()).isTrue();
        }
    }

    @Nested
    @DisplayName("author attribution and review cycles")
    class Attribution {

        @Test
        @DisplayName("no recorded author reports null, and the interface supplies the words")
        void noAuthor() {
            assertThat(detail().authorName()).isNull();
            assertThat(detail().authorDeactivated()).isFalse();
        }

        @Test
        @DisplayName("a suspended author is flagged and the dish stays actionable (FR-026)")
        void suspendedAuthor() {
            UUID authorId = UUID.randomUUID();
            dish.setCreatedBy(authorId);
            User author = new User();
            author.setId(authorId);
            author.setFullName("Camila Duarte");
            author.setStatus(UserStatus.SUSPENDED);
            when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

            assertThat(detail().authorName()).isEqualTo("Camila Duarte");
            assertThat(detail().authorDeactivated()).isTrue();
        }

        @Test
        @DisplayName("a dangling author reference degrades to null instead of throwing")
        void danglingAuthor() {
            dish.setCreatedBy(UUID.randomUUID());

            assertThat(detail().authorName()).isNull();
        }

        @ParameterizedTest
        @CsvSource({"0, false", "1, true", "3, true"})
        @DisplayName("the resubmission flag follows the review cycle count")
        void resubmissionFollowsCycles(long cycles, boolean isResubmission) {
            when(reviewHistoryRepository.countByDishIdAndTenantId(any(), any()))
                    .thenReturn(cycles);

            DishReviewDetailResponse response = detail();

            assertThat(response.reviewCycleCount()).isEqualTo((int) cycles);
            assertThat(response.isResubmission()).isEqualTo(isResubmission);
        }
    }
}
