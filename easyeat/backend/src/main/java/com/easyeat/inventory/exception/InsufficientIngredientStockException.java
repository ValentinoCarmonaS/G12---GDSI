package com.easyeat.inventory.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Excepción de dominio arrojada cuando un ingrediente carece de stock suficiente para satisfacer una receta,
 * provocando el rollback integral de la transacción ("todo o nada").
 */
public class InsufficientIngredientStockException extends RuntimeException {

    private final UUID ingredientId;
    private final String ingredientName;
    private final BigDecimal demandedQuantity;
    private final BigDecimal availableQuantity;
    private final String unitOfMeasure;

    public InsufficientIngredientStockException(String message) {
        super(message);
        this.ingredientId = null;
        this.ingredientName = null;
        this.demandedQuantity = null;
        this.availableQuantity = null;
        this.unitOfMeasure = null;
    }

    public InsufficientIngredientStockException(UUID ingredientId,
                                                String ingredientName,
                                                BigDecimal demandedQuantity,
                                                BigDecimal availableQuantity,
                                                String unitOfMeasure) {
        super(String.format("Stock insuficiente para el ingrediente '%s' (%s): disponible %s %s, demandado %s %s",
                ingredientName, ingredientId, availableQuantity, unitOfMeasure, demandedQuantity, unitOfMeasure));
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.demandedQuantity = demandedQuantity;
        this.availableQuantity = availableQuantity;
        this.unitOfMeasure = unitOfMeasure;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public BigDecimal getDemandedQuantity() {
        return demandedQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }
}
