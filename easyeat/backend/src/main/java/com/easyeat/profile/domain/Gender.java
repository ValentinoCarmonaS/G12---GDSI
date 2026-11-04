package com.easyeat.profile.domain;

public enum Gender {
    FEMALE,
    MALE,
    NON_BINARY,
    PREFER_NOT_TO_SAY;

    public static Gender fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "FEMALE", "FEMENINO" -> FEMALE;
            case "MALE", "MASCULINO" -> MALE;
            case "NON_BINARY", "NO BINARIO", "NO_BINARIO" -> NON_BINARY;
            case "PREFER_NOT_TO_SAY", "PREFIERO NO RESPONDER", "PREFIERO_NO_RESPONDER" -> PREFER_NOT_TO_SAY;
            default -> throw new IllegalArgumentException("Unknown gender: " + value);
        };
    }
}
