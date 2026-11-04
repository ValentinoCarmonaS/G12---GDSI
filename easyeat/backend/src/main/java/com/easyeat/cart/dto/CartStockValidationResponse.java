package com.easyeat.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class CartStockValidationResponse {

    @JsonProperty("isValid")
    private boolean isValid;
    private List<ItemResponse> items;

    public CartStockValidationResponse() {
    }

    public CartStockValidationResponse(boolean isValid, List<ItemResponse> items) {
        this.isValid = isValid;
        this.items = items;
    }

    @JsonProperty("isValid")
    public boolean isValid() {
        return isValid;
    }

    @JsonProperty("isValid")
    public void setValid(boolean valid) {
        isValid = valid;
    }

    public List<ItemResponse> getItems() {
        return items;
    }

    public void setItems(List<ItemResponse> items) {
        this.items = items;
    }

    public static class ItemResponse {

        private UUID dishId;

        @JsonProperty("isAvailable")
        private boolean isAvailable;
        private int availableStock;
        private int requestedQuantity;

        public ItemResponse() {
        }

        public ItemResponse(UUID dishId, boolean isAvailable, int availableStock, int requestedQuantity) {
            this.dishId = dishId;
            this.isAvailable = isAvailable;
            this.availableStock = availableStock;
            this.requestedQuantity = requestedQuantity;
        }

        public UUID getDishId() {
            return dishId;
        }

        public void setDishId(UUID dishId) {
            this.dishId = dishId;
        }

        @JsonProperty("isAvailable")
        public boolean isAvailable() {
            return isAvailable;
        }

        @JsonProperty("isAvailable")
        public void setAvailable(boolean available) {
            isAvailable = available;
        }

        public int getAvailableStock() {
            return availableStock;
        }

        public void setAvailableStock(int availableStock) {
            this.availableStock = availableStock;
        }

        public int getRequestedQuantity() {
            return requestedQuantity;
        }

        public void setRequestedQuantity(int requestedQuantity) {
            this.requestedQuantity = requestedQuantity;
        }
    }
}
