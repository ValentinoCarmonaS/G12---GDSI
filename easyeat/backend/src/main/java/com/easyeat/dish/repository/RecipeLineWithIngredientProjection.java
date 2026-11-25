package com.easyeat.dish.repository;

import com.easyeat.inventory.dto.IngredientStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecipeLineWithIngredientProjection {
    UUID getDishId();
    UUID getIngredientId();
    BigDecimal getQuantity();
    String getIngredientName();
    IngredientStatus getIngredientStatus();
    BigDecimal getCurrentStock();
}
