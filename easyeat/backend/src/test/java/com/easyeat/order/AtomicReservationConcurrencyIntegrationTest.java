package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.dto.PaymentInitiationRequest;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T014: AtomicReservationConcurrencyIntegrationTest (INV-01, INV-07, CB-10, SC-005)")
class AtomicReservationConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutPaymentService checkoutPaymentService;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Test
    @DisplayName("20 hilos compitiendo concurrentemente por último cupo de franja y stock: exactamente 1 triunfa con reserva ACTIVE y 19 son rechazados atómicamente")
    void shouldAtomicallyHandle20ConcurrentReservationAttempts() throws InterruptedException {
        Establishment establishment = createTestEstablishment("Restaurante Concurrencia");
        establishment.setMaxCapacityPerSlot(1);
        establishmentRepository.save(establishment);

        Dish dish = createTestDish(establishment.getId(), "Plato Concurrente", new BigDecimal("3500.00"));

        Ingredient ingredient = new Ingredient(establishment.getId(), "Carne Picada", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("200.00"));
        ingredient.setCurrentStock(new BigDecimal("1.00"));
        ingredient.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(ingredient);

        DishRecipeIngredient recipe = new DishRecipeIngredient(
                new DishRecipeIngredientId(dish.getId(), ingredient.getId()),
                new BigDecimal("1.00"),
                1
        );
        dishRecipeIngredientRepository.save(recipe);

        LocalDate pickupDate = LocalDate.now().plusDays(1);
        LocalTime slotStart = LocalTime.of(12, 30);
        LocalTime slotEnd = slotStart.plusMinutes(15);

        int threadCount = 20;
        List<User> diners = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            diners.add(createTestDiner("concurrency_user_" + i + "_" + UUID.randomUUID() + "@test.com"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger slotRejectedCount = new AtomicInteger(0);
        AtomicInteger stockRejectedCount = new AtomicInteger(0);
        AtomicInteger otherErrorsCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            User diner = diners.get(i);
            PaymentInitiationRequest request = new PaymentInitiationRequest(
                    establishment.getId(),
                    FulfillmentMode.SCHEDULED,
                    PaymentMethod.MERCADO_PAGO,
                    pickupDate,
                    slotStart,
                    slotEnd,
                    List.of(new PaymentInitiationRequest.OrderItemRequest(dish.getId(), 1))
            );

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    checkoutPaymentService.initiatePayment(diner.getId(), request);
                    successCount.incrementAndGet();
                } catch (SlotCapacityExceededException e) {
                    slotRejectedCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("stock")) {
                        stockRejectedCount.incrementAndGet();
                    } else {
                        otherErrorsCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(slotRejectedCount.get() + stockRejectedCount.get()).isEqualTo(19);
        assertThat(otherErrorsCount.get()).isEqualTo(0);

        long activeReservations = temporaryReservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
                .count();
        assertThat(activeReservations).isEqualTo(1);
    }
}

