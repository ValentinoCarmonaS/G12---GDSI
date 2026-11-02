package com.easyeat.operations.dto;

import com.easyeat.operations.domain.SlotCapacityChange;
import com.easyeat.operations.exception.InvalidCapacityException;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One slot of the capacity list sent by the admin. {@code expectedVersion} is null for a slot
 * with no configuration yet. A negative capacity is not a Bean Validation error: the service
 * rejects it (INV-6) so that it answers with the "Capacidad inválida" problem of the contract.
 * The capacity travels as a decimal so that 5.5 is seen and rejected (FR-007) instead of being
 * silently truncated to 5 by the JSON reader.
 */
public record UpdateSlotCapacityRequest(
        @NotNull(message = "slotDate is required")
        LocalDate slotDate,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "maxCapacity is required")
        BigDecimal maxCapacity,

        Long expectedVersion
) {
    public SlotCapacityChange toChange() {
        BigDecimal normalized = maxCapacity.stripTrailingZeros();
        if (normalized.scale() > 0 || normalized.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw InvalidCapacityException.negative();
        }
        return new SlotCapacityChange(slotDate, startTime, maxCapacity.intValueExact(), expectedVersion);
    }
}
