package com.easyeat.profile.exception;

public class DietaryTaxonomyException extends RuntimeException {

    public DietaryTaxonomyException(String message) {
        super(message);
    }

    public static DietaryTaxonomyException forInvalidCode(String restrictionCode) {
        return new DietaryTaxonomyException(String.format("Código de restricción dietaria inválido o no reconocido: %s", restrictionCode));
    }

    public DietaryTaxonomyException(String message, Throwable cause) {
        super(message, cause);
    }
}

