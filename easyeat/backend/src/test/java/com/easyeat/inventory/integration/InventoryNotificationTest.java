package com.easyeat.inventory.integration;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.service.IngredientService;
import com.easyeat.notification.repository.AdminNotificationRepository;
import com.easyeat.notification.service.AdminNotificationService;
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
import static org.mockito.Mockito.*;

/**
 * T024: Integration / Verification test ensuring no external notifications
 * (email, SMS, push, order creation) or notification records are triggered
 * when an ingredient enters or remains in critical stock state (INV-5, FR-008, SC-005).
 *
 * In strict TDD red phase: fails until T029/T030 wires criticalOnly filtering and alert behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("T024: InventoryNotificationTest (INV-5, FR-008, SC-005)")
class InventoryNotificationTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @Mock
    private AdminNotificationService adminNotificationService;

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
        Method listMethod = Arrays.stream(IngredientService.class.getMethods())
                .filter(m -> "listIngredients".equals(m.getName()))
                .findFirst()
                .orElse(null);

        if (listMethod != null && listMethod.getParameterCount() < 3) {
            fail("IngredientService.listIngredients must accept Boolean criticalOnly parameter (TDD Red: T029 pending)");
        }
    }

    @Test
    @DisplayName("INV-5, FR-008, SC-005: testNoExternalNotificationsEmitted - Critical stock state produces zero external notifications (email, SMS, push) or admin notification records")
    void testNoExternalNotificationsEmitted() {
        assertCriticalOnlyParameterSupported();

        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = new Ingredient(tenantId, "Milk", IngredientCategory.DAIRY, UnitOfMeasure.L, new BigDecimal("12.0000"));
        ingredient.setId(ingredientId);
        ingredient.setCriticalStockThreshold(10);
        ingredient.setCurrentStock(new BigDecimal("3.0000")); // 3 < 10 -> isCritical = true

        when(ingredientRepository.findByIdAndTenantId(ingredientId, tenantId)).thenReturn(Optional.of(ingredient));

        var response = ingredientService.getIngredient(ingredientId);

        assertThat(response.isCritical())
                .as("Ingredient must be evaluated as critical")
                .isTrue();

        // Verify that critical status did NOT trigger any admin notifications or side-effects
        verifyNoInteractions(adminNotificationRepository);
        verifyNoInteractions(adminNotificationService);
    }
}
