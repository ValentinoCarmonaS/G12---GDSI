package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.dto.CartItemEvaluation;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogComplianceService {

    private final DishRepository dishRepository;
    private final UserDietaryRestrictionRepository userDietaryRestrictionRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;

    public CatalogComplianceService(DishRepository dishRepository,
                                  UserDietaryRestrictionRepository userDietaryRestrictionRepository,
                                  DietaryRestrictionRepository dietaryRestrictionRepository) {
        this.dishRepository = dishRepository;
        this.userDietaryRestrictionRepository = userDietaryRestrictionRepository;
        this.dietaryRestrictionRepository = dietaryRestrictionRepository;
    }

    public List<EnrichedDishResponse> getEnrichedCatalog(UUID tenantId, User user) {
        List<Dish> dishes = dishRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (dishes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> activeUserRestrictions = resolveActiveRestrictions(user);
        Map<String, DietaryRestriction> taxonomy = resolveTaxonomy();

        return dishes.stream()
                .map(dish -> evaluateDish(dish, activeUserRestrictions, taxonomy))
                .collect(Collectors.toList());
    }

    public CartComplianceResponse evaluateCartCompliance(CartComplianceRequest request, User user) {
        if (request.getDishIds() == null || request.getDishIds().isEmpty()) {
            return new CartComplianceResponse(Collections.emptyList());
        }

        Set<String> activeUserRestrictions = resolveActiveRestrictions(user);
        Map<String, DietaryRestriction> taxonomy = resolveTaxonomy();

        List<CartItemEvaluation> evaluations = new ArrayList<>();
        for (UUID dishId : request.getDishIds()) {
            Dish dish = dishRepository.findById(dishId)
                    .orElseThrow(() -> new DishNotFoundException(dishId));

            EnrichedDishResponse enriched = evaluateDish(dish, activeUserRestrictions, taxonomy);
            evaluations.add(new CartItemEvaluation(
                    dish.getId(),
                    enriched.getIsCompliant(),
                    enriched.getConflictingRestrictions()
            ));
        }

        return new CartComplianceResponse(evaluations);
    }

    private EnrichedDishResponse evaluateDish(Dish dish, Set<String> activeUserRestrictions,
                                              Map<String, DietaryRestriction> taxonomy) {
        if (activeUserRestrictions.isEmpty()) {
            return new EnrichedDishResponse(
                    dish.getId(),
                    dish.getTenantId(),
                    dish.getName(),
                    dish.getDescription(),
                    dish.getPrice(),
                    dish.getIsActive(),
                    true,
                    Collections.emptyList()
            );
        }

        List<String> conflicts = new ArrayList<>();
        Set<String> dishRestrictions = dish.getRestrictionCodes() != null ? dish.getRestrictionCodes() : Collections.emptySet();

        for (String userCode : activeUserRestrictions) {
            DietaryRestriction meta = taxonomy.get(userCode);
            if (meta == null) {
                continue;
            }

            if (meta.getCategory() == DietaryCategory.DIETARY_LIFESTYLE) {
                // If lifestyle: dish MUST have the tag to be compliant
                if (!dishRestrictions.contains(userCode)) {
                    conflicts.add(userCode);
                }
            } else if (meta.getCategory() == DietaryCategory.COMMON_ALLERGEN) {
                // If allergen: dish MUST NOT have the allergen tag
                if (dishRestrictions.contains(userCode)) {
                    conflicts.add(userCode);
                }
            }
        }

        boolean isCompliant = conflicts.isEmpty();
        return new EnrichedDishResponse(
                dish.getId(),
                dish.getTenantId(),
                dish.getName(),
                dish.getDescription(),
                dish.getPrice(),
                dish.getIsActive(),
                isCompliant,
                conflicts
        );
    }

    private Set<String> resolveActiveRestrictions(User user) {
        if (user == null || user.getId() == null) {
            return Collections.emptySet();
        }
        return userDietaryRestrictionRepository.findByIdUserId(user.getId()).stream()
                .map(udr -> udr.getId().getRestrictionCode())
                .collect(Collectors.toSet());
    }

    private Map<String, DietaryRestriction> resolveTaxonomy() {
        return dietaryRestrictionRepository.findAll().stream()
                .collect(Collectors.toMap(DietaryRestriction::getCode, Function.identity(), (a, b) -> a));
    }
}
