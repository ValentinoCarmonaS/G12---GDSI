package com.easyeat.operations.dto;

import com.easyeat.operations.domain.ScheduleChange;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One day of the weekly schedule as sent by the admin. {@code expectedVersion} is null for a day
 * that has never been configured. The opening-before-closing rule (INV-7) is checked by
 * {@code OperatingScheduleService}, the single place that also knows whether the day is closed.
 */
public record UpdateScheduleRequest(
        @NotNull(message = "dayOfWeek is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "openingTime is required")
        LocalTime openingTime,

        @NotNull(message = "closingTime is required")
        LocalTime closingTime,

        @NotNull(message = "isClosed is required")
        Boolean isClosed,

        Long expectedVersion
) {
    public ScheduleChange toChange() {
        return new ScheduleChange(dayOfWeek, openingTime, closingTime, isClosed, expectedVersion);
    }
}
