package com.easyeat.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational settings for kitchen KDS modifiers per establishment.
 */
@Entity
@Table(name = "establishment_kds_settings")
public class EstablishmentKdsSettings {

    @Id
    @Column(name = "establishment_id", nullable = false)
    private UUID establishmentId;

    @Column(name = "global_delay_minutes", nullable = false)
    private int globalDelayMinutes = 0;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public EstablishmentKdsSettings() {
    }

    public EstablishmentKdsSettings(UUID establishmentId, int globalDelayMinutes, UUID updatedByUserId) {
        this.establishmentId = establishmentId;
        this.globalDelayMinutes = Math.max(0, globalDelayMinutes);
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onSave() {
        if (this.globalDelayMinutes < 0) {
            this.globalDelayMinutes = 0;
        }
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public int getGlobalDelayMinutes() {
        return globalDelayMinutes;
    }

    public void setGlobalDelayMinutes(int globalDelayMinutes) {
        this.globalDelayMinutes = Math.max(0, globalDelayMinutes);
    }

    public UUID getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(UUID updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
