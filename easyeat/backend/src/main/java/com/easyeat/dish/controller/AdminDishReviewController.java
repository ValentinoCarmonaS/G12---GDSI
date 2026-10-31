package com.easyeat.dish.controller;

import com.easyeat.dish.dto.DishAuditHistoryResponse;
import com.easyeat.dish.dto.DishReviewDetailResponse;
import com.easyeat.dish.dto.DishReviewInboxPageResponse;
import com.easyeat.dish.dto.InboxTab;
import com.easyeat.dish.service.DishAccessGuard;
import com.easyeat.dish.service.DishReviewDetailService;
import com.easyeat.dish.service.DishReviewInboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Administrator's read surface over dishes under review (FR-002 to FR-006).
 *
 * <p>The role gate matches FR-001 exactly, including the {@code ADMIN} alias, and it is applied at
 * the class level so a method added later cannot be left unguarded by omission. That is the same
 * posture {@code AdminDishCostAuditController} takes.
 *
 * <p>Pricing, approval and rejection are read-write transitions and live in the sibling {@code
 * AdminDishDecisionController} instead, per {@code plan.md}'s source tree, which splits the two
 * by read/write rather than merging all seven operations into one class. (Corrected 2026-10-29:
 * this comment previously predicted they would land here; T068 placed the first of them in the
 * sibling class, as planned.)
 */
@RestController
@RequestMapping("/api/v1/admin/dishes")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
public class AdminDishReviewController {

    private final DishReviewInboxService inboxService;
    private final DishReviewDetailService detailService;

    public AdminDishReviewController(DishReviewInboxService inboxService,
                                     DishReviewDetailService detailService) {
        this.inboxService = inboxService;
        this.detailService = detailService;
    }

    /**
     * The segmented inbox (FR-002 to FR-005).
     *
     * <p>{@code tab} defaults to {@code PENDING}, the view the badge counts. An unrecognised value
     * is a 400 rather than a fall back to that default: showing the Administrator a list they did
     * not ask for is worse than telling them the request was wrong. Spring's converter produces
     * the 400 for a value outside the enum, so no manual parsing is needed.
     *
     * <p>A page past the last one is a 200 with an empty {@code items[]} and accurate metadata,
     * never a 404 (FR-036).
     */
    @GetMapping("/review-inbox")
    public ResponseEntity<DishReviewInboxPageResponse> getReviewInbox(
            @RequestParam(name = "tab", defaultValue = "PENDING") InboxTab tab,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        return ResponseEntity.ok(inboxService.getInbox(tab, page, size));
    }

    /**
     * The complete culinary file with the itemized Food Cost audit (FR-006).
     *
     * <p>A dish with no recipe is a 200 carrying {@code recipeAbsent: true}, not an error: the
     * absence is a fact about the dish (FR-037). An unknown dish is a 404; a dish belonging to
     * another establishment is a 403, not a 404 (FR-001, SC-003) — {@link DishAccessGuard} is
     * what tells the two apart. (Corrected 2026-10-29: this comment previously claimed 404 for
     * both, which was the bug fixed on this branch's upstream commits.)
     */
    @GetMapping("/{id}/review-detail")
    public ResponseEntity<DishReviewDetailResponse> getReviewDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(detailService.getDetail(id));
    }

    /**
     * Both audit trails, chronological (FR-010, FR-022, SC-005) — the only surface where review
     * decisions and price changes are presented together.
     */
    @GetMapping("/{id}/review-history")
    public ResponseEntity<DishAuditHistoryResponse> getReviewHistory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(detailService.getHistory(id));
    }
}
