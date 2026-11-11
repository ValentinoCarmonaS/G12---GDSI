package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.inventory.service.StockDeductionService;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.*;
import com.easyeat.order.event.OrderPaymentApprovedEvent;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class CheckoutConfirmationServiceImpl implements CheckoutConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutConfirmationServiceImpl.class);
    private static final String DEFAULT_RECIPIENT_EMAIL = "comensal@easyeat.com";
    private static final Set<OrderStatus> ALREADY_CONFIRMED_STATUSES = Set.of(
            OrderStatus.IN_PROGRESS,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.COMPLETED,
            OrderStatus.PAID,
            OrderStatus.CONFIRMED
    );

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final KdsQueueService kdsQueueService;
    private final DishRepository dishRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final StockDeductionService stockDeductionService;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayResolver paymentGatewayResolver;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CheckoutConfirmationServiceImpl(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            KdsQueueService kdsQueueService,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            StockDeductionService stockDeductionService
    ) {
        this(
                orderRepository,
                temporaryReservationRepository,
                kdsQueueService,
                dishRepository,
                dishRecipeIngredientRepository,
                ingredientRepository,
                stockDeductionService,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Autowired
    public CheckoutConfirmationServiceImpl(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            KdsQueueService kdsQueueService,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            StockDeductionService stockDeductionService,
            @Autowired(required = false) PaymentRepository paymentRepository,
            @Autowired(required = false) PaymentGatewayResolver paymentGatewayResolver,
            @Autowired(required = false) MercadoPagoProperties mercadoPagoProperties,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.kdsQueueService = kdsQueueService;
        this.dishRepository = dishRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.stockDeductionService = stockDeductionService;
        this.paymentRepository = paymentRepository;
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentConfirmationResponse confirmPayment(UUID dinerId, PaymentConfirmationRequest request) {
        Objects.requireNonNull(request, "Request cannot be null");
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException(request.orderId()));

        validateOwnership(order, dinerId);

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            throw new InvalidOrderStateException(
                    "No se puede confirmar una orden que ya se encuentra en estado " + order.getStatus()
            );
        }

        // Idempotency check across all post-confirmation statuses (INV-07, CB-12)
        if (ALREADY_CONFIRMED_STATUSES.contains(order.getStatus())) {
            log.info("Order {} is already in status {}. Returning idempotent response.", order.getId(), order.getStatus());
            KdsTicket kdsTicket = kdsQueueService.dispatchOrderToKitchen(order);
            return new PaymentConfirmationResponse(
                    order.getId(),
                    order.getStatus().name(),
                    "APPROVED",
                    false,
                    mapToSummaryDto(kdsTicket),
                    true
            );
        }

        verifyApprovedWithGatewayIfConfigured(dinerId, order, request);

        // Debit stock for all ingredients in the order
        debitIngredientsStock(order);

        // Transition order to IN_PROGRESS
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Confirm temporary reservation
        temporaryReservationRepository.findByOrderId(order.getId()).ifPresent(res -> {
            res.confirm();
            temporaryReservationRepository.save(res);
        });

        // Mark latest in-process payment row as APPROVED safely
        approveExistingPaymentIfInProcess(order.getId(), request.gatewayPaymentId(), "accredited");

        // Dispatch to KDS
        KdsTicket ticket = kdsQueueService.dispatchOrderToKitchen(order);

        // Publish domain event (delivered asynchronously AFTER_COMMIT)
        publishOrderPaymentApproved(order);

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

        validateOwnership(order, dinerId);

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            throw new InvalidOrderStateException(
                    "No se puede registrar un pago pendiente para una orden en estado " + order.getStatus()
            );
        }

        if (!ALREADY_CONFIRMED_STATUSES.contains(order.getStatus())) {
            order.setStatus(OrderStatus.PENDING_ACCREDITATION);
            order.setUpdatedAt(Instant.now());
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

        validateOwnership(order, dinerId);

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            throw new InvalidOrderStateException(
                    "No se puede confirmar una orden bonificada en estado " + order.getStatus()
            );
        }

        if (ALREADY_CONFIRMED_STATUSES.contains(order.getStatus())) {
            log.info("Zero-amount order {} is already in status {}. Returning idempotent response.", order.getId(), order.getStatus());
            KdsTicket existingTicket = kdsQueueService.dispatchOrderToKitchen(order);
            return new ZeroAmountOrderResponse(
                    order.getId(),
                    order.getStatus().name(),
                    BigDecimal.ZERO,
                    true,
                    false,
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
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Confirm temporary reservation
        temporaryReservationRepository.findByOrderId(order.getId()).ifPresent(res -> {
            res.confirm();
            temporaryReservationRepository.save(res);
        });

        // Dispatch to KDS
        KdsTicket ticket = kdsQueueService.dispatchOrderToKitchen(order);

        // Publish domain event (delivered asynchronously AFTER_COMMIT)
        publishOrderPaymentApproved(order);

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

    private void validateOwnership(Order order, UUID dinerId) {
        if (dinerId != null && order.getUserId() != null && !order.getUserId().equals(dinerId)) {
            throw new AccessDeniedException("No tenés permiso para confirmar o modificar esta orden.");
        }
    }

    private void verifyApprovedWithGatewayIfConfigured(UUID dinerId, Order order, PaymentConfirmationRequest request) {
        if (dinerId == null || !hasConfiguredAccessToken() || paymentGatewayResolver == null) {
            return;
        }
        if (request.gatewayPaymentId() == null || request.gatewayPaymentId().isBlank()) {
            throw new InvalidOrderStateException("El pago aún no cuenta con identificador de pasarela para validar su estado.");
        }

        PaymentGatewayPort gatewayPort = paymentGatewayResolver.resolve(PaymentMethod.MERCADO_PAGO);
        PaymentGatewayPort.GatewayPaymentResult gatewayResult = gatewayPort.queryPayment(request.gatewayPaymentId());
        if (gatewayResult == null || gatewayResult.status() != PaymentStatus.APPROVED) {
            throw new InvalidOrderStateException("El pago aún no fue aprobado por Mercado Pago.");
        }
        if (gatewayResult.externalReference() != null
                && !gatewayResult.externalReference().equals(order.getId().toString())) {
            throw new InvalidOrderStateException("El comprobante de pago no corresponde a la orden indicada.");
        }
    }

    private boolean hasConfiguredAccessToken() {
        return mercadoPagoProperties != null
                && mercadoPagoProperties.accessToken() != null
                && !mercadoPagoProperties.accessToken().isBlank();
    }

    private void approveExistingPaymentIfInProcess(UUID orderId, String gatewayPaymentId, String statusDetail) {
        if (paymentRepository == null) {
            return;
        }
        paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.IN_PROCESS) {
                payment.approve(gatewayPaymentId, statusDetail);
                paymentRepository.save(payment);
            }
        });
    }

    private void publishOrderPaymentApproved(Order order) {
        if (eventPublisher == null) {
            return;
        }
        String recipientEmail = DEFAULT_RECIPIENT_EMAIL;
        if (userRepository != null && order.getUserId() != null) {
            recipientEmail = userRepository.findById(order.getUserId())
                    .map(User::getEmail)
                    .orElse(DEFAULT_RECIPIENT_EMAIL);
        }
        String trackingUrl = "/pedidos/" + order.getId() + "/seguimiento";
        BigDecimal totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;

        eventPublisher.publishEvent(new OrderPaymentApprovedEvent(
                order.getId(),
                order.getEstablishmentId(),
                order.getUserId(),
                recipientEmail,
                totalAmount,
                trackingUrl
        ));
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
