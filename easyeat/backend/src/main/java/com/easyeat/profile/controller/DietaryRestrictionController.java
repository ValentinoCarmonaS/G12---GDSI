package com.easyeat.profile.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import com.easyeat.profile.service.DietaryRestrictionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DietaryRestrictionController {

    private final DietaryRestrictionService dietaryRestrictionService;

    public DietaryRestrictionController(DietaryRestrictionService dietaryRestrictionService) {
        this.dietaryRestrictionService = dietaryRestrictionService;
    }

    @GetMapping("/dietary-restrictions")
    public ResponseEntity<List<DietaryRestrictionItemDto>> getDietaryTaxonomy() {
        return ResponseEntity.ok(dietaryRestrictionService.getDietaryTaxonomy());
    }

    @PutMapping("/profile/dietary-restrictions")
    public ResponseEntity<List<DietaryRestrictionItemDto>> updateDietaryPreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DietaryPreferencesRequest request
    ) {
        return ResponseEntity.ok(dietaryRestrictionService.updateUserPreferences(user, request));
    }
}
