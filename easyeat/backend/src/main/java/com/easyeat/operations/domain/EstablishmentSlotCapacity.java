package com.easyeat.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Kitchen capacity of one establishment for one 15-minute slot, plus the counter of
 * approved orders. A slot with no row behaves as capacity 0 / sold out (EC-007).
 * {@code approvedOrdersCount} is only ever incremented by the atomic conditional
 * update of the repository, never by changing the capacity (INV-8).
 */
@Entity
@Table(name = "establishment_slot_capacities")
public class EstablishmentSlotCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "establishment_id", nullable = false, updatable = false)
    private UUID establishmentId;

    @Column(name = "slot_date", nullable = false, updatable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "approved_orders_count", nullable = false)
    private int approvedOrdersCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EstablishmentSlotCapacity() {
    }

    public EstablishmentSlotCapacity(UUID establishmentId, TimeSlot slot, int maxCapacity) {
        this(establishmentId, slot, maxCapacity, 0);
    }

    public EstablishmentSlotCapacity(UUID establishmentId, TimeSlot slot, int maxCapacity, int approvedOrdersCount) {
        requireNonNegative(maxCapacity, "maxCapacity");
        requireNonNegative(approvedOrdersCount, "approvedOrdersCount");
        this.establishmentId = establishmentId;
        this.slotDate = slot.slotDate();
        this.startTime = slot.startTime();
        this.endTime = slot.endTime();
        this.maxCapacity = maxCapacity;
        this.approvedOrdersCount = approvedOrdersCount;
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

    public void changeMaxCapacity(int newMaxCapacity) {
        requireNonNegative(newMaxCapacity, "maxCapacity");
        this.maxCapacity = newMaxCapacity;
    }

    /** INV-3: sold out once approved orders reach or exceed the capacity. */
    public boolean isSoldOut() {
        return approvedOrdersCount >= maxCapacity;
    }

    public TimeSlot toTimeSlot() {
        return new TimeSlot(slotDate, startTime);
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be greater than or equal to 0");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getApprovedOrdersCount() {
        return approvedOrdersCount;
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
