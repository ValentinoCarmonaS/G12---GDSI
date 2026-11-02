package com.easyeat.profile.domain;

import java.util.Set;

public final class DemographicsValidator {

    public static final int MIN_AGE = 14;
    public static final int MAX_AGE = 120;
    public static final int MAX_ADDRESS_LENGTH = 255;

    private static final Set<String> VALID_GENDERS = Set.of(
            "FEMALE", "MALE", "NON_BINARY", "PREFER_NOT_TO_SAY",
            "FEMENINO", "MASCULINO", "NO BINARIO", "NO_BINARIO", "PREFIERO NO RESPONDER", "PREFIERO_NO_RESPONDER"
    );

    private DemographicsValidator() {
    }

    public static void validate(Integer age, String gender, String address) {
        validateAge(age);
        validateGender(gender);
        validateAddress(address);
    }

    public static void validateAge(Integer age) {
        if (age == null) {
            return;
        }
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new IllegalArgumentException("La edad debe ser un número entero entre 14 y 120 años.");
        }
    }

    public static void validateGender(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            return;
        }
        String normalized = gender.trim().toUpperCase();
        if (!VALID_GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("El género seleccionado no es válido.");
        }
    }

    public static void validateAddress(String address) {
        if (address == null) {
            return;
        }
        if (address.length() > MAX_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("El domicilio no puede exceder los 255 caracteres.");
        }
    }
}
