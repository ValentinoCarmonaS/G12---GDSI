package com.easyeat.profile.dto;

public record DietaryRestrictionItemDto(
        String code,
        String name,
        String category,
        String description
) {
}
