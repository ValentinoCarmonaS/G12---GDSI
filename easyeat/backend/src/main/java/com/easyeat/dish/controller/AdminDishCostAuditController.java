package com.easyeat.dish.controller;

import com.easyeat.dish.dto.FoodCostAuditResponse;
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

    public AdminDishCostAuditController(FoodCostAuditService foodCostAuditService) {
        this.foodCostAuditService = foodCostAuditService;
    }

    @GetMapping("/{id}/cost-audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    public ResponseEntity<FoodCostAuditResponse> auditDishCost(@PathVariable UUID id) {
        FoodCostAuditResponse response = foodCostAuditService.auditDishCost(id);
        return ResponseEntity.ok(response);
    }
}
