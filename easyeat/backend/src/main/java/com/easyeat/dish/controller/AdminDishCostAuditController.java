package com.easyeat.dish.controller;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.FoodCostAuditResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.service.FoodCostAuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dishes")
public class AdminDishCostAuditController {

    private final FoodCostAuditService foodCostAuditService;
    private final DishRepository dishRepository;

    public AdminDishCostAuditController(FoodCostAuditService foodCostAuditService, DishRepository dishRepository) {
        this.foodCostAuditService = foodCostAuditService;
        this.dishRepository = dishRepository;
    }

    @GetMapping("/{id}/cost-audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    public ResponseEntity<FoodCostAuditResponse> auditDishCost(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DishNotFoundException(id));

        if (dish.getStatus() != DishStatus.PENDING_PRICE) {
            throw new DishLockedException(
                    String.format("El plato con ID %s no puede ser auditado financieramente en estado '%s'. Requiere estado 'PENDING_PRICE'.",
                            id, dish.getStatus() != null ? dish.getStatus().name() : "NULL")
            );
        }

        FoodCostAuditResponse response = foodCostAuditService.auditDishCost(dish);
        return ResponseEntity.ok(response);
    }
}
