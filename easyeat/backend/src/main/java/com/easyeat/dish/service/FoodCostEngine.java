package com.easyeat.dish.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FoodCostEngine {

    public record IngredientCostItem(BigDecimal quantity, BigDecimal unitPurchaseCost) {}

    public record CalculationResult(
            List<BigDecimal> lineSubtotals,
            BigDecimal totalCost,
            boolean hasUnpricedIngredients,
            Set<Integer> unpricedIndices
    ) {}

    public CalculationResult calculate(List<IngredientCostItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La lista de insumos no puede ser nula ni vacía para el cálculo de costos.");
        }

        List<BigDecimal> lineSubtotals = new ArrayList<>(items.size());
        Set<Integer> unpricedIndices = new HashSet<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            IngredientCostItem item = items.get(i);
            BigDecimal quantity = item.quantity() != null ? item.quantity() : BigDecimal.ZERO;
            BigDecimal unitPurchaseCost = item.unitPurchaseCost();

            boolean isUnpriced = (unitPurchaseCost == null || unitPurchaseCost.compareTo(BigDecimal.ZERO) <= 0);
            if (isUnpriced) {
                unpricedIndices.add(i);
            }

            BigDecimal costToMultiply = (unitPurchaseCost != null && unitPurchaseCost.compareTo(BigDecimal.ZERO) > 0)
                    ? unitPurchaseCost
                    : BigDecimal.ZERO;

            BigDecimal subtotal = quantity.multiply(costToMultiply).setScale(2, RoundingMode.HALF_UP);
            lineSubtotals.add(subtotal);
            totalCost = totalCost.add(subtotal);
        }

        totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);
        boolean hasUnpriced = !unpricedIndices.isEmpty();

        return new CalculationResult(
                Collections.unmodifiableList(lineSubtotals),
                totalCost,
                hasUnpriced,
                Collections.unmodifiableSet(unpricedIndices)
        );
    }
}

