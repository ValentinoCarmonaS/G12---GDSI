package com.easyeat.inventory.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InsufficientIngredientStockExceptionTest {

    @Test
    @DisplayName("Should capture details of missing ingredient and format descriptive message")
    void shouldCaptureDetails() {
        UUID ingredientId = UUID.randomUUID();
        InsufficientIngredientStockException exception = new InsufficientIngredientStockException(
                ingredientId,
                "Queso Cheddar",
                BigDecimal.valueOf(2.5000),
                BigDecimal.valueOf(1.0000),
                "KILOGRAM"
        );

        assertThat(exception.getIngredientId()).isEqualTo(ingredientId);
        assertThat(exception.getIngredientName()).isEqualTo("Queso Cheddar");
        assertThat(exception.getDemandedQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2.5000));
        assertThat(exception.getAvailableQuantity()).isEqualByComparingTo(BigDecimal.valueOf(1.0000));
        assertThat(exception.getUnitOfMeasure()).isEqualTo("KILOGRAM");
        assertThat(exception.getMessage()).contains("Queso Cheddar", "disponible 1.0", "demandado 2.5");
    }
}
