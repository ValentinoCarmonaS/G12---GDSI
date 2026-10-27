package com.easyeat.profile.dto;

import com.easyeat.profile.domain.DietaryRestriction;

public record DietaryRestrictionItemDto(
        String code,
        String name,
        String category,
        String description
) {
    public static DietaryRestrictionItemDto fromEntity(DietaryRestriction entity) {
        return new DietaryRestrictionItemDto(
                entity.getCode(),
                entity.getName(),
                entity.getCategory() != null ? entity.getCategory().name() : null,
                entity.getDescription()
        );
    }
}
