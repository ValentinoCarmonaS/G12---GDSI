package com.easyeat.inventory.service;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.*;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.exception.DuplicateIngredientNameException;
import com.easyeat.inventory.exception.InvalidLifecycleTransitionException;
import com.easyeat.inventory.exception.UnitOfMeasureModificationException;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private IngredientService ingredientService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("createIngredient - successful creation initializes ACTIVE status and zero stock")
    void createIngredient_success() {
        CreateIngredientRequest request = new CreateIngredientRequest(
                "  Roma Tomato  ",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                new BigDecimal("1250.5000")
        );

        when(ingredientRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Roma Tomato")).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> {
            Ingredient saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AdminIngredientDetailResponse response = ingredientService.createIngredient(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Roma Tomato");
        assertThat(response.category()).isEqualTo(IngredientCategory.PRODUCE);
        assertThat(response.unitOfMeasure()).isEqualTo(UnitOfMeasure.KG);
        assertThat(response.unitPurchaseCost()).isEqualByComparingTo(new BigDecimal("1250.5000"));
        assertThat(response.status()).isEqualTo(IngredientStatus.ACTIVE);
        assertThat(response.currentStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.tenantId()).isEqualTo(tenantId);

        ArgumentCaptor<Ingredient> captor = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientRepository).save(captor.capture());
        Ingredient captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("Roma Tomato");
        assertThat(captured.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("createIngredient - duplicate name in same tenant throws DuplicateIngredientNameException")
    void createIngredient_duplicateName_throwsException() {
        CreateIngredientRequest request = new CreateIngredientRequest(
                "Roma Tomato",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                new BigDecimal("1000")
        );

        when(ingredientRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Roma Tomato")).thenReturn(true);

        assertThatThrownBy(() -> ingredientService.createIngredient(request))
                .isInstanceOf(DuplicateIngredientNameException.class)
                .hasMessageContaining("Roma Tomato");

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("getIngredient - returns details when found")
    void getIngredient_success() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Garlic", IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("50.0000"));
        ingredient.setId(id);

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));

        AdminIngredientDetailResponse response = ingredientService.getIngredient(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Garlic");
        assertThat(response.category()).isEqualTo(IngredientCategory.SPICES);
    }

    @Test
    @DisplayName("getIngredient - throws NoSuchElementException when not found")
    void getIngredient_notFound() {
        UUID id = UUID.randomUUID();
        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingredientService.getIngredient(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("updateIngredient - successfully updates name, category, and cost")
    void updateIngredient_success() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Milk", IngredientCategory.DAIRY, UnitOfMeasure.L, new BigDecimal("100.0000"));
        ingredient.setId(id);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Whole Milk",
                IngredientCategory.DAIRY,
                UnitOfMeasure.L,
                new BigDecimal("120.0000")
        );

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, "Whole Milk", id)).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredient(id, request);

        assertThat(response.name()).isEqualTo("Whole Milk");
        assertThat(response.unitPurchaseCost()).isEqualByComparingTo(new BigDecimal("120.0000"));
    }

    @Test
    @DisplayName("updateIngredient - duplicate name on update throws DuplicateIngredientNameException")
    void updateIngredient_duplicateName_throwsException() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Milk", IngredientCategory.DAIRY, UnitOfMeasure.L, new BigDecimal("100.0000"));
        ingredient.setId(id);

        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Heavy Cream",
                IngredientCategory.DAIRY,
                UnitOfMeasure.L,
                new BigDecimal("150.0000")
        );

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, "Heavy Cream", id)).thenReturn(true);

        assertThatThrownBy(() -> ingredientService.updateIngredient(id, request))
                .isInstanceOf(DuplicateIngredientNameException.class);

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateIngredient - changing unit of measure is blocked when physical stock > 0")
    void updateIngredient_changeUnitBlockedWhenStockPositive() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("500.0000"));
        ingredient.setId(id);
        ingredient.setCurrentStock(new BigDecimal("15.5000")); // Positive stock exists

        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Flour",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.G, // Attempting to change KG -> G
                new BigDecimal("500.0000")
        );

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> ingredientService.updateIngredient(id, request))
                .isInstanceOf(UnitOfMeasureModificationException.class);

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateIngredient - changing unit of measure is allowed when physical stock == 0")
    void updateIngredient_changeUnitAllowedWhenStockZero() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Sugar", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("400.0000"));
        ingredient.setId(id);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Sugar",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.G,
                new BigDecimal("400.0000")
        );

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredient(id, request);

        assertThat(response.unitOfMeasure()).isEqualTo(UnitOfMeasure.G);
    }

    @Test
    @DisplayName("updateIngredientStatus - ACTIVE to DISCONTINUED allowed when stock > 0")
    void updateStatus_activeToDiscontinued_allowedWithStock() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Vanilla Pods", IngredientCategory.SPICES, UnitOfMeasure.U, new BigDecimal("2000.0000"));
        ingredient.setId(id);
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredient.setCurrentStock(new BigDecimal("10.0000"));

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredientStatus(id, new IngredientStatusUpdateRequest(IngredientStatus.DISCONTINUED));

        assertThat(response.status()).isEqualTo(IngredientStatus.DISCONTINUED);
    }

    @Test
    @DisplayName("updateIngredientStatus - DISCONTINUED to ACTIVE explicit reactivation allowed")
    void updateStatus_discontinuedToActive_allowed() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Coriander", IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("300.0000"));
        ingredient.setId(id);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredientStatus(id, new IngredientStatusUpdateRequest(IngredientStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(IngredientStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateIngredientStatus - transition to ARCHIVED is rejected when stock > 0")
    void updateStatus_archived_rejectedWhenStockPositive() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Beef Ribs", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("8500.0000"));
        ingredient.setId(id);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredient.setCurrentStock(new BigDecimal("4.2500"));

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> ingredientService.updateIngredientStatus(id, new IngredientStatusUpdateRequest(IngredientStatus.ARCHIVED)))
                .isInstanceOf(InvalidLifecycleTransitionException.class)
                .hasMessageContaining("physical stock greater than zero");

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateIngredientStatus - transition to ARCHIVED is permitted when stock == 0")
    void updateStatus_archived_permittedWhenStockZero() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Beef Ribs", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("8500.0000"));
        ingredient.setId(id);
        ingredient.setStatus(IngredientStatus.DISCONTINUED);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredientStatus(id, new IngredientStatusUpdateRequest(IngredientStatus.ARCHIVED));

        assertThat(response.status()).isEqualTo(IngredientStatus.ARCHIVED);
    }

    @Test
    @DisplayName("updateIngredientStatus - transition from ARCHIVED is blocked")
    void updateStatus_fromArchived_throwsException() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Old Salmon", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("5000.0000"));
        ingredient.setId(id);
        ingredient.setStatus(IngredientStatus.ARCHIVED);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> ingredientService.updateIngredientStatus(id, new IngredientStatusUpdateRequest(IngredientStatus.ACTIVE)))
                .isInstanceOf(InvalidLifecycleTransitionException.class);

        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("listIngredients - delegates to repository with filters")
    void listIngredients_withFilters() {
        Ingredient ingredient = new Ingredient(tenantId, "Salt", IngredientCategory.SPICES, UnitOfMeasure.G, new BigDecimal("10.0000"));
        ingredient.setId(UUID.randomUUID());

        when(ingredientRepository.findAllByTenantIdAndFilters(tenantId, IngredientCategory.SPICES, IngredientStatus.ACTIVE))
                .thenReturn(List.of(ingredient));

        List<AdminIngredientDetailResponse> result = ingredientService.listIngredients(IngredientCategory.SPICES, IngredientStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Salt");
    }

    @Test
    @DisplayName("deleteIngredient - succeeds and archives when stock == 0")
    void deleteIngredient_successWhenStockZero() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("200.0000"));
        ingredient.setId(id);
        ingredient.setCurrentStock(BigDecimal.ZERO);

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ingredientService.deleteIngredient(id);

        assertThat(ingredient.getStatus()).isEqualTo(IngredientStatus.ARCHIVED);
        verify(ingredientRepository).save(ingredient);
    }

    @Test
    @DisplayName("deleteIngredient - throws InvalidLifecycleTransitionException when stock > 0")
    void deleteIngredient_throwsWhenStockPositive() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, new BigDecimal("200.0000"));
        ingredient.setId(id);
        ingredient.setCurrentStock(new BigDecimal("5.0000"));

        when(ingredientRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(ingredient));

        assertThatThrownBy(() -> ingredientService.deleteIngredient(id))
                .isInstanceOf(InvalidLifecycleTransitionException.class)
                .hasMessageContaining("physical stock greater than zero");

        verify(ingredientRepository, never()).save(any());
    }
}
