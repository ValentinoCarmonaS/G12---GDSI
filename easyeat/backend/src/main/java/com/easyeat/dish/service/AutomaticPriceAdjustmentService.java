package com.easyeat.dish.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service interface for automatic retail price (PVP) adjustments upon ingredient cost variations (C-09).
 */
public interface AutomaticPriceAdjustmentService {

    /**
     * Process an ingredient purchase cost variation and recalculate affected published dishes asynchronously or synchronously.
     *
     * @param tenantId Tenant UUID
     * @param ingredientId Modified ingredient UUID
     * @param previousCost Previous unit purchase cost
     * @param newCost New unit purchase cost
     * @param actorId User/Admin UUID who registered the stock adjustment
     */
    void processIngredientCostChange(
            UUID tenantId,
            UUID ingredientId,
            BigDecimal previousCost,
            BigDecimal newCost,
            UUID actorId
    );
}
