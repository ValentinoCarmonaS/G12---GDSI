package com.easyeat.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CartComplianceRequest {

    @NotEmpty(message = "La lista de IDs de platos no puede estar vacía")
    private List<UUID> dishIds = new ArrayList<>();

    public CartComplianceRequest() {
    }

    public CartComplianceRequest(List<UUID> dishIds) {
        this.dishIds = dishIds != null ? dishIds : new ArrayList<>();
    }

    public List<UUID> getDishIds() {
        return dishIds;
    }

    public void setDishIds(List<UUID> dishIds) {
        this.dishIds = dishIds != null ? dishIds : new ArrayList<>();
    }
}
