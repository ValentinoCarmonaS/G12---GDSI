package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.OrderConfirmationTrackingResponse;
import com.easyeat.order.event.OrderPaymentApprovedEvent;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for assembling consolidated order confirmation, green screen data and live tracking.
 * Guarantees emission of OrderPaymentApprovedEvent upon payment confirmation (AS-1, AS-3, INV-001, INV-004).
 */
@Service
public class OrderConfirmationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationTrackingService.class);
    private static final Set<OrderStatus> APPROVED_STATUSES = Set.of(
            OrderStatus.IN_PROGRESS,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.COMPLETED,
            OrderStatus.PAID,
            OrderStatus.CONFIRMED
    );

    private final OrderRepository orderRepository;
    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final KdsTicketRepository kdsTicketRepository;

    public OrderConfirmationTrackingService(
            OrderRepository orderRepository,
            EstablishmentRepository establishmentRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this(orderRepository, establishmentRepository, userRepository, eventPublisher, null);
    }

    @Autowired
    public OrderConfirmationTrackingService(
            OrderRepository orderRepository,
            EstablishmentRepository establishmentRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) KdsTicketRepository kdsTicketRepository
    ) {
        this.orderRepository = orderRepository;
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.kdsTicketRepository = kdsTicketRepository;
    }

    /**
     * Assembles consolidated response for confirmation and tracking views.
     */
    @Transactional(readOnly = true)
    public OrderConfirmationTrackingResponse getConfirmationTracking(UUID orderId, UUID currentUserId) {
        if (orderId == null) {
            throw new OrderNotFoundException("ID de orden no proporcionado");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (currentUserId != null && order.getUserId() != null && !order.getUserId().equals(currentUserId)) {
            log.warn("Unauthorized access attempt to orderId={} by userId={}", orderId, currentUserId);
            throw new OrderNotFoundException(orderId);
        }

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.EXPIRED
                || order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException("La orden en estado " + order.getStatus() + " no admite vista de confirmación");
        }

        // INV-001: Authoritative approval condition
        boolean isApproved = APPROVED_STATUSES.contains(order.getStatus());

        String approvalStatus = isApproved ? "APPROVED" : "PENDING";

        String establishmentName = establishmentRepository.findById(order.getEstablishmentId())
                .map(Establishment::getName)
                .orElse("Restaurante");

        Integer ticketNumber = (kdsTicketRepository != null)
                ? kdsTicketRepository.findByOrderId(order.getId())
                        .map(KdsTicket::getTicketNumber)
                        .orElse(null)
                : null;

        List<OrderConfirmationTrackingResponse.OrderTrackingItemDto> items = order.getItems().stream()
                .map(item -> new OrderConfirmationTrackingResponse.OrderTrackingItemDto(
                        item.getDishId(),
                        item.getDishName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        String trackingUrl = "/pedidos/" + order.getId() + "/seguimiento";

        return new OrderConfirmationTrackingResponse(
                order.getId(),
                ticketNumber,
                order.getStatus(),
                approvalStatus,
                order.getEstablishmentId(),
                establishmentName,
                order.getFulfillmentMode(),
                order.getPickupDate(),
                order.getTimeSlotStart(),
                order.getTimeSlotEnd(),
                order.getTotalAmount(),
                items,
                trackingUrl,
                Instant.now()
        );
    }

    /**
     * Marks order as payment approved and triggers domain event for async email receipt dispatch.
     */
    @Transactional
    public void confirmAndApprovePayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);

        String recipientEmail = "comensal@easyeat.com";
        if (order.getUserId() != null) {
            recipientEmail = userRepository.findById(order.getUserId())
                    .map(User::getEmail)
                    .orElse("comensal@easyeat.com");
        }

        String trackingUrl = "/pedidos/" + order.getId() + "/seguimiento";

        OrderPaymentApprovedEvent event = new OrderPaymentApprovedEvent(
                order.getId(),
                order.getEstablishmentId(),
                order.getUserId(),
                recipientEmail,
                order.getTotalAmount(),
                trackingUrl
        );

        eventPublisher.publishEvent(event);
        log.info("Published OrderPaymentApprovedEvent for orderId={}", orderId);
    }
}
