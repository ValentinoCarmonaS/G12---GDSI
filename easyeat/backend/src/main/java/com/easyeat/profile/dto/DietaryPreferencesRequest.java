package com.easyeat.profile.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DietaryPreferencesRequest(
        @NotNull(message = "La lista de códigos de restricción es obligatoria")
        List<String> restrictionCodes
) {
    public DietaryPreferencesRequest {
        if (restrictionCodes == null) {
            restrictionCodes = List.of();
        }
    }
}
