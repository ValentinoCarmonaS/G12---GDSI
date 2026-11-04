package com.easyeat.catalog.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "establishments")
public class Establishment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EstablishmentStatus status = EstablishmentStatus.ACTIVE;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime = LocalTime.of(11, 0);

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime = LocalTime.of(23, 0);

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "America/Argentina/Buenos_Aires";

    @Column(name = "lead_time_minutes", nullable = false)
    private Integer leadTimeMinutes = 30;

    @Column(name = "max_capacity_per_slot", nullable = false)
    private Integer maxCapacityPerSlot = 10;

    @Column(name = "operating_days", nullable = false, length = 100)
    private String operatingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Establishment() {
    }

    public Establishment(UUID id, String name, EstablishmentStatus status, LocalTime openingTime, LocalTime closingTime, String timezone) {
        this.id = id;
        this.name = name;
        this.status = status != null ? status : EstablishmentStatus.ACTIVE;
        this.openingTime = openingTime != null ? openingTime : LocalTime.of(11, 0);
        this.closingTime = closingTime != null ? closingTime : LocalTime.of(23, 0);
        this.timezone = timezone != null ? timezone : "America/Argentina/Buenos_Aires";
        this.leadTimeMinutes = 30;
        this.maxCapacityPerSlot = 10;
        this.operatingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
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

    public Integer getLeadTimeMinutes() {
        return leadTimeMinutes;
    }

    public void setLeadTimeMinutes(Integer leadTimeMinutes) {
        this.leadTimeMinutes = leadTimeMinutes;
    }

    public Integer getMaxCapacityPerSlot() {
        return maxCapacityPerSlot;
    }

    public void setMaxCapacityPerSlot(Integer maxCapacityPerSlot) {
        this.maxCapacityPerSlot = maxCapacityPerSlot;
    }

    public String getOperatingDays() {
        return operatingDays;
    }

    public void setOperatingDays(String operatingDays) {
        this.operatingDays = operatingDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isOpen(LocalTime now) {
        if (status != EstablishmentStatus.ACTIVE) {
            return false;
        }
        if (openingTime == null || closingTime == null) {
            return false;
        }
        if (openingTime.isBefore(closingTime)) {
            return !now.isBefore(openingTime) && now.isBefore(closingTime);
        } else if (openingTime.isAfter(closingTime)) {
            return !now.isBefore(openingTime) || now.isBefore(closingTime);
        } else {
            return true;
        }
    }

    public boolean isOpen() {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone != null ? timezone : "America/Argentina/Buenos_Aires");
        } catch (Exception e) {
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        }
        return isOpen(LocalTime.now(zoneId));
    }

    public boolean isOpenOn(LocalDate date) {
        if (date == null) {
            return false;
        }
        return isOpenOn(date.getDayOfWeek());
    }

    public boolean isOpenOn(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null || operatingDays == null || operatingDays.isBlank()) {
            return false;
        }
        for (String day : operatingDays.split(",")) {
            if (day.trim().equalsIgnoreCase(dayOfWeek.name())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSlotWithinOperatingHours(LocalTime start, LocalTime end) {
        if (start == null || end == null || openingTime == null || closingTime == null) {
            return false;
        }
        return !start.isBefore(openingTime) && !end.isAfter(closingTime);
    }

    public LocalTime calculateCurrentOngoingSlot(LocalTime now) {
        if (now == null) {
            return null;
        }
        int minute = (now.getMinute() / 15) * 15;
        return LocalTime.of(now.getHour(), minute);
    }
}
