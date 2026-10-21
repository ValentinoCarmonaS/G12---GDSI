package com.easyeat.auth.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxBytesValidatorTest {

    private MaxBytesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MaxBytesValidator();
    }

    @Test
    @DisplayName("Should pass when password is under 72 UTF-8 bytes")
    void shouldPassWhenUnder72Bytes() {
        assertTrue(validator.isValid("SecurePassword1", null));
    }

    @Test
    @DisplayName("Should pass when password is exactly 72 UTF-8 bytes")
    void shouldPassWhenExactly72Bytes() {
        String exactly72Bytes = "a".repeat(72);
        assertTrue(validator.isValid(exactly72Bytes, null));
    }

    @Test
    @DisplayName("Should fail when password exceeds 72 UTF-8 bytes with ASCII characters")
    void shouldFailWhenExceeds72BytesAscii() {
        String exceeds72Bytes = "a".repeat(73);
        assertFalse(validator.isValid(exceeds72Bytes, null));
    }

    @Test
    @DisplayName("Should fail when password exceeds 72 UTF-8 bytes using multibyte characters")
    void shouldFailWhenExceeds72BytesMultiByte() {
        // '€' is 3 bytes in UTF-8. 25 * 3 = 75 bytes (> 72)
        String euroString = "€".repeat(25);
        assertFalse(validator.isValid(euroString, null));
    }

    @Test
    @DisplayName("Should pass null value allowing @NotNull to handle nullness")
    void shouldPassNull() {
        assertTrue(validator.isValid(null, null));
    }
}
