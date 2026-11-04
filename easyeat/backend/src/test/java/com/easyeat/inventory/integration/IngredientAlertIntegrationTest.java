package com.easyeat.inventory.integration;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.AdminIngredientDetailResponse;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * T022: Integration test for reactive critical stock alert lifecycle
 * (US2 AC-2.1, AC-2.2, AC-2.3, EC-003, EC-005, EC-006, EC-007, INV-14).
 *
 * Verifies that stock reductions (sales, adjustments), replenishments, and threshold
 * changes dynamically and reactively update the isCritical calculation on next query.
 *
 * In strict TDD red phase: fails until T029/T030 wires the critical alert evaluation end-to-end.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("T022: Ingredient Alert Integration Test (US2 AC-2.1..AC-2.3, EC-003, EC-005..EC-007, INV-14)")
class IngredientAlertIntegrationTest {

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

    private void assertCriticalOnlyParameterSupported() {
        boolean hasCriticalOnlySupport = Arrays.stream(IngredientService.class.getMethods())
                .filter(m -> "listIngredients".equals(m.getName()))
                .anyMatch(m -> m.getParameterCount() >= 3);

        if (!hasCriticalOnlySupport) {
            fail("IngredientService.listIngredients must accept Boolean criticalOnly parameter (TDD Red: T029 pending)");
        }
    }

    @Test
    @DisplayName("US2 AC-2.1, EC-005: testSaleTriggersAlert - Sale/consumption reducing stock strictly below threshold triggers isCritical=true on next query")
    void testSaleTriggersAlert() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Tomato Sauce", IngredientCategory.PRODUCE, UnitOfMeasure.KG, BigDecimal.valueOf(10.0));
        ingredient.setCriticalStockThreshold(15);
        ingredient.setCurrentStock(BigDecimal.valueOf(20.0)); // initially above threshold

        assertThat(ingredient.isCritical()).isFalse();

        // Simulate sale/consumption reducing stock to 10 (< 15)
        ingredient.setCurrentStock(BigDecimal.valueOf(10.0));

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        AdminIngredientDetailResponse response = ingredientService.getIngredient(ingredientId);
        assertThat(response.isCritical())
                .as("After sale reduction below threshold, next query must reflect isCritical=true (EC-005, INV-14)")
                .isTrue();
    }

    @Test
    @DisplayName("US2 AC-2.2, EC-006: testAdjustmentTriggersAlert - Manual stock adjustment below threshold immediately triggers isCritical=true")
    void testAdjustmentTriggersAlert() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Olive Oil", IngredientCategory.DRY_GOODS, UnitOfMeasure.L, BigDecimal.valueOf(25.0));
        ingredient.setCriticalStockThreshold(8);
        ingredient.setCurrentStock(BigDecimal.valueOf(12.0));

        // Manual adjustment decreases stock to 5
        ingredient.setCurrentStock(BigDecimal.valueOf(5.0));

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        AdminIngredientDetailResponse response = ingredientService.getIngredient(ingredientId);
        assertThat(response.isCritical())
                .as("Manual adjustment reducing stock below threshold must trigger isCritical=true immediately (EC-006, INV-14)")
                .isTrue();
    }

    @Test
    @DisplayName("US2 AC-2.3, EC-007: testReplenishmentRemovesAlert - Replenishment at or above threshold removes critical alert")
    void testReplenishmentRemovesAlert() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Flour", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, BigDecimal.valueOf(5.0));
        ingredient.setCriticalStockThreshold(20);
        ingredient.setCurrentStock(BigDecimal.valueOf(10.0)); // critical state: 10 < 20

        assertThat(ingredient.isCritical()).isTrue();

        // Replenishment brings stock to 25 (>= 20)
        ingredient.setCurrentStock(BigDecimal.valueOf(25.0));

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        AdminIngredientDetailResponse response = ingredientService.getIngredient(ingredientId);
        assertThat(response.isCritical())
                .as("Replenishment bringing stock >= threshold must clear isCritical to false (EC-007, FR-006)")
                .isFalse();
    }

    @Test
    @DisplayName("EC-003: testHigherThresholdTriggersAlert - Setting threshold higher than current stock immediately triggers critical state")
    void testHigherThresholdTriggersAlert() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Butter", IngredientCategory.DAIRY, UnitOfMeasure.KG, BigDecimal.valueOf(18.0));
        ingredient.setCurrentStock(BigDecimal.valueOf(15.0));
        ingredient.setCriticalStockThreshold(10); // 15 >= 10 -> not critical

        assertThat(ingredient.isCritical()).isFalse();

        // Admin increases threshold to 25 (> 15)
        ingredient.setCriticalStockThreshold(25);

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        AdminIngredientDetailResponse response = ingredientService.getIngredient(ingredientId);
        assertThat(response.isCritical())
                .as("Increasing threshold above current stock enters critical alert immediately (EC-003, INV-14)")
                .isTrue();
    }

    @Test
    @DisplayName("INV-14: testThresholdUpdateRecalculatesImmediately - Updating threshold via updateIngredient recalculates isCritical immediately")
    void testThresholdUpdateRecalculatesImmediately() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Sugar", IngredientCategory.DRY_GOODS, UnitOfMeasure.KG, BigDecimal.valueOf(4.0));
        ingredient.setCurrentStock(BigDecimal.valueOf(30.0));
        ingredient.setCriticalStockThreshold(10); // 30 >= 10 -> not critical

        UpdateIngredientRequest request = new UpdateIngredientRequest(
                "Sugar",
                IngredientCategory.DRY_GOODS,
                UnitOfMeasure.KG,
                BigDecimal.valueOf(4.0),
                50 // new threshold > 30
        );

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(eq(tenantId), eq("Sugar"), eq(ingredientId))).thenReturn(false);
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

        AdminIngredientDetailResponse response = ingredientService.updateIngredient(ingredientId, request);
        assertThat(response.isCritical())
                .as("Updating threshold to 50 when stock is 30 must immediately calculate isCritical=true (INV-14)")
                .isTrue();
    }
}
