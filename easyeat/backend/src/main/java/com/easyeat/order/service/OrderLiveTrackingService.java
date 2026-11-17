package com.easyeat.order.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.order.domain.EstablishmentKdsSettings;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsDelayAdjustmentRequest;
import com.easyeat.order.dto.KdsDelayAdjustmentResponse;
import com.easyeat.order.dto.KdsTicketResponse;
import com.easyeat.order.dto.OrderLiveTrackingResponse;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.repository.EstablishmentKdsSettingsRepository;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderLiveTrackingService {

    private static final Logger log = LoggerFactory.getLogger(OrderLiveTrackingService.class);

    private OrderRepository orderRepository;
    private EstablishmentKdsSettingsRepository kdsSettingsRepository;
    private EstablishmentRepository establishmentRepository;
    private Clock clock;
    private KdsTicketRepository kdsTicketRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    public OrderLiveTrackingService() {
        this.clock = Clock.systemUTC();
    }

    public OrderLiveTrackingService(
            OrderRepository orderRepository,
            EstablishmentKdsSettingsRepository kdsSettingsRepository
    ) {
        this(orderRepository, kdsSettingsRepository, null, Clock.systemUTC(), null);
    }

    public OrderLiveTrackingService(
            OrderRepository orderRepository,
            EstablishmentKdsSettingsRepository kdsSettingsRepository,
            EstablishmentRepository establishmentRepository,
            Clock clock
    ) {
        this(orderRepository, kdsSettingsRepository, establishmentRepository, clock, null);
    }

    @Autowired
    public OrderLiveTrackingService(
            OrderRepository orderRepository,
            EstablishmentKdsSettingsRepository kdsSettingsRepository,
            @Autowired(required = false) EstablishmentRepository establishmentRepository,
            @Autowired(required = false) Clock clock,
            @Autowired(required = false) KdsTicketRepository kdsTicketRepository
    ) {
        this.orderRepository = orderRepository;
        this.kdsSettingsRepository = kdsSettingsRepository;
        this.establishmentRepository = establishmentRepository;
        this.clock = (clock != null) ? clock : Clock.systemUTC();
        this.kdsTicketRepository = kdsTicketRepository;
    }

    private Instant currentInstant() {
        return (clock != null) ? clock.instant() : Instant.now();
    }

    private ZoneId currentZoneId() {
        if (clock != null && clock.getZone() != null && !clock.getZone().equals(ZoneOffset.UTC)) {
            return clock.getZone();
        }
        return ZoneId.of("America/Argentina/Buenos_Aires");
    }

    private ZoneId resolveZoneId(UUID establishmentId) {
        ZoneId zoneId = currentZoneId();
        if (establishmentRepository != null && establishmentId != null) {
            zoneId = establishmentRepository.findById(establishmentId)
                    .map(Establishment::getTimezone)
                    .filter(tz -> tz != null && !tz.isBlank())
                    .map(tz -> {
                        try {
                            return ZoneId.of(tz);
                        } catch (Exception ignored) {
                            return null;
                        }
                    })
                    .orElse(zoneId);
        }
        return zoneId;
    }

    private Integer resolveTicketNumber(UUID orderId) {
        if (kdsTicketRepository == null || orderId == null) {
            return null;
        }
        return kdsTicketRepository.findByOrderId(orderId)
                .map(KdsTicket::getTicketNumber)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public OrderLiveTrackingResponse getLiveTracking(UUID orderId, UUID currentUserId) {
        if (orderId == null) {
            throw new OrderNotFoundException("ID de orden no proporcionado");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (currentUserId != null && order.getUserId() != null && !order.getUserId().equals(currentUserId)) {
            log.warn("Unauthorized access attempt to orderId={} by userId={}", orderId, currentUserId);
            throw new OrderNotFoundException(orderId);
        }

        Optional<KdsTicket> ticketOpt = (kdsTicketRepository != null)
                ? kdsTicketRepository.findByOrderId(order.getId())
                : Optional.empty();

        Integer ticketNumber = ticketOpt.map(KdsTicket::getTicketNumber).orElse(null);
        Instant now = currentInstant();

        List<OrderLiveTrackingResponse.LiveTrackingItemDto> items = (order.getItems() != null)
                ? order.getItems().stream()
                    .map(i -> new OrderLiveTrackingResponse.LiveTrackingItemDto(i.getDishName(), i.getQuantity()))
                    .toList()
                : List.of();

        // Terminal state: READY_FOR_PICKUP or COMPLETED (D-05, AS-8, INV-008, CB-008)
        boolean isReady = order.getStatus() == OrderStatus.READY_FOR_PICKUP ||
                order.getStatus() == OrderStatus.COMPLETED ||
                ticketOpt.map(t -> t.getStatus() == KdsTicketStatus.READY_FOR_PICKUP || t.getStatus() == KdsTicketStatus.COMPLETED).orElse(false);

        if (isReady) {
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus() == OrderStatus.COMPLETED ? OrderStatus.COMPLETED : OrderStatus.READY_FOR_PICKUP,
                    order.getFulfillmentMode(),
                    false,
                    null,
                    100,
                    true,
                    null,
                    "¡Tu pedido está listo para retirar! Por favor, acercate al mostrador para retirar tu comida.",
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    order.getSecurityCode(),
                    ticketOpt.map(KdsTicket::getStartedAt).orElse(null),
                    ticketOpt.map(KdsTicket::getReadyAt).orElse(null),
                    items
            );
        }

        // Spec 021: Adaptive state QUEUED (COM-8.1.1)
        if (ticketOpt.isPresent() && ticketOpt.get().getStatus() == KdsTicketStatus.QUEUED) {
            KdsTicket ticket = ticketOpt.get();
            LocalDate today = LocalDate.now(resolveZoneId(order.getEstablishmentId()));
            long ahead = 0;
            if (kdsTicketRepository != null && order.getEstablishmentId() != null) {
                ahead = kdsTicketRepository.countQueuedTicketsAhead(
                        order.getEstablishmentId(),
                        ticket.getPickupDate(),
                        ticket.getTimeSlotStart(),
                        ticket.getQueuedAt(),
                        ticket.getId(),
                        today
                );
            }
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    false,
                    null,
                    null,
                    false,
                    null,
                    "En cola en cocina",
                    now,
                    (int) ahead,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    items
            );
        }

        // Spec 021: Adaptive state IN_PREPARATION (COM-8.1.1, COM-7.3.2 BIS, T024)
        if (ticketOpt.isPresent() && ticketOpt.get().getStatus() == KdsTicketStatus.IN_PREPARATION) {
            KdsTicket ticket = ticketOpt.get();
            Instant startedAt = ticket.getStartedAt() != null ? ticket.getStartedAt() : (order.getCreatedAt() != null ? order.getCreatedAt() : now);
            int elapsedMinutes = (int) Math.max(0, Duration.between(startedAt, now).toMinutes());
            int specificDelay = ticket.getSpecificDelayMinutes();
            // COM-7.3.2 BIS: Base estimada de 20 min + specificDelayMinutes
            int totalEstimatedMinutes = 20 + specificDelay;
            // Floor de 1 minuto restante ante tiempo excedido (RF-016)
            int remainingMinutes = Math.max(1, totalEstimatedMinutes - elapsedMinutes);

            // Ceiling de 92% para la barra de progreso ante tiempo excedido (RF-016)
            double ratio = (double) elapsedMinutes / (double) totalEstimatedMinutes;
            int rawProgress = (int) Math.floor(15.0 + (ratio * 77.0));
            int progressPercent = Math.min(92, Math.max(15, rawProgress));

            String message = elapsedMinutes >= totalEstimatedMinutes
                    ? "Dando los últimos toques a tu pedido"
                    : "Cocinando / En preparación";

            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    true,
                    remainingMinutes,
                    progressPercent,
                    false,
                    null,
                    message,
                    now,
                    null,
                    totalEstimatedMinutes,
                    elapsedMinutes,
                    remainingMinutes,
                    progressPercent,
                    specificDelay,
                    null,
                    startedAt,
                    null,
                    items
            );
        }

        // Fallback when ticket is absent: orders not yet active in kitchen or already terminal/cancelled/expired
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    false,
                    null,
                    0,
                    false,
                    null,
                    resolveInactiveStatusMessage(order.getStatus()),
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    items
            );
        }

        // IMMEDIATE fulfillment mode fallback (D-03, AS-5, INV-005, AS-7, INV-007)
        if (order.getFulfillmentMode() == FulfillmentMode.IMMEDIATE) {
            long activeAhead = 0;
            if (orderRepository != null && order.getEstablishmentId() != null) {
                activeAhead = orderRepository.countActiveOrdersAhead(
                        order.getEstablishmentId(),
                        order.getCreatedAt() != null ? order.getCreatedAt() : now
                );
            }

            int globalDelay = 0;
            if (kdsSettingsRepository != null && order.getEstablishmentId() != null) {
                globalDelay = kdsSettingsRepository.findById(order.getEstablishmentId())
                        .map(EstablishmentKdsSettings::getGlobalDelayMinutes)
                        .orElse(0);
            }

            int baseMinutes = 20;
            int totalEstimatedMinutes = Math.max(1, baseMinutes + (int) (activeAhead * 2) + globalDelay);

            Instant createdAt = order.getCreatedAt() != null ? order.getCreatedAt() : now;
            long elapsedMinutes = Math.max(0, Duration.between(createdAt, now).toMinutes());

            int estimatedMinutes = (int) Math.max(1, totalEstimatedMinutes - elapsedMinutes);

            int progressPercentage;
            String message;
            if (elapsedMinutes >= totalEstimatedMinutes) {
                progressPercentage = 92;
                message = "Dando los últimos toques a tu pedido";
            } else {
                double ratio = (double) elapsedMinutes / (double) totalEstimatedMinutes;
                progressPercentage = (int) Math.round(15.0 + (ratio * 77.0));
                progressPercentage = Math.min(92, Math.max(15, progressPercentage));
                message = "Preparando tu pedido";
            }

            String kitchenStatus = "NORMAL";
            if (globalDelay > 0) {
                kitchenStatus = "DELAYED";
            } else if (activeAhead >= 5) {
                kitchenStatus = "HIGH_DEMAND";
            }

            OrderLiveTrackingResponse.KdsQueueSaturationDto saturationDto =
                    new OrderLiveTrackingResponse.KdsQueueSaturationDto(
                            (int) activeAhead,
                            kitchenStatus,
                            globalDelay
                    );

            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    true,
                    estimatedMinutes,
                    progressPercentage,
                    false,
                    saturationDto,
                    message,
                    now,
                    null,
                    totalEstimatedMinutes,
                    (int) elapsedMinutes,
                    estimatedMinutes,
                    progressPercentage,
                    0,
                    null,
                    createdAt,
                    null,
                    items
            );
        }

        // SCHEDULED fulfillment mode fallback (D-04, AS-6, INV-006, Q1/A1, CB-005, CB-006)
        ZoneId zoneId = currentZoneId();
        if (establishmentRepository != null && order.getEstablishmentId() != null) {
            zoneId = establishmentRepository.findById(order.getEstablishmentId())
                    .map(Establishment::getTimezone)
                    .filter(tz -> tz != null && !tz.isBlank())
                    .map(tz -> {
                        try {
                            return ZoneId.of(tz);
                        } catch (Exception ignored) {
                            return null;
                        }
                    })
                    .orElse(zoneId);
        }

        LocalDate pickupDate = order.getPickupDate() != null ? order.getPickupDate() : LocalDate.now(zoneId);
        LocalTime slotStart = order.getTimeSlotStart() != null ? order.getTimeSlotStart() : LocalTime.of(12, 0);
        ZonedDateTime slotStartZoned = ZonedDateTime.of(pickupDate, slotStart, zoneId);
        ZonedDateTime nowZoned = now.atZone(zoneId);

        long minutesUntilSlot = Duration.between(nowZoned, slotStartZoned).toMinutes();

        if (minutesUntilSlot > 15) {
            // Static phase (> 15 min): suppress countdown
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    false,
                    null,
                    10,
                    false,
                    null,
                    "Tu turno de retiro está programado para hoy a las " + slotStart + " hs.",
                    now,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    items
            );
        } else if (minutesUntilSlot > 0) {
            // Proximity countdown phase (1..15 min): active countdown towards slot start
            int minutesRemaining = (int) minutesUntilSlot;
            int progressPercentage = Math.min(92, Math.max(15, (15 - minutesRemaining) * 5 + 15));

            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    true,
                    minutesRemaining,
                    progressPercentage,
                    false,
                    null,
                    "¡Tu turno se acerca! Preparación en curso",
                    now,
                    null,
                    null,
                    null,
                    minutesRemaining,
                    progressPercentage,
                    0,
                    null,
                    null,
                    null,
                    items
            );
        } else {
            // In-slot phase (<= 0 min): slot started, final touches before counter dispatch
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    true,
                    0,
                    92,
                    false,
                    null,
                    "¡Tu turno ha comenzado! La cocina está dando los últimos toques a tu pedido.",
                    now,
                    null,
                    20,
                    0,
                    1,
                    92,
                    0,
                    null,
                    now,
                    null,
                    items
            );
        }
    }

    @Transactional
    public KdsDelayAdjustmentResponse updateGlobalDelay(KdsDelayAdjustmentRequest request, UUID userId) {
        UUID establishmentId = request.establishmentId();
        EstablishmentKdsSettings settings = kdsSettingsRepository.findById(establishmentId)
                .orElseGet(() -> new EstablishmentKdsSettings(establishmentId, 0, userId));

        settings.setGlobalDelayMinutes(request.globalDelayMinutes());
        settings.setUpdatedByUserId(userId);
        settings.setUpdatedAt(currentInstant());
        kdsSettingsRepository.save(settings);

        return new KdsDelayAdjustmentResponse(
                establishmentId,
                settings.getGlobalDelayMinutes(),
                settings.getUpdatedAt()
        );
    }

    @Transactional
    public KdsTicketResponse markTicketReadyForPickup(UUID ticketOrOrderId, UUID userId) {
        if (kdsTicketRepository != null) {
            Optional<KdsTicket> ticketOpt = kdsTicketRepository.findByIdForUpdate(ticketOrOrderId)
                    .or(() -> kdsTicketRepository.findById(ticketOrOrderId));
            if (ticketOpt.isPresent()) {
                KdsTicket ticket = ticketOpt.get();
                UUID ticketEstablishmentId = (ticket.getEstablishment() != null)
                        ? ticket.getEstablishment().getId()
                        : null;
                validateUserTenantAccess(userId, ticketEstablishmentId);

                if (ticket.getStatus() == KdsTicketStatus.COMPLETED) {
                    throw new InvalidOrderStateException(
                            "La orden ya ha sido completada y entregada al cliente.",
                            "ORDER_ALREADY_COMPLETED"
                    );
                }

                Order order = orderRepository.findByIdForUpdate(ticket.getOrderId())
                        .or(() -> orderRepository.findById(ticket.getOrderId()))
                        .orElseThrow(() -> new OrderNotFoundException(ticket.getOrderId()));

                if (order.getStatus() == OrderStatus.COMPLETED) {
                    throw new InvalidOrderStateException(
                            "La orden ya ha sido completada y entregada al cliente.",
                            "ORDER_ALREADY_COMPLETED"
                    );
                }

                // Idempotency: if already READY_FOR_PICKUP, return original readyAt without mutating
                if (ticket.getStatus() == KdsTicketStatus.READY_FOR_PICKUP) {
                    Instant existingReadyAt = ticket.getReadyAt() != null
                            ? ticket.getReadyAt()
                            : (order.getReadyAt() != null ? order.getReadyAt() : currentInstant());
                    return new KdsTicketResponse(
                            ticket.getId(),
                            order.getId(),
                            ticket.getTicketNumber(),
                            OrderStatus.READY_FOR_PICKUP,
                            existingReadyAt
                    );
                }

                Instant now = currentInstant();
                ticket.setStatus(KdsTicketStatus.READY_FOR_PICKUP);
                ticket.setReadyAt(now);
                kdsTicketRepository.save(ticket);

                order.setStatus(OrderStatus.READY_FOR_PICKUP);
                order.setReadyAt(now);
                order.setUpdatedAt(now);
                orderRepository.save(order);

                return new KdsTicketResponse(
                        ticket.getId(),
                        order.getId(),
                        ticket.getTicketNumber(),
                        OrderStatus.READY_FOR_PICKUP,
                        now
                );
            }
        }

        Order order = orderRepository.findByIdForUpdate(ticketOrOrderId)
                .or(() -> orderRepository.findById(ticketOrOrderId))
                .orElseThrow(() -> new OrderNotFoundException(ticketOrOrderId));

        validateUserTenantAccess(userId, order.getEstablishmentId());

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException(
                    "La orden ya ha sido completada y entregada al cliente.",
                    "ORDER_ALREADY_COMPLETED"
            );
        }

        UUID resolvedTicketId = ticketOrOrderId;
        Integer resolvedTicketNumber = null;
        KdsTicket linkedTicket = null;
        if (kdsTicketRepository != null) {
            linkedTicket = kdsTicketRepository.findByOrderId(order.getId()).orElse(null);
            if (linkedTicket != null) {
                if (linkedTicket.getStatus() == KdsTicketStatus.COMPLETED) {
                    throw new InvalidOrderStateException(
                            "La orden ya ha sido completada y entregada al cliente.",
                            "ORDER_ALREADY_COMPLETED"
                    );
                }
                resolvedTicketId = linkedTicket.getId();
                resolvedTicketNumber = linkedTicket.getTicketNumber();
            }
        }

        if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            Instant existingReadyAt = order.getReadyAt() != null
                    ? order.getReadyAt()
                    : (linkedTicket != null && linkedTicket.getReadyAt() != null
                            ? linkedTicket.getReadyAt()
                            : currentInstant());
            return new KdsTicketResponse(
                    resolvedTicketId,
                    order.getId(),
                    resolvedTicketNumber,
                    OrderStatus.READY_FOR_PICKUP,
                    existingReadyAt
            );
        }

        Instant now = currentInstant();
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setReadyAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        if (linkedTicket != null) {
            linkedTicket.setStatus(KdsTicketStatus.READY_FOR_PICKUP);
            linkedTicket.setReadyAt(now);
            kdsTicketRepository.save(linkedTicket);
        }

        return new KdsTicketResponse(
                resolvedTicketId,
                order.getId(),
                resolvedTicketNumber,
                OrderStatus.READY_FOR_PICKUP,
                now
        );
    }

    private void validateUserTenantAccess(UUID userId, UUID resourceEstablishmentId) {
        if (userRepository == null || userId == null || resourceEstablishmentId == null) {
            return;
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (user.getRole() == UserRole.ADMINISTRADOR) {
            return;
        }
        if (user.getTenantId() != null && !user.getTenantId().equals(resourceEstablishmentId)) {
            throw new TenantViolationException("La comanda no pertenece al establecimiento del usuario autenticado.");
        }
    }

    private String resolveInactiveStatusMessage(OrderStatus status) {
        if (status == null) {
            return "Estado de seguimiento en actualización.";
        }
        return switch (status) {
            case PENDING_ACCREDITATION ->
                    "Tu pago está pendiente de acreditación. El seguimiento en cocina comenzará cuando se confirme.";
            case PENDING_PAYMENT ->
                    "Tu pedido aún aguarda la confirmación de pago antes de ingresar a cocina.";
            case PAID, CONFIRMED ->
                    "Tu pago fue aprobado. Estamos asignando tu comanda a la cocina.";
            case CANCELLED ->
                    "Este pedido fue cancelado.";
            case EXPIRED ->
                    "Este intento de pedido expiró por tiempo antes de completarse el pago.";
            default -> "El pedido aún no se encuentra en preparación.";
        };
    }
}
