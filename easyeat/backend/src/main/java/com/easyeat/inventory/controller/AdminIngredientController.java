package com.easyeat.inventory.controller;

import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.inventory.service.StockAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ingredients")
@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")
public class AdminIngredientController {

    private final IngredientService ingredientService;
    private final StockAdjustmentService stockAdjustmentService;

    public AdminIngredientController(IngredientService ingredientService,
                                     StockAdjustmentService stockAdjustmentService) {
        this.ingredientService = ingredientService;
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminIngredientDetailResponse createIngredient(@Valid @RequestBody CreateIngredientRequest request) {
        return ingredientService.createIngredient(request);
    }

    @GetMapping
    public List<AdminIngredientDetailResponse> listIngredients(
            @RequestParam(required = false) IngredientCategory category,
            @RequestParam(required = false) IngredientStatus status) {
        return ingredientService.listIngredients(category, status);
    }

    @GetMapping("/{id}")
    public AdminIngredientDetailResponse getIngredient(@PathVariable UUID id) {
        return ingredientService.getIngredient(id);
    }

    @PutMapping("/{id}")
    public AdminIngredientDetailResponse updateIngredient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIngredientRequest request) {
        return ingredientService.updateIngredient(id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminIngredientDetailResponse updateIngredientStatus(
            @PathVariable UUID id,
            @Valid @RequestBody IngredientStatusUpdateRequest request) {
        return ingredientService.updateIngredientStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIngredient(@PathVariable UUID id) {
        ingredientService.deleteIngredient(id);
    }

    @PostMapping("/{id}/stock-adjustments")
    public StockAdjustmentResponse adjustStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return stockAdjustmentService.adjustStock(id, request);
    }

    @GetMapping("/{id}/batches")
    public List<IngredientBatchResponse> getActiveBatches(@PathVariable UUID id) {
        return stockAdjustmentService.getActiveBatches(id);
    }

    @GetMapping(value = {"/{id}/stock-audit", "/{id}/stock-adjustments"})
    public List<StockAuditEntryResponse> getAuditHistory(@PathVariable UUID id) {
        return stockAdjustmentService.getAuditHistory(id);
    }
}
