package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.service.CatalogComplianceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartComplianceController {

    private final CatalogComplianceService catalogComplianceService;

    public CartComplianceController(CatalogComplianceService catalogComplianceService) {
        this.catalogComplianceService = catalogComplianceService;
    }

    @PostMapping("/evaluate-compliance")
    public ResponseEntity<CartComplianceResponse> evaluateCompliance(
            @Valid @RequestBody CartComplianceRequest request,
            @AuthenticationPrincipal User user) {
        CartComplianceResponse response = catalogComplianceService.evaluateCartCompliance(request, user);
        return ResponseEntity.ok(response);
    }
}
