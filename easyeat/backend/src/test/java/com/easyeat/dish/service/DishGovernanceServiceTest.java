package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.domain.ReviewEventType;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.DishRejectionResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishAlreadyDecidedException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.exception.InvalidRejectionCommentException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.PublicationGateException;
import com.easyeat.dish.exception.StaleDishPriceException;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
import com.easyeat.dish.repository.DishPublicationGateRepository;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DishGovernanceService}'s price-save (FR-009, FR-010, FR-011, FR-013, FR-014, FR-025,
 * SC-008) and publication-gate/approval (FR-015 through FR-019, FR-025, FR-033, SC-001) portions.
 * Rejection is added to this suite by US4, which is why the class is created here rather than per
 * story (same posture as {@code DishReviewDetailServiceTest} and the controller it backs).
 */
@DisplayName("DishGovernanceService")
class DishGovernanceServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final BigDecimal FOOD_COST = new BigDecimal("4500.00");

    private DishRepository dishRepository;
    private DishPriceHistoryRepository priceHistoryRepository;
    private FoodCostAuditService foodCostAuditService;
    private UserRepository userRepository;
    private DishPublicationGateRepository gateRepository;
    private DishReviewHistoryRepository reviewHistoryRepository;
    private DishAccessGuard accessGuard;
    private DishGovernanceService service;

    private Dish dish;

    @BeforeEach
    void setUp() {
        dishRepository = Mockito.mock(DishRepository.class);
        priceHistoryRepository = Mockito.mock(DishPriceHistoryRepository.class);
        foodCostAuditService = Mockito.mock(FoodCostAuditService.class);
        userRepository = Mockito.mock(UserRepository.class);
        gateRepository = Mockito.mock(DishPublicationGateRepository.class);
        reviewHistoryRepository = Mockito.mock(DishReviewHistoryRepository.class);
        accessGuard = Mockito.mock(DishAccessGuard.class);

        service = new DishGovernanceService(dishRepository, priceHistoryRepository,
                foodCostAuditService, new RetailPricingCalculator(), userRepository,
                gateRepository, reviewHistoryRepository, accessGuard);

        TenantContextHolder.setTenantId(TENANT);

        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setEmail("admin@easyeat.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        dish = new Dish();
        dish.setId(UUID.randomUUID());
        dish.setTenantId(TENANT);
        dish.setName("Risotto de Hongos");
        dish.setStatus(DishStatus.PENDING_PRICE);

        when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT)).thenReturn(dish);
        // Still needed for the stale-price re-fetch inside setRetailPrice, which reads the same
        // dish's current price after a lost compare-and-set rather than re-checking ownership.
        when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                .thenReturn(Optional.of(dish));
        when(foodCostAuditService.auditDishCost(dish))
                .thenReturn(audit(FOOD_COST, false));
        when(priceHistoryRepository.save(any(DishPriceHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Gate satisfied by default (FR-015): a positive price, at least one active recipe
        // line and confirmed allergens. Individual approval tests narrow these to exercise one
        // failing condition at a time.
        dish.setRetailPrice(new BigDecimal("11250.00"));
        dish.setAllergensConfirmed(true);
        when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(1L);
        when(reviewHistoryRepository.findMaxCycleNumberByDishId(dish.getId()))
                .thenReturn(Optional.empty());
        when(reviewHistoryRepository.save(any(DishReviewHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static FoodCostAuditResponse audit(BigDecimal totalFoodCost, boolean hasUnpriced) {
        return new FoodCostAuditResponse(UUID.randomUUID(), "Risotto de Hongos", totalFoodCost,
                hasUnpriced, List.of(), List.of(), List.of());
    }

    @Nested
    @DisplayName("permitted states (FR-009)")
    class PermittedStates {

        @Test
        @DisplayName("saving is permitted on a PENDING_PRICE dish")
        void savesWhenPending() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false);

            assertThat(response.status()).isEqualTo(DishStatus.PENDING_PRICE);
        }

        @Test
        @DisplayName("saving is permitted on a PUBLISHED dish, which keeps its status, recipe "
                + "and visibility (FR-011)")
        void savesWhenPublishedWithoutAlteringItsOtherAttributes() {
            dish.setStatus(DishStatus.PUBLISHED);
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT,
                    new BigDecimal("11250.00"), new BigDecimal("13000.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("13000.00"), new BigDecimal("11250.00"), false);

            assertThat(response.status()).isEqualTo(DishStatus.PUBLISHED);
            // FR-011: the conditional UPDATE names only retail_price, and this service holds no
            // reference to a recipe or visibility repository at all — there is nothing through
            // which it could touch either, which is the guarantee structurally rather than by a
            // runtime check.
            verify(dishRepository).updateRetailPriceIfUnchanged(dish.getId(), TENANT,
                    new BigDecimal("11250.00"), new BigDecimal("13000.00"));
        }

        @Test
        @DisplayName("a dish in DRAFT cannot be priced (409 dish-invalid-state)")
        void draftRejected() {
            dish.setStatus(DishStatus.DRAFT);

            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false))
                    .isInstanceOf(DishStateConflictException.class)
                    .satisfies(ex -> {
                        DishStateConflictException conflict = (DishStateConflictException) ex;
                        assertThat(conflict.getCurrentStatus()).isEqualTo("DRAFT");
                    });
        }
    }

    @Nested
    @DisplayName("lookup failures (FR-001, SC-003)")
    class LookupFailures {

        @Test
        @DisplayName("a dish that does not exist anywhere is 404")
        void unknownDish() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new DishNotFoundException(dish.getId()));

            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false))
                    .isInstanceOf(DishNotFoundException.class);
        }

        @Test
        @DisplayName("a dish belonging to another establishment is forbidden, not merely absent "
                + "— spec.md's Edge Cases rule out 404 as an alternative here")
        void crossTenantDishIsForbidden() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new TenantViolationException(TENANT, UUID.randomUUID()));

            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false))
                    .isInstanceOf(TenantViolationException.class);
        }
    }

    @Nested
    @DisplayName("price validation (FR-013)")
    class PriceValidation {

        @Test
        @DisplayName("zero is rejected")
        void zeroRejected() {
            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(), BigDecimal.ZERO, null,
                    false))
                    .isInstanceOf(InvalidRetailPriceException.class);
        }

        @Test
        @DisplayName("a negative price is rejected")
        void negativeRejected() {
            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("-1.00"), null, false))
                    .isInstanceOf(InvalidRetailPriceException.class);
        }

        @Test
        @DisplayName("more than two decimals is rejected")
        void excessScaleRejected() {
            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.123"), null, false))
                    .isInstanceOf(InvalidRetailPriceException.class);
        }

        @Test
        @DisplayName("a null price is rejected rather than reaching the repository")
        void nullRejected() {
            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(), null, null, false))
                    .isInstanceOf(InvalidRetailPriceException.class);
            verifyNoInteractions(dishRepository);
        }
    }

    @Nested
    @DisplayName("optimistic concurrency (FR-025)")
    class Concurrency {

        @Test
        @DisplayName("a mismatched expectedRetailPrice is reported as the prevailing price, "
                + "never silently overwritten")
        void staleExpectedPriceReported() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT,
                    new BigDecimal("10800.00"), new BigDecimal("12000.00"))).thenReturn(0);
            dish.setRetailPrice(new BigDecimal("11250.00"));
            when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                    .thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("12000.00"), new BigDecimal("10800.00"), false))
                    .isInstanceOf(StaleDishPriceException.class)
                    .satisfies(ex -> {
                        StaleDishPriceException stale = (StaleDishPriceException) ex;
                        assertThat(stale.getCurrentRetailPrice())
                                .isEqualTo(new BigDecimal("11250.00"));
                    });

            verifyNoInteractions(priceHistoryRepository);
        }

        @Test
        @DisplayName("the first pricing of a dish compares against a null expected price")
        void firstPricingComparesAgainstNull() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            service.setRetailPrice(dish.getId(), new BigDecimal("11250.00"), null, false);

            verify(dishRepository).updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"));
        }
    }

    @Nested
    @DisplayName("the negative-margin confirmation (FR-014)")
    class NegativeMarginConfirmation {

        @Test
        @DisplayName("a price below the Food Cost without acknowledgement is blocked, and the "
                + "write never happens")
        void blockedWithoutAcknowledgement() {
            assertThatThrownBy(() -> service.setRetailPrice(dish.getId(),
                    new BigDecimal("100.00"), null, false))
                    .isInstanceOf(AcknowledgementRequiredException.class);

            verifyNoInteractions(priceHistoryRepository);
        }

        @Test
        @DisplayName("acknowledging it lets the save proceed")
        void proceedsWhenAcknowledged() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("100.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("100.00"), null, true);

            assertThat(response.margin().marginPct()).isNegative();
        }

        @Test
        @DisplayName("a price at or above the Food Cost needs no acknowledgement")
        void positiveMarginNeedsNoAcknowledgement() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false);

            assertThat(response.margin().marginPct()).isPositive();
        }
    }

    @Nested
    @DisplayName("the immutable audit row (FR-010, SC-008)")
    class AuditRow {

        @Test
        @DisplayName("captures actor, previous price, new price and the prevailing Food Cost")
        void capturesAuditFields() {
            dish.setRetailPrice(new BigDecimal("10800.00"));
            when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                    .thenReturn(Optional.of(dish));
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT,
                    new BigDecimal("10800.00"), new BigDecimal("11250.00"))).thenReturn(1);

            service.setRetailPrice(dish.getId(), new BigDecimal("11250.00"),
                    new BigDecimal("10800.00"), false);

            ArgumentCaptor<DishPriceHistory> captor = ArgumentCaptor.forClass(DishPriceHistory.class);
            verify(priceHistoryRepository).save(captor.capture());
            DishPriceHistory entry = captor.getValue();

            assertThat(entry.getDishId()).isEqualTo(dish.getId());
            assertThat(entry.getTenantId()).isEqualTo(TENANT);
            assertThat(entry.getAdminId()).isEqualTo(ADMIN_ID);
            assertThat(entry.getPreviousPrice()).isEqualTo(new BigDecimal("10800.00"));
            assertThat(entry.getNewPrice()).isEqualTo(new BigDecimal("11250.00"));
            assertThat(entry.getFoodCostSnapshot()).isEqualTo(FOOD_COST);
        }

        @Test
        @DisplayName("the first pricing of a dish records a null previous price, a fact rather "
                + "than an omission")
        void firstPricingRecordsNullPrevious() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            service.setRetailPrice(dish.getId(), new BigDecimal("11250.00"), null, false);

            ArgumentCaptor<DishPriceHistory> captor = ArgumentCaptor.forClass(DishPriceHistory.class);
            verify(priceHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getPreviousPrice()).isNull();
        }

        @Test
        @DisplayName("the identifier of the written row is returned so the response can "
                + "reference it")
        void returnsTheWrittenEntryId() {
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false);

            ArgumentCaptor<DishPriceHistory> captor = ArgumentCaptor.forClass(DishPriceHistory.class);
            verify(priceHistoryRepository).save(captor.capture());
            assertThat(response.priceHistoryEntryId()).isEqualTo(captor.getValue().getId());
        }
    }

    @Nested
    @DisplayName("a dish with no recipe (FR-037's parity for the price-save path)")
    class EmptyRecipe {

        @Test
        @DisplayName("the save still succeeds, treating the Food Cost as unavailable rather than "
                + "failing the request")
        void stillSavesWithoutAnItemizableCost() {
            when(foodCostAuditService.auditDishCost(dish))
                    .thenThrow(new EmptyRecipeException(dish.getId()));
            when(dishRepository.updateRetailPriceIfUnchanged(dish.getId(), TENANT, null,
                    new BigDecimal("11250.00"))).thenReturn(1);

            DishPricingResponse response = service.setRetailPrice(dish.getId(),
                    new BigDecimal("11250.00"), null, false);

            assertThat(response.margin().foodCost()).isNull();
            assertThat(response.margin().marginPct()).isNull();
            assertThat(response.margin().marginDerivable()).isFalse();
        }
    }

    @Nested
    @DisplayName("approve: the publication gate (FR-015, FR-016, FR-019)")
    class Approval {

        @Test
        @DisplayName("a dish satisfying all three conditions transitions to PUBLISHED and writes "
                + "the approval audit row")
        void approvesWhenGateSatisfied() {
            when(dishRepository.updateApprovalIfPending(eq(dish.getId()), eq(TENANT), any(Instant.class)))
                    .thenReturn(1);

            DishApprovalResponse response = service.approve(dish.getId(), false, false);

            assertThat(response.status()).isEqualTo(DishStatus.PUBLISHED);
            assertThat(response.alreadyPublished()).isFalse();

            ArgumentCaptor<DishReviewHistory> captor = ArgumentCaptor.forClass(DishReviewHistory.class);
            verify(reviewHistoryRepository).save(captor.capture());
            DishReviewHistory entry = captor.getValue();
            // Echoes back whatever id the write produced, same as DishPricingResponse does for
            // the price-history row (T067) — the mocked repository never generates one, so this
            // asserts equality with the written entry rather than non-nullity.
            assertThat(response.reviewHistoryEntryId()).isEqualTo(entry.getId());
            assertThat(entry.getEventType()).isEqualTo(ReviewEventType.APPROVED);
            assertThat(entry.getAdminId()).isEqualTo(ADMIN_ID);
            assertThat(entry.getDishId()).isEqualTo(dish.getId());
            assertThat(entry.getCycleNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("a dish that does not exist anywhere is 404")
        void unknownDish() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new DishNotFoundException(dish.getId()));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(DishNotFoundException.class);
        }

        @Test
        @DisplayName("a dish belonging to another establishment is forbidden, not merely absent "
                + "(FR-001, SC-003) — spec.md's Edge Cases rule out 404 as an alternative here")
        void crossTenantDishIsForbidden() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new TenantViolationException(TENANT, UUID.randomUUID()));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(TenantViolationException.class);
        }

        @Test
        @DisplayName("the cycle number is the dish's highest recorded cycle plus one, not "
                + "hardcoded to one")
        void cycleNumberIsMaxPlusOne() {
            when(reviewHistoryRepository.findMaxCycleNumberByDishId(dish.getId()))
                    .thenReturn(Optional.of(2));
            when(dishRepository.updateApprovalIfPending(eq(dish.getId()), eq(TENANT), any(Instant.class)))
                    .thenReturn(1);

            service.approve(dish.getId(), false, false);

            ArgumentCaptor<DishReviewHistory> captor = ArgumentCaptor.forClass(DishReviewHistory.class);
            verify(reviewHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getCycleNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("approving an already-PUBLISHED dish is idempotent: 200, no status change, "
                + "no new audit row, the original publishedAt")
        void idempotentOnAlreadyPublished() {
            Instant originalPublishedAt = Instant.parse("2026-10-20T10:00:00Z");
            dish.setStatus(DishStatus.PUBLISHED);
            dish.setPublishedAt(originalPublishedAt);

            DishApprovalResponse response = service.approve(dish.getId(), false, false);

            assertThat(response.status()).isEqualTo(DishStatus.PUBLISHED);
            assertThat(response.alreadyPublished()).isTrue();
            assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
            assertThat(response.reviewHistoryEntryId()).isNull();
            verifyNoInteractions(reviewHistoryRepository);
            verify(dishRepository, never()).updateApprovalIfPending(any(), any(), any());
        }

        @Test
        @DisplayName("approving a dish in DRAFT is refused (FR-019)")
        void draftRejected() {
            dish.setStatus(DishStatus.DRAFT);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(DishStateConflictException.class);
            verifyNoInteractions(reviewHistoryRepository);
        }

        @Test
        @DisplayName("a missing retail price blocks the gate, naming RETAIL_PRICE_MISSING")
        void missingPriceBlocksGate() {
            dish.setRetailPrice(null);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class)
                    .satisfies(ex -> assertThat(((PublicationGateException) ex).getUnmetConditions())
                            .containsExactly(PublicationGateCondition.RETAIL_PRICE_MISSING));
        }

        @Test
        @DisplayName("no recipe lines at all blocks the gate, naming EMPTY_RECIPE")
        void noRecipeLinesBlocksGate() {
            when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(0L);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class)
                    .satisfies(ex -> assertThat(((PublicationGateException) ex).getUnmetConditions())
                            .containsExactly(PublicationGateCondition.EMPTY_RECIPE));
        }

        @Test
        @DisplayName("a recipe of only DISCONTINUED ingredients blocks the gate while the audit "
                + "still reports a non-zero Food Cost (T076a)")
        void discontinuedOnlyRecipeBlocksGateDespiteNonZeroCost() {
            when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(0L);
            when(foodCostAuditService.auditDishCost(dish)).thenReturn(audit(FOOD_COST, false));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class)
                    .satisfies(ex -> assertThat(((PublicationGateException) ex).getUnmetConditions())
                            .contains(PublicationGateCondition.EMPTY_RECIPE));
            // The cost audit (mocked above as non-zero) is exactly what T076a says cannot be used
            // to derive condition 2 — the gate blocked on the dedicated active-recipe-line count,
            // not on a zero cost that was never there.
        }

        @Test
        @DisplayName("unconfirmed allergens block the gate, naming ALLERGENS_UNCONFIRMED")
        void unconfirmedAllergensBlockGate() {
            dish.setAllergensConfirmed(false);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class)
                    .satisfies(ex -> assertThat(((PublicationGateException) ex).getUnmetConditions())
                            .containsExactly(PublicationGateCondition.ALLERGENS_UNCONFIRMED));
        }

        @Test
        @DisplayName("every unmet condition is reported at once, not just the first found")
        void allUnmetConditionsReportedTogether() {
            dish.setRetailPrice(null);
            dish.setAllergensConfirmed(false);
            when(gateRepository.countActiveRecipeLines(dish.getId(), TENANT)).thenReturn(0L);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class)
                    .satisfies(ex -> assertThat(((PublicationGateException) ex).getUnmetConditions())
                            .containsExactlyInAnyOrder(
                                    PublicationGateCondition.RETAIL_PRICE_MISSING,
                                    PublicationGateCondition.EMPTY_RECIPE,
                                    PublicationGateCondition.ALLERGENS_UNCONFIRMED));
        }

        @Test
        @DisplayName("a gate failure is reported even when an acknowledgement would otherwise "
                + "also be missing — the gate is never offered as something a confirmation clears")
        void gateFailureTakesPriorityOverAcknowledgement() {
            dish.setAllergensConfirmed(false);
            when(foodCostAuditService.auditDishCost(dish)).thenReturn(audit(FOOD_COST, true));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(PublicationGateException.class);
        }

        @Test
        @DisplayName("unpriced ingredients without acknowledgement block approval (FR-017)")
        void unpricedIngredientsRequireAcknowledgement() {
            when(foodCostAuditService.auditDishCost(dish)).thenReturn(audit(FOOD_COST, true));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(AcknowledgementRequiredException.class)
                    .satisfies(ex -> assertThat(
                            ((AcknowledgementRequiredException) ex).getRequiredAcknowledgements())
                            .containsExactly(AcknowledgementKind.UNPRICED_INGREDIENTS));
        }

        @Test
        @DisplayName("acknowledging unpriced ingredients lets the approval proceed")
        void acknowledgingUnpricedIngredientsProceeds() {
            when(foodCostAuditService.auditDishCost(dish)).thenReturn(audit(FOOD_COST, true));
            when(dishRepository.updateApprovalIfPending(eq(dish.getId()), eq(TENANT), any(Instant.class)))
                    .thenReturn(1);

            DishApprovalResponse response = service.approve(dish.getId(), true, false);

            assertThat(response.status()).isEqualTo(DishStatus.PUBLISHED);
        }

        @Test
        @DisplayName("a negative margin without acknowledgement blocks approval (FR-014, FR-017)")
        void negativeMarginRequiresAcknowledgement() {
            dish.setRetailPrice(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(AcknowledgementRequiredException.class)
                    .satisfies(ex -> assertThat(
                            ((AcknowledgementRequiredException) ex).getRequiredAcknowledgements())
                            .containsExactly(AcknowledgementKind.NEGATIVE_MARGIN));
        }

        @Test
        @DisplayName("acknowledging the negative margin lets the approval proceed")
        void acknowledgingNegativeMarginProceeds() {
            dish.setRetailPrice(new BigDecimal("100.00"));
            when(dishRepository.updateApprovalIfPending(eq(dish.getId()), eq(TENANT), any(Instant.class)))
                    .thenReturn(1);

            DishApprovalResponse response = service.approve(dish.getId(), false, true);

            assertThat(response.status()).isEqualTo(DishStatus.PUBLISHED);
        }

        @Test
        @DisplayName("a lost race (zero rows affected despite a satisfied gate) is reported as "
                + "dish-already-decided, carrying the status that now prevails")
        void raceReportedAsAlreadyDecided() {
            // The initial read (via accessGuard, stubbed in setUp to return the PENDING_PRICE
            // dish) sees the gate pass; between that read and the conditional UPDATE, a
            // concurrent rejection commits, so the UPDATE affects no row and the re-fetch that
            // follows — the service's own dishRepository.findByIdAndTenantId call, not
            // accessGuard's — sees the new status. A second Dish instance rather than mutating
            // the first, because the real race is exactly two different reads of two different
            // states.
            Dish racedDish = new Dish();
            racedDish.setId(dish.getId());
            racedDish.setTenantId(TENANT);
            racedDish.setStatus(DishStatus.DRAFT);

            when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                    .thenReturn(Optional.of(racedDish));
            when(dishRepository.updateApprovalIfPending(eq(dish.getId()), eq(TENANT), any(Instant.class)))
                    .thenReturn(0);

            assertThatThrownBy(() -> service.approve(dish.getId(), false, false))
                    .isInstanceOf(DishAlreadyDecidedException.class)
                    .satisfies(ex -> assertThat(
                            ((DishAlreadyDecidedException) ex).getCurrentStatus()).isEqualTo("DRAFT"));
            verifyNoInteractions(reviewHistoryRepository);
        }
    }

    @Nested
    @DisplayName("reject: feedback to the kitchen (FR-020, FR-021, FR-022)")
    class Rejection {

        private static final String VALID_COMMENT =
                "El costo del salmón dispara el food cost. Reemplazalo por merluza y reenviá la ficha.";

        @Test
        @DisplayName("a valid rejection transitions PENDING_PRICE -> DRAFT and writes the audit row")
        void rejectsAndWritesAuditRow() {
            when(dishRepository.updateRejectionIfPending(dish.getId(), TENANT)).thenReturn(1);

            DishRejectionResponse response = service.reject(dish.getId(), VALID_COMMENT);

            assertThat(response.status()).isEqualTo(DishStatus.DRAFT);
            assertThat(response.cycleNumber()).isEqualTo(1);

            ArgumentCaptor<DishReviewHistory> captor = ArgumentCaptor.forClass(DishReviewHistory.class);
            verify(reviewHistoryRepository).save(captor.capture());
            DishReviewHistory entry = captor.getValue();
            assertThat(entry.getEventType()).isEqualTo(ReviewEventType.REJECTED);
            assertThat(entry.getComment()).isEqualTo(VALID_COMMENT);
            assertThat(entry.getAdminId()).isEqualTo(ADMIN_ID);
            assertThat(response.reviewHistoryEntryId()).isEqualTo(entry.getId());
        }

        @Test
        @DisplayName("the cycle number across repeated reject -> resubmit rounds is the dish's "
                + "highest recorded cycle plus one")
        void cycleNumberAcrossRepeatedRounds() {
            when(reviewHistoryRepository.findMaxCycleNumberByDishId(dish.getId()))
                    .thenReturn(Optional.of(1));
            when(dishRepository.updateRejectionIfPending(dish.getId(), TENANT)).thenReturn(1);

            DishRejectionResponse response = service.reject(dish.getId(), VALID_COMMENT);

            assertThat(response.cycleNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("the assigned retail price is preserved: rejection writes no price-history "
                + "row (FR-020 as amended)")
        void retailPricePreservedNoPriceHistoryRow() {
            dish.setRetailPrice(new BigDecimal("11250.00"));
            when(dishRepository.updateRejectionIfPending(dish.getId(), TENANT)).thenReturn(1);

            service.reject(dish.getId(), VALID_COMMENT);

            verifyNoInteractions(priceHistoryRepository);
            // "Preserved" means the rejection transition never touches retail_price at all: the
            // conditional UPDATE it issues names only status, never retail_price, so there is
            // nothing for this test to read back and assert equal — the absence of a write *is*
            // the preservation.
            verify(dishRepository, never()).updateRetailPriceIfUnchanged(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a comment shorter than 10 characters is rejected (400)")
        void shortCommentRejected() {
            assertThatThrownBy(() -> service.reject(dish.getId(), "too short"))
                    .isInstanceOf(InvalidRejectionCommentException.class);
        }

        @Test
        @DisplayName("a comment longer than 1000 characters is rejected (400)")
        void longCommentRejected() {
            String tooLong = "a".repeat(1001);
            assertThatThrownBy(() -> service.reject(dish.getId(), tooLong))
                    .isInstanceOf(InvalidRejectionCommentException.class);
        }

        @Test
        @DisplayName("a whitespace-only comment is rejected (400)")
        void whitespaceOnlyCommentRejected() {
            assertThatThrownBy(() -> service.reject(dish.getId(), "          "))
                    .isInstanceOf(InvalidRejectionCommentException.class);
        }

        @Test
        @DisplayName("a null comment is rejected without reaching the repository")
        void nullCommentRejected() {
            assertThatThrownBy(() -> service.reject(dish.getId(), null))
                    .isInstanceOf(InvalidRejectionCommentException.class);
            verifyNoInteractions(dishRepository);
        }

        @Test
        @DisplayName("the comment is stored verbatim: markup is never stripped (FR-021)")
        void commentStoredVerbatim() {
            String withMarkup = "<script>alert('x')</script> El precio no cierra, revisá el costo.";
            when(dishRepository.updateRejectionIfPending(dish.getId(), TENANT)).thenReturn(1);

            service.reject(dish.getId(), withMarkup);

            ArgumentCaptor<DishReviewHistory> captor = ArgumentCaptor.forClass(DishReviewHistory.class);
            verify(reviewHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getComment()).isEqualTo(withMarkup);
        }

        @Test
        @DisplayName("rejecting a PUBLISHED dish is refused (409, FR-024)")
        void publishedDishRejected() {
            dish.setStatus(DishStatus.PUBLISHED);

            assertThatThrownBy(() -> service.reject(dish.getId(), VALID_COMMENT))
                    .isInstanceOf(DishStateConflictException.class);
        }

        @Test
        @DisplayName("rejecting a dish already in DRAFT is refused (409, FR-019)")
        void draftDishRejected() {
            dish.setStatus(DishStatus.DRAFT);

            assertThatThrownBy(() -> service.reject(dish.getId(), VALID_COMMENT))
                    .isInstanceOf(DishStateConflictException.class);
        }

        @Test
        @DisplayName("a lost race is reported as dish-already-decided")
        void raceReportedAsAlreadyDecided() {
            Dish racedDish = new Dish();
            racedDish.setId(dish.getId());
            racedDish.setTenantId(TENANT);
            racedDish.setStatus(DishStatus.PUBLISHED);

            when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                    .thenReturn(Optional.of(racedDish));
            when(dishRepository.updateRejectionIfPending(dish.getId(), TENANT)).thenReturn(0);

            assertThatThrownBy(() -> service.reject(dish.getId(), VALID_COMMENT))
                    .isInstanceOf(DishAlreadyDecidedException.class)
                    .satisfies(ex -> assertThat(
                            ((DishAlreadyDecidedException) ex).getCurrentStatus()).isEqualTo("PUBLISHED"));
            verifyNoInteractions(reviewHistoryRepository);
        }

        @Test
        @DisplayName("a dish that does not exist anywhere is 404")
        void unknownDish() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new DishNotFoundException(dish.getId()));

            assertThatThrownBy(() -> service.reject(dish.getId(), VALID_COMMENT))
                    .isInstanceOf(DishNotFoundException.class);
        }

        @Test
        @DisplayName("a dish belonging to another establishment is forbidden, not merely absent")
        void crossTenantDishIsForbidden() {
            when(accessGuard.requireOwnedByTenant(dish.getId(), TENANT))
                    .thenThrow(new TenantViolationException(TENANT, UUID.randomUUID()));

            assertThatThrownBy(() -> service.reject(dish.getId(), VALID_COMMENT))
                    .isInstanceOf(TenantViolationException.class);
        }
    }
}
