package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DinerCatalogService {

    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final CatalogComplianceService catalogComplianceService;

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService) {
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.catalogComplianceService = catalogComplianceService;
    }

    public DinerCatalogResponse getCatalog(UUID tenantId, User user) {
        throw new UnsupportedOperationException("TDD RED: not implemented yet");
    }
}

