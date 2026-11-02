package com.easyeat.operations.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Opening hours of one establishment for one day of the week. A day with no row is
 * closed (EC-008). The opening-before-closing rule (INV-7) is enforced by the DTO
 * validation and by the {@code chk_schedule_times} database constraint.
 */
@Entity
@Table(name = "establishment_operating_schedules")
public class EstablishmentOperatingSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "establishment_id", nullable = false, updatable = false)
    private UUID establishmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, updatable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EstablishmentOperatingSchedule() {
    }

    public EstablishmentOperatingSchedule(UUID establishmentId, DayOfWeek dayOfWeek,
                                          LocalTime openingTime, LocalTime closingTime, boolean closed) {
        this.establishmentId = establishmentId;
        this.dayOfWeek = dayOfWeek;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.closed = closed;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(LocalTime openingTime, LocalTime closingTime, boolean closed) {
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.closed = closed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public boolean isClosed() {
        return closed;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
