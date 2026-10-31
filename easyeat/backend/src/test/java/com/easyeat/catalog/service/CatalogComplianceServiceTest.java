package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.dto.CartComplianceRequest;
import com.easyeat.catalog.dto.CartComplianceResponse;
import com.easyeat.catalog.dto.EnrichedDishResponse;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestrictionId;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogComplianceService Tests (T039, US3, COM-4.2.1)")
class CatalogComplianceServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @InjectMocks
    private CatalogComplianceService catalogComplianceService;

    private UUID tenantId;
    private User diner;
    private List<DietaryRestriction> taxonomy;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        diner = new User();
        diner.setId(UUID.randomUUID());
        diner.setEmail("diner@easyeat.com");

        taxonomy = List.of(
                new DietaryRestriction("CELIAC", "Sin TACC / Celíaco", DietaryCategory.DIETARY_LIFESTYLE, "Excluye trigo, avena, cebada y centeno."),
                new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Excluye productos de origen animal."),
                new DietaryRestriction("VEGETARIAN", "Vegetariano", DietaryCategory.DIETARY_LIFESTYLE, "Excluye carnes y pescados."),
                new DietaryRestriction("DAIRY", "Lácteos", DietaryCategory.COMMON_ALLERGEN, "Presencia de lácteos."),
                new DietaryRestriction("PEANUTS", "Maní", DietaryCategory.COMMON_ALLERGEN, "Presencia de maní.")
        );
    }

    @Test
    @DisplayName("Guest fallback: should mark all dishes compliant with zero conflicts when user is null (FR-026)")
    void getEnrichedCatalog_guestUser_returnsAllCompliant() {
        Dish dish1 = createDish(tenantId, "Milanesa", new BigDecimal("7500.00"), Set.of("DAIRY", "WHEAT"));
        Dish dish2 = createDish(tenantId, "Ensalada Verde", new BigDecimal("5000.00"), Set.of("VEGAN", "CELIAC"));
        when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED)).thenReturn(List.of(dish1, dish2));

        List<EnrichedDishResponse> catalog = catalogComplianceService.getEnrichedCatalog(tenantId, null);

        assertThat(catalog).hasSize(2);
        assertThat(catalog.get(0).getIsCompliant()).isTrue();
        assertThat(catalog.get(0).getConflictingRestrictions()).isEmpty();
        assertThat(catalog.get(1).getIsCompliant()).isTrue();
        assertThat(catalog.get(1).getConflictingRestrictions()).isEmpty();
        verifyNoInteractions(userDietaryRestrictionRepository);
    }

    @Test
    @DisplayName("Diner with zero restrictions: should mark all dishes compliant (FR-025)")
    void getEnrichedCatalog_dinerWithoutRestrictions_returnsAllCompliant() {
        Dish dish1 = createDish(tenantId, "Hamburguesa", new BigDecimal("8500.00"), Set.of("DAIRY"));
        when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED)).thenReturn(List.of(dish1));
        when(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).thenReturn(Collections.emptyList());

        List<EnrichedDishResponse> catalog = catalogComplianceService.getEnrichedCatalog(tenantId, diner);

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).getIsCompliant()).isTrue();
        assertThat(catalog.get(0).getConflictingRestrictions()).isEmpty();
    }

    @Test
    @DisplayName("Strict conjunction AND logic: diner with VEGAN and PEANUTS allergen restriction (FR-018)")
    void getEnrichedCatalog_strictConjunction_lifestyleAndAllergen() {
        when(dietaryRestrictionRepository.findAll()).thenReturn(taxonomy);
        when(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).thenReturn(List.of(
                new UserDietaryRestriction(new UserDietaryRestrictionId(diner.getId(), "VEGAN")),
                new UserDietaryRestriction(new UserDietaryRestrictionId(diner.getId(), "PEANUTS"))
        ));

        // Dish 1: Compliant (has VEGAN, does not contain PEANUTS)
        Dish dish1 = createDish(tenantId, "Buddha Bowl", new BigDecimal("7000.00"), Set.of("VEGAN"));
        // Dish 2: Violates VEGAN (does not have VEGAN tag)
        Dish dish2 = createDish(tenantId, "Bife de Chorizo", new BigDecimal("12000.00"), Set.of());
        // Dish 3: Violates PEANUTS (has VEGAN, but contains PEANUTS)
        Dish dish3 = createDish(tenantId, "Curry con Maní", new BigDecimal("8500.00"), Set.of("VEGAN", "PEANUTS"));
        // Dish 4: Violates both VEGAN and PEANUTS
        Dish dish4 = createDish(tenantId, "Postre Mixto", new BigDecimal("4500.00"), Set.of("PEANUTS", "DAIRY"));

        when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED)).thenReturn(List.of(dish1, dish2, dish3, dish4));

        List<EnrichedDishResponse> catalog = catalogComplianceService.getEnrichedCatalog(tenantId, diner);

        assertThat(catalog).hasSize(4);

        // Dish 1
        assertThat(catalog.get(0).getName()).isEqualTo("Buddha Bowl");
        assertThat(catalog.get(0).getIsCompliant()).isTrue();
        assertThat(catalog.get(0).getConflictingRestrictions()).isEmpty();

        // Dish 2
        assertThat(catalog.get(1).getName()).isEqualTo("Bife de Chorizo");
        assertThat(catalog.get(1).getIsCompliant()).isFalse();
        assertThat(catalog.get(1).getConflictingRestrictions()).containsExactly("VEGAN");

        // Dish 3
        assertThat(catalog.get(2).getName()).isEqualTo("Curry con Maní");
        assertThat(catalog.get(2).getIsCompliant()).isFalse();
        assertThat(catalog.get(2).getConflictingRestrictions()).containsExactly("PEANUTS");

        // Dish 4
        assertThat(catalog.get(3).getName()).isEqualTo("Postre Mixto");
        assertThat(catalog.get(3).getIsCompliant()).isFalse();
        assertThat(catalog.get(3).getConflictingRestrictions()).containsExactlyInAnyOrder("VEGAN", "PEANUTS");
    }

    @Test
    @DisplayName("Evaluate Cart compliance: evaluates multiple items accurately against diner profile (FR-015)")
    void evaluateCartCompliance_multipleItems_evaluatesCorrectly() {
        when(dietaryRestrictionRepository.findAll()).thenReturn(taxonomy);
        when(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).thenReturn(List.of(
                new UserDietaryRestriction(new UserDietaryRestrictionId(diner.getId(), "CELIAC")),
                new UserDietaryRestriction(new UserDietaryRestrictionId(diner.getId(), "DAIRY"))
        ));

        Dish dish1 = createDish(tenantId, "Risotto sin TACC", new BigDecimal("9000.00"), Set.of("CELIAC"));
        Dish dish2 = createDish(tenantId, "Pasta con Queso", new BigDecimal("8000.00"), Set.of("DAIRY"));

        when(dishRepository.findById(dish1.getId())).thenReturn(Optional.of(dish1));
        when(dishRepository.findById(dish2.getId())).thenReturn(Optional.of(dish2));

        CartComplianceRequest request = new CartComplianceRequest(List.of(dish1.getId(), dish2.getId()));
        CartComplianceResponse response = catalogComplianceService.evaluateCartCompliance(request, diner);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getDishId()).isEqualTo(dish1.getId());
        assertThat(response.getItems().get(0).getIsCompliant()).isTrue();
        assertThat(response.getItems().get(0).getConflictingRestrictions()).isEmpty();

        assertThat(response.getItems().get(1).getDishId()).isEqualTo(dish2.getId());
        assertThat(response.getItems().get(1).getIsCompliant()).isFalse();
        assertThat(response.getItems().get(1).getConflictingRestrictions()).containsExactlyInAnyOrder("CELIAC", "DAIRY");
    }

    @Test
    @DisplayName("Evaluate Cart compliance: throws DishNotFoundException when dish does not exist")
    void evaluateCartCompliance_dishNotFound_throwsException() {
        UUID unknownDishId = UUID.randomUUID();
        when(userDietaryRestrictionRepository.findByIdUserId(diner.getId())).thenReturn(Collections.emptyList());
        when(dishRepository.findById(unknownDishId)).thenReturn(Optional.empty());

        CartComplianceRequest request = new CartComplianceRequest(List.of(unknownDishId));

        assertThatThrownBy(() -> catalogComplianceService.evaluateCartCompliance(request, diner))
                .isInstanceOf(DishNotFoundException.class)
                .hasMessageContaining(unknownDishId.toString());
    }

    private Dish createDish(UUID tenantId, String name, BigDecimal price, Set<String> restrictions) {
        Dish dish = new Dish(tenantId, name, "Descripción de " + name, price, true);
        dish.setId(UUID.randomUUID());
        dish.setStatus(DishStatus.PUBLISHED);
        dish.setRetailPrice(price);
        dish.setRestrictionCodes(new HashSet<>(restrictions));
        return dish;
    }
}
