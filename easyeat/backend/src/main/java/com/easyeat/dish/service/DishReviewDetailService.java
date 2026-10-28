package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.dto.DishReviewDetailResponse;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.dto.MarginSummary;
import com.easyeat.dish.dto.PublicationGateStatus;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.EmptyRecipeException;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishPublicationGateRepository;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete culinary file an Administrator reads before deciding (FR-006), with the margin
 * recomputed on this consultation (FR-012) and the publication gate reported as status (FR-015).
 *
 * <p>Three boundaries this service respects rather than crosses. It never recomputes the Food
 * Cost: spec 006's service is injected and its payload embedded verbatim, which R-006-1 made
 * possible by moving the status gate to its controller. It never rounds the Food Cost again,
 * because T007 established it arrives at scale 2. And it never enforces the gate — FR-015
 * re-evaluates all three conditions at the instant the approval action arrives, so what this
 * returns is what the Administrator was shown and nothing the transition may rely on.
 */
@Service
public class DishReviewDetailService {

    private final DishRepository dishRepository;
    private final FoodCostAuditService foodCostAuditService;
    private final DishReviewHistoryRepository reviewHistoryRepository;
    private final DishPublicationGateRepository gateRepository;
    private final DishPhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final RetailPricingCalculator calculator;

    public DishReviewDetailService(DishRepository dishRepository,
                                   FoodCostAuditService foodCostAuditService,
                                   DishReviewHistoryRepository reviewHistoryRepository,
                                   DishPublicationGateRepository gateRepository,
                                   DishPhotoRepository photoRepository,
                                   UserRepository userRepository,
                                   RetailPricingCalculator calculator) {
        this.dishRepository = dishRepository;
        this.foodCostAuditService = foodCostAuditService;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.gateRepository = gateRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public DishReviewDetailResponse getDetail(UUID dishId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        // T042a / FR-037: a dish with no recipe lines makes spec 006's service raise rather than
        // return a zero cost. That is correct for a question about itemized cost, and wrong to
        // propagate here: the absence of a recipe is a fact about the dish, not a fault in the
        // consultation, and the Administrator still needs every other block of the file.
        FoodCostAuditResponse audit = null;
        boolean recipeAbsent = false;
        try {
            audit = foodCostAuditService.auditDishCost(dish);
        } catch (EmptyRecipeException ignored) {
            recipeAbsent = true;
        }

        BigDecimal foodCost = audit == null ? null : audit.totalFoodCost();
        boolean hasUnpricedIngredients = audit != null && audit.hasUnpricedIngredients();

        MarginSummary margin = margin(dish.getRetailPrice(), foodCost, hasUnpricedIngredients);
        long activeRecipeLines = gateRepository.countActiveRecipeLines(dishId, tenantId);
        PublicationGateStatus gate = gate(dish, activeRecipeLines, margin);

        long reviewCycles = reviewHistoryRepository.countByDishIdAndTenantId(dishId, tenantId);
        User author = author(dish.getCreatedBy());

        return new DishReviewDetailResponse(
                dish.getId(),
                dish.getName(),
                dish.getDescription(),
                dish.getCategory(),
                dish.getStatus(),
                dish.getRetailPrice(),
                photoUrl(dishId),
                author == null ? null : author.getFullName(),
                author != null && author.getStatus() == UserStatus.SUSPENDED,
                Boolean.TRUE.equals(dish.getAllergensConfirmed()),
                dish.getSubmittedAt(),
                dish.getPublishedAt(),
                (int) reviewCycles,
                reviewCycles > 0,
                margin,
                gate,
                recipeAbsent,
                audit);
    }

    /**
     * FR-012: derived here, on this call, from the Food Cost prevailing now. Nothing about the
     * margin is read from the dish — the persisted price is the authoritative figure and the
     * margin is a reading of it.
     */
    private MarginSummary margin(BigDecimal retailPrice, BigDecimal foodCost,
                                 boolean hasUnpricedIngredients) {
        boolean derivable = foodCost != null && calculator.marginDerivable(foodCost);

        if (retailPrice == null || foodCost == null) {
            // No price, or no recipe to cost. Reporting zero would claim the dish breaks even.
            return new MarginSummary(foodCost, retailPrice, null, null, null,
                    hasUnpricedIngredients, derivable);
        }

        BigDecimal marginPct = calculator.marginFrom(foodCost, retailPrice);
        return new MarginSummary(
                foodCost,
                retailPrice,
                marginPct,
                calculator.marginTolerancePp(foodCost, retailPrice),
                calculator.toleranceThresholdPrice(marginPct),
                hasUnpricedIngredients,
                derivable);
    }

    /**
     * Every unmet condition at once, so the Administrator fixes them in one pass rather than
     * discovering a second one after fixing the first.
     *
     * <p>{@code satisfied} ignores the acknowledgements on purpose: FR-017 makes an
     * acknowledgement an additional precondition and never a gate failure, so a dish with a
     * negative margin and three met conditions is publishable once the Administrator confirms it.
     */
    private PublicationGateStatus gate(Dish dish, long activeRecipeLines, MarginSummary margin) {
        List<PublicationGateCondition> unmet = new ArrayList<>();

        if (dish.getRetailPrice() == null || dish.getRetailPrice().signum() <= 0) {
            unmet.add(PublicationGateCondition.RETAIL_PRICE_MISSING);
        }
        if (activeRecipeLines == 0) {
            // Covers both shapes with one number: no recipe at all, and a recipe whose every
            // ingredient has been discontinued. FR-015 treats them the same because neither can
            // be produced; recipeAbsent is what tells the reader which it is.
            unmet.add(PublicationGateCondition.EMPTY_RECIPE);
        }
        if (!Boolean.TRUE.equals(dish.getAllergensConfirmed())) {
            unmet.add(PublicationGateCondition.ALLERGENS_UNCONFIRMED);
        }

        List<AcknowledgementKind> acknowledgements = new ArrayList<>();
        if (margin.hasUnpricedIngredients()) {
            acknowledgements.add(AcknowledgementKind.UNPRICED_INGREDIENTS);
        }
        if (margin.marginPct() != null && margin.marginPct().signum() < 0) {
            acknowledgements.add(AcknowledgementKind.NEGATIVE_MARGIN);
        }

        return new PublicationGateStatus(unmet.isEmpty(), List.copyOf(unmet),
                List.copyOf(acknowledgements));
    }

    /**
     * The path of spec 005's existing endpoint, or {@code null}. This feature serves no image
     * bytes of its own, and a path that would 404 is worse than an honest absence.
     */
    private String photoUrl(UUID dishId) {
        return photoRepository.findByDishId(dishId)
                .map(photo -> "/api/v1/dishes/" + dishId + "/photo")
                .orElse(null);
    }

    /**
     * A {@code created_by} that no longer resolves is tolerated rather than fatal: one deleted
     * user must not make a dish unreadable, which is the degraded attribution of FR-027.
     */
    private User author(UUID authorId) {
        return authorId == null ? null : userRepository.findById(authorId).orElse(null);
    }
}
