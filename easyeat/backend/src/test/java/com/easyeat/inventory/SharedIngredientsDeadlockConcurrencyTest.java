package com.easyeat.inventory;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.service.StockDeductionService;
import com.easyeat.order.BaseIntegrationTest;
import com.easyeat.order.dto.OrderItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T030: SharedIngredientsDeadlockConcurrencyTest (C3, CB-06, INV-01, INV-08)")
class SharedIngredientsDeadlockConcurrencyTest extends BaseIntegrationTest {

    @Autowired(required = false)
    private StockDeductionService stockDeductionService;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Test
    @DisplayName("C3, CB-06: 20 hilos concurrentes compitiendo por insumos compartidos en orden invertido no provocan deadlock")
    void shouldHandle20ConcurrentThreadsWithoutDeadlockDueToPessimisticOrderAscending() throws InterruptedException {
        assertThat(stockDeductionService).as("StockDeductionService must be implemented").isNotNull();

        Establishment establishment = createTestEstablishment("Restaurante AntiDeadlock");

        // Creamos 3 insumos compartidos
        Ingredient ingA = new Ingredient(establishment.getId(), "Insumo Alfa", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("100.00"));
        ingA.setCurrentStock(new BigDecimal("1000.0000"));
        ingA.setStatus(IngredientStatus.ACTIVE);
        ingA = ingredientRepository.save(ingA);

        Ingredient ingB = new Ingredient(establishment.getId(), "Insumo Beta", IngredientCategory.DAIRY, UnitOfMeasure.KG, new BigDecimal("150.00"));
        ingB.setCurrentStock(new BigDecimal("1000.0000"));
        ingB.setStatus(IngredientStatus.ACTIVE);
        ingB = ingredientRepository.save(ingB);

        Ingredient ingC = new Ingredient(establishment.getId(), "Insumo Gamma", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("200.00"));
        ingC.setCurrentStock(new BigDecimal("1000.0000"));
        ingC.setStatus(IngredientStatus.ACTIVE);
        ingC = ingredientRepository.save(ingC);

        // Plato 1 requiere A y B
        Dish dish1 = createTestDish(establishment.getId(), "Plato A-B", new BigDecimal("1200.00"));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish1.getId(), ingA.getId(), new BigDecimal("1.0000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish1.getId(), ingB.getId(), new BigDecimal("1.0000")));

        // Plato 2 requiere B y C
        Dish dish2 = createTestDish(establishment.getId(), "Plato B-C", new BigDecimal("1400.00"));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish2.getId(), ingB.getId(), new BigDecimal("1.0000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish2.getId(), ingC.getId(), new BigDecimal("1.0000")));

        // Plato 3 requiere C y A (ciclo potencial si no se ordenan por ID ASC)
        Dish dish3 = createTestDish(establishment.getId(), "Plato C-A", new BigDecimal("1600.00"));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish3.getId(), ingC.getId(), new BigDecimal("1.0000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish3.getId(), ingA.getId(), new BigDecimal("1.0000")));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UUID orderId = UUID.randomUUID();
                    List<OrderItemSnapshot> items;

                    if (index % 3 == 0) {
                        items = List.of(new OrderItemSnapshot(dish1.getId(), dish1.getName(), 1));
                    } else if (index % 3 == 1) {
                        items = List.of(new OrderItemSnapshot(dish2.getId(), dish2.getName(), 1));
                    } else {
                        items = List.of(new OrderItemSnapshot(dish3.getId(), dish3.getName(), 1));
                    }

                    stockDeductionService.deductRecipeStockForOrder(orderId, establishment.getId(), items);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    errorCount.incrementAndGet();
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Disparo concurrente instantáneo
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("Execution timed out; deadlock suspected!").isTrue();
        assertThat(exceptions).as("Unexpected concurrency errors / deadlocks: " + exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Verificamos que las deducciones matemáticas coinciden exactamente con los 20 pedidos ejecutados
        Ingredient finalA = ingredientRepository.findById(ingA.getId()).orElseThrow();
        Ingredient finalB = ingredientRepository.findById(ingB.getId()).orElseThrow();
        Ingredient finalC = ingredientRepository.findById(ingC.getId()).orElseThrow();

        // Número de llamadas a cada plato:
        // index % 3 == 0: 7 veces (0, 3, 6, 9, 12, 15, 18) -> dish1 (A: 7, B: 7)
        // index % 3 == 1: 7 veces (1, 4, 7, 10, 13, 16, 19) -> dish2 (B: 7, C: 7)
        // index % 3 == 2: 6 veces (2, 5, 8, 11, 14, 17) -> dish3 (C: 6, A: 6)
        // Total A demandado: 7 + 6 = 13. Stock final: 1000 - 13 = 987
        // Total B demandado: 7 + 7 = 14. Stock final: 1000 - 14 = 986
        // Total C demandado: 7 + 6 = 13. Stock final: 1000 - 13 = 987
        assertThat(finalA.getCurrentStock()).isEqualByComparingTo(new BigDecimal("987.0000"));
        assertThat(finalB.getCurrentStock()).isEqualByComparingTo(new BigDecimal("986.0000"));
        assertThat(finalC.getCurrentStock()).isEqualByComparingTo(new BigDecimal("987.0000"));
    }
}
