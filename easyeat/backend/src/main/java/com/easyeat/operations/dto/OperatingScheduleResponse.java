package com.easyeat.operations.dto;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record OperatingScheduleResponse(
        UUID id,
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime openingTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime closingTime,
        boolean isClosed,
        long version
) {
    public static OperatingScheduleResponse from(EstablishmentOperatingSchedule schedule) {
        return new OperatingScheduleResponse(schedule.getId(), schedule.getDayOfWeek(),
                schedule.getOpeningTime(), schedule.getClosingTime(), schedule.isClosed(), schedule.getVersion());
    }
}
