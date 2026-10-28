package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.CatalogTestFixtures;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.CategoryGroupDto;
import com.easyeat.catalog.dto.DinerCatalogResponse;
import com.easyeat.catalog.dto.DishCatalogItemDto;
import com.easyeat.catalog.dto.EnrichedDishResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DinerCatalogService Unit Tests (US2 - COM-4.1.1)")
class DinerCatalogServiceTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishPhotoRepository dishPhotoRepository;

    @Mock
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private CatalogComplianceService catalogComplianceService;

    private DinerCatalogService dinerCatalogService;

    private UUID tenantId;
    private Establishment activeEstablishment;
    private User mockUser;

    @BeforeEach
    void setUp() {
        dinerCatalogService = new DinerCatalogService(
                establishmentRepository,
                dishRepository,
                dishPhotoRepository,
                dishRecipeIngredientRepository,
                ingredientRepository,
                catalogComplianceService
        );

        tenantId = CatalogTestFixtures.DEFAULT_TENANT_ID;
        activeEstablishment = new Establishment(
                tenantId,
                "La Farola de Palermo",
                EstablishmentStatus.ACTIVE,
                LocalTime.of(0, 0),
                LocalTime.of(23, 59, 59),
                "America/Argentina/Buenos_Aires"
        );

        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Establishment Validation & Error Handling")
    class EstablishmentValidationTests {

        @Test
        @DisplayName("Should throw EstablishmentNotFoundException when establishment does not exist")
        void shouldThrowWhenEstablishmentNotFound() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dinerCatalogService.getCatalog(tenantId, mockUser))
                    .isInstanceOf(EstablishmentNotFoundException.class)
                    .hasMessageContaining(tenantId.toString());
        }

        @Test
        @DisplayName("Should throw EstablishmentInactiveException when establishment is PAUSED")
        void shouldThrowWhenEstablishmentIsPaused() {
            Establishment paused = new Establishment(tenantId, "Parrilla Pausada", EstablishmentStatus.PAUSED,
                    LocalTime.of(11, 0), LocalTime.of(23, 0), "America/Argentina/Buenos_Aires");
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(paused));

            assertThatThrownBy(() -> dinerCatalogService.getCatalog(tenantId, mockUser))
                    .isInstanceOf(EstablishmentInactiveException.class);
        }

        @Test
        @DisplayName("Should throw EstablishmentInactiveException when establishment is DISABLED")
        void shouldThrowWhenEstablishmentIsDisabled() {
            Establishment disabled = new Establishment(tenantId, "Local Deshabilitado", EstablishmentStatus.DISABLED,
                    LocalTime.of(11, 0), LocalTime.of(23, 0), "America/Argentina/Buenos_Aires");
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> dinerCatalogService.getCatalog(tenantId, mockUser))
                    .isInstanceOf(EstablishmentInactiveException.class);
        }
    }

    @Nested
    @DisplayName("Category Grouping & Dish Filtering")
    class CategoryGroupingTests {

        @Test
        @DisplayName("Should group dishes by gastronomic category and exclude empty categories")
        void shouldGroupDishesAndHideEmptyCategories() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(activeEstablishment));

            Dish mainDish = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Milanesa Napolitana")
                    .category(DishCategory.PLATOS_PRINCIPALES)
                    .status(DishStatus.PUBLISHED)
                    .price(BigDecimal.valueOf(5500.00))
                    .build();

            Dish dessert = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Flan Mixto")
                    .category(DishCategory.POSTRES)
                    .status(DishStatus.PUBLISHED)
                    .price(BigDecimal.valueOf(2200.00))
                    .build();

            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of(mainDish, dessert));

            when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), eq(mockUser)))
                    .thenReturn(List.of(
                            new EnrichedDishResponse(mainDish.getId(), tenantId, mainDish.getName(), mainDish.getDescription(), mainDish.getPrice(), true, true, List.of()),
                            new EnrichedDishResponse(dessert.getId(), tenantId, dessert.getName(), dessert.getDescription(), dessert.getPrice(), true, true, List.of())
                    ));

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            assertThat(response).isNotNull();
            assertThat(response.getEstablishment().getName()).isEqualTo("La Farola de Palermo");
            assertThat(response.getCategories()).hasSize(2);

            List<String> categoryNames = response.getCategories().stream().map(CategoryGroupDto::getCategory).toList();
            assertThat(categoryNames).containsExactly("Platos principales", "Postres");
            assertThat(categoryNames).doesNotContain("Entradas", "Bebidas", "Ensaladas");
        }

        @Test
        @DisplayName("Should exclude dishes that are not PUBLISHED even if isActive is true")
        void shouldExcludeNonPublishedDishes() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(activeEstablishment));

            Dish publishedDish = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Empanada Criolla")
                    .category(DishCategory.ENTRADAS)
                    .status(DishStatus.PUBLISHED)
                    .build();

            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of(publishedDish));

            when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), eq(mockUser)))
                    .thenReturn(List.of(
                            new EnrichedDishResponse(publishedDish.getId(), tenantId, publishedDish.getName(), publishedDish.getDescription(), publishedDish.getPrice(), true, true, List.of())
                    ));

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            assertThat(response.getCategories()).hasSize(1);
            assertThat(response.getCategories().get(0).getCategory()).isEqualTo("Entradas");
            assertThat(response.getCategories().get(0).getDishes()).hasSize(1);
            assertThat(response.getCategories().get(0).getDishes().get(0).getName()).isEqualTo("Empanada Criolla");
        }
    }

    @Nested
    @DisplayName("Photo URL & Recipe Ingredients Enrichment")
    class PhotoAndIngredientsTests {

        @Test
        @DisplayName("Should format photoUrl when photo exists and return null when photo is missing")
        void shouldFormatPhotoUrlCorrectly() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(activeEstablishment));

            Dish dishWithPhoto = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Café Expresso")
                    .category(DishCategory.BEBIDAS)
                    .status(DishStatus.PUBLISHED)
                    .build();

            Dish dishWithoutPhoto = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Agua Mineral")
                    .category(DishCategory.BEBIDAS)
                    .status(DishStatus.PUBLISHED)
                    .build();

            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of(dishWithPhoto, dishWithoutPhoto));

            when(dishPhotoRepository.existsByDishId(dishWithPhoto.getId())).thenReturn(true);
            when(dishPhotoRepository.existsByDishId(dishWithoutPhoto.getId())).thenReturn(false);

            when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), eq(mockUser)))
                    .thenReturn(List.of(
                            new EnrichedDishResponse(dishWithPhoto.getId(), tenantId, dishWithPhoto.getName(), null, dishWithPhoto.getPrice(), true, true, List.of()),
                            new EnrichedDishResponse(dishWithoutPhoto.getId(), tenantId, dishWithoutPhoto.getName(), null, dishWithoutPhoto.getPrice(), true, true, List.of())
                    ));

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            List<DishCatalogItemDto> beverages = response.getCategories().get(0).getDishes();
            DishCatalogItemDto withPhotoDto = beverages.stream()
                    .filter(d -> d.getId().equals(dishWithPhoto.getId())).findFirst().orElseThrow();
            DishCatalogItemDto withoutPhotoDto = beverages.stream()
                    .filter(d -> d.getId().equals(dishWithoutPhoto.getId())).findFirst().orElseThrow();

            assertThat(withPhotoDto.getPhotoUrl()).isEqualTo("/api/v1/dishes/" + dishWithPhoto.getId() + "/photo");
            assertThat(withoutPhotoDto.getPhotoUrl()).isNull();
        }

        @Test
        @DisplayName("Should resolve recipe ingredient names for each dish")
        void shouldPopulateRecipeIngredients() {
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(activeEstablishment));

            Dish dish = CatalogTestFixtures.catalogDishBuilder()
                    .tenantId(tenantId)
                    .name("Ensalada César")
                    .category(DishCategory.ENSALADAS)
                    .status(DishStatus.PUBLISHED)
                    .build();

            UUID ingId1 = UUID.randomUUID();
            UUID ingId2 = UUID.randomUUID();

            DishRecipeIngredient line1 = CatalogTestFixtures.createRecipeIngredientLine(dish.getId(), ingId1, BigDecimal.valueOf(0.200));
            DishRecipeIngredient line2 = CatalogTestFixtures.createRecipeIngredientLine(dish.getId(), ingId2, BigDecimal.valueOf(0.050));

            Ingredient ing1 = new Ingredient();
            ing1.setName("Lechuga Capuchina");
            Ingredient ing2 = new Ingredient();
            ing2.setName("Queso Parmesano");

            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of(dish));

            when(dishRecipeIngredientRepository.findByIdDishId(dish.getId()))
                    .thenReturn(List.of(line1, line2));

            when(ingredientRepository.findByIdAndTenantId(ingId1, tenantId)).thenReturn(Optional.of(ing1));
            when(ingredientRepository.findByIdAndTenantId(ingId2, tenantId)).thenReturn(Optional.of(ing2));

            when(catalogComplianceService.getEnrichedCatalog(eq(tenantId), eq(mockUser)))
                    .thenReturn(List.of(
                            new EnrichedDishResponse(dish.getId(), tenantId, dish.getName(), null, dish.getPrice(), true, true, List.of())
                    ));

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            DishCatalogItemDto item = response.getCategories().get(0).getDishes().get(0);
            assertThat(item.getRecipeIngredients()).containsExactlyInAnyOrder("Lechuga Capuchina", "Queso Parmesano");
        }
    }

    @Nested
    @DisplayName("Operating Hours & Read-Only Evaluation")
    class OperatingHoursTests {

        @Test
        @DisplayName("Should evaluate isOpen as true when current time is within operating hours")
        void shouldEvaluateIsOpenTrueWhenWithinSchedule() {
            // Hours: 00:00 to 23:59:59 (always open)
            Establishment alwaysOpen = new Establishment(tenantId, "Siempre Abierto", EstablishmentStatus.ACTIVE,
                    LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "America/Argentina/Buenos_Aires");
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(alwaysOpen));
            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of());

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            assertThat(response.getEstablishment().getIsOpen()).isTrue();
            assertThat(response.getEstablishment().getNextOpeningSchedule()).isNull();
        }

        @Test
        @DisplayName("Should evaluate isOpen as false and populate nextOpeningSchedule when outside hours")
        void shouldEvaluateIsOpenFalseWhenOutsideSchedule() {
            // Hours: 04:00 to 04:01 (closed almost always)
            Establishment closedNow = new Establishment(tenantId, "Cerrado Casi Siempre", EstablishmentStatus.ACTIVE,
                    LocalTime.of(4, 0), LocalTime.of(4, 1), "America/Argentina/Buenos_Aires");
            when(establishmentRepository.findById(tenantId)).thenReturn(Optional.of(closedNow));
            when(dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED))
                    .thenReturn(List.of());

            DinerCatalogResponse response = dinerCatalogService.getCatalog(tenantId, mockUser);

            assertThat(response.getEstablishment().getIsOpen()).isFalse();
            assertThat(response.getEstablishment().getNextOpeningSchedule()).isNotNull();
            assertThat(response.getEstablishment().getNextOpeningSchedule()).contains("04:00");
        }
    }
}
