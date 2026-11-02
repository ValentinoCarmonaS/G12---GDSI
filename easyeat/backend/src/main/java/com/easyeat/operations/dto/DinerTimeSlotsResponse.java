package com.easyeat.operations.dto;

import com.easyeat.operations.domain.TimeSlot;
import com.easyeat.operations.service.DinerTimeSlotService.Availability;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DinerTimeSlotsResponse(
        LocalDate date,
        boolean isClosed,
        @JsonInclude(JsonInclude.Include.ALWAYS) String closureReason,
        String timezone,
        List<Slot> slots
) {
    public enum SlotStatus { AVAILABLE, SOLD_OUT }

    public record Slot(
            @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
            SlotStatus status
    ) {
        static Slot available(TimeSlot slot) {
            return new Slot(slot.startTime(), slot.endTime(), SlotStatus.AVAILABLE);
        }
    }

    public static DinerTimeSlotsResponse from(Availability availability) {
        return new DinerTimeSlotsResponse(availability.date(), availability.closed(),
                availability.closureReason(), availability.timezone().getId(),
                availability.slots().stream().map(Slot::available).toList());
    }
}
