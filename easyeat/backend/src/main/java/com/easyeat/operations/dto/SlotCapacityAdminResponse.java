package com.easyeat.operations.dto;

import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.service.SlotCapacityService.Row;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** {@code id} and {@code version} are null for a slot that has no capacity configured yet (EC-007). */
public record SlotCapacityAdminResponse(
        UUID id,
        LocalDate slotDate,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
        int maxCapacity,
        int approvedOrdersCount,
        boolean isSoldOut,
        Long version
) {
    public static SlotCapacityAdminResponse from(Row row) {
        return new SlotCapacityAdminResponse(row.id(), row.slot().slotDate(), row.slot().startTime(),
                row.slot().endTime(), row.maxCapacity(), row.approvedOrdersCount(), row.soldOut(), row.version());
    }

    public static SlotCapacityAdminResponse from(EstablishmentSlotCapacity capacity) {
        return new SlotCapacityAdminResponse(capacity.getId(), capacity.getSlotDate(), capacity.getStartTime(),
                capacity.getEndTime(), capacity.getMaxCapacity(), capacity.getApprovedOrdersCount(),
                capacity.isSoldOut(), capacity.getVersion());
    }
}
