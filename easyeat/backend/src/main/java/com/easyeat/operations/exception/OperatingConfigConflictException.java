package com.easyeat.operations.exception;

/** EC-012: the configuration was modified by another administrator since it was loaded. */
public class OperatingConfigConflictException extends RuntimeException {

    public OperatingConfigConflictException() {
        super("La configuración fue modificada por otro administrador. Por favor recargue la pantalla para ver los cambios.");
    }
}
