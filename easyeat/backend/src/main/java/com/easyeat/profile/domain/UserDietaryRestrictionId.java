package com.easyeat.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserDietaryRestrictionId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "restriction_code", length = 50, nullable = false)
    private String restrictionCode;

    public UserDietaryRestrictionId() {
    }

    public UserDietaryRestrictionId(UUID userId, String restrictionCode) {
        this.userId = userId;
        this.restrictionCode = restrictionCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRestrictionCode() {
        return restrictionCode;
    }

    public void setRestrictionCode(String restrictionCode) {
        this.restrictionCode = restrictionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDietaryRestrictionId that = (UserDietaryRestrictionId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(restrictionCode, that.restrictionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, restrictionCode);
    }
}
