package com.easyeat.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

    private int maxBytes = 72;

    @Override
    public void initialize(MaxBytes constraintAnnotation) {
        this.maxBytes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Failing stub for TDD (RED phase)
        return false;
    }
}
