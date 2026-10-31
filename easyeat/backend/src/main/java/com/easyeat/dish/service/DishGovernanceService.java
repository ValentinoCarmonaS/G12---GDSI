package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.dto.MarginSummary;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.StaleDishPriceException;
import com.easyeat.dish.repository.DishPriceHistoryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The commercial terms transition of US2 (FR-009 through FR-014, FR-025, SC-008): the single
 * write path by which a retail price is assigned or changed.
 *
 * <p>{@code DishGovernanceService} is created here rather than per story because US3 and US4
 * extend it with the approval and rejection transitions — three stories sharing one class over
 * the three writes {@code plan.md} groups together, rather than three competing services.
 *
 * <p>Three boundaries this method respects rather than crosses, the same ones {@link
 * DishReviewDetailService} already states for the read side. It never recomputes the Food Cost
 * itself: spec 006's {@link FoodCostAuditService} is injected and its total read verbatim. It
 * never enforces the publication gate — FR-015 belongs to the approval transition, not to a
 * price save, and FR-009 names no recipe precondition at all. And it carries no entity version:
 * FR-025 is enforced by a conditional {@code UPDATE} that restates the exact price the
 * Administrator read, confined to this one write path rather than reaching every writer of
 * {@code dishes}.
 */
@Service
public class DishGovernanceService {

    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final DishRepository dishRepository;
    private final DishPriceHistoryRepository priceHistoryRepository;
    private final FoodCostAuditService foodCostAuditService;
    private final RetailPricingCalculator calculator;
    private final UserRepository userRepository;
    private final DishAccessGuard accessGuard;

    public DishGovernanceService(DishRepository dishRepository,
                                 DishPriceHistoryRepository priceHistoryRepository,
                                 FoodCostAuditService foodCostAuditService,
                                 RetailPricingCalculator calculator,
                                 UserRepository userRepository,
                                 DishAccessGuard accessGuard) {
        this.dishRepository = dishRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.foodCostAuditService = foodCostAuditService;
        this.calculator = calculator;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    /**
     * Assigns or changes a dish's retail price (FR-009), permitted on {@code PENDING_PRICE} and
     * on {@code PUBLISHED} alike (FR-011).
     *
     * <p>Order of checks, and why it is this order: the price's own shape (FR-013) needs no
     * database access and is rejected first; the dish's existence and state come next, because
     * neither the concurrency guard nor the margin can be evaluated without a dish to read; the
     * negative-margin acknowledgement (FR-014) is checked against the Food Cost prevailing now,
     * before any write, so a blocked save never reaches the price-history repository; and the
     * conditional {@code UPDATE} is the write itself, so FR-025's guard is the last thing that
     * can fail.
     *
     * @throws InvalidRetailPriceException the price is absent, not strictly positive, or carries
     *     more than two decimals (FR-013).
     * @throws DishNotFoundException no dish with this id exists in the caller's establishment.
     * @throws DishStateConflictException the dish is in {@code DRAFT}: pricing was never legal
     *     from that state.
     * @throws AcknowledgementRequiredException the price yields a negative margin and {@code
     *     acknowledgeNegativeMargin} was not sent (FR-014).
     * @throws StaleDishPriceException another Administrator saved a different price first
     *     (FR-025); carries the price that actually prevails.
     */
    @Transactional
    public DishPricingResponse setRetailPrice(UUID dishId, BigDecimal retailPrice,
                                              BigDecimal expectedRetailPrice,
                                              boolean acknowledgeNegativeMargin) {
        if (retailPrice == null || retailPrice.signum() <= 0
                || retailPrice.scale() > RetailPricingCalculator.MONEY_SCALE) {
            throw new InvalidRetailPriceException(retailPrice);
        }

        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        // FR-001, SC-003: 403 for another tenant's dish, 404 only when it truly does not exist.
        Dish dish = accessGuard.requireOwnedByTenant(dishId, tenantId);

        if (dish.getStatus() != DishStatus.PENDING_PRICE && dish.getStatus() != DishStatus.PUBLISHED) {
            throw new DishStateConflictException(dishId, dish.getStatus().name(),
                    "PENDING_PRICE or PUBLISHED");
        }

        // FR-037's parity: a dish with no recipe lines makes spec 006's service raise rather
        // than return a zero cost. That is a fact about the dish, not a fault in this save —
        // FR-009 names no recipe precondition — so the Food Cost is reported undeterminable and
        // the save proceeds.
        FoodCostAuditResponse audit = null;
        try {
            audit = foodCostAuditService.auditDishCost(dish);
        } catch (EmptyRecipeException ignored) {
            // Intentionally swallowed: see the Javadoc paragraph above.
        }
        BigDecimal foodCost = audit == null ? null : audit.totalFoodCost();
        boolean hasUnpricedIngredients = audit != null && audit.hasUnpricedIngredients();

        BigDecimal marginPct = foodCost == null ? null : calculator.marginFrom(foodCost, retailPrice);
        if (marginPct != null && marginPct.signum() < 0 && !acknowledgeNegativeMargin) {
            throw new AcknowledgementRequiredException(dishId,
                    List.of(AcknowledgementKind.NEGATIVE_MARGIN));
        }

        int updated = dishRepository.updateRetailPriceIfUnchanged(dishId, tenantId,
                expectedRetailPrice, retailPrice);
        if (updated == 0) {
            BigDecimal current = dishRepository.findByIdAndTenantId(dishId, tenantId)
                    .map(Dish::getRetailPrice)
                    .orElse(null);
            throw new StaleDishPriceException(dishId, expectedRetailPrice, current);
        }

        DishPriceHistory entry = DishPriceHistory.record(dishId, tenantId, resolveCurrentUserId(),
                expectedRetailPrice, retailPrice,
                foodCost == null ? BigDecimal.ZERO : foodCost);
        priceHistoryRepository.save(entry);

        MarginSummary margin = buildMarginSummary(retailPrice, foodCost, hasUnpricedIngredients);
        return new DishPricingResponse(dishId, dish.getStatus(), margin, entry.getId());
    }

    /**
     * FR-012: derived from the Food Cost just read, never from anything persisted. {@code
     * retailPrice} is always non-null here — {@link #setRetailPrice} rejects a null one before
     * this is called — unlike {@link DishReviewDetailService}'s own margin builder, which also
     * handles an unpriced dish.
     */
    private MarginSummary buildMarginSummary(BigDecimal retailPrice, BigDecimal foodCost,
                                              boolean hasUnpricedIngredients) {
        if (foodCost == null) {
            return new MarginSummary(null, retailPrice, null, null, null, hasUnpricedIngredients,
                    false);
        }

        BigDecimal marginPct = calculator.marginFrom(foodCost, retailPrice);
        return new MarginSummary(
                foodCost,
                retailPrice,
                marginPct,
                calculator.marginTolerancePp(foodCost, retailPrice),
                calculator.toleranceThresholdPrice(marginPct),
                hasUnpricedIngredients,
                calculator.marginDerivable(foodCost));
    }

    /**
     * The acting Administrator, resolved the same way {@code
     * StockAdjustmentService.resolveCurrentUserId} already does for inventory's own audit trail:
     * the domain {@link User} principal first, a Spring {@link UserDetails} resolved by email
     * next, a bare string principal last, and a sentinel zero UUID only if none of those apply.
     * Duplicated rather than extracted — Principle V forbids refactoring another module's shipped
     * method during this story, and two call sites of a four-branch method do not yet justify a
     * shared abstraction (Principle IV).
     */
    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return SYSTEM_ACTOR_ID;
        }

        if (auth.getPrincipal() instanceof User user) {
            return user.getId();
        }

        if (auth.getPrincipal() instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(SYSTEM_ACTOR_ID);
        }

        if (auth.getPrincipal() instanceof String principalStr) {
            try {
                return UUID.fromString(principalStr);
            } catch (IllegalArgumentException e) {
                return userRepository.findByEmail(principalStr)
                        .map(User::getId)
                        .orElse(SYSTEM_ACTOR_ID);
            }
        }

        return SYSTEM_ACTOR_ID;
    }
}
