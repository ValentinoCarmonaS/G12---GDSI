package com.easyeat.catalog.dto;

import java.util.ArrayList;
import java.util.List;

public class CartComplianceResponse {

    private List<CartItemEvaluation> items = new ArrayList<>();

    public CartComplianceResponse() {
    }

    public CartComplianceResponse(List<CartItemEvaluation> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<CartItemEvaluation> getItems() {
        return items;
    }

    public void setItems(List<CartItemEvaluation> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
