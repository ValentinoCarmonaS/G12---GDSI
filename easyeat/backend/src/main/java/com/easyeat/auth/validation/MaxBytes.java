package com.easyeat.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {
    String message() default "Value exceeds maximum allowed byte length";
    int value() default 72;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
