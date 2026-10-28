package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.AvailabilityMapResponse;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.service.DinerCatalogService;
import com.easyeat.catalog.service.DishAvailabilityEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/establishments")
public class DinerCatalogController {

    private final DinerCatalogService dinerCatalogService;
    private final DishAvailabilityEngine dishAvailabilityEngine;

    public DinerCatalogController(DinerCatalogService dinerCatalogService) {
        this(dinerCatalogService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DinerCatalogController(
            DinerCatalogService dinerCatalogService,
            DishAvailabilityEngine dishAvailabilityEngine) {
        this.dinerCatalogService = dinerCatalogService;
        this.dishAvailabilityEngine = dishAvailabilityEngine;
    }

    @GetMapping("/{tenantId}/catalog")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<DinerCatalogResponse> getEstablishmentCatalog(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal User user) {
        DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tenantId}/catalog/availability")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<AvailabilityMapResponse> getCatalogAvailability(
            @PathVariable UUID tenantId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        if (dishAvailabilityEngine == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        Map<UUID, Boolean> map = dishAvailabilityEngine.computeAvailabilityMap(tenantId);
        String etag = dishAvailabilityEngine.computeETag(map);
        String quotedEtag = "\"" + etag + "\"";

        if (ifNoneMatch != null && (ifNoneMatch.equals(quotedEtag) || ifNoneMatch.equals(etag))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(quotedEtag)
                    .build();
        }

        return ResponseEntity.ok()
                .eTag(quotedEtag)
                .body(new AvailabilityMapResponse(map));
    }
}
