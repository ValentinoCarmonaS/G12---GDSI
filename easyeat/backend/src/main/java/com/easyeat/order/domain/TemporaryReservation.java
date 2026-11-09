package com.easyeat.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "temporary_reservations")
public class TemporaryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 50)
    private ReleaseReason releaseReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected TemporaryReservation() {
    }

    public TemporaryReservation(Order order, String idempotencyKey, Instant expiresAt) {
        this.order = order;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        this.releaseReason = null;
        this.updatedAt = this.confirmedAt;
    }

    public void release(ReleaseReason reason) {
        this.status = ReservationStatus.RELEASED;
        this.releaseReason = reason;
        this.releasedAt = Instant.now();
        this.updatedAt = this.releasedAt;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.releaseReason = ReleaseReason.EXPIRED;
        this.releasedAt = Instant.now();
        this.updatedAt = this.releasedAt;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public ReleaseReason getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(ReleaseReason releaseReason) {
        this.releaseReason = releaseReason;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}

