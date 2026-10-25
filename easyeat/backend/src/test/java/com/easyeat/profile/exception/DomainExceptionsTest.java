package com.easyeat.profile.exception;

import com.easyeat.catalog.exception.DishNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    @DisplayName("EmailVerificationException should retain custom message and default constructor")
    void emailVerificationException() {
        EmailVerificationException exDefault = new EmailVerificationException();
        assertThat(exDefault.getMessage()).isNotEmpty();

        EmailVerificationException exCustom = new EmailVerificationException("Token expired");
        assertThat(exCustom.getMessage()).isEqualTo("Token expired");
    }

    @Test
    @DisplayName("CooldownRateLimitException should retain retryAfterSeconds and message")
    void cooldownRateLimitException() {
        CooldownRateLimitException ex = new CooldownRateLimitException(45);
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(45);
        assertThat(ex.getMessage()).contains("45");

        CooldownRateLimitException exCustom = new CooldownRateLimitException(30, "Wait 30 seconds");
        assertThat(exCustom.getRetryAfterSeconds()).isEqualTo(30);
        assertThat(exCustom.getMessage()).isEqualTo("Wait 30 seconds");
    }

    @Test
    @DisplayName("DietaryTaxonomyException should retain code and description")
    void dietaryTaxonomyException() {
        DietaryTaxonomyException ex = DietaryTaxonomyException.forInvalidCode("INVALID_CODE");
        assertThat(ex.getMessage()).contains("INVALID_CODE");

        DietaryTaxonomyException exCustom = new DietaryTaxonomyException("Custom taxonomy error");
        assertThat(exCustom.getMessage()).isEqualTo("Custom taxonomy error");

    }

    @Test
    @DisplayName("DishNotFoundException should retain dishId and message")
    void dishNotFoundException() {
        UUID dishId = UUID.randomUUID();
        DishNotFoundException ex = new DishNotFoundException(dishId);
        assertThat(ex.getMessage()).contains(dishId.toString());

        DishNotFoundException exCustom = new DishNotFoundException("Dish not found");
        assertThat(exCustom.getMessage()).isEqualTo("Dish not found");
    }
}
