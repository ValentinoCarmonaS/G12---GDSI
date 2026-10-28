package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishCategory;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.DuplicateDishNameException;
import com.easyeat.dish.repository.DishPhotoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishBasicServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishPhotoRepository dishPhotoRepository;

    @InjectMocks
    private DishService dishService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID chefId = DishTestFixtures.DEFAULT_CHEF_ID;
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Create Dish Draft (FR-001, FR-002, FR-003, CHF-2.2.1)")
    class CreateDishDraftTests {

        @Test
        @DisplayName("Should trim whitespace from name, set DRAFT status, and persist draft")
        void createDraft_shouldTrimWhitespaceFromNameAndAssignDraftStatus() {
            String untrimmedName = "   Risotto de Hongos Portobello   ";
            String expectedTrimmedName = "Risotto de Hongos Portobello";
            CreateDishRequest request = new CreateDishRequest(
                    untrimmedName,
                    DishCategory.PLATOS_PRINCIPALES,
                    "Arroz arborio con hongos salteados"
            );

            when(dishRepository.existsByTenantIdAndNameLowerTrimmed(tenantId, expectedTrimmedName)).thenReturn(false);
            when(dishRepository.save(any(Dish.class))).thenAnswer(invocation -> {
                Dish d = invocation.getArgument(0);
                d.setId(dishId);
                d.setCreatedAt(Instant.now());
                d.setUpdatedAt(Instant.now());
                return d;
            });

            DishDetailResponse response = dishService.createDraft(request, chefId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(dishId);
            assertThat(response.name()).isEqualTo(expectedTrimmedName);
            assertThat(response.category()).isEqualTo(DishCategory.PLATOS_PRINCIPALES);
            assertThat(response.status()).isEqualTo(DishStatus.DRAFT);
            assertThat(response.allergensConfirmed()).isFalse();
            assertThat(response.createdById()).isEqualTo(chefId);

            ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
            verify(dishRepository).save(captor.capture());
            Dish savedDish = captor.getValue();
            assertThat(savedDish.getName()).isEqualTo(expectedTrimmedName);
            assertThat(savedDish.getTenantId()).isEqualTo(tenantId);
            assertThat(savedDish.getStatus()).isEqualTo(DishStatus.DRAFT);
            assertThat(savedDish.getCreatedBy()).isEqualTo(chefId);
        }

        @Test
        @DisplayName("Should reject draft creation when name already exists in same tenant (case-insensitive trimmed)")
        void createDraft_shouldThrowDuplicateDishNameException_whenNameAlreadyExistsInSameTenant() {
            CreateDishRequest request = new CreateDishRequest(
                    "risotto de hongos portobello",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Descripción válida"
            );

            when(dishRepository.existsByTenantIdAndNameLowerTrimmed(eq(tenantId), eq("risotto de hongos portobello")))
                    .thenReturn(true);

            assertThatThrownBy(() -> dishService.createDraft(request, chefId))
                    .isInstanceOf(DuplicateDishNameException.class)
                    .hasMessageContaining("risotto de hongos portobello");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name exceeds 100 characters")
        void createDraft_shouldThrowException_whenNameExceeds100Characters() {
            String longName = "A".repeat(101);
            CreateDishRequest request = new CreateDishRequest(
                    longName,
                    DishCategory.PLATOS_PRINCIPALES,
                    "Descripción"
            );

            assertThatThrownBy(() -> dishService.createDraft(request, chefId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when name is empty or only whitespace")
        void createDraft_shouldThrowException_whenNameIsBlank() {
            CreateDishRequest request = new CreateDishRequest(
                    "    ",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Descripción"
            );

            assertThatThrownBy(() -> dishService.createDraft(request, chefId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombre");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when category is null")
        void createDraft_shouldThrowException_whenCategoryIsNull() {
            CreateDishRequest request = new CreateDishRequest(
                    "Plato Válido",
                    null,
                    "Descripción"
            );

            assertThatThrownBy(() -> dishService.createDraft(request, chefId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoría");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when description exceeds 500 characters")
        void createDraft_shouldThrowException_whenDescriptionExceeds500Characters() {
            String longDescription = "D".repeat(501);
            CreateDishRequest request = new CreateDishRequest(
                    "Plato Válido",
                    DishCategory.PLATOS_PRINCIPALES,
                    longDescription
            );

            assertThatThrownBy(() -> dishService.createDraft(request, chefId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("500");

            verify(dishRepository, never()).save(any(Dish.class));
        }
    }

    @Nested
    @DisplayName("Update Basic Dish Identity (FR-005, FR-006, CHF-2.2.1)")
    class UpdateDishIdentityTests {

        @Test
        @DisplayName("Should update name, category, and description when dish is in DRAFT status")
        void updateBasicInfo_shouldUpdateFields_whenInDraftStatus() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            UpdateDishRequest request = new UpdateDishRequest(
                    "  Risotto Especial  ",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Nueva descripción"
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
            when(dishRepository.existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, "Risotto Especial", dishId))
                    .thenReturn(false);
            when(dishRepository.save(any(Dish.class))).thenAnswer(i -> i.getArgument(0));

            DishDetailResponse response = dishService.updateBasicInfo(dishId, request);

            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Risotto Especial");
            assertThat(response.description()).isEqualTo("Nueva descripción");
            assertThat(dish.getName()).isEqualTo("Risotto Especial");
        }

        @Test
        @DisplayName("Should throw DuplicateDishNameException when updated name collides with another dish in tenant")
        void updateBasicInfo_shouldThrowDuplicateException_whenCollidesWithAnotherDish() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            UpdateDishRequest request = new UpdateDishRequest(
                    "Plato Duplicado",
                    DishCategory.ENTRADAS,
                    null
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
            when(dishRepository.existsByTenantIdAndNameLowerTrimmedAndIdNot(tenantId, "Plato Duplicado", dishId))
                    .thenReturn(true);

            assertThatThrownBy(() -> dishService.updateBasicInfo(dishId, request))
                    .isInstanceOf(DuplicateDishNameException.class);

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw DishLockedException when attempting to update dish in PENDING_PRICE status")
        void updateBasicInfo_shouldThrowDishLockedException_whenDishInPendingPrice() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.PENDING_PRICE);
            dish.setTenantId(tenantId);

            UpdateDishRequest request = new UpdateDishRequest(
                    "Intento de Modificación",
                    DishCategory.PLATOS_PRINCIPALES,
                    "Desc"
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishService.updateBasicInfo(dishId, request))
                    .isInstanceOf(DishLockedException.class)
                    .hasMessageContaining("PENDING_PRICE");

            verify(dishRepository, never()).save(any(Dish.class));
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when dish ID does not exist in tenant")
        void updateBasicInfo_shouldThrowNotFound_whenDishDoesNotExist() {
            UpdateDishRequest request = new UpdateDishRequest(
                    "Plato Inexistente",
                    DishCategory.PLATOS_PRINCIPALES,
                    null
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishService.updateBasicInfo(dishId, request))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Fetch & List Dish Drafts (FR-021, CHF-2.2.1, CHF-2.2.4)")
    class FetchAndListDishesTests {

        @Test
        @DisplayName("Should return dish detail with completeness check when found")
        void getDishById_shouldReturnDishDetail() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setTenantId(tenantId);
            dish.setStatus(DishStatus.DRAFT);
            dish.setAllergensConfirmed(false);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            DishDetailResponse response = dishService.getDishById(dishId);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(dishId);
            assertThat(response.isCompleteForReview()).isFalse();
            assertThat(response.missingConditions()).isNotEmpty();
        }

        @Test
        @DisplayName("Should return paginated list of establishment dishes with status filter")
        void listDishes_shouldReturnPaginatedResults() {
            Dish dish1 = DishTestFixtures.createStandardDish();
            dish1.setStatus(DishStatus.DRAFT);
            dish1.setTenantId(tenantId);

            Pageable pageable = PageRequest.of(0, 20);
            when(dishRepository.findByTenantIdAndStatus(tenantId, DishStatus.DRAFT, pageable))
                    .thenReturn(new PageImpl<>(List.of(dish1), pageable, 1));

            DishPageResponse response = dishService.listDishes("DRAFT", pageable);

            assertThat(response).isNotNull();
            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().getFirst().name()).isEqualTo(dish1.getName());
        }

        @Test
        @DisplayName("Should return all dishes when status filter is ALL or omitted")
        void listDishes_shouldReturnAllDishes_whenFilterIsAll() {
            Dish dish1 = DishTestFixtures.createStandardDish();
            dish1.setStatus(DishStatus.DRAFT);
            dish1.setTenantId(tenantId);

            Pageable pageable = PageRequest.of(0, 20);
            when(dishRepository.findByTenantId(tenantId, pageable))
                    .thenReturn(new PageImpl<>(List.of(dish1), pageable, 1));

            DishPageResponse response = dishService.listDishes("ALL", pageable);

            assertThat(response).isNotNull();
            assertThat(response.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Delete Dish Draft (FR-007, CHF-2.2.1)")
    class DeleteDishDraftTests {

        @Test
        @DisplayName("Should permanently remove draft and delete associated photo when in DRAFT status")
        void deleteDraft_shouldDeleteDishAndPhoto_whenInDraftStatus() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            dishService.deleteDraft(dishId);

            verify(dishPhotoRepository).deleteByDishId(dishId);
            verify(dishRepository).delete(dish);
        }

        @Test
        @DisplayName("Should throw DishLockedException when trying to delete dish in PENDING_PRICE status")
        void deleteDraft_shouldThrowDishLockedException_whenInPendingPriceStatus() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.PENDING_PRICE);
            dish.setTenantId(tenantId);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishService.deleteDraft(dishId))
                    .isInstanceOf(DishLockedException.class);

            verify(dishPhotoRepository, never()).deleteByDishId(any());
            verify(dishRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when dish to delete does not exist")
        void deleteDraft_shouldThrowNotFound_whenDishDoesNotExist() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishService.deleteDraft(dishId))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }
}
