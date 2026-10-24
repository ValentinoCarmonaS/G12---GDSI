package com.easyeat.staff.dto;

import java.time.Instant;
import java.util.UUID;

public record StaffMemberResponse(
        UUID id,
        String fullName,
        String email,
        StaffRole role,
        StaffAccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
