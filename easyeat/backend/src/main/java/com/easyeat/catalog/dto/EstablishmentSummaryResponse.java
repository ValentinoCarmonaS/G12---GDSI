package com.easyeat.catalog.dto;

import com.easyeat.catalog.domain.EstablishmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public class EstablishmentSummaryResponse {

    private UUID id;
    private String name;
    private EstablishmentStatus status;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime openingTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime closingTime;

    private String timezone;
    private boolean isOpen;

    public EstablishmentSummaryResponse() {
    }

    public EstablishmentSummaryResponse(UUID id, String name, EstablishmentStatus status,
                                        LocalTime openingTime, LocalTime closingTime,
                                        String timezone, boolean isOpen) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.timezone = timezone;
        this.isOpen = isOpen;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EstablishmentStatus getStatus() {
        return status;
    }

    public void setStatus(EstablishmentStatus status) {
        this.status = status;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EstablishmentSummaryResponse that = (EstablishmentSummaryResponse) o;
        return isOpen == that.isOpen && Objects.equals(id, that.id) && Objects.equals(name, that.name) && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, status, isOpen);
    }
}

