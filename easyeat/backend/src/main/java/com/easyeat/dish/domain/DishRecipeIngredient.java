package com.easyeat.dish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dish_recipe_ingredients")
public class DishRecipeIngredient {

    @EmbeddedId
    private DishRecipeIngredientId id = new DishRecipeIngredientId();

    @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DishRecipeIngredient() {
    }

    public DishRecipeIngredient(DishRecipeIngredientId id, BigDecimal quantity) {
        this.id = id != null ? id : new DishRecipeIngredientId();
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public DishRecipeIngredient(UUID dishId, UUID ingredientId, BigDecimal quantity) {
        this.id = new DishRecipeIngredientId(dishId, ingredientId);
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public DishRecipeIngredientId getId() {
        return id;
    }

    public void setId(DishRecipeIngredientId id) {
        this.id = id != null ? id : new DishRecipeIngredientId();
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getDishId() {
        return id != null ? id.getDishId() : null;
    }

    public void setDishId(UUID dishId) {
        if (this.id == null) {
            this.id = new DishRecipeIngredientId();
        }
        this.id.setDishId(dishId);
    }

    public UUID getIngredientId() {
        return id != null ? id.getIngredientId() : null;
    }

    public void setIngredientId(UUID ingredientId) {
        if (this.id == null) {
            this.id = new DishRecipeIngredientId();
        }
        this.id.setIngredientId(ingredientId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DishRecipeIngredient that = (DishRecipeIngredient) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DishRecipeIngredient{" +
                "id=" + id +
                ", quantity=" + quantity +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
