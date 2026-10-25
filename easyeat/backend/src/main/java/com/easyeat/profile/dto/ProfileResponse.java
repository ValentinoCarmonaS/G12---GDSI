package com.easyeat.profile.dto;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String email,
        String pendingEmail,
        boolean emailVerificationPending,
        List<DietaryRestrictionItemDto> dietaryRestrictions
) {
}
