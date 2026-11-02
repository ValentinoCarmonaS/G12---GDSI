package com.easyeat.operations.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Visibility flag of one online payment method for one establishment (INV-4, INV-9).
 * Stores no credentials, tokens or bank data (A-005).
 */
@Entity
@Table(name = "establishment_payment_methods")
public class EstablishmentPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "establishment_id", nullable = false, updatable = false)
    private UUID establishmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EstablishmentPaymentMethod() {
    }

    public EstablishmentPaymentMethod(UUID establishmentId, PaymentMethod paymentMethod, boolean enabled) {
        this.establishmentId = establishmentId;
        this.paymentMethod = paymentMethod;
        this.enabled = enabled;
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

    public UUID getId() {
        return id;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
