package com.easyeat.operations.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One requested change to a day of the weekly schedule. {@code expectedVersion} is the version the
 * admin last saw, or {@code null} for a day that has never been configured (EC-012).
 */
public record ScheduleChange(DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime,
                             boolean closed, Long expectedVersion) {
}
