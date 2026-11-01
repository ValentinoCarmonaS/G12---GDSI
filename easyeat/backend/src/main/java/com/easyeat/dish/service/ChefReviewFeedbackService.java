package com.easyeat.dish.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishReviewHistory;
import com.easyeat.dish.domain.ReviewEventType;
import com.easyeat.dish.dto.ChefReviewFeedbackEntry;
import com.easyeat.dish.dto.ChefReviewFeedbackResponse;
import com.easyeat.dish.repository.DishReviewHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Chef's own read of their rejection feedback (FR-023, SC-003): a financially blind
 * projection by construction, not by omission.
 *
 * <p>This class is injected with none of {@code FoodCostAuditService}, {@code
 * RetailPricingCalculator} or {@code DishPriceHistoryRepository} — there is no field through
 * which a cost, margin or price could reach a response built here, which is the structural
 * guarantee {@code ChefReviewFeedbackResponse}'s own Javadoc states.
 */
@Service
public class ChefReviewFeedbackService {

    private final DishAccessGuard accessGuard;
    private final DishReviewHistoryRepository reviewHistoryRepository;
    private final UserRepository userRepository;

    public ChefReviewFeedbackService(DishAccessGuard accessGuard,
                                     DishReviewHistoryRepository reviewHistoryRepository,
                                     UserRepository userRepository) {
        this.accessGuard = accessGuard;
        this.reviewHistoryRepository = reviewHistoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Rejection feedback only, newest cycle first — the contract's own ordering for this
     * endpoint, opposite to the ascending order {@code DishReviewDetailService.getHistory} uses
     * for the Administrator's combined audit trail, because a Chef opening this page wants to
     * see what to fix now, not the oldest cycle first.
     */
    @Transactional(readOnly = true)
    public ChefReviewFeedbackResponse getFeedback(UUID dishId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        // FR-001's carve-out: this is the Chef's own operation (FR-023), but the same 403-vs-404
        // distinction applies — spec.md's Edge Cases state it as the platform-wide response.
        Dish dish = accessGuard.requireOwnedByTenant(dishId, tenantId);

        List<DishReviewHistory> rejections = reviewHistoryRepository
                .findByDishIdAndTenantIdOrderByCreatedAtAsc(dishId, tenantId).stream()
                .filter(row -> row.getEventType() == ReviewEventType.REJECTED || row.getEventType() == ReviewEventType.CANCELLED)
                .toList();

        Set<UUID> reviewerIds = new HashSet<>();
        rejections.forEach(row -> reviewerIds.add(row.getAdminId()));
        Map<UUID, String> namesById = userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        List<ChefReviewFeedbackEntry> entries = rejections.stream()
                .sorted(Comparator.comparing(DishReviewHistory::getCreatedAt).reversed())
                .map(row -> new ChefReviewFeedbackEntry(
                        row.getCycleNumber(),
                        namesById.getOrDefault(row.getAdminId(), "Administrador"),
                        row.getComment(),
                        row.getCreatedAt()))
                .toList();

        return new ChefReviewFeedbackResponse(dishId, dish.getStatus(), entries);
    }
}
