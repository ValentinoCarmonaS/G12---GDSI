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
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
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
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final com.easyeat.operations.repository.OperatingScheduleRepository operatingScheduleRepository;

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository, ingredientRepository, catalogComplianceService, null, null, null);
    }

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            DishAvailabilityEngine dishAvailabilityEngine) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository, ingredientRepository, catalogComplianceService, dishAvailabilityEngine, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DishAvailabilityEngine dishAvailabilityEngine,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DietaryRestrictionRepository dietaryRestrictionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.easyeat.operations.repository.OperatingScheduleRepository operatingScheduleRepository) {
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.catalogComplianceService = catalogComplianceService;
        this.dishAvailabilityEngine = dishAvailabilityEngine;
        this.dietaryRestrictionRepository = dietaryRestrictionRepository;
        this.operatingScheduleRepository = operatingScheduleRepository;
    }

    public DinerCatalogResponse getCatalog(UUID tenantId, User user) {
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(() -> new EstablishmentNotFoundException(tenantId));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(tenantId, establishment.getStatus() != null ? establishment.getStatus().name() : "INACTIVE");
        }

        EstablishmentDetailDto establishmentDetail = buildEstablishmentDetail(establishment);

        List<Dish> publishedDishes = dishRepository.findByTenantIdAndStatusAndIsActiveTrueAndPausedByChefFalse(tenantId, DishStatus.PUBLISHED);
        if (publishedDishes.isEmpty()) {
            return new DinerCatalogResponse(establishmentDetail, Collections.emptyList());
        }

        Map<UUID, EnrichedDishResponse> complianceMap = resolveComplianceMap(tenantId, user);
        Map<String, DietaryRestriction> allergenTaxonomy = resolveAllergenTaxonomy();
        Map<String, DietaryRestriction> lifestyleTaxonomy = resolveLifestyleTaxonomy();

        Map<DishCategory, List<DishCatalogItemDto>> dishesByCategory = new EnumMap<>(DishCategory.class);

        for (Dish dish : publishedDishes) {
            if (dish.getCategory() == null) {
                continue;
            }

            DishCatalogItemDto itemDto = buildDishCatalogItem(dish, tenantId, complianceMap, allergenTaxonomy, lifestyleTaxonomy);
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

    private Map<String, DietaryRestriction> resolveAllergenTaxonomy() {
        if (dietaryRestrictionRepository == null) {
            return Collections.emptyMap();
        }
        try {
            return dietaryRestrictionRepository.findAll().stream()
                    .filter(dr -> dr.getCategory() == DietaryCategory.COMMON_ALLERGEN)
                    .collect(Collectors.toMap(DietaryRestriction::getCode, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, DietaryRestriction> resolveLifestyleTaxonomy() {
        if (dietaryRestrictionRepository == null) {
            return Collections.emptyMap();
        }
        try {
            return dietaryRestrictionRepository.findAll().stream()
                    .filter(dr -> dr.getCategory() == DietaryCategory.DIETARY_LIFESTYLE)
                    .collect(Collectors.toMap(DietaryRestriction::getCode, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
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
        boolean isClosedToday = false;

        if (operatingScheduleRepository != null) {
            try {
                var scheduleOpt = operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), now.getDayOfWeek());
                if (scheduleOpt.isPresent()) {
                    var sched = scheduleOpt.get();
                    if (sched.isClosed()) {
                        isClosedToday = true;
                    } else {
                        opening = sched.getOpeningTime();
                        closing = sched.getClosingTime();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        boolean isOpen;
        if (isClosedToday) {
            isOpen = false;
        } else if (opening.equals(closing)) {
            isOpen = true;
        } else if (opening.isBefore(closing)) {
            isOpen = !currentTime.isBefore(opening) && !currentTime.isAfter(closing);
        } else {
            // Overnight opening
            isOpen = !currentTime.isBefore(opening) || !currentTime.isAfter(closing);
        }

        String nextSchedule = null;
        if (!isOpen) {
            if (isClosedToday) {
                nextSchedule = "Cerrado por el día de hoy";
            } else {
                nextSchedule = "Abre a las " + opening.format(DateTimeFormatter.ofPattern("HH:mm"));
            }
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
            Map<UUID, EnrichedDishResponse> complianceMap,
            Map<String, DietaryRestriction> allergenTaxonomy,
            Map<String, DietaryRestriction> lifestyleTaxonomy) {

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

        Integer availableStock = dishAvailabilityEngine != null
                ? dishAvailabilityEngine.calculateAvailableStock(dish.getId(), tenantId)
                : null;

        List<String> neutralAllergens = resolveNeutralAllergens(dish, allergenTaxonomy);
        List<String> neutralLifestyles = resolveNeutralLifestyles(dish, lifestyleTaxonomy);

        return new DishCatalogItemDto(
                dish.getId(),
                dish.getName(),
                dish.getDescription(),
                dish.getCategory() != null ? dish.getCategory().getValue() : null,
                price,
                photoUrl,
                isAvailable,
                availableStock,
                isCompliant,
                conflictingRestrictions,
                recipeIngredients,
                neutralAllergens,
                neutralLifestyles
        );
    }

    private List<String> resolveNeutralLifestyles(Dish dish, Map<String, DietaryRestriction> lifestyleTaxonomy) {
        Set<String> codes = dish.getRestrictionCodes();
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> lifestyles = new ArrayList<>();
        for (String code : codes) {
            if (lifestyleTaxonomy.containsKey(code)) {
                lifestyles.add(lifestyleTaxonomy.get(code).getName());
            } else if (FALLBACK_LIFESTYLE_NAMES.containsKey(code)) {
                lifestyles.add(FALLBACK_LIFESTYLE_NAMES.get(code));
            }
        }
        Collections.sort(lifestyles);
        return lifestyles;
    }

    private static final Map<String, String> FALLBACK_LIFESTYLE_NAMES = Map.of(
            "CELIAC", "Sin TACC / Celíaco",
            "VEGAN", "Vegano",
            "VEGETARIAN", "Vegetariano"
    );

    private List<String> resolveNeutralAllergens(Dish dish, Map<String, DietaryRestriction> allergenTaxonomy) {
        Set<String> codes = dish.getRestrictionCodes();
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> allergens = new ArrayList<>();
        for (String code : codes) {
            if (allergenTaxonomy.containsKey(code)) {
                allergens.add(allergenTaxonomy.get(code).getName());
            } else if (FALLBACK_ALLERGEN_NAMES.containsKey(code)) {
                allergens.add(FALLBACK_ALLERGEN_NAMES.get(code));
            }
        }
        Collections.sort(allergens);
        return allergens;
    }

    private static final Map<String, String> FALLBACK_ALLERGEN_NAMES = Map.of(
            "DAIRY", "Lácteos / Lactosa",
            "PEANUTS", "Maní",
            "TREE_NUTS", "Frutos secos",
            "EGGS", "Huevos",
            "FISH", "Pescado",
            "SHELLFISH", "Mariscos / Crustáceos",
            "SOY", "Soja",
            "WHEAT", "Trigo / Gluten"
    );

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
