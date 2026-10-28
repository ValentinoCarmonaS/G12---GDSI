package com.easyeat.catalog.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.CategoryGroupDto;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.dto.DishCatalogItemDto;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.service.DinerCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Legacy CatalogController for backward compatibility with Spec 003.
 *
 * @deprecated Since Spec 008, replaced by {@link DinerCatalogController}.
 */
@Deprecated(since = "008", forRemoval = false)
@RestController
@RequestMapping("/api/v1/establishments")
public class CatalogController {

    private final DinerCatalogService dinerCatalogService;

    public CatalogController(DinerCatalogService dinerCatalogService) {
        this.dinerCatalogService = dinerCatalogService;
    }

    /**
     * Legacy endpoint returning flat list of enriched dishes.
     *
     * @deprecated Use {@code GET /api/v1/establishments/{tenantId}/catalog} instead.
     */
    @Deprecated(since = "008", forRemoval = false)
    @GetMapping("/{tenantId}/dishes")
    public ResponseEntity<List<EnrichedDishResponse>> getEstablishmentDishes(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal User user) {
        DinerCatalogResponse catalogResponse = dinerCatalogService.getCatalog(tenantId, user);
        if (catalogResponse == null || catalogResponse.getCategories() == null || catalogResponse.getCategories().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<EnrichedDishResponse> dishes = new ArrayList<>();
        for (CategoryGroupDto categoryGroup : catalogResponse.getCategories()) {
            if (categoryGroup.getDishes() != null) {
                for (DishCatalogItemDto item : categoryGroup.getDishes()) {
                    dishes.add(new EnrichedDishResponse(
                            item.getId(),
                            tenantId,
                            item.getName(),
                            item.getDescription(),
                            item.getPrice(),
                            Boolean.TRUE,
                            item.getIsCompliant(),
                            item.getConflictingRestrictions() != null ? item.getConflictingRestrictions() : Collections.emptyList()
                    ));
                }
            }
        }
        return ResponseEntity.ok(dishes);
    }
}
