package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
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

        Integer ticketNumber = resolveTicketNumber(order.getId());
        Instant now = currentInstant();

        // Terminal state: READY_FOR_PICKUP or COMPLETED (D-05, AS-8, INV-008, CB-008)
        if (order.getStatus() == OrderStatus.READY_FOR_PICKUP || order.getStatus() == OrderStatus.COMPLETED) {
            return new OrderLiveTrackingResponse(
                    order.getId(),
                    ticketNumber,
                    order.getStatus(),
                    order.getFulfillmentMode(),
                    false,
                    null,
                    100,
                    true,
                    null,
                    "¡Tu pedido está listo! Por favor, acercate al mostrador para retirar tu comida.",
                    now
            );
        }

        // Orders not yet active in kitchen or already terminal/cancelled/expired never show active countdown
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
                    now
            );
        }

        // IMMEDIATE fulfillment mode (D-03, AS-5, INV-005, AS-7, INV-007)
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
                    now
            );
        }

        // SCHEDULED fulfillment mode (D-04, AS-6, INV-006, Q1/A1, CB-005, CB-006)
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
                    now
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
                    now
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
                    now
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
        Instant now = currentInstant();
        if (kdsTicketRepository != null) {
            Optional<KdsTicket> ticketOpt = kdsTicketRepository.findById(ticketOrOrderId);
            if (ticketOpt.isPresent()) {
                KdsTicket ticket = ticketOpt.get();
                Order order = orderRepository.findById(ticket.getOrderId())
                        .orElseThrow(() -> new OrderNotFoundException(ticket.getOrderId()));

                ticket.setStatus(KdsTicketStatus.READY_FOR_PICKUP);
                ticket.setReadyAt(now);
                kdsTicketRepository.save(ticket);

                order.setStatus(OrderStatus.READY_FOR_PICKUP);
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

        Order order = orderRepository.findById(ticketOrOrderId)
                .orElseThrow(() -> new OrderNotFoundException(ticketOrOrderId));

        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        UUID resolvedTicketId = ticketOrOrderId;
        Integer resolvedTicketNumber = null;
        if (kdsTicketRepository != null) {
            Optional<KdsTicket> linkedTicketOpt = kdsTicketRepository.findByOrderId(order.getId());
            if (linkedTicketOpt.isPresent()) {
                KdsTicket ticket = linkedTicketOpt.get();
                ticket.setStatus(KdsTicketStatus.READY_FOR_PICKUP);
                ticket.setReadyAt(now);
                kdsTicketRepository.save(ticket);
                resolvedTicketId = ticket.getId();
                resolvedTicketNumber = ticket.getTicketNumber();
            }
        }

        return new KdsTicketResponse(
                resolvedTicketId,
                order.getId(),
                resolvedTicketNumber,
                OrderStatus.READY_FOR_PICKUP,
                now
        );
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
