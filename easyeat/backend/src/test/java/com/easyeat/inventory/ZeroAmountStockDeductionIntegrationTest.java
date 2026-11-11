package com.easyeat.inventory;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.StockDeductionResult;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.entity.StockAuditEntry;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import com.easyeat.inventory.service.StockDeductionService;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.dto.OrderItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T029: ZeroAmountStockDeductionIntegrationTest (AS-6b, CB-04, FR-005, INV-06)")
class ZeroAmountStockDeductionIntegrationTest extends BaseIntegrationTest {

    @Autowired(required = false)
    private StockDeductionService stockDeductionService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private StockAuditEntryRepository stockAuditEntryRepository;

    @Test
    @DisplayName("AS-6b, CB-04: Deducción atómica en órdenes con bonificación al 100% monto cero descuenta materias primas y audita")
    void shouldDeductStockForZeroAmountBonusOrderWithoutMonetaryTransaction() {
        assertThat(stockDeductionService).as("StockDeductionService must be implemented").isNotNull();

        Establishment establishment = createTestEstablishment("Restaurante Promociones");
        Dish freeDish = createTestDish(establishment.getId(), "Plato Promocional 100% Off", BigDecimal.ZERO);

        Ingredient vegetable = new Ingredient(
                establishment.getId(), "Verduras Frescas", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("800.00")
        );
        vegetable.setCurrentStock(new BigDecimal("8.0000"));
        vegetable.setStatus(IngredientStatus.ACTIVE);
        vegetable = ingredientRepository.save(vegetable);

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(freeDish.getId(), vegetable.getId(), new BigDecimal("0.5000")));

        UUID orderId = UUID.randomUUID();
        // 2 platos bonificados -> 2 * 0.5000 = 1.0000 kg demandado
        List<OrderItemSnapshot> snapshots = List.of(
                new OrderItemSnapshot(freeDish.getId(), freeDish.getName(), 2)
        );

        StockDeductionResult result = stockDeductionService.deductRecipeStockForOrder(orderId, establishment.getId(), snapshots);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.totalIngredientsAffected()).isEqualTo(1);
        assertThat(result.alreadyProcessed()).isFalse();

        // Stock verificado: 8.0000 - 1.0000 = 7.0000
        Ingredient updatedVegetable = ingredientRepository.findById(vegetable.getId()).orElseThrow();
        assertThat(updatedVegetable.getCurrentStock()).isEqualByComparingTo(new BigDecimal("7.0000"));

        // Auditoría verificada con justificación explícita de la orden
        List<StockAuditEntry> audits = stockAuditEntryRepository
                .findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(establishment.getId(), vegetable.getId());
        assertThat(audits).isNotEmpty();
        StockAuditEntry entry = audits.get(0);
        assertThat(entry.getPreviousStock()).isEqualByComparingTo(new BigDecimal("8.0000"));
        assertThat(entry.getNewStock()).isEqualByComparingTo(new BigDecimal("7.0000"));
        assertThat(entry.getNetDelta()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(entry.getJustification()).contains(orderId.toString());
    }
}
