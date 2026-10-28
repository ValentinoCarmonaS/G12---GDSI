package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.service.DinerCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/establishments")
public class DinerCatalogController {

    private final DinerCatalogService dinerCatalogService;

    public DinerCatalogController(DinerCatalogService dinerCatalogService) {
        this.dinerCatalogService = dinerCatalogService;
    }

    @GetMapping("/{tenantId}/catalog")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<DinerCatalogResponse> getEstablishmentCatalog(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal User user) {
        throw new UnsupportedOperationException("TDD RED: not implemented yet");
    }
}

