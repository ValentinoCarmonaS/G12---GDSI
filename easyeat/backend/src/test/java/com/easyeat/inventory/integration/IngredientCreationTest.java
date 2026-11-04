package com.easyeat.inventory.integration;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.AdminIngredientDetailResponse;
import com.easyeat.inventory.dto.CreateIngredientRequest;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("T011: Ingredient Creation Test With Threshold (US1 AC-1.1, FR-001, FR-003)")
class IngredientCreationTest {

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
    @DisplayName("US1 AC-1.1: testWithValidThreshold - Admin creates ingredient with valid threshold -> persisted and returned")
    void testWithValidThreshold() throws Exception {
        boolean hasField = Arrays.stream(CreateIngredientRequest.class.getRecordComponents())
                .anyMatch(rc -> "criticalStockThreshold".equals(rc.getName()));
        if (!hasField) {
            fail("CreateIngredientRequest does not yet have criticalStockThreshold field (TDD failing state)");
        }

        Constructor<?> ctor = Arrays.stream(CreateIngredientRequest.class.getConstructors())
                .filter(c -> c.getParameterCount() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("5-arg constructor for CreateIngredientRequest not found"));

        CreateIngredientRequest request = (CreateIngredientRequest) ctor.newInstance(
                "Fresh Mozzarella",
                IngredientCategory.DAIRY,
                UnitOfMeasure.KG,
                BigDecimal.valueOf(25.00),
                10
        );

        when(ingredientRepository.existsByTenantIdAndNameIgnoreCase(eq(tenantId), eq("Fresh Mozzarella"))).thenReturn(false);

        ArgumentCaptor<Ingredient> ingredientCaptor = ArgumentCaptor.forClass(Ingredient.class);
        when(ingredientRepository.save(ingredientCaptor.capture())).thenAnswer(invocation -> {
            Ingredient ing = invocation.getArgument(0);
            ing.setCreatedAt(Instant.now());
            ing.setUpdatedAt(Instant.now());
            return ing;
        });

        AdminIngredientDetailResponse response = ingredientService.createIngredient(request);

        Ingredient captured = ingredientCaptor.getValue();
        assertThat(captured.getCriticalStockThreshold())
                .as("Ingredient entity must have criticalStockThreshold persisted")
                .isEqualTo(10);

        Method thresholdGetter = Arrays.stream(AdminIngredientDetailResponse.class.getRecordComponents())
                .filter(rc -> "criticalStockThreshold".equals(rc.getName()))
                .map(java.lang.reflect.RecordComponent::getAccessor)
                .findFirst()
                .orElse(null);

        assertThat(thresholdGetter)
                .as("AdminIngredientDetailResponse must expose criticalStockThreshold")
                .isNotNull();

        Object returnedThreshold = thresholdGetter.invoke(response);
        assertThat(returnedThreshold).isEqualTo(10);
    }
}
