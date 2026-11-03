package com.easyeat.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PickupSlotCatalogResponse {

    private UUID establishmentId;
    private LocalDate date;
    private Instant serverTime;
    private String timezone;
    @JsonProperty("isEstablishmentOpen")
    private boolean isEstablishmentOpen;
    @JsonProperty("isOperatingDay")
    private boolean isOperatingDay;
    private Integer leadTimeMinutes;
    private List<TimeSlotAvailability> slots = new ArrayList<>();

    public PickupSlotCatalogResponse() {
    }

    public PickupSlotCatalogResponse(UUID establishmentId,
                                     LocalDate date,
                                     Instant serverTime,
                                     String timezone,
                                     boolean isEstablishmentOpen,
                                     boolean isOperatingDay,
                                     Integer leadTimeMinutes,
                                     List<TimeSlotAvailability> slots) {
        this.establishmentId = establishmentId;
        this.date = date;
        this.serverTime = serverTime;
        this.timezone = timezone;
        this.isEstablishmentOpen = isEstablishmentOpen;
        this.isOperatingDay = isOperatingDay;
        this.leadTimeMinutes = leadTimeMinutes;
        this.slots = slots != null ? slots : new ArrayList<>();
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Instant getServerTime() {
        return serverTime;
    }

    public void setServerTime(Instant serverTime) {
        this.serverTime = serverTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isEstablishmentOpen() {
        return isEstablishmentOpen;
    }

    public void setEstablishmentOpen(boolean establishmentOpen) {
        isEstablishmentOpen = establishmentOpen;
    }

    public boolean isOperatingDay() {
        return isOperatingDay;
    }

    public void setOperatingDay(boolean operatingDay) {
        isOperatingDay = operatingDay;
    }

    public Integer getLeadTimeMinutes() {
        return leadTimeMinutes;
    }

    public void setLeadTimeMinutes(Integer leadTimeMinutes) {
        this.leadTimeMinutes = leadTimeMinutes;
    }

    public List<TimeSlotAvailability> getSlots() {
        return slots;
    }

    public void setSlots(List<TimeSlotAvailability> slots) {
        this.slots = slots;
    }
}
