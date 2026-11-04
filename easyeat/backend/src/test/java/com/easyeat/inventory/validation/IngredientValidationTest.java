package com.easyeat.inventory.validation;

import com.easyeat.inventory.dto.CreateIngredientRequest;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.dto.UpdateIngredientRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("T009: Ingredient Validation Test (FR-002, INV-2, SC-002, EC-022)")
class IngredientValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private boolean hasCriticalStockThreshold(Class<?> clazz) {
        return Arrays.stream(clazz.getRecordComponents())
                .anyMatch(rc -> "criticalStockThreshold".equals(rc.getName()));
    }

    @Test
    @DisplayName("INV-2, FR-002, SC-002: testNegativeAndDecimalRejection - negative criticalStockThreshold must be rejected")
    void testNegativeAndDecimalRejection() throws Exception {
        if (!hasCriticalStockThreshold(CreateIngredientRequest.class)) {
            fail("CreateIngredientRequest does not yet have criticalStockThreshold field (TDD failing state)");
        }

        Constructor<?> ctor = Arrays.stream(CreateIngredientRequest.class.getConstructors())
                .filter(c -> c.getParameterCount() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("5-arg constructor for CreateIngredientRequest not found"));

        Object negativeRequest = ctor.newInstance(
                "Tomato",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                BigDecimal.valueOf(10.0),
                -5
        );

        Set<ConstraintViolation<Object>> violations = validator.validate(negativeRequest);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("criticalStockThreshold"));
    }

    @Test
    @DisplayName("EC-022: testArbitrarilyHighThresholdAccepted - arbitrarily high integer >= 0 is valid")
    void testArbitrarilyHighThresholdAccepted() throws Exception {
        if (!hasCriticalStockThreshold(CreateIngredientRequest.class)) {
            fail("CreateIngredientRequest does not yet have criticalStockThreshold field (TDD failing state)");
        }

        Constructor<?> ctor = Arrays.stream(CreateIngredientRequest.class.getConstructors())
                .filter(c -> c.getParameterCount() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("5-arg constructor for CreateIngredientRequest not found"));

        Object highRequest = ctor.newInstance(
                "Flour",
                IngredientCategory.PRODUCE,
                UnitOfMeasure.KG,
                BigDecimal.valueOf(10.0),
                1_000_000
        );

        Set<ConstraintViolation<Object>> violations = validator.validate(highRequest);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("criticalStockThreshold"));
    }
}
