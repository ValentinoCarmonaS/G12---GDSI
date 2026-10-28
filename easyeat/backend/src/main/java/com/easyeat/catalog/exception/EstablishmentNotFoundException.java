package com.easyeat.catalog.exception;

import java.util.UUID;

public class EstablishmentNotFoundException extends RuntimeException {

    private final UUID establishmentId;

    public EstablishmentNotFoundException(UUID establishmentId) {
        super(String.format("Establecimiento no encontrado con ID: %s", establishmentId));
        this.establishmentId = establishmentId;
    }

    public EstablishmentNotFoundException(String message) {
        super(message);
        this.establishmentId = null;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }
}
