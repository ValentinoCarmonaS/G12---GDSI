package com.easyeat.order.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class TimeSlotAvailability {

    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate date;
    private Integer maxCapacity;
    private Long bookedCount;
    private Double occupancyPercentage;
    private String status; // "AVAILABLE", "HIGH_DEMAND", "SOLD_OUT", "EXPIRED_OR_LEAD_TIME"
    private boolean isSelectable;

    public TimeSlotAvailability() {
    }

    public TimeSlotAvailability(LocalTime startTime,
                                LocalTime endTime,
                                LocalDate date,
                                Integer maxCapacity,
                                Long bookedCount,
                                Double occupancyPercentage,
                                String status,
                                boolean isSelectable) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.date = date;
        this.maxCapacity = maxCapacity;
        this.bookedCount = bookedCount;
        this.occupancyPercentage = occupancyPercentage;
        this.status = status;
        this.isSelectable = isSelectable;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public Long getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(Long bookedCount) {
        this.bookedCount = bookedCount;
    }

    public Double getOccupancyPercentage() {
        return occupancyPercentage;
    }

    public void setOccupancyPercentage(Double occupancyPercentage) {
        this.occupancyPercentage = occupancyPercentage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSelectable() {
        return isSelectable;
    }

    public void setSelectable(boolean selectable) {
        isSelectable = selectable;
    }
}
