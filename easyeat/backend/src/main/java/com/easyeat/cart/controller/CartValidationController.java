package com.easyeat.cart.controller;

import com.easyeat.cart.dto.CartStockValidationRequest;
import com.easyeat.cart.dto.CartStockValidationResponse;
import com.easyeat.catalog.service.DishAvailabilityEngine;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for batch stock availability verification in the shopping cart (T035, COM-6.1.3, INV-3).
 * Synchronizes client debounced quantity updates with live kitchen inventory.
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartValidationController {

    private final DishAvailabilityEngine dishAvailabilityEngine;

    public CartValidationController(DishAvailabilityEngine dishAvailabilityEngine) {
        this.dishAvailabilityEngine = dishAvailabilityEngine;
    }

    @PostMapping("/validate-stock")
    public ResponseEntity<CartStockValidationResponse> validateStock(
            @Valid @RequestBody CartStockValidationRequest request) {
        List<CartStockValidationResponse.ItemResponse> evaluatedItems = new ArrayList<>();
        boolean overallValid = true;

        if (request.getItems() != null) {
            for (CartStockValidationRequest.ItemRequest item : request.getItems()) {
                int available = dishAvailabilityEngine.calculateAvailableStock(item.getDishId(), request.getTenantId());
                int requested = item.getQuantity() != null ? item.getQuantity() : 0;
                boolean isAvailable = requested <= available;

                if (!isAvailable) {
                    overallValid = false;
                }

                evaluatedItems.add(new CartStockValidationResponse.ItemResponse(
                        item.getDishId(),
                        isAvailable,
                        available,
                        requested
                ));
            }
        }

        return ResponseEntity.ok(new CartStockValidationResponse(overallValid, evaluatedItems));
    }
}
