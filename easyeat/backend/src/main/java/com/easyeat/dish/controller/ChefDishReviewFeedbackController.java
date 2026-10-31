package com.easyeat.dish.controller;

import com.easyeat.dish.dto.ChefReviewFeedbackResponse;
import com.easyeat.dish.service.ChefReviewFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Chef's own read of rejection feedback on their establishment's dishes (FR-023, SC-003).
 *
 * <p>Deliberately not the admin surface: FR-001's enumeration of administrative operations is
 * exhaustive and excludes this one by name, so the role gate here is {@code CHEF}, not {@code
 * ADMIN}/{@code ADMINISTRADOR}.
 */
@RestController
@RequestMapping("/api/v1/dishes")
@PreAuthorize("hasRole('CHEF')")
public class ChefDishReviewFeedbackController {

    private final ChefReviewFeedbackService feedbackService;

    public ChefDishReviewFeedbackController(ChefReviewFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/{id}/review-feedback")
    public ResponseEntity<ChefReviewFeedbackResponse> getReviewFeedback(
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(feedbackService.getFeedback(id));
    }
}
