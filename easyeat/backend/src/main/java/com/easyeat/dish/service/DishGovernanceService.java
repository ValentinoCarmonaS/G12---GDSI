package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.DishPriceHistory;
import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.DishRejectionResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.dto.MarginSummary;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The three decision transitions US2, US3 and US4 share over a dish: the commercial price save
 * (FR-009 through FR-014, FR-025, SC-008) and the publication approval (FR-015 through FR-019,
 * FR-025, FR-033, SC-001) implemented here; rejection is added to this class by US4.
 *
 * <p>{@code DishGovernanceService} is created in the earliest story that needs it and extended
 * by later ones, per {@code plan.md}'s source tree — three stories sharing one class over the
 * three writes it groups together, rather than three competing services.
 *
 * <p>Three boundaries both methods respect rather than cross, the same ones {@link
 * DishReviewDetailService} already states for the read side. Neither recomputes the Food Cost
 * itself: spec 006's {@link FoodCostAuditService} is injected and its total read verbatim.
 * {@code setRetailPrice} never enforces the publication gate, and FR-009 names no recipe
 * precondition at all; {@code approve} is the one place that gate is enforced, and only at the
 * instant the action is received, never from an earlier reading. And neither carries an entity
 * version: FR-025 is enforced by a conditional {@code UPDATE} per operation that restates the
 * exact state the Administrator acted on, confined to the one write path it guards rather than
 * reaching every writer of {@code dishes}.
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
    private final DishPublicationGateRepository gateRepository;
    private final DishReviewHistoryRepository reviewHistoryRepository;
    private final DishAccessGuard accessGuard;

    public DishGovernanceService(DishRepository dishRepository,
                                 DishPriceHistoryRepository priceHistoryRepository,
                                 FoodCostAuditService foodCostAuditService,
                                 RetailPricingCalculator calculator,
                                 UserRepository userRepository,
                                 DishPublicationGateRepository gateRepository,
                                 DishReviewHistoryRepository reviewHistoryRepository,
                                 DishAccessGuard accessGuard) {
        this.dishRepository = dishRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.foodCostAuditService = foodCostAuditService;
        this.calculator = calculator;
        this.userRepository = userRepository;
        this.gateRepository = gateRepository;
        this.reviewHistoryRepository = reviewHistoryRepository;
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
     * Evaluates the publication gate and, if it is satisfied, publishes the dish (FR-015 through
     * FR-019, FR-025, FR-033, SC-001).
     *
     * <p>Order of checks. An already-{@code PUBLISHED} dish is an idempotent no-op before
     * anything else is evaluated (FR-018) — its gate is not re-checked, because re-publishing is
     * not the operation being asked for. A {@code DRAFT} dish is refused outright (FR-019): the
     * action was never legal from that state. Only then are the three gate conditions evaluated,
     * all at once rather than short-circuited, so every unmet one is reported together (FR-015).
     * The acknowledgement check (FR-017) runs only once the gate is fully satisfied — it is
     * subordinate to the gate and must never be offered as a way past it. The conditional {@code
     * UPDATE} is the write itself, so FR-025's guard is the last thing that can fail.
     *
     * <p>{@code auditDishCost} is called without catching {@link EmptyRecipeException} here,
     * unlike {@link #setRetailPrice}: gate condition 2 having already passed means at least one
     * recipe line exists, so the recipe cannot be empty by the time this is reached.
     *
     * @throws DishNotFoundException no dish with this id exists in the caller's establishment.
     * @throws DishStateConflictException the dish is in {@code DRAFT} (FR-019).
     * @throws PublicationGateException one or more of the three gate conditions is unmet
     *     (FR-015); carries every unmet condition.
     * @throws AcknowledgementRequiredException the gate is satisfied but an unpriced ingredient
     *     or a negative margin was not acknowledged (FR-017).
     * @throws DishAlreadyDecidedException another Administrator's decision committed first
     *     (FR-025); carries the status that now prevails.
     */
    @Transactional
    public DishApprovalResponse approve(UUID dishId, boolean acknowledgeUnpricedIngredients,
                                        boolean acknowledgeNegativeMargin) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        // FR-001, SC-003: 403 for another tenant's dish, 404 only when it truly does not exist.
        Dish dish = accessGuard.requireOwnedByTenant(dishId, tenantId);

        if (dish.getStatus() == DishStatus.PUBLISHED) {
            return new DishApprovalResponse(dishId, DishStatus.PUBLISHED, true,
                    dish.getPublishedAt(), null);
        }
        if (dish.getStatus() != DishStatus.PENDING_PRICE) {
            throw new DishStateConflictException(dishId, dish.getStatus().name(),
                    "PENDING_PRICE");
        }

        List<PublicationGateCondition> unmet = new ArrayList<>();
        if (dish.getRetailPrice() == null || dish.getRetailPrice().signum() <= 0) {
            unmet.add(PublicationGateCondition.RETAIL_PRICE_MISSING);
        }
        if (gateRepository.countActiveRecipeLines(dishId, tenantId) == 0) {
            unmet.add(PublicationGateCondition.EMPTY_RECIPE);
        }
        if (!Boolean.TRUE.equals(dish.getAllergensConfirmed())) {
            unmet.add(PublicationGateCondition.ALLERGENS_UNCONFIRMED);
        }
        if (!unmet.isEmpty()) {
            throw new PublicationGateException(dishId, unmet);
        }

        FoodCostAuditResponse audit = foodCostAuditService.auditDishCost(dish);
        BigDecimal marginPct = calculator.marginFrom(audit.totalFoodCost(), dish.getRetailPrice());

        List<AcknowledgementKind> requiredAcks = new ArrayList<>();
        if (audit.hasUnpricedIngredients() && !acknowledgeUnpricedIngredients) {
            requiredAcks.add(AcknowledgementKind.UNPRICED_INGREDIENTS);
        }
        if (marginPct.signum() < 0 && !acknowledgeNegativeMargin) {
            requiredAcks.add(AcknowledgementKind.NEGATIVE_MARGIN);
        }
        if (!requiredAcks.isEmpty()) {
            throw new AcknowledgementRequiredException(dishId, requiredAcks);
        }

        Instant publishedAt = Instant.now();
        int updated = dishRepository.updateApprovalIfPending(dishId, tenantId, publishedAt);
        if (updated == 0) {
            String currentStatus = dishRepository.findByIdAndTenantId(dishId, tenantId)
                    .map(d -> d.getStatus().name())
                    .orElse(dish.getStatus().name());
            throw new DishAlreadyDecidedException(dishId, currentStatus, Instant.now());
        }

        int cycleNumber = reviewHistoryRepository.findMaxCycleNumberByDishId(dishId).orElse(0) + 1;
        DishReviewHistory entry = DishReviewHistory.approved(dishId, tenantId, cycleNumber,
                resolveCurrentUserId());
        reviewHistoryRepository.save(entry);

        return new DishApprovalResponse(dishId, DishStatus.PUBLISHED, false, publishedAt,
                entry.getId());
    }

    /**
     * Returns a dish to the kitchen with mandatory feedback (FR-020 through FR-022, FR-024,
     * FR-025).
     *
     * <p>The comment is validated before any database access, the same posture {@link
     * #setRetailPrice} takes for its own input. The conditional {@code UPDATE} this issues names
     * only {@code status} — never {@code retail_price} — which is how FR-020's "the assigned
     * retail price MUST be preserved across the rejection" is kept true: there is no write path
     * here that could touch it, and no {@code dish_price_history} row is written either, since a
     * rejection is not a price change.
     *
     * @throws InvalidRejectionCommentException the comment is absent, blank, or outside 10 to
     *     1000 characters after trimming (FR-021).
     * @throws DishStateConflictException the dish is in {@code DRAFT} or {@code PUBLISHED}:
     *     rejection is only legal from {@code PENDING_PRICE} (FR-019, FR-024).
     * @throws DishAlreadyDecidedException another Administrator's decision committed first
     *     (FR-025); carries the status that now prevails.
     */
    @Transactional
    public DishRejectionResponse reject(UUID dishId, String comment) {
        String trimmed = comment == null ? null : comment.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() < 10 || trimmed.length() > 1000) {
            throw new InvalidRejectionCommentException(comment);
        }

        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        // FR-001, SC-003: 403 for another tenant's dish, 404 only when it truly does not exist.
        Dish dish = accessGuard.requireOwnedByTenant(dishId, tenantId);

        if (dish.getStatus() != DishStatus.PENDING_PRICE) {
            throw new DishStateConflictException(dishId, dish.getStatus().name(),
                    "PENDING_PRICE");
        }

        int updated = dishRepository.updateRejectionIfPending(dishId, tenantId);
        if (updated == 0) {
            String currentStatus = dishRepository.findByIdAndTenantId(dishId, tenantId)
                    .map(d -> d.getStatus().name())
                    .orElse(dish.getStatus().name());
            throw new DishAlreadyDecidedException(dishId, currentStatus, Instant.now());
        }

        int cycleNumber = reviewHistoryRepository.findMaxCycleNumberByDishId(dishId).orElse(0) + 1;
        DishReviewHistory entry = DishReviewHistory.rejected(dishId, tenantId, cycleNumber,
                resolveCurrentUserId(), trimmed);
        reviewHistoryRepository.save(entry);

        return new DishRejectionResponse(dishId, DishStatus.DRAFT, cycleNumber, entry.getId());
    }

    @Transactional
    public void cancel(UUID dishId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Dish dish = accessGuard.requireOwnedByTenant(dishId, tenantId);

        if (dish.getStatus() != DishStatus.PENDING_PRICE) {
            throw new DishStateConflictException(dishId, dish.getStatus().name(),
                    "PENDING_PRICE");
        }

        int updated = dishRepository.updateCancellationIfPending(dishId, tenantId);
        if (updated == 0) {
            String currentStatus = dishRepository.findByIdAndTenantId(dishId, tenantId)
                    .map(d -> d.getStatus().name())
                    .orElse(dish.getStatus().name());
            throw new DishAlreadyDecidedException(dishId, currentStatus, Instant.now());
        }

        int cycleNumber = reviewHistoryRepository.findMaxCycleNumberByDishId(dishId).orElse(0) + 1;
        DishReviewHistory entry = DishReviewHistory.cancelled(dishId, tenantId, cycleNumber,
            resolveCurrentUserId(), null);
        reviewHistoryRepository.save(entry);
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
