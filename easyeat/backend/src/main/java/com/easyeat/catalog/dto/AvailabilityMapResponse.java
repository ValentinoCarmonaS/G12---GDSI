package com.easyeat.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class AvailabilityMapResponse {

    @JsonProperty("availability")
    private Map<UUID, Boolean> availability;

    public AvailabilityMapResponse() {
        this.availability = Collections.emptyMap();
    }

    public AvailabilityMapResponse(Map<UUID, Boolean> availability) {
        this.availability = availability != null ? availability : Collections.emptyMap();
    }

    public Map<UUID, Boolean> getAvailability() {
        return availability;
    }

    public void setAvailability(Map<UUID, Boolean> availability) {
        this.availability = availability != null ? availability : Collections.emptyMap();
    }
}
