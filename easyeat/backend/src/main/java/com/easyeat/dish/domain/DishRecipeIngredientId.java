package com.easyeat.dish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DishRecipeIngredientId implements Serializable {

    @Column(name = "dish_id", nullable = false)
    private UUID dishId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    public DishRecipeIngredientId() {
    }

    public DishRecipeIngredientId(UUID dishId, UUID ingredientId) {
        this.dishId = dishId;
        this.ingredientId = ingredientId;
    }

    public UUID getDishId() {
        return dishId;
    }

    public void setDishId(UUID dishId) {
        this.dishId = dishId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(UUID ingredientId) {
        this.ingredientId = ingredientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DishRecipeIngredientId that = (DishRecipeIngredientId) o;
        return Objects.equals(dishId, that.dishId) &&
                Objects.equals(ingredientId, that.ingredientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dishId, ingredientId);
    }

    @Override
    public String toString() {
        return "DishRecipeIngredientId{" +
                "dishId=" + dishId +
                ", ingredientId=" + ingredientId +
                '}';
    }
}
