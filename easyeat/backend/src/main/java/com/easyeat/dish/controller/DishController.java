package com.easyeat.dish.controller;

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
    public ResponseEntity<DishDetailResponse> createDishDraft(@Valid @RequestBody CreateDishRequest request) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @GetMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishPageResponse> listDishes(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Pageable pageable
    ) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishDetailResponse> getDishById(@PathVariable UUID id) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<DishDetailResponse> updateDishBasicInfo(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDishRequest request
    ) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteDishDraft(@PathVariable UUID id) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }
}
