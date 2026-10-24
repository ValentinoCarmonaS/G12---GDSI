package com.easyeat.staff.dto;

import java.time.Instant;
import java.util.UUID;

public record StaffRegistrationResponse(
        UUID id,
        String fullName,
        String email,
        StaffRole role,
        StaffAccountStatus status,
        String temporaryCredential,
        Instant createdAt
) {}
