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
import com.easyeat.dish.repository.DishPhotoRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.repository.RecipeLineWithIngredientProjection;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.operations.cache.OperatingScheduleCache;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.profile.cache.DietaryTaxonomyCache;
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Map<String, String> FALLBACK_LIFESTYLE_NAMES = Map.of(
            "VEGAN", "Vegano",
            "VEGETARIAN", "Vegetariano"
    );

    private static final Map<String, String> FALLBACK_ALLERGEN_NAMES = Map.of(
            "CELIAC", "Sin TACC / Celíaco",
            "DAIRY", "Lácteos / Lactosa",
            "PEANUTS", "Maní",
            "TREE_NUTS", "Frutos secos",
            "EGGS", "Huevos",
            "FISH", "Pescado",
            "SHELLFISH", "Mariscos / Crustáceos",
            "SOY", "Soja",
            "WHEAT", "Trigo / Gluten"
    );

    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final CatalogComplianceService catalogComplianceService;
    private final DishAvailabilityEngine dishAvailabilityEngine;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final OperatingScheduleRepository operatingScheduleRepository;
    private final DietaryTaxonomyCache dietaryTaxonomyCache;
    private final OperatingScheduleCache operatingScheduleCache;
    private final UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository,
                ingredientRepository, catalogComplianceService, null, null, null, null, null, null);
    }

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            DishAvailabilityEngine dishAvailabilityEngine) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository,
                ingredientRepository, catalogComplianceService, dishAvailabilityEngine, null, null, null, null, null);
    }

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            DishAvailabilityEngine dishAvailabilityEngine,
            DietaryRestrictionRepository dietaryRestrictionRepository) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository,
                ingredientRepository, catalogComplianceService, dishAvailabilityEngine, dietaryRestrictionRepository,
                null, null, null, null);
    }

    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            DishAvailabilityEngine dishAvailabilityEngine,
            DietaryRestrictionRepository dietaryRestrictionRepository,
            OperatingScheduleRepository operatingScheduleRepository) {
        this(establishmentRepository, dishRepository, dishPhotoRepository, dishRecipeIngredientRepository,
                ingredientRepository, catalogComplianceService, dishAvailabilityEngine, dietaryRestrictionRepository,
                operatingScheduleRepository, null, null, null);
    }

    @Autowired
    public DinerCatalogService(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishPhotoRepository dishPhotoRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            CatalogComplianceService catalogComplianceService,
            @Autowired(required = false) DishAvailabilityEngine dishAvailabilityEngine,
            @Autowired(required = false) DietaryRestrictionRepository dietaryRestrictionRepository,
            @Autowired(required = false) OperatingScheduleRepository operatingScheduleRepository,
            @Autowired(required = false) DietaryTaxonomyCache dietaryTaxonomyCache,
            @Autowired(required = false) OperatingScheduleCache operatingScheduleCache,
            @Autowired(required = false) UserDietaryRestrictionRepository userDietaryRestrictionRepository) {
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.catalogComplianceService = catalogComplianceService;
        this.dishAvailabilityEngine = dishAvailabilityEngine;
        this.dietaryRestrictionRepository = dietaryRestrictionRepository;
        this.operatingScheduleRepository = operatingScheduleRepository;
        this.dietaryTaxonomyCache = dietaryTaxonomyCache;
        this.operatingScheduleCache = operatingScheduleCache;
        this.userDietaryRestrictionRepository = userDietaryRestrictionRepository;
    }

    public DinerCatalogResponse getCatalog(UUID tenantId, User user) {
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(() -> new EstablishmentNotFoundException(tenantId));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(
                    tenantId,
                    establishment.getStatus() != null ? establishment.getStatus().name() : "INACTIVE");
        }

        EstablishmentDetailDto establishmentDetail = buildEstablishmentDetail(establishment);

        List<Dish> publishedDishes = dishRepository.findPublicCatalogDishesWithRestrictions(tenantId);
        if (publishedDishes == null || publishedDishes.isEmpty()) {
            return new DinerCatalogResponse(establishmentDetail, Collections.emptyList());
        }

        List<UUID> dishIds = publishedDishes.stream().map(Dish::getId).filter(Objects::nonNull).toList();
        Set<UUID> dishIdsWithPhoto = dishIds.isEmpty()
                ? Collections.emptySet()
                : safeSet(dishPhotoRepository.findDishIdsWithPhoto(dishIds));

        List<RecipeLineWithIngredientProjection> recipeLines = dishIds.isEmpty()
                ? Collections.emptyList()
                : safeList(dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(dishIds, tenantId));

        Map<UUID, List<String>> recipeIngredientsByDishId = groupRecipeIngredientNames(recipeLines);
        Map<UUID, DishAvailabilityEngine.DishAvailabilityResult> availabilityByDishId = dishAvailabilityEngine != null
                ? dishAvailabilityEngine.computeBatchAvailability(publishedDishes, tenantId, recipeLines)
                : Collections.emptyMap();

        Map<String, DietaryRestriction> fullTaxonomy = resolveFullTaxonomy();
        Map<String, DietaryRestriction> allergenTaxonomy = filterTaxonomyByCategory(fullTaxonomy, DietaryCategory.COMMON_ALLERGEN);
        Map<String, DietaryRestriction> lifestyleTaxonomy = filterTaxonomyByCategory(fullTaxonomy, DietaryCategory.DIETARY_LIFESTYLE);

        Map<UUID, EnrichedDishResponse> complianceMap = resolveComplianceMap(tenantId, user, publishedDishes, fullTaxonomy);

        Map<DishCategory, List<DishCatalogItemDto>> dishesByCategory = new EnumMap<>(DishCategory.class);
        for (Dish dish : publishedDishes) {
            if (dish.getCategory() == null) {
                continue;
            }

            DishCatalogItemDto itemDto = buildDishCatalogItem(
                    dish,
                    dishIdsWithPhoto,
                    recipeIngredientsByDishId,
                    availabilityByDishId,
                    complianceMap,
                    allergenTaxonomy,
                    lifestyleTaxonomy
            );
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

    private <T> Set<T> safeSet(Set<T> set) {
        return set != null ? set : Collections.emptySet();
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private Map<UUID, List<String>> groupRecipeIngredientNames(List<RecipeLineWithIngredientProjection> recipeLines) {
        Map<UUID, List<String>> grouped = new HashMap<>();
        for (RecipeLineWithIngredientProjection line : recipeLines) {
            if (line.getDishId() == null) {
                continue;
            }
            String name = line.getIngredientName();
            if (name != null && !name.isBlank()) {
                grouped.computeIfAbsent(line.getDishId(), k -> new ArrayList<>()).add(name);
            }
        }
        return grouped;
    }

    private Map<String, DietaryRestriction> resolveFullTaxonomy() {
        if (dietaryTaxonomyCache != null) {
            try {
                return dietaryTaxonomyCache.getMapByCode();
            } catch (Exception ignored) {
            }
        }
        if (dietaryRestrictionRepository == null) {
            return Collections.emptyMap();
        }
        try {
            return dietaryRestrictionRepository.findAll().stream()
                    .collect(Collectors.toMap(DietaryRestriction::getCode, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, DietaryRestriction> filterTaxonomyByCategory(
            Map<String, DietaryRestriction> fullTaxonomy,
            DietaryCategory category) {
        if (fullTaxonomy == null || fullTaxonomy.isEmpty()) {
            return Collections.emptyMap();
        }
        return fullTaxonomy.values().stream()
                .filter(dr -> dr.getCategory() == category)
                .collect(Collectors.toMap(DietaryRestriction::getCode, Function.identity(), (a, b) -> a));
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

        Optional<EstablishmentOperatingSchedule> scheduleOpt = Optional.empty();
        if (operatingScheduleCache != null) {
            try {
                scheduleOpt = Optional.ofNullable(
                        operatingScheduleCache.getWeekMap(establishment.getId()).get(now.getDayOfWeek()));
            } catch (Exception ignored) {
            }
        } else if (operatingScheduleRepository != null) {
            try {
                scheduleOpt = operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), now.getDayOfWeek());
            } catch (Exception ignored) {
            }
        }

        if (scheduleOpt.isPresent()) {
            var sched = scheduleOpt.get();
            if (sched.isClosed()) {
                isClosedToday = true;
            } else {
                opening = sched.getOpeningTime();
                closing = sched.getClosingTime();
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

    private Map<UUID, EnrichedDishResponse> resolveComplianceMap(
            UUID tenantId,
            User user,
            List<Dish> publishedDishes,
            Map<String, DietaryRestriction> taxonomy) {
        if (user == null || user.getId() == null) {
            return Collections.emptyMap();
        }

        if (userDietaryRestrictionRepository != null) {
            try {
                Set<String> activeRestrictions = userDietaryRestrictionRepository.findByIdUserId(user.getId()).stream()
                        .map(udr -> udr.getId().getRestrictionCode())
                        .collect(Collectors.toSet());
                if (activeRestrictions.isEmpty()) {
                    return Collections.emptyMap();
                }

                Map<UUID, EnrichedDishResponse> map = new HashMap<>();
                for (Dish dish : publishedDishes) {
                    List<String> conflicts = new ArrayList<>();
                    Set<String> dishRestrictions = dish.getRestrictionCodes() != null
                            ? dish.getRestrictionCodes()
                            : Collections.emptySet();
                    for (String userCode : activeRestrictions) {
                        DietaryRestriction meta = taxonomy.get(userCode);
                        if (meta == null) {
                            continue;
                        }
                        if (meta.getCategory() == DietaryCategory.DIETARY_LIFESTYLE) {
                            if (!dishRestrictions.contains(userCode)) {
                                conflicts.add(userCode);
                            }
                        } else if (meta.getCategory() == DietaryCategory.COMMON_ALLERGEN) {
                            if (dishRestrictions.contains(userCode)) {
                                conflicts.add(userCode);
                            }
                        }
                    }
                    map.put(dish.getId(), new EnrichedDishResponse(
                            dish.getId(),
                            dish.getTenantId(),
                            dish.getName(),
                            dish.getDescription(),
                            dish.getRetailPrice(),
                            dish.getIsActive(),
                            conflicts.isEmpty(),
                            conflicts
                    ));
                }
                return map;
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        }

        if (catalogComplianceService == null) {
            return Collections.emptyMap();
        }
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
            Set<UUID> dishIdsWithPhoto,
            Map<UUID, List<String>> recipeIngredientsByDishId,
            Map<UUID, DishAvailabilityEngine.DishAvailabilityResult> availabilityByDishId,
            Map<UUID, EnrichedDishResponse> complianceMap,
            Map<String, DietaryRestriction> allergenTaxonomy,
            Map<String, DietaryRestriction> lifestyleTaxonomy) {

        String photoUrl = dishIdsWithPhoto.contains(dish.getId())
                ? "/api/v1/dishes/" + dish.getId() + "/photo"
                : null;

        List<String> recipeIngredients = recipeIngredientsByDishId.getOrDefault(dish.getId(), Collections.emptyList());

        EnrichedDishResponse compliance = complianceMap.get(dish.getId());
        boolean isCompliant = compliance == null || Boolean.TRUE.equals(compliance.getIsCompliant());
        List<String> conflictingRestrictions = compliance != null && compliance.getConflictingRestrictions() != null
                ? compliance.getConflictingRestrictions()
                : Collections.emptyList();

        BigDecimal price = dish.getRetailPrice() != null ? dish.getRetailPrice() : BigDecimal.ZERO;
        DishAvailabilityEngine.DishAvailabilityResult availabilityResult = availabilityByDishId.get(dish.getId());
        boolean isAvailable = dishAvailabilityEngine != null
                ? (availabilityResult != null && availabilityResult.isAvailable())
                : (dish.getIsActive() != null && dish.getIsActive());

        Integer availableStock = dishAvailabilityEngine != null
                ? (availabilityResult != null ? availabilityResult.availableStock() : 0)
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
}
