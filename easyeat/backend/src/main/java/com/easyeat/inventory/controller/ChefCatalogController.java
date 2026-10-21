package com.easyeat.inventory.controller;

import com.easyeat.inventory.dto.ChefIngredientPageResponse;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.service.IngredientService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chef/ingredients")
@PreAuthorize("hasRole('CHEF')")
public class ChefCatalogController {

    private final IngredientService ingredientService;

    public ChefCatalogController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @GetMapping
    public ChefIngredientPageResponse getCatalog(
            @RequestParam(required = false) IngredientCategory category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ingredientService.getChefCatalog(category, search, pageable);
    }
}
