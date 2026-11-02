package com.easyeat.operations.dto;

import com.easyeat.operations.domain.SlotCapacityChange;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One slot of the capacity list sent by the admin. {@code expectedVersion} is null for a slot
 * with no configuration yet. A negative capacity is not a Bean Validation error: the service
 * rejects it (INV-6) so that it answers with the "Capacidad inválida" problem of the contract.
 * The strict format makes 5.5 a 400 instead of silently becoming 5 (FR-007).
 */
public record UpdateSlotCapacityRequest(
        @NotNull(message = "slotDate is required")
        LocalDate slotDate,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "maxCapacity is required")
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE)
        Integer maxCapacity,

        Long expectedVersion
) {
    public SlotCapacityChange toChange() {
        return new SlotCapacityChange(slotDate, startTime, maxCapacity, expectedVersion);
    }
}
