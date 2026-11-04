package com.easyeat.operations.exception;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** INV-7: the opening time must be strictly before the closing time. */
public class InvalidScheduleException extends RuntimeException {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    public InvalidScheduleException(LocalTime openingTime, LocalTime closingTime) {
        super(String.format("La hora de apertura (%s) debe ser estrictamente anterior a la hora de cierre (%s).",
                openingTime.format(HOUR_MINUTE), closingTime.format(HOUR_MINUTE)));
    }
}
