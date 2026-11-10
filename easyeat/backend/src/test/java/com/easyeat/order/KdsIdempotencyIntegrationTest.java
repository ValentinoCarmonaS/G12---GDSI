package com.easyeat.order;

import com.easyeat.auth.domain.User;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientCategory;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.dto.UnitOfMeasure;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.repository.StockAuditEntryRepository;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentConfirmationResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderItemRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import com.easyeat.order.service.CheckoutConfirmationService;
import com.easyeat.order.service.WebhookProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T037: KdsIdempotencyIntegrationTest (INV-07, CB-12, FR-012)")
class KdsIdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CheckoutConfirmationService checkoutConfirmationService;

    @Autowired
    private WebhookProcessingService webhookProcessingService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private TemporaryReservationRepository temporaryReservationRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private DishRecipeIngredientRepository dishRecipeIngredientRepository;

    @Autowired
    private StockAuditEntryRepository stockAuditEntryRepository;

    @Autowired
    private KdsTicketRepository kdsTicketRepository;

    @Test
    @DisplayName("INV-07 / CB-12: Webhook de pasarela y recarga de página sobre orden confirmada no re-debitan stock ni duplican KDS tickets")
    void duplicateWebhookAndPageReloadShouldNotRedeductStockOrDuplicateKdsTickets() {
        Establishment establishment = createTestEstablishment("Bistro Idempotency");
        Dish burger = createTestDish(establishment.getId(), "Hamburguesa Completa", new BigDecimal("4500.00"));
        User diner = createTestDiner("diner.idempotency." + UUID.randomUUID() + "@test.com");

        Ingredient meat = new Ingredient(establishment.getId(), "Carne Picada", IngredientCategory.MEAT, UnitOfMeasure.KG, new BigDecimal("1200.00"));
        meat.setCurrentStock(new BigDecimal("10.0000"));
        meat.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(meat);

        Ingredient bread = new Ingredient(establishment.getId(), "Pan Brioche", IngredientCategory.DRY_GOODS, UnitOfMeasure.U, new BigDecimal("200.00"));
        bread.setCurrentStock(new BigDecimal("20.0000"));
        bread.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(bread);

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(burger.getId(), meat.getId(), new BigDecimal("0.2000")));
        dishRecipeIngredientRepository.save(new DishRecipeIngredient(burger.getId(), bread.getId(), new BigDecimal("1.0000")));

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 30));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("9000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        // 2 burgers -> 0.4000 kg meat, 2 bread units
        OrderItem item = new OrderItem(order, burger.getId(), burger.getName(), burger.getPrice(), 2, new BigDecimal("9000.00"));
        orderItemRepository.save(item);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        // 1. Primer evento: el webhook asíncrono de la pasarela aprueba el pago
        webhookProcessingService.processAsynchronousNotification(
                "MERCADOPAGO",
                "mp_pay_999888",
                order.getId().toString(),
                "approved",
                "accredited"
        );

        // Verificaciones tras el primer evento
        Order confirmedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        Ingredient meatAfterFirst = ingredientRepository.findById(meat.getId()).orElseThrow();
        Ingredient breadAfterFirst = ingredientRepository.findById(bread.getId()).orElseThrow();
        assertThat(meatAfterFirst.getCurrentStock()).isEqualByComparingTo("9.6000"); // 10 - 0.4
        assertThat(breadAfterFirst.getCurrentStock()).isEqualByComparingTo("18.0000"); // 20 - 2

        List<KdsTicket> ticketsFirst = kdsTicketRepository.findByEstablishmentId(establishment.getId(), Pageable.unpaged()).getContent();
        assertThat(ticketsFirst).hasSize(1);
        KdsTicket firstTicket = ticketsFirst.get(0);
        assertThat(firstTicket.getOrderId()).isEqualTo(order.getId());
        assertThat(firstTicket.getStatus()).isEqualTo(KdsTicketStatus.QUEUED);

        long auditCountAfterFirst = stockAuditEntryRepository.findAll().stream()
                .filter(a -> a.getIngredientId().equals(meat.getId()) || a.getIngredientId().equals(bread.getId()))
                .count();
        assertThat(auditCountAfterFirst).isEqualTo(2);

        // 2. Segundo evento: el comensal recarga la pantalla de retorno /checkout/payment-return (PaymentConfirmationRequest)
        PaymentConfirmationRequest returnPageRequest = new PaymentConfirmationRequest(
                order.getId(),
                "mp_pay_999888",
                "approved"
        );
        PaymentConfirmationResponse returnPageResponse = checkoutConfirmationService.confirmPayment(diner.getId(), returnPageRequest);

        assertThat(returnPageResponse.idempotentReplay()).isTrue();
        assertThat(returnPageResponse.orderStatus()).isEqualTo("IN_PROGRESS");
        assertThat(returnPageResponse.kdsTicket()).isNotNull();
        assertThat(returnPageResponse.kdsTicket().ticketId()).isEqualTo(firstTicket.getId());

        // 3. Tercer evento: reintento de webhook duplicado por red desde la pasarela
        webhookProcessingService.processAsynchronousNotification(
                "MERCADOPAGO",
                "mp_pay_999888",
                order.getId().toString(),
                "approved",
                "accredited"
        );

        // 4. Verificaciones finales de idempotencia estricta
        Ingredient meatFinal = ingredientRepository.findById(meat.getId()).orElseThrow();
        Ingredient breadFinal = ingredientRepository.findById(bread.getId()).orElseThrow();
        assertThat(meatFinal.getCurrentStock()).isEqualByComparingTo("9.6000");
        assertThat(breadFinal.getCurrentStock()).isEqualByComparingTo("18.0000");

        List<KdsTicket> ticketsFinal = kdsTicketRepository.findByEstablishmentId(establishment.getId(), Pageable.unpaged()).getContent();
        assertThat(ticketsFinal).hasSize(1);

        long auditCountFinal = stockAuditEntryRepository.findAll().stream()
                .filter(a -> a.getIngredientId().equals(meat.getId()) || a.getIngredientId().equals(bread.getId()))
                .count();
        assertThat(auditCountFinal).isEqualTo(2);
    }

    @Test
    @DisplayName("INV-07 / Concurrencia: Múltiples confirmaciones concurrentes sobre la misma orden no duplican tickets ni descuentan doble")
    void concurrentConfirmationRequestsShouldMaintainSingleDeductionAndSingleKdsTicket() throws Exception {
        Establishment establishment = createTestEstablishment("Bistro Concurrent Idempotency");
        Dish dish = createTestDish(establishment.getId(), "Ensalada César", new BigDecimal("3000.00"));
        User diner = createTestDiner("diner.concurrent." + UUID.randomUUID() + "@test.com");

        Ingredient lettuce = new Ingredient(establishment.getId(), "Lechuga", IngredientCategory.PRODUCE, UnitOfMeasure.KG, new BigDecimal("500.00"));
        lettuce.setCurrentStock(new BigDecimal("5.0000"));
        lettuce.setStatus(IngredientStatus.ACTIVE);
        ingredientRepository.save(lettuce);

        dishRecipeIngredientRepository.save(new DishRecipeIngredient(dish.getId(), lettuce.getId(), new BigDecimal("0.3000")));

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(diner.getId());
        order.setFulfillmentMode(FulfillmentMode.IMMEDIATE);
        order.setPickupDate(LocalDate.now());
        order.setTimeSlotStart(LocalTime.of(12, 0));
        order.setTimeSlotEnd(LocalTime.of(12, 30));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("3000.00"));
        order.setExpiresAt(Instant.now().plusSeconds(900));
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, dish.getId(), dish.getName(), dish.getPrice(), 1, new BigDecimal("3000.00"));
        orderItemRepository.save(item);

        TemporaryReservation reservation = new TemporaryReservation(
                order,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        temporaryReservationRepository.save(reservation);

        int threadCount = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<PaymentConfirmationResponse>> futures = new CopyOnWriteArrayList<>();

        PaymentConfirmationRequest request = new PaymentConfirmationRequest(
                order.getId(),
                "mp_concurrent_tx",
                "approved"
        );

        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(() -> {
                latch.await();
                return checkoutConfirmationService.confirmPayment(diner.getId(), request);
            }));
        }

        latch.countDown();
        executorService.shutdown();
        boolean finished = executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        int successfulResponses = 0;
        int idempotentReplays = 0;
        for (Future<PaymentConfirmationResponse> future : futures) {
            PaymentConfirmationResponse resp = future.get();
            assertThat(resp).isNotNull();
            assertThat(resp.orderStatus()).isEqualTo("IN_PROGRESS");
            if (resp.idempotentReplay()) {
                idempotentReplays++;
            } else {
                successfulResponses++;
            }
        }

        assertThat(successfulResponses).isEqualTo(1);
        assertThat(idempotentReplays).isEqualTo(threadCount - 1);

        Ingredient finalLettuce = ingredientRepository.findById(lettuce.getId()).orElseThrow();
        assertThat(finalLettuce.getCurrentStock()).isEqualByComparingTo("4.7000"); // 5.0 - 0.3

        List<KdsTicket> tickets = kdsTicketRepository.findByEstablishmentId(establishment.getId(), Pageable.unpaged()).getContent();
        assertThat(tickets).hasSize(1);
    }
}
