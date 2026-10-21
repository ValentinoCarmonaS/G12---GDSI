package com.easyeat.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

    private int maxBytes = 72;

    @Override
    public void initialize(MaxBytes constraintAnnotation) {
        this.maxBytes = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
