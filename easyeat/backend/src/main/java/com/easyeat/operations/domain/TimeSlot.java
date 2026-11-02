package com.easyeat.operations.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Deterministic 15-minute block of a calendar day, semi-open {@code [start, end)} (INV-5).
 */
public record TimeSlot(LocalDate slotDate, LocalTime startTime) {

    public static final int DURATION_MINUTES = 15;

    public TimeSlot {
        Objects.requireNonNull(slotDate, "slotDate");
        Objects.requireNonNull(startTime, "startTime");
        if (startTime.plusMinutes(DURATION_MINUTES).isBefore(startTime)) {
            throw new IllegalArgumentException("A slot starting at " + startTime + " would end after midnight");
        }
    }

    public LocalTime endTime() {
        return startTime.plusMinutes(DURATION_MINUTES);
    }

    public boolean contains(LocalTime instant) {
        return !instant.isBefore(startTime) && instant.isBefore(endTime());
    }
}
