package com.easyeat.inventory.service;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.exception.DuplicateIngredientNameException;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.UnitOfMeasureModificationException;
import com.easyeat.inventory.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class IngredientService {

    private final IngredientRepository ingredientRepository;

    public IngredientService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    public AdminIngredientDetailResponse createIngredient(CreateIngredientRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (ingredientRepository.existsByTenantIdAndNameIgnoreCase(tenantId, trimmedName)) {
            throw new DuplicateIngredientNameException("An ingredient with name '" + trimmedName + "' already exists in this tenant.");
        }

        Ingredient ingredient = new Ingredient(
                tenantId,
                trimmedName,
                request.category(),
                request.unitOfMeasure(),
                request.unitPurchaseCost()
        );
        Ingredient saved = ingredientRepository.save(ingredient);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminIngredientDetailResponse getIngredient(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Ingredient ingredient = ingredientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found with id: " + id));
        return toResponse(ingredient);
    }

    @Transactional(readOnly = true)
    public List<AdminIngredientDetailResponse> listIngredients(IngredientCategory category, IngredientStatus status) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return ingredientRepository.findAllByTenantIdAndFilters(tenantId, category, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminIngredientDetailResponse updateIngredient(UUID id, UpdateIngredientRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Ingredient ingredient = ingredientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found with id: " + id));

        String trimmedName = request.name() != null ? request.name().trim() : "";
        if (ingredientRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, trimmedName, id)) {
            throw new DuplicateIngredientNameException("An ingredient with name '" + trimmedName + "' already exists in this tenant.");
        }

        if (ingredient.getUnitOfMeasure() != request.unitOfMeasure()) {
            if (ingredient.getCurrentStock() != null && ingredient.getCurrentStock().compareTo(BigDecimal.ZERO) > 0) {
                throw new UnitOfMeasureModificationException(
                        "Cannot change unit of measure for ingredient with physical stock greater than zero: " + ingredient.getCurrentStock());
            }
            ingredient.setUnitOfMeasure(request.unitOfMeasure());
        }

        ingredient.setName(trimmedName);
        ingredient.setCategory(request.category());
        ingredient.setUnitPurchaseCost(request.unitPurchaseCost());

        Ingredient updated = ingredientRepository.save(ingredient);
        return toResponse(updated);
    }

    public AdminIngredientDetailResponse updateIngredientStatus(UUID id, IngredientStatusUpdateRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Ingredient ingredient = ingredientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found with id: " + id));

        IngredientStatus targetStatus = request.targetStatus();
        ingredient.setStatus(targetStatus);
        Ingredient updated = ingredientRepository.save(ingredient);
        return toResponse(updated);
    }

    public void deleteIngredient(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Ingredient ingredient = ingredientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Ingredient not found with id: " + id));

        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredientRepository.save(ingredient);
    }

    @Transactional(readOnly = true)
    public ChefIngredientPageResponse getChefCatalog(IngredientCategory category, String search, org.springframework.data.domain.Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        org.springframework.data.domain.Pageable resolvedPageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            resolvedPageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by("name").ascending()
            );
        }
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        org.springframework.data.domain.Page<Ingredient> page = ingredientRepository.findChefCatalog(tenantId, category, searchParam, resolvedPageable);

        List<ChefIngredientItemResponse> items = page.getContent().stream().map(ing -> {
            StockAvailabilityStatus stockStatus = (ing.getCurrentStock() != null && ing.getCurrentStock().compareTo(BigDecimal.ZERO) > 0)
                    ? StockAvailabilityStatus.IN_STOCK
                    : StockAvailabilityStatus.OUT_OF_STOCK;
            return new ChefIngredientItemResponse(
                    ing.getId(),
                    ing.getName(),
                    ing.getCategory(),
                    ing.getUnitOfMeasure(),
                    stockStatus,
                    ing.getCurrentStock() != null ? ing.getCurrentStock() : BigDecimal.ZERO.setScale(4)
            );
        }).toList();

        return new ChefIngredientPageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    private AdminIngredientDetailResponse toResponse(Ingredient ingredient) {
        return new AdminIngredientDetailResponse(
                ingredient.getId(),
                ingredient.getTenantId(),
                ingredient.getName(),
                ingredient.getCategory(),
                ingredient.getUnitOfMeasure(),
                ingredient.getUnitPurchaseCost(),
                ingredient.getStatus(),
                ingredient.getCurrentStock(),
                ingredient.getCreatedAt(),
                ingredient.getUpdatedAt()
        );
    }
}
