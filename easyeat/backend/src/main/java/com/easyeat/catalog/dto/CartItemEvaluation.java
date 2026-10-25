package com.easyeat.catalog.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CartItemEvaluation {

    private UUID dishId;
    private Boolean isCompliant;
    private List<String> conflictingRestrictions = new ArrayList<>();

    public CartItemEvaluation() {
    }

    public CartItemEvaluation(UUID dishId, Boolean isCompliant, List<String> conflictingRestrictions) {
        this.dishId = dishId;
        this.isCompliant = isCompliant;
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
    }

    public UUID getDishId() {
        return dishId;
    }

    public void setDishId(UUID dishId) {
        this.dishId = dishId;
    }

    public Boolean getIsCompliant() {
        return isCompliant;
    }

    public void setIsCompliant(Boolean compliant) {
        isCompliant = compliant;
    }

    public List<String> getConflictingRestrictions() {
        return conflictingRestrictions;
    }

    public void setConflictingRestrictions(List<String> conflictingRestrictions) {
        this.conflictingRestrictions = conflictingRestrictions != null ? conflictingRestrictions : new ArrayList<>();
    }
}
