package com.easyeat.catalog;

import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T040: BrandingColorValidationTest - Hexadecimal Pattern ^#[0-9A-Fa-f]{6}$ and Validation Rules")
class BrandingColorValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#10B981", "#059669", "#FFFFFF", "#000000", "#abcdef", "#ABCDEF", "#123456"})
    @DisplayName("Accepts valid 6-character hex colors prefixed with hash")
    void shouldAcceptValidHexColors(String validHex) {
        RestaurantBrandingUpdateRequest request = new RestaurantBrandingUpdateRequest(
                "Café Central",
                "Descripción válida",
                validHex,
                "#065F46",
                "#F59E0B"
        );

        Set<ConstraintViolation<RestaurantBrandingUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#123", "10B981", "#10B9811", "#GGGGGG", "rgb(0,0,0)", "red", "#12345", "#", ""})
    @DisplayName("Rejects malformed hex colors violating pattern ^#[0-9A-Fa-f]{6}$")
    void shouldRejectMalformedHexColors(String invalidHex) {
        RestaurantBrandingUpdateRequest request = new RestaurantBrandingUpdateRequest(
                "Café Central",
                "Descripción válida",
                invalidHex,
                "#065F46",
                "#F59E0B"
        );

        Set<ConstraintViolation<RestaurantBrandingUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("primaryColor"));
    }

    @Test
    @DisplayName("Rejects fantasyName when shorter than 2 or longer than 150 chars")
    void shouldValidateFantasyNameBounds() {
        RestaurantBrandingUpdateRequest shortName = new RestaurantBrandingUpdateRequest(
                "A",
                "Descripción",
                "#10B981",
                "#065F46",
                "#F59E0B"
        );
        assertThat(validator.validate(shortName)).anyMatch(v -> v.getPropertyPath().toString().equals("fantasyName"));

        String longStr = "A".repeat(151);
        RestaurantBrandingUpdateRequest longName = new RestaurantBrandingUpdateRequest(
                longStr,
                "Descripción",
                "#10B981",
                "#065F46",
                "#F59E0B"
        );
        assertThat(validator.validate(longName)).anyMatch(v -> v.getPropertyPath().toString().equals("fantasyName"));
    }
}
