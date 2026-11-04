package com.easyeat.inventory.integration;

import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T021: Integration & Repository Contract Tests for Critical Stock Filtering
 * (US2 AC-2.6, FR-020, FR-021, SC-015, INV-7, INV-17, EC-002).
 *
 * Verifies repository contract and query constraints for criticalOnly filtering.
 * In strict TDD red phase: fails until T028 declares and implements the repository query.
 */
@DisplayName("T021: IngredientRepository Critical Filter Test (US2 AC-2.6, FR-020, SC-015)")
class IngredientRepositoryCriticalFilterTest {

    private Method findCriticalFilterMethod() {
        return Arrays.stream(IngredientRepository.class.getMethods())
                .filter(m -> m.getName().contains("Critical") || Arrays.stream(m.getParameters()).anyMatch(p -> "criticalOnly".equals(p.getName())))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("US2 AC-2.6, FR-020, SC-015: testCriticalOnly - query method must exist and declare required parameters and return type")
    void testCriticalOnly() {
        Method method = findCriticalFilterMethod();
        if (method == null) {
            fail("IngredientRepository must declare a query method supporting criticalOnly filtering (TDD Red: T028 pending)");
        }

        assertThat(List.class.isAssignableFrom(method.getReturnType()) || Page.class.isAssignableFrom(method.getReturnType()))
                .as("Critical filter query method must return List<Ingredient> or Page<Ingredient>")
                .isTrue();

        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation)
                .as("Critical filter query method should have @Query annotation defining the filtering logic")
                .isNotNull();

        String queryValue = queryAnnotation.value();
        assertThat(queryValue)
                .as("Query must filter by status ACTIVE, threshold IS NOT NULL and currentStock < criticalStockThreshold")
                .contains("currentStock <")
                .contains("criticalStockThreshold");
    }

    @Test
    @DisplayName("INV-17, FR-021: testDiscontinuedExcludedFromCriticalFilter - query must exclude DISCONTINUED ingredients")
    void testDiscontinuedExcludedFromCriticalFilter() {
        Method method = findCriticalFilterMethod();
        if (method == null) {
            fail("IngredientRepository must declare a query method supporting criticalOnly filtering (TDD Red: T028 pending)");
        }

        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();
        String queryValue = queryAnnotation.value();

        assertThat(queryValue)
                .as("Critical filter query must strictly filter ACTIVE status so DISCONTINUED ingredients are excluded")
                .containsIgnoringCase("ACTIVE");
    }

    @Test
    @DisplayName("INV-7, EC-002: testNullThresholdExcludedFromFilter - query must exclude ingredients where threshold IS NULL")
    void testNullThresholdExcludedFromFilter() {
        Method method = findCriticalFilterMethod();
        if (method == null) {
            fail("IngredientRepository must declare a query method supporting criticalOnly filtering (TDD Red: T028 pending)");
        }

        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();
        String queryValue = queryAnnotation.value();

        assertThat(queryValue)
                .as("Critical filter query must explicitly ensure criticalStockThreshold is not null")
                .containsIgnoringCase("IS NOT NULL");
    }
}
