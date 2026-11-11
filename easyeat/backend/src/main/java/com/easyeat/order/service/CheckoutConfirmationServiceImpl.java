package com.easyeat.order.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.exception.InsufficientIngredientStockException;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.service.StockDeductionService;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.*;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CheckoutConfirmationServiceImpl implements CheckoutConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutConfirmationServiceImpl.class);

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final KdsQueueService kdsQueueService;
    private final DishRepository dishRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final StockDeductionService stockDeductionService;

    @Autowired
    public CheckoutConfirmationServiceImpl(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            KdsQueueService kdsQueueService,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            StockDeductionService stockDeductionService
    ) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.kdsQueueService = kdsQueueService;
        this.dishRepository = dishRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.stockDeductionService = stockDeductionService;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentConfirmationResponse confirmPayment(UUID dinerId, PaymentConfirmationRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException(request.orderId()));

        // Idempotency check (INV-07, CB-12)
        if (order.getStatus() == OrderStatus.IN_PROGRESS) {
            log.info("Order {} is already IN_PROGRESS. Returning idempotent response.", order.getId());
            KdsTicket kdsTicket = kdsQueueService.dispatchOrderToKitchen(order);
            return new PaymentConfirmationResponse(
                    order.getId(),
                    OrderStatus.IN_PROGRESS.name(),
                    "APPROVED",
                    true,
                    mapToSummaryDto(kdsTicket),
                    true
            );
        }

        // Debit stock for all ingredients in the order
        debitIngredientsStock(order);

        // Transition order to IN_PROGRESS
        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        // Confirm temporary reservation
        temporaryReservationRepository.findByOrderId(order.getId()).ifPresent(res -> {
            res.confirm();
            temporaryReservationRepository.save(res);
        });

        // Dispatch to KDS
        KdsTicket ticket = kdsQueueService.dispatchOrderToKitchen(order);

        return new PaymentConfirmationResponse(
                order.getId(),
                OrderStatus.IN_PROGRESS.name(),
                "APPROVED",
                true,
                mapToSummaryDto(ticket),
                false
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PendingPaymentResponse registerPendingPayment(UUID dinerId, PendingPaymentRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException(request.orderId()));

        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            order.setStatus(OrderStatus.PENDING_ACCREDITATION);
            orderRepository.save(order);
        }

        return new PendingPaymentResponse(
                order.getId(),
                OrderStatus.PENDING_ACCREDITATION.name(),
                "PENDING",
                false, // stockDebited = false (INV-03)
                false, // kdsQueued = false (INV-04)
                true,  // reservationFrozen = true
                "Tu pago está pendiente de confirmación. Tu pedido pasará a cocina tan pronto como se reciba la aprobación final."
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ZeroAmountOrderResponse confirmZeroAmountOrder(UUID dinerId, ZeroAmountOrderRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException(request.orderId()));

        if (order.getStatus() == OrderStatus.IN_PROGRESS) {
            log.info("Zero-amount order {} is already IN_PROGRESS. Returning idempotent response.", order.getId());
            KdsTicket existingTicket = kdsQueueService.dispatchOrderToKitchen(order);
            return new ZeroAmountOrderResponse(
                    order.getId(),
                    OrderStatus.IN_PROGRESS.name(),
                    BigDecimal.ZERO,
                    true,
                    true,
                    mapToSummaryDto(existingTicket),
                    "¡Orden 100% bonificada confirmada! Tu pedido ya fue enviado a la cocina."
            );
        }

        if (order.getTotalAmount() != null && order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("La orden tiene un monto superior a cero; debe procesarse mediante pasarela.");
        }

        // Debit stock for all ingredients in the order
        debitIngredientsStock(order);

        // Transition order to IN_PROGRESS
        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        // Confirm temporary reservation
        temporaryReservationRepository.findByOrderId(order.getId()).ifPresent(res -> {
            res.confirm();
            temporaryReservationRepository.save(res);
        });

        // Dispatch to KDS
        KdsTicket ticket = kdsQueueService.dispatchOrderToKitchen(order);

        return new ZeroAmountOrderResponse(
                order.getId(),
                OrderStatus.IN_PROGRESS.name(),
                BigDecimal.ZERO,
                true,
                true,
                mapToSummaryDto(ticket),
                "¡Orden 100% bonificada confirmada! Tu pedido ya fue enviado a la cocina."
        );
    }

    private void debitIngredientsStock(Order order) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }

        List<OrderItemSnapshot> snapshots = items.stream()
                .map(item -> new OrderItemSnapshot(
                        item.getDishId(),
                        dishRepository.findById(item.getDishId()).map(Dish::getName).orElse("Plato"),
                        item.getQuantity()
                ))
                .toList();

        stockDeductionService.deductRecipeStockForOrder(order.getId(), order.getEstablishmentId(), snapshots);
    }

    private KdsTicketSummaryDto mapToSummaryDto(KdsTicket ticket) {
        if (ticket == null) return null;
        return new KdsTicketSummaryDto(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus().name(),
                ticket.getQueuedAt(),
                new KdsTicketSummaryDto.EstimatedPickup(
                        ticket.getPickupDate(),
                        ticket.getTimeSlotStart(),
                        ticket.getTimeSlotEnd()
                )
        );
    }
}
