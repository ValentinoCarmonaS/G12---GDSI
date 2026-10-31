package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.CategoryGroupDto;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.dto.DishCatalogItemDto;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.dto.EstablishmentDetailDto;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DinerCatalogService {

    private static final List<DishCategory> CANONICAL_CATEGORIES = List.of(
            DishCategory.ENTRADAS,
            DishCategory.PLATOS_PRINCIPALES,
            DishCategory.POSTRES,
            DishCategory.BEBIDAS,
            DishCategory.ENSALADAS
    );

    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final CatalogComplianceService catalogComplianceService;
    private final DishAvailabilityEngine dishAvailabilityEngine;

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository, ingredientRepository, catalogComplianceService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            DishAvailabilityEngine dishAvailabilityEngine) {
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.catalogComplianceService = catalogComplianceService;
        this.dishAvailabilityEngine = dishAvailabilityEngine;
    }

    public DinerCatalogResponse getCatalog(UUID tenantId, User user) {
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(() -> new EstablishmentNotFoundException(tenantId));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(tenantId, establishment.getStatus() != null ? establishment.getStatus().name() : "INACTIVE");
        }

        EstablishmentDetailDto establishmentDetail = buildEstablishmentDetail(establishment);

        List<Dish> publishedDishes = dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED);
        if (publishedDishes.isEmpty()) {
            return new DinerCatalogResponse(establishmentDetail, Collections.emptyList());
        }

        Map<UUID, EnrichedDishResponse> complianceMap = resolveComplianceMap(tenantId, user);

        Map<DishCategory, List<DishCatalogItemDto>> dishesByCategory = new EnumMap<>(DishCategory.class);

        for (Dish dish : publishedDishes) {
            if (dish.getCategory() == null) {
                continue;
            }

            DishCatalogItemDto itemDto = buildDishCatalogItem(dish, tenantId, complianceMap);
            dishesByCategory.computeIfAbsent(dish.getCategory(), k -> new ArrayList<>()).add(itemDto);
        }

        List<CategoryGroupDto> categoryGroups = new ArrayList<>();
        for (DishCategory category : CANONICAL_CATEGORIES) {
            List<DishCatalogItemDto> categoryDishes = dishesByCategory.get(category);
            if (categoryDishes != null && !categoryDishes.isEmpty()) {
                categoryGroups.add(new CategoryGroupDto(category.getValue(), categoryDishes));
            }
        }

        return new DinerCatalogResponse(establishmentDetail, categoryGroups);
    }

    private EstablishmentDetailDto buildEstablishmentDetail(Establishment establishment) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(establishment.getTimezone() != null ? establishment.getTimezone() : "America/Argentina/Buenos_Aires");
        } catch (Exception e) {
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalTime currentTime = now.toLocalTime();

        LocalTime opening = establishment.getOpeningTime() != null ? establishment.getOpeningTime() : LocalTime.of(11, 0);
        LocalTime closing = establishment.getClosingTime() != null ? establishment.getClosingTime() : LocalTime.of(23, 0);

        boolean isOpen;
        if (opening.equals(closing)) {
            isOpen = true;
        } else if (opening.isBefore(closing)) {
            isOpen = !currentTime.isBefore(opening) && !currentTime.isAfter(closing);
        } else {
            // Overnight opening
            isOpen = !currentTime.isBefore(opening) || !currentTime.isAfter(closing);
        }

        String nextSchedule = null;
        if (!isOpen) {
            nextSchedule = "Abre a las " + opening.format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        return new EstablishmentDetailDto(
                establishment.getId(),
                establishment.getName(),
                establishment.getStatus(),
                isOpen,
                nextSchedule
        );
    }

    private Map<UUID, EnrichedDishResponse> resolveComplianceMap(UUID tenantId, User user) {
        try {
            List<EnrichedDishResponse> complianceList = catalogComplianceService.getEnrichedCatalog(tenantId, user);
            if (complianceList == null) {
                return Collections.emptyMap();
            }
            return complianceList.stream()
                    .collect(Collectors.toMap(EnrichedDishResponse::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private DishCatalogItemDto buildDishCatalogItem(
            Dish dish,
            UUID tenantId,
            Map<UUID, EnrichedDishResponse> complianceMap) {

        String photoUrl = dishPhotoRepository.existsByDishId(dish.getId())
                ? "/api/v1/dishes/" + dish.getId() + "/photo"
                : null;

        List<String> recipeIngredients = resolveRecipeIngredients(dish.getId(), tenantId);

        EnrichedDishResponse compliance = complianceMap.get(dish.getId());
        boolean isCompliant = compliance == null || Boolean.TRUE.equals(compliance.getIsCompliant());
        List<String> conflictingRestrictions = compliance != null && compliance.getConflictingRestrictions() != null
                ? compliance.getConflictingRestrictions()
                : Collections.emptyList();

        // Spec 007 FR-033/D2: the diner-facing price is the Administrator-assigned retail price,
        // never the legacy `price` column. Corrected 2026-10-31 on integration into
        // develop/sprint-2 — this service predates spec 007 and read the legacy column.
        BigDecimal price = dish.getRetailPrice() != null ? dish.getRetailPrice() : BigDecimal.ZERO;
        boolean isAvailable = dishAvailabilityEngine != null
                ? dishAvailabilityEngine.isDishAvailable(dish.getId(), tenantId)
                : (dish.getIsActive() != null && dish.getIsActive());

        return new DishCatalogItemDto(
                dish.getId(),
                dish.getName(),
                dish.getDescription(),
                dish.getCategory() != null ? dish.getCategory().getValue() : null,
                price,
                photoUrl,
                isAvailable,
                isCompliant,
                conflictingRestrictions,
                recipeIngredients
        );
    }

    private List<String> resolveRecipeIngredients(UUID dishId, UUID tenantId) {
        List<DishRecipeIngredient> lines = dishRecipeIngredientRepository.findByIdDishId(dishId);
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ingredientNames = new ArrayList<>();
        for (DishRecipeIngredient line : lines) {
            Ingredient ing = ingredientRepository.findByIdAndTenantId(line.getIngredientId(), tenantId).orElse(null);
            if (ing != null && ing.getName() != null && !ing.getName().isBlank()) {
                ingredientNames.add(ing.getName());
            }
        }
        return ingredientNames;
    }
}
