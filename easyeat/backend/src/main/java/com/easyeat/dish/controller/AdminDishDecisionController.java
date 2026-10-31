package com.easyeat.dish.controller;

import com.easyeat.dish.dto.ApproveDishRequest;
import com.easyeat.dish.dto.DishApprovalResponse;
import com.easyeat.dish.dto.DishPricingResponse;
import com.easyeat.dish.dto.SetRetailPriceRequest;
import com.easyeat.dish.service.DishGovernanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Administrator's write surface over a dish's commercial decisions (FR-009 to FR-025):
 * pricing and approval here, rejection added by US4.
 *
 * <p>Created separately from {@code AdminDishReviewController} per {@code plan.md}'s source
 * tree, which splits the read surface from the three mutating transitions. The role gate matches
 * FR-001 exactly, applied at the class level for the same reason as that controller: a method
 * added later cannot be left unguarded by omission.
 */
@RestController
@RequestMapping("/api/v1/admin/dishes")
@PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
public class AdminDishDecisionController {

    private final DishGovernanceService governanceService;

    public AdminDishDecisionController(DishGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * Assigns or changes the retail price (FR-009, FR-013, FR-014, FR-025).
     *
     * <p>{@code expectedRetailPrice} is required and nullable; an absent key fails JSON binding
     * before this method is entered, surfaced by {@code GlobalExceptionHandler} as 400 {@code
     * expected-retail-price-required} rather than reaching here as a silent {@code null}.
     */
    @PutMapping("/{id}/retail-price")
    public ResponseEntity<DishPricingResponse> setRetailPrice(
            @PathVariable("id") UUID id,
            @RequestBody SetRetailPriceRequest request) {

        return ResponseEntity.ok(governanceService.setRetailPrice(
                id, request.retailPrice(), request.expectedRetailPrice(),
                request.acknowledgeNegativeMargin()));
    }

    /**
     * Evaluates the publication gate and, if satisfied, publishes the dish (FR-015 through
     * FR-019, FR-025, FR-033, SC-001).
     *
     * <p>An already-{@code PUBLISHED} dish is a 200 idempotent no-op (FR-018), never a conflict;
     * {@code DRAFT} is a 409 (FR-019); a failed gate is a 422 naming every unmet condition
     * (FR-015); an unacknowledged advisory is a distinct 422 (FR-017).
     */
    @PostMapping("/{id}/approval")
    public ResponseEntity<DishApprovalResponse> approve(
            @PathVariable("id") UUID id,
            @RequestBody ApproveDishRequest request) {

        return ResponseEntity.ok(governanceService.approve(
                id, request.acknowledgeUnpricedIngredients(), request.acknowledgeNegativeMargin()));
    }
}
