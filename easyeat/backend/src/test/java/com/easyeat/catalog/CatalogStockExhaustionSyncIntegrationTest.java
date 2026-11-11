package com.easyeat.catalog;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.service.DishAvailabilityEngine;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
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

@DisplayName("T031: CatalogStockExhaustionSyncIntegrationTest (FR-013, INV-10, C3)")
class CatalogStockExhaustionSyncIntegrationTest extends BaseIntegrationTest {

    @Autowired(required = false)
    private StockDeductionService stockDeductionService;

    @Autowired
    private DishAvailabilityEngine dishAvailabilityEngine;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Test
    @DisplayName("FR-013, INV-10: Al agotarse un insumo tras la deducción, el catálogo sincroniza inmediatamente a available = false")
    void shouldImmediatelyReflectUnavailabilityInCatalogWhenIngredientExhausts() {
        assertThat(stockDeductionService).as("StockDeductionService must be implemented").isNotNull();

        Establishment establishment = createTestEstablishment("Restaurante Catálogo Sincronizado");

        // Plato A: Salmón Grillé (requiere 0.2000 kg de Salmón)
        Dish salmonDish = createTestDish(establishment.getId(), "Salmón Grillé", new BigDecimal("12000.00"));

        // Plato B: Tartar de Salmón (comparte el mismo insumo, requiere 0.1500 kg de Salmón)
        Dish tartarDish = createTestDish(establishment.getId(), "Tartar de Salmón", new BigDecimal("9500.00"));

        Ingredient salmon = new Ingredient(
                establishment.getId(), "Salmón Rosado Fresco", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("15000.00")
        );
        // Stock inicial suficiente para exactamente 1 Salmón Grillé
        salmon.setCurrentStock(new BigDecimal("0.2000"));
        salmon.setStatus(IngredientStatus.ACTIVE);
        salmon = ingredientRepository.save(salmon);

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(salmonDish.getId(), salmon.getId(), new BigDecimal("0.2000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(tartarDish.getId(), salmon.getId(), new BigDecimal("0.1500")));

        // Antes del pedido, ambos platos tienen stock
        assertThat(dishAvailabilityEngine.isDishAvailable(salmonDish.getId(), establishment.getId())).isTrue();
        assertThat(dishAvailabilityEngine.isDishAvailable(tartarDish.getId(), establishment.getId())).isTrue();
        assertThat(dishAvailabilityEngine.calculateAvailableStock(salmonDish.getId(), establishment.getId())).isEqualTo(1);
        assertThat(dishAvailabilityEngine.calculateAvailableStock(tartarDish.getId(), establishment.getId())).isEqualTo(1);

        // Se confirma una orden por 1 Salmón Grillé (consume los 0.2000 kg restantes dejando stock en 0.0000)
        UUID orderId = UUID.randomUUID();
        stockDeductionService.deductRecipeStockForOrder(
                orderId,
                establishment.getId(),
                List.of(new OrderItemSnapshot(salmonDish.getId(), salmonDish.getName(), 1))
        );

        // Stock agotado físicamente
        Ingredient updatedSalmon = ingredientRepository.findById(salmon.getId()).orElseThrow();
        assertThat(updatedSalmon.getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);

        // Sincronización INMEDIATA sin jobs ni retrasos asíncronos (FR-013, INV-10)
        assertThat(dishAvailabilityEngine.isDishAvailable(salmonDish.getId(), establishment.getId())).isFalse();
        assertThat(dishAvailabilityEngine.calculateAvailableStock(salmonDish.getId(), establishment.getId())).isEqualTo(0);

        // Insumo compartido: Tartar de Salmón también debe figurar inmediatamente como no disponible (C3)
        assertThat(dishAvailabilityEngine.isDishAvailable(tartarDish.getId(), establishment.getId())).isFalse();
        assertThat(dishAvailabilityEngine.calculateAvailableStock(tartarDish.getId(), establishment.getId())).isEqualTo(0);
    }
}
