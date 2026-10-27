package com.easyeat.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DishDietaryRestrictionId implements Serializable {

    @Column(name = "dish_id", nullable = false)
    private UUID dishId;

    @Column(name = "restriction_code", nullable = false, length = 50)
    private String restrictionCode;

    public DishDietaryRestrictionId() {
    }

    public DishDietaryRestrictionId(UUID dishId, String restrictionCode) {
        this.dishId = dishId;
        this.restrictionCode = restrictionCode;
    }

    public UUID getDishId() {
        return dishId;
    }

    public void setDishId(UUID dishId) {
        this.dishId = dishId;
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
        DishDietaryRestrictionId that = (DishDietaryRestrictionId) o;
        return Objects.equals(dishId, that.dishId) && Objects.equals(restrictionCode, that.restrictionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dishId, restrictionCode);
    }
}
