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
import java.util.UUID;

@Service
public class DishService {

    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;

    public DishService(DishRepository dishRepository, DishPhotoRepository dishPhotoRepository) {
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
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
                Collections.emptyList(),
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

        return new DishSummaryResponse(
                dish.getId(),
                dish.getTenantId(),
                dish.getName(),
                dish.getCategory(),
                dish.getDescription(),
                photoUrl,
                dish.getStatus(),
                Boolean.TRUE.equals(dish.getAllergensConfirmed()),
                0,
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
        if (!Boolean.TRUE.equals(dish.getAllergensConfirmed())) {
            conditions.add("Se debe confirmar la sección de alérgenos");
        }
        return conditions;
    }
}
