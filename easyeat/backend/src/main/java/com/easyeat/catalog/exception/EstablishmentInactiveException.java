package com.easyeat.catalog.exception;

import java.util.UUID;

public class EstablishmentInactiveException extends RuntimeException {

    private final UUID establishmentId;
    private final String currentStatus;

    public EstablishmentInactiveException(UUID establishmentId, String currentStatus) {
        super(String.format("El establecimiento con ID %s no se encuentra activo (estado actual: %s)", establishmentId, currentStatus));
        this.establishmentId = establishmentId;
        this.currentStatus = currentStatus;
    }

    public EstablishmentInactiveException(String message) {
        super(message);
        this.establishmentId = null;
        this.currentStatus = null;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
