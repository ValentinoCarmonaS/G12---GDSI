package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DietaryRestrictionService {

    public List<DietaryRestrictionItemDto> getDietaryTaxonomy() {
        return List.of();
    }

    public List<DietaryRestrictionItemDto> getUserPreferences(UUID userId) {
        return List.of();
    }

    public List<DietaryRestrictionItemDto> updateUserPreferences(User user, DietaryPreferencesRequest request) {
        return List.of();
    }
}
