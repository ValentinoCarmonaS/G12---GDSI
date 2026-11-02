package com.easyeat.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class CartStockValidationRequest {

    @NotNull(message = "tenantId must not be null")
    private UUID tenantId;

    @NotEmpty(message = "items list must not be empty")
    @Valid
    private List<ItemRequest> items;

    public CartStockValidationRequest() {
    }

    public CartStockValidationRequest(UUID tenantId, List<ItemRequest> items) {
        this.tenantId = tenantId;
        this.items = items;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public List<ItemRequest> getItems() {
        return items;
    }

    public void setItems(List<ItemRequest> items) {
        this.items = items;
    }

    public static class ItemRequest {

        @NotNull(message = "dishId must not be null")
        private UUID dishId;

        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;

        public ItemRequest() {
        }

        public ItemRequest(UUID dishId, Integer quantity) {
            this.dishId = dishId;
            this.quantity = quantity;
        }

        public UUID getDishId() {
            return dishId;
        }

        public void setDishId(UUID dishId) {
            this.dishId = dishId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
