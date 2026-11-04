package com.easyeat.catalog.dto;

import java.util.UUID;

public record DishAvailabilityDetailsResponse(
        UUID dishId,
        UnavailabilityCauses causes,
        boolean isOrderable,
        boolean isVisibleToDiner
) {
    public record UnavailabilityCauses(
            boolean sinStock,
            boolean pausaChef,
            boolean desactivacionAdmin
    ) {
    }

    public UUID getDishId() {
        return dishId;
    }

    public UnavailabilityCauses getCauses() {
        return causes;
    }
}
