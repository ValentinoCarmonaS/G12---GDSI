package com.easyeat.profile.dto;

import java.util.List;
import java.util.UUID;

public record ExtendedProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String email,
        String pendingEmail,
        boolean emailVerificationPending,
        Integer age,
        String gender,
        String address,
        String avatarUrl,
        List<DietaryRestrictionItemDto> dietaryRestrictions
) {
}

