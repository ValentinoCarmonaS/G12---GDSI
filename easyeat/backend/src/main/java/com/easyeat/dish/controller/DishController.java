package com.easyeat.dish.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.service.DishService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dishes")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishDetailResponse> createDishDraft(
            @Valid @RequestBody CreateDishRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        UUID chefId = null;
        if (principal instanceof User user) {
            chefId = user.getId();
        }
        DishDetailResponse created = dishService.createDraft(request, chefId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishPageResponse> listDishes(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Pageable pageable
    ) {
        DishPageResponse response = dishService.listDishes(status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishDetailResponse> getDishById(@PathVariable UUID id) {
        DishDetailResponse response = dishService.getDishById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishDetailResponse> updateDishBasicInfo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDishRequest request
    ) {
        DishDetailResponse response = dishService.updateBasicInfo(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteDishDraft(@PathVariable UUID id) {
        dishService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/recipe")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<com.easyeat.dish.dto.RecipeResponse> updateDishRecipe(
            @PathVariable UUID id,
            @Valid @RequestBody com.easyeat.dish.dto.RecipeUpdateRequest request
    ) {
        com.easyeat.dish.dto.RecipeResponse response = dishService.updateRecipe(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/recipe/ingredients/{ingredientId}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> removeRecipeIngredient(
            @PathVariable UUID id,
            @PathVariable UUID ingredientId
    ) {
        dishService.removeRecipeIngredient(id, ingredientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/recipe")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<com.easyeat.dish.dto.RecipeResponse> getDishRecipe(@PathVariable UUID id) {
        com.easyeat.dish.dto.RecipeResponse response = dishService.getRecipe(id);
        return ResponseEntity.ok(response);
    }
}
