package com.easyeat.profile.dto;

import java.util.List;
import java.util.UUID;

/**
 * Complete representation of diner profile state, contact details, email verification status,
 * and configured dietary restrictions.
 */
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
