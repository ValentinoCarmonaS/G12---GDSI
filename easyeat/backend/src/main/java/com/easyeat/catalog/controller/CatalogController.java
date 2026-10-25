package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.service.CatalogComplianceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/establishments")
public class CatalogController {

    private final CatalogComplianceService catalogComplianceService;

    public CatalogController(CatalogComplianceService catalogComplianceService) {
        this.catalogComplianceService = catalogComplianceService;
    }

    @GetMapping("/{tenantId}/dishes")
    public ResponseEntity<List<EnrichedDishResponse>> getEstablishmentDishes(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal User user) {
        List<EnrichedDishResponse> catalog = catalogComplianceService.getEnrichedCatalog(tenantId, user);
        return ResponseEntity.ok(catalog);
    }
}
