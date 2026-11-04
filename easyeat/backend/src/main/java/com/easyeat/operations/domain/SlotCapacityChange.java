package com.easyeat.operations.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One requested capacity for a 15-minute slot. {@code expectedVersion} is the version the admin
 * last saw, or {@code null} for a slot that has no configuration yet (EC-012).
 */
public record SlotCapacityChange(LocalDate slotDate, LocalTime startTime, int maxCapacity, Long expectedVersion) {
}
