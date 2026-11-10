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
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("T028: AtomicStockDeductionIntegrationTest (AS-6, CB-07, CB-08, FR-007, FR-008, FR-009, INV-01)")
class AtomicStockDeductionIntegrationTest extends BaseIntegrationTest {

    @Autowired(required = false)
    private StockDeductionService stockDeductionService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private StockAuditEntryRepository stockAuditEntryRepository;

    @Test
    @DisplayName("AS-6: Deducción atómica exacta de recetas enteras y fraccionarias con registro de auditoría")
    void shouldDeductExactFractionalQuantitiesAndRecordAuditEntries() {
        assertThat(stockDeductionService).as("StockDeductionService must be implemented").isNotNull();

        Establishment establishment = createTestEstablishment("Restaurante Atómico");
        Dish burger = createTestDish(establishment.getId(), "Hamburguesa Completa", new BigDecimal("5500.00"));

        // Insumo 1: Carne (decimal/fraccionaria: 0.1800 kg por plato)
        Ingredient beef = new Ingredient(establishment.getId(), "Carne Picada", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("4500.00"));
        beef.setCurrentStock(new BigDecimal("10.0000"));
        beef.setStatus(IngredientStatus.ACTIVE);
        beef = ingredientRepository.save(beef);

        // Insumo 2: Pan de hamburguesa (entera: 1.0000 unidad por plato)
        Ingredient bun = new Ingredient(establishment.getId(), "Pan Brioche", IngredientCategory.PRODUCE, UnitOfMeasure.U, new BigDecimal("300.00"));
        bun.setCurrentStock(new BigDecimal("20.0000"));
        bun.setStatus(IngredientStatus.ACTIVE);
        bun = ingredientRepository.save(bun);

        DishRecipeIngredient beefRecipe = new DishRecipeIngredient(burger.getId(), beef.getId(), new BigDecimal("0.1800"));
        dishRecipeIngredientRepository.save(beefRecipe);

        DishRecipeIngredient bunRecipe = new DishRecipeIngredient(burger.getId(), bun.getId(), new BigDecimal("1.0000"));
        dishRecipeIngredientRepository.save(bunRecipe);

        UUID orderId = UUID.randomUUID();
        // 3 porciones de Hamburguesa Completa
        // Carne demandada: 3 * 0.1800 = 0.5400 kg. Stock final: 10.0000 - 0.5400 = 9.4600 kg.
        // Pan demandado: 3 * 1.0000 = 3.0000 unidades. Stock final: 20.0000 - 3.0000 = 17.0000 unidades.
        List<OrderItemSnapshot> snapshots = List.of(
                new OrderItemSnapshot(burger.getId(), burger.getName(), 3)
        );

        StockDeductionResult result = stockDeductionService.deductRecipeStockForOrder(orderId, establishment.getId(), snapshots);

        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.tenantId()).isEqualTo(establishment.getId());
        assertThat(result.totalIngredientsAffected()).isEqualTo(2);
        assertThat(result.alreadyProcessed()).isFalse();

        // Verificar existencias actualizadas en base de datos
        Ingredient updatedBeef = ingredientRepository.findById(beef.getId()).orElseThrow();
        Ingredient updatedBun = ingredientRepository.findById(bun.getId()).orElseThrow();

        assertThat(updatedBeef.getCurrentStock()).isEqualByComparingTo(new BigDecimal("9.4600"));
        assertThat(updatedBun.getCurrentStock()).isEqualByComparingTo(new BigDecimal("17.0000"));

        // Verificar registros en stock_audit_entries
        List<StockAuditEntry> beefAudits = stockAuditEntryRepository
                .findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(establishment.getId(), beef.getId());
        assertThat(beefAudits).isNotEmpty();
        StockAuditEntry beefEntry = beefAudits.get(0);
        assertThat(beefEntry.getPreviousStock()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(beefEntry.getNewStock()).isEqualByComparingTo(new BigDecimal("9.4600"));
        assertThat(beefEntry.getNetDelta()).isEqualByComparingTo(new BigDecimal("0.5400"));

        List<StockAuditEntry> bunAudits = stockAuditEntryRepository
                .findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(establishment.getId(), bun.getId());
        assertThat(bunAudits).isNotEmpty();
        StockAuditEntry bunEntry = bunAudits.get(0);
        assertThat(bunEntry.getPreviousStock()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(bunEntry.getNewStock()).isEqualByComparingTo(new BigDecimal("17.0000"));
        assertThat(bunEntry.getNetDelta()).isEqualByComparingTo(new BigDecimal("3.0000"));
    }

    @Test
    @DisplayName("CB-07, CB-08, INV-01: 'Todo o nada' ante insuficiencia parcial - Rollback absoluto sin descuentos residuales")
    void shouldRollbackCompletelyWithoutPartialDeductionsWhenAnyIngredientIsInsufficient() {
        assertThat(stockDeductionService).as("StockDeductionService must be implemented").isNotNull();

        Establishment establishment = createTestEstablishment("Restaurante Rollback");
        Dish burger = createTestDish(establishment.getId(), "Mega Burger", new BigDecimal("6500.00"));

        // Insumo 1: Carne (suficiente: 5.0000 kg)
        Ingredient beef = new Ingredient(establishment.getId(), "Carne Suficiente", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("4500.00"));
        beef.setCurrentStock(new BigDecimal("5.0000"));
        beef.setStatus(IngredientStatus.ACTIVE);
        beef = ingredientRepository.save(beef);

        // Insumo 2: Queso (insuficiente: se necesitan 0.6000 kg para 3 hamburguesas pero hay 0.2000 kg)
        Ingredient cheese = new Ingredient(establishment.getId(), "Queso Cheddar Escaso", IngredientCategory.DAIRY, UnitOfMeasure.KG, new BigDecimal("6000.00"));
        cheese.setCurrentStock(new BigDecimal("0.2000"));
        cheese.setStatus(IngredientStatus.ACTIVE);
        cheese = ingredientRepository.save(cheese);

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(burger.getId(), beef.getId(), new BigDecimal("0.2500")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(burger.getId(), cheese.getId(), new BigDecimal("0.2000")));

        UUID orderId = UUID.randomUUID();
        List<OrderItemSnapshot> snapshots = List.of(
                new OrderItemSnapshot(burger.getId(), burger.getName(), 3)
        );

        // Demanda requerida: Carne 0.7500 kg (alcanza), Queso 0.6000 kg (supera los 0.2000 kg existentes)
        assertThatThrownBy(() -> stockDeductionService.deductRecipeStockForOrder(orderId, establishment.getId(), snapshots))
                .isInstanceOf(InsufficientIngredientStockException.class);

        // Comprobación de invariante: ni un gramo de carne fue debitado (Rollback completo)
        Ingredient untouchedBeef = ingredientRepository.findById(beef.getId()).orElseThrow();
        Ingredient untouchedCheese = ingredientRepository.findById(cheese.getId()).orElseThrow();

        assertThat(untouchedBeef.getCurrentStock()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(untouchedCheese.getCurrentStock()).isEqualByComparingTo(new BigDecimal("0.2000"));

        // Comprobación de que no se persistieron auditorías
        List<StockAuditEntry> beefAudits = stockAuditEntryRepository
                .findAllByTenantIdAndIngredientIdOrderByCreatedAtDesc(establishment.getId(), beef.getId());
        assertThat(beefAudits).isEmpty();
    }
}
