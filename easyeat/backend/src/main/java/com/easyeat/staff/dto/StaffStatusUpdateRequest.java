package com.easyeat.staff.dto;

import jakarta.validation.constraints.NotNull;

public record StaffStatusUpdateRequest(
        @NotNull(message = "Status is required")
        StaffAccountStatus status
) {
}
