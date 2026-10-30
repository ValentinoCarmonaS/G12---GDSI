package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.StaleDishPriceException;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The price-save portion of {@link DishGovernanceService} (FR-009, FR-010, FR-011, FR-013,
 * FR-014, FR-025, SC-008). Approval and rejection are added to this suite by US3 and US4, which is
 * why the class is created here rather than per story (same posture as {@code
 * DishReviewDetailServiceTest} and the controller it backs).
 */
@DisplayName("DishGovernanceService: setRetailPrice")
class DishGovernanceServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final BigDecimal FOOD_COST = new BigDecimal("4500.00");

    private DishRepository dishRepository;
    private DishPriceHistoryRepository priceHistoryRepository;
    private FoodCostAuditService foodCostAuditService;
    private UserRepository userRepository;
    private DishGovernanceService service;

    private Dish dish;

    @BeforeEach
    void setUp() {
        dishRepository = Mockito.mock(DishRepository.class);
        priceHistoryRepository = Mockito.mock(DishPriceHistoryRepository.class);
        foodCostAuditService = Mockito.mock(FoodCostAuditService.class);
        userRepository = Mockito.mock(UserRepository.class);

        service = new DishGovernanceService(dishRepository, priceHistoryRepository,
                foodCostAuditService, new RetailPricingCalculator(), userRepository);

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

        when(dishRepository.findByIdAndTenantId(dish.getId(), TENANT))
                .thenReturn(Optional.of(dish));
        when(foodCostAuditService.auditDishCost(dish))
                .thenReturn(audit(FOOD_COST, false));
        when(priceHistoryRepository.save(any(DishPriceHistory.class)))
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
}
