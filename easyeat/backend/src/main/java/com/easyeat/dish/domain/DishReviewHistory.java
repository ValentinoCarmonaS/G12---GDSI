package com.easyeat.dish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One administrative decision on a dish: approval, rejection, or cancellation (FR-034, FR-035,
 * FR-041).
 *
 * <p>Immutable, and structurally so. {@code @Immutable} stops Hibernate emitting an UPDATE for
 * this mapping at all; there are no setters and no {@code @PreUpdate}; and the repository that
 * persists it exposes no deletion. FR-034 asks that a violation be impossible to express rather
 * than merely detectable, and those three together are what deliver that.
 *
 * <p>There is no {@code updated_at}: a row that cannot change has nothing to say about when it
 * last did.
 *
 * <p>Construction goes through the named decision factories rather than a public constructor,
 * which makes FR-021's rejection rule part of the type. A rejection cannot exist without a usable
 * comment because {@code rejected} demands one; approval and cancellation do not accept a
 * required feedback comment.
 */
@Entity
@Immutable
@Table(name = "dish_review_history")
public class DishReviewHistory {

    /** FR-021: measured after trimming, which is why the check trims first. */
    private static final int COMMENT_MIN_LENGTH = 10;
    private static final int COMMENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dish_id", nullable = false, updatable = false)
    private UUID dishId;

    /**
     * Denormalised from the dish so every audit query is tenant-filtered without a join: a query
     * that forgets the join still cannot read across tenants.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cycle_number", nullable = false, updatable = false)
    private Integer cycleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20, updatable = false)
    private ReviewEventType eventType;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "comment", updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA requires a no-argument constructor; nothing else may use it. */
    protected DishReviewHistory() {
    }

    private DishReviewHistory(UUID dishId, UUID tenantId, Integer cycleNumber,
                              ReviewEventType eventType, UUID adminId, String comment) {
        this.dishId = requireId(dishId, "dishId");
        this.tenantId = requireId(tenantId, "tenantId");
        this.cycleNumber = requirePositiveCycle(cycleNumber);
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.adminId = requireId(adminId, "adminId");
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    /**
     * An approval. Takes no comment: FR-017's advisories are acknowledged at the moment of the
     * decision and are not feedback to the Chef, so there is nothing to record here.
     */
    public static DishReviewHistory approved(UUID dishId, UUID tenantId, Integer cycleNumber,
                                             UUID adminId) {
        return new DishReviewHistory(dishId, tenantId, cycleNumber, ReviewEventType.APPROVED,
                adminId, null);
    }

    /**
     * A rejection, which FR-020 and FR-021 make inseparable from its feedback: 10 to 1000
     * characters measured <em>after</em> trimming, never blank. The comment is stored trimmed and
     * verbatim — any markup it contains is never interpreted, it is escaped on output.
     */
    public static DishReviewHistory rejected(UUID dishId, UUID tenantId, Integer cycleNumber,
                                              UUID adminId, String comment) {
        return new DishReviewHistory(dishId, tenantId, cycleNumber, ReviewEventType.REJECTED,
                adminId, requireUsableComment(comment));
    }

    public static DishReviewHistory cancelled(UUID dishId, UUID tenantId, Integer cycleNumber,
                                               UUID adminId, String comment) {
        return new DishReviewHistory(dishId, tenantId, cycleNumber, ReviewEventType.CANCELLED,
                adminId, comment);
    }

    private static String requireUsableComment(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "A rejection requires feedback for the Chef (FR-020): the comment is missing.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < COMMENT_MIN_LENGTH || trimmed.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "A rejection comment must be between " + COMMENT_MIN_LENGTH + " and "
                            + COMMENT_MAX_LENGTH + " characters after trimming (FR-021). "
                            + "Received " + trimmed.length() + ".");
        }
        return trimmed;
    }

    private static Integer requirePositiveCycle(Integer cycleNumber) {
        if (cycleNumber == null || cycleNumber < 1) {
            throw new IllegalArgumentException(
                    "cycleNumber is monotonic from 1 per dish (FR-022). Received " + cycleNumber);
        }
        return cycleNumber;
    }

    private static UUID requireId(UUID value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDishId() {
        return dishId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Integer getCycleNumber() {
        return cycleNumber;
    }

    public ReviewEventType getEventType() {
        return eventType;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DishReviewHistory that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "DishReviewHistory{dishId=" + dishId + ", cycle=" + cycleNumber
                + ", eventType=" + eventType + ", createdAt=" + createdAt + '}';
    }
}
