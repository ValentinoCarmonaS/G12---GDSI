package com.easyeat.catalog.dto;

import com.easyeat.catalog.domain.EstablishmentStatus;

import java.util.UUID;

public class EstablishmentDetailDto {

    private UUID id;
    private String name;
    private EstablishmentStatus status;
    private Boolean isOpen;
    private String nextOpeningSchedule;

    public EstablishmentDetailDto() {
    }

    public EstablishmentDetailDto(UUID id, String name, EstablishmentStatus status, Boolean isOpen, String nextOpeningSchedule) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.isOpen = isOpen;
        this.nextOpeningSchedule = nextOpeningSchedule;
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

    public Boolean getIsOpen() {
        return isOpen;
    }

    public void setIsOpen(Boolean open) {
        isOpen = open;
    }

    public String getNextOpeningSchedule() {
        return nextOpeningSchedule;
    }

    public void setNextOpeningSchedule(String nextOpeningSchedule) {
        this.nextOpeningSchedule = nextOpeningSchedule;
    }
}

