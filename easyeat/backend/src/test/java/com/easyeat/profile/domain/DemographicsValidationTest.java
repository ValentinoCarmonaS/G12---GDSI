package com.easyeat.profile.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Demographics Domain Validation Unit Test (T007, INV-5, INV-8)")
class DemographicsValidationTest {

    @Test
    @DisplayName("Should pass when all demographic fields are null")
    void shouldPass_whenFieldsAreNull() {
        assertThatCode(() -> DemographicsValidator.validate(null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass when age is within valid biological range (14 to 120)")
    void shouldPass_whenAgeIsValid() {
        assertThatCode(() -> DemographicsValidator.validateAge(14)).doesNotThrowAnyException();
        assertThatCode(() -> DemographicsValidator.validateAge(28)).doesNotThrowAnyException();
        assertThatCode(() -> DemographicsValidator.validateAge(120)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail when age is below 14 or above 120")
    void shouldFail_whenAgeIsOutOfRange() {
        assertThatThrownBy(() -> DemographicsValidator.validateAge(13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La edad debe ser un número entero entre 14 y 120 años.");

        assertThatThrownBy(() -> DemographicsValidator.validateAge(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La edad debe ser un número entero entre 14 y 120 años.");

        assertThatThrownBy(() -> DemographicsValidator.validateAge(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La edad debe ser un número entero entre 14 y 120 años.");

        assertThatThrownBy(() -> DemographicsValidator.validateAge(121))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La edad debe ser un número entero entre 14 y 120 años.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FEMALE", "MALE", "NON_BINARY", "PREFER_NOT_TO_SAY",
            "female", "male", "non_binary", "prefer_not_to_say",
            "Femenino", "Masculino", "No binario", "Prefiero no responder",
            "femenino", "masculino", "no binario", "prefiero no responder"
    })
    @DisplayName("Should pass when gender is within closed taxonomy")
    void shouldPass_whenGenderIsValid(String gender) {
        assertThatCode(() -> DemographicsValidator.validateGender(gender))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail when gender is not in closed taxonomy")
    void shouldFail_whenGenderIsInvalid() {
        assertThatThrownBy(() -> DemographicsValidator.validateGender("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El género seleccionado no es válido.");

        assertThatThrownBy(() -> DemographicsValidator.validateGender("Other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El género seleccionado no es válido.");
    }

    @Test
    @DisplayName("Should pass when address length is <= 255 characters")
    void shouldPass_whenAddressIsValid() {
        assertThatCode(() -> DemographicsValidator.validateAddress("Av. Corrientes 1234, CABA"))
                .doesNotThrowAnyException();
        assertThatCode(() -> DemographicsValidator.validateAddress("A".repeat(255)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail when address length exceeds 255 characters")
    void shouldFail_whenAddressExceeds255Chars() {
        assertThatThrownBy(() -> DemographicsValidator.validateAddress("A".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El domicilio no puede exceder los 255 caracteres.");
    }
}
