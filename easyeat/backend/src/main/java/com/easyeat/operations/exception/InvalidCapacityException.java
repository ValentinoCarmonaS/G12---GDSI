package com.easyeat.operations.exception;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** INV-6 (negative capacity) and INV-5 (a slot start that is not on a 15-minute boundary). */
public class InvalidCapacityException extends RuntimeException {

    private InvalidCapacityException(String message) {
        super(message);
    }

    public static InvalidCapacityException negative() {
        return new InvalidCapacityException("La capacidad máxima por franja debe ser un número entero mayor o igual a 0.");
    }

    public static InvalidCapacityException misaligned(LocalTime startTime) {
        return new InvalidCapacityException(String.format(
                "El inicio de la franja (%s) debe coincidir con un bloque de 15 minutos.",
                startTime.format(DateTimeFormatter.ofPattern("HH:mm"))));
    }
}
