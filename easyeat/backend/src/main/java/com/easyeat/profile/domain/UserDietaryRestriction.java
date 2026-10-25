package com.easyeat.profile.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_dietary_restrictions")
public class UserDietaryRestriction {

    @EmbeddedId
    private UserDietaryRestrictionId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserDietaryRestriction() {
    }

    public UserDietaryRestriction(UserDietaryRestrictionId id) {
        this.id = id;
        this.createdAt = Instant.now();
    }

    public UserDietaryRestriction(UUID userId, String restrictionCode) {
        this.id = new UserDietaryRestrictionId(userId, restrictionCode);
        this.createdAt = Instant.now();
    }

    public UserDietaryRestrictionId getId() {
        return id;
    }

    public void setId(UserDietaryRestrictionId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
