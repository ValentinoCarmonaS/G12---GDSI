package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.DishSummaryResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.DuplicateDishNameException;
import com.easyeat.dish.repository.DishPhotoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.easyeat.dish.dto.RecipeIngredientItem;
import com.easyeat.dish.dto.RecipeResponse;
import com.easyeat.dish.dto.RecipeUpdateRequest;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class DishService {

    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;

    public DishService(
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository) {
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional
    public RecipeResponse updateRecipe(UUID dishId, RecipeUpdateRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        if (dish.getStatus() != DishStatus.DRAFT) {
            throw new DishLockedException(dishId, dish.getStatus().name());
        }

        if (request == null || request.ingredients() == null) {
            throw new IllegalArgumentException("La lista de ingredientes es obligatoria");
        }

        java.util.Set<UUID> seenIds = new java.util.HashSet<>();
        for (RecipeIngredientItem item : request.ingredients()) {
            if (item.ingredientId() == null || !seenIds.add(item.ingredientId())) {
                throw new IllegalArgumentException("Ingrediente duplicado en la receta: " + item.ingredientId());
            }
        }

        for (RecipeIngredientItem item : request.ingredients()) {

            if (item.quantity() == null || item.quantity() <= 0) {
                BigDecimal qty = item.quantity() != null ? BigDecimal.valueOf(item.quantity()) : BigDecimal.ZERO;
                throw new com.easyeat.dish.exception.InvalidQuantityException(qty, "", "La cantidad debe ser estrictamente mayor a cero");
            }

            com.easyeat.inventory.entity.Ingredient ingredient = ingredientRepository.findByIdAndTenantId(item.ingredientId(), tenantId)
                    .orElseThrow(() -> new com.easyeat.dish.exception.InvalidIngredientException(item.ingredientId(), "El ingrediente no existe en el establecimiento"));

            if (ingredient.getStatus() != com.easyeat.inventory.dto.IngredientStatus.ACTIVE) {
                throw new com.easyeat.dish.exception.InvalidIngredientException(item.ingredientId(), "El ingrediente está discontinuado o inactivo");
            }

            if (ingredient.getUnitOfMeasure() == com.easyeat.inventory.dto.UnitOfMeasure.U) {
                BigDecimal qty = BigDecimal.valueOf(item.quantity());
                if (qty.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                    throw new com.easyeat.dish.exception.InvalidQuantityException(qty, "u", "Las unidades discretas ('u') solo aceptan números enteros");
                }
            }
        }

        dishRecipeIngredientRepository.deleteByIdDishId(dishId);

        List<com.easyeat.dish.domain.DishRecipeIngredient> entities = new ArrayList<>();
        List<RecipeIngredientItem> responseItems = new ArrayList<>();

        for (RecipeIngredientItem item : request.ingredients()) {
            com.easyeat.inventory.entity.Ingredient ingredient = ingredientRepository.findByIdAndTenantId(item.ingredientId(), tenantId).get();
            BigDecimal qty = BigDecimal.valueOf(item.quantity());
            entities.add(new com.easyeat.dish.domain.DishRecipeIngredient(dishId, item.ingredientId(), qty));

            String uom = ingredient.getUnitOfMeasure() != null ? ingredient.getUnitOfMeasure().getValue() : null;
            responseItems.add(new RecipeIngredientItem(
                    ingredient.getId(),
                    ingredient.getName(),
                    item.quantity(),
                    uom,
                    ingredient.getStatus() == com.easyeat.inventory.dto.IngredientStatus.ACTIVE
            ));
        }

        dishRecipeIngredientRepository.saveAll(entities);
        return new RecipeResponse(dishId, responseItems);
    }

    @Transactional
    public void removeRecipeIngredient(UUID dishId, UUID ingredientId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        if (dish.getStatus() != DishStatus.DRAFT) {
            throw new DishLockedException(dishId, dish.getStatus().name());
        }

        if (!dishRecipeIngredientRepository.existsByIdDishIdAndIdIngredientId(dishId, ingredientId)) {
            throw new com.easyeat.dish.exception.InvalidIngredientException(ingredientId, "El ingrediente no forma parte de la receta");
        }

        dishRecipeIngredientRepository.deleteByIdDishIdAndIdIngredientId(dishId, ingredientId);
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(UUID dishId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        List<com.easyeat.dish.domain.DishRecipeIngredient> lines = dishRecipeIngredientRepository.findByIdDishId(dish.getId());
        List<RecipeIngredientItem> enriched = lines.stream().map(line -> {
            com.easyeat.inventory.entity.Ingredient ing = ingredientRepository.findByIdAndTenantId(line.getIngredientId(), tenantId).orElse(null);
            String name = ing != null ? ing.getName() : "Desconocido";
            String uom = ing != null && ing.getUnitOfMeasure() != null ? ing.getUnitOfMeasure().getValue() : null;
            boolean active = ing != null && ing.getStatus() == com.easyeat.inventory.dto.IngredientStatus.ACTIVE;
            return new RecipeIngredientItem(line.getIngredientId(), name, line.getQuantity().doubleValue(), uom, active);
        }).toList();

        return new RecipeResponse(dish.getId(), enriched);
    }

    @Transactional
    public DishDetailResponse createDraft(CreateDishRequest request, UUID chefId) {
        validateBasicInfo(request.name(), request.category(), request.description());

        UUID tenantId = TenantContextHolder.getTenantId();
        String trimmedName = request.name().trim();

        if (dishRepository.existsByTenantIdAndNameLowerTrimmed(tenantId, trimmedName)) {
            throw new DuplicateDishNameException(trimmedName);
        }

        Dish dish = new Dish();
        dish.setTenantId(tenantId);
        dish.setName(trimmedName);
        dish.setCategory(request.category());
        dish.setDescription(request.description());
        dish.setStatus(DishStatus.DRAFT);
        dish.setCreatedBy(chefId);
        dish.setAllergensConfirmed(false);
        dish.setIsActive(false);

        Dish saved = dishRepository.save(dish);
        return mapToDetailResponse(saved);
    }

    @Transactional
    public DishDetailResponse updateBasicInfo(UUID id, UpdateDishRequest request) {
        validateBasicInfo(request.name(), request.category(), request.description());

        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DishNotFoundException(id));

        if (dish.getStatus() != DishStatus.DRAFT) {
            throw new DishLockedException(id, dish.getStatus().name());
        }

        String trimmedName = request.name().trim();
        if (dishRepository.existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, trimmedName, id)) {
            throw new DuplicateDishNameException(trimmedName);
        }

        dish.setName(trimmedName);
        dish.setCategory(request.category());
        dish.setDescription(request.description());

        Dish saved = dishRepository.save(dish);
        return mapToDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public DishDetailResponse getDishById(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DishNotFoundException(id));
        return mapToDetailResponse(dish);
    }

    @Transactional(readOnly = true)
    public DishPageResponse listDishes(String statusFilter, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<Dish> page;
        if (statusFilter == null || statusFilter.equalsIgnoreCase("ALL") || statusFilter.isBlank()) {
            page = dishRepository.findByTenantId(tenantId, pageable);
        } else {
            DishStatus status = DishStatus.valueOf(statusFilter.toUpperCase());
            page = dishRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }

        List<DishSummaryResponse> content = page.getContent().stream()
                .map(this::mapToSummaryResponse)
                .toList();

        return new DishPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional
    public void deleteDraft(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new DishNotFoundException(id));

        if (dish.getStatus() != DishStatus.DRAFT) {
            throw new DishLockedException(id, dish.getStatus().name());
        }

        dishPhotoRepository.deleteByDishId(id);
        dishRepository.delete(dish);
    }

    private void validateBasicInfo(String name, DishCategory category, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del plato es obligatorio y no puede estar vacío");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("El nombre del plato no puede superar los 100 caracteres");
        }
        if (category == null) {
            throw new IllegalArgumentException("La categoría del plato es obligatoria");
        }
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("La descripción no puede superar los 500 caracteres");
        }
    }

    private DishDetailResponse mapToDetailResponse(Dish dish) {
        List<String> missingConditions = computeMissingConditions(dish);
        boolean isCompleteForReview = missingConditions.isEmpty();

        String photoUrl = dishPhotoRepository.existsByDishId(dish.getId())
                ? "/api/v1/dishes/" + dish.getId() + "/photo"
                : null;

        List<String> restrictions = dish.getRestrictionCodes() != null
                ? new ArrayList<>(dish.getRestrictionCodes())
                : Collections.emptyList();

        List<com.easyeat.dish.domain.DishRecipeIngredient> recipeLines = dishRecipeIngredientRepository.findByIdDishId(dish.getId());
        List<RecipeIngredientItem> enrichedRecipe = recipeLines.stream().map(line -> {
            com.easyeat.inventory.entity.Ingredient ingredient = ingredientRepository.findByIdAndTenantId(line.getIngredientId(), dish.getTenantId()).orElse(null);
            String ingredientName = ingredient != null ? ingredient.getName() : "Desconocido";
            String unitOfMeasure = ingredient != null && ingredient.getUnitOfMeasure() != null ? ingredient.getUnitOfMeasure().getValue() : null;
            boolean isActive = ingredient != null && ingredient.getStatus() == com.easyeat.inventory.dto.IngredientStatus.ACTIVE;
            return new RecipeIngredientItem(
                    line.getIngredientId(),
                    ingredientName,
                    line.getQuantity() != null ? line.getQuantity().doubleValue() : null,
                    unitOfMeasure,
                    isActive
            );
        }).toList();

        return new DishDetailResponse(
                dish.getId(),
                dish.getTenantId(),
                dish.getName(),
                dish.getCategory(),
                dish.getDescription(),
                photoUrl,
                dish.getStatus(),
                Boolean.TRUE.equals(dish.getAllergensConfirmed()),
                restrictions,
                enrichedRecipe,
                isCompleteForReview,
                missingConditions,
                dish.getCreatedBy(),
                dish.getCreatedAt(),
                dish.getUpdatedAt()
        );
    }

    private DishSummaryResponse mapToSummaryResponse(Dish dish) {
        List<String> missingConditions = computeMissingConditions(dish);
        boolean isCompleteForReview = missingConditions.isEmpty();

        String photoUrl = dishPhotoRepository.existsByDishId(dish.getId())
                ? "/api/v1/dishes/" + dish.getId() + "/photo"
                : null;

        int recipeCount = (int) dishRecipeIngredientRepository.countByIdDishId(dish.getId());

        return new DishSummaryResponse(
                dish.getId(),
                dish.getTenantId(),
                dish.getName(),
                dish.getCategory(),
                dish.getDescription(),
                photoUrl,
                dish.getStatus(),
                Boolean.TRUE.equals(dish.getAllergensConfirmed()),
                recipeCount,
                isCompleteForReview,
                dish.getUpdatedAt(),
                dish.getCreatedAt()
        );
    }

    private List<String> computeMissingConditions(Dish dish) {
        List<String> conditions = new ArrayList<>();
        if (dish.getName() == null || dish.getName().isBlank()) {
            conditions.add("El nombre del plato es obligatorio");
        }
        if (dish.getCategory() == null) {
            conditions.add("La categoría del plato es obligatoria");
        }
        long recipeCount = dishRecipeIngredientRepository.countByIdDishId(dish.getId());
        if (recipeCount == 0) {
            conditions.add("La receta debe contener al menos un ingrediente");
        }
        if (!Boolean.TRUE.equals(dish.getAllergensConfirmed())) {
            conditions.add("Se debe confirmar la sección de alérgenos");
        }
        return conditions;
    }
}
