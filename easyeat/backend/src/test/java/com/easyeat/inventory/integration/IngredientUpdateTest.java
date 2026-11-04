package com.easyeat.inventory.integration;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.AdminIngredientDetailResponse;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.dto.UpdateIngredientRequest;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.service.IngredientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("T012: Ingredient Update Test With Threshold (US1 AC-1.2, INV-1, FR-001, FR-003)")
class IngredientUpdateTest {

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
    @DisplayName("US1 AC-1.2, INV-1: testIndividualThresholdIsolation - Admin edits threshold of one ingredient without affecting others")
    void testIndividualThresholdIsolation() throws Exception {
        boolean hasField = Arrays.stream(UpdateIngredientRequest.class.getRecordComponents())
                .anyMatch(rc -> "criticalStockThreshold".equals(rc.getName()));
        if (!hasField) {
            fail("UpdateIngredientRequest does not yet have criticalStockThreshold field (TDD failing state)");
        }

        UUID targetId = UUID.randomUUID();
        Ingredient targetIngredient = new Ingredient(
                tenantId,
                "Fresh Basil",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.G,
                BigDecimal.valueOf(12.0)
        );
        targetIngredient.setCriticalStockThreshold(20);

        UUID untouchedId = UUID.randomUUID();
        Ingredient untouchedIngredient = new Ingredient(
                tenantId,
                "Oregano",
                IngredientCategory.SPICES,
                UnitOfMeasure.G,
                BigDecimal.valueOf(5.0)
        );
        untouchedIngredient.setCriticalStockThreshold(50);

        when(ingredientRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(targetIngredient));
        when(ingredientRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(eq(tenantId), eq("Fresh Basil"), eq(targetId))).thenReturn(false);

        ArgumentCaptor<Ingredient> ingredientCaptor = ArgumentCaptor.forClass(Ingredient.class);
        when(ingredientRepository.save(ingredientCaptor.capture())).thenAnswer(invocation -> {
            Ingredient ing = invocation.getArgument(0);
            ing.setUpdatedAt(Instant.now());
            return ing;
        });

        Constructor<?> ctor = Arrays.stream(UpdateIngredientRequest.class.getConstructors())
                .filter(c -> c.getParameterCount() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("5-arg constructor for UpdateIngredientRequest not found"));

        UpdateIngredientRequest request = (UpdateIngredientRequest) ctor.newInstance(
                "Fresh Basil",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.G,
                BigDecimal.valueOf(12.0),
                35 // updated threshold
        );

        AdminIngredientDetailResponse response = ingredientService.updateIngredient(targetId, request);

        Ingredient savedTarget = ingredientCaptor.getValue();
        assertThat(savedTarget.getCriticalStockThreshold())
                .as("Target ingredient must update its threshold to 35")
                .isEqualTo(35);

        assertThat(untouchedIngredient.getCriticalStockThreshold())
                .as("Other ingredient threshold must remain strictly unchanged (INV-1)")
                .isEqualTo(50);

        Method thresholdGetter = Arrays.stream(AdminIngredientDetailResponse.class.getRecordComponents())
                .filter(rc -> "criticalStockThreshold".equals(rc.getName()))
                .map(java.lang.reflect.RecordComponent::getAccessor)
                .findFirst()
                .orElse(null);

        assertThat(thresholdGetter)
                .as("AdminIngredientDetailResponse must expose criticalStockThreshold")
                .isNotNull();

        Object returnedThreshold = thresholdGetter.invoke(response);
        assertThat(returnedThreshold).isEqualTo(35);
    }
}
