package com.easyeat.order.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.order.config.MercadoPagoProperties;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.domain.Payment;
import com.easyeat.order.domain.PaymentStatus;
import com.easyeat.order.domain.ReleaseReason;
import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.DinerOrderSummaryResponse;
import com.easyeat.order.dto.DinerOrderSummaryResponse.DinerOrderItemResponse;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.dto.OrderDetailResponse;
import com.easyeat.order.dto.OrderItemDetail;
import com.easyeat.order.dto.OrderSummaryResponse;
import com.easyeat.order.exception.ActiveReservationExistsException;
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.LeadTimeViolationException;
import com.easyeat.order.exception.OrderNotFoundException;
import com.easyeat.order.exception.SlotCapacityExceededException;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final Clock clock;
    private final OperatingScheduleRepository operatingScheduleRepository;
    private final PaymentRepository paymentRepository;
    private final KdsTicketRepository kdsTicketRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final MercadoPagoProperties mercadoPagoProperties;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        EstablishmentRepository establishmentRepository,
                        DishRepository dishRepository,
                        @Autowired(required = false) Clock clock,
                        @Autowired(required = false) OperatingScheduleRepository operatingScheduleRepository,
                        @Autowired(required = false) PaymentRepository paymentRepository,
                        @Autowired(required = false) KdsTicketRepository kdsTicketRepository,
                        @Autowired(required = false) TemporaryReservationRepository temporaryReservationRepository,
                        @Autowired(required = false) MercadoPagoProperties mercadoPagoProperties) {
        this.orderRepository = orderRepository;
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
        this.operatingScheduleRepository = operatingScheduleRepository;
        this.paymentRepository = paymentRepository;
        this.kdsTicketRepository = kdsTicketRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    public OrderService(OrderRepository orderRepository,
                        EstablishmentRepository establishmentRepository,
                        DishRepository dishRepository,
                        Clock clock,
                        OperatingScheduleRepository operatingScheduleRepository) {
        this(orderRepository, establishmentRepository, dishRepository, clock, operatingScheduleRepository,
                null, null, null, null);
    }

    public OrderService(OrderRepository orderRepository,
                        EstablishmentRepository establishmentRepository,
                        DishRepository dishRepository) {
        this(orderRepository, establishmentRepository, dishRepository, Clock.systemDefaultZone(), null);
    }

    public OrderService(OrderRepository orderRepository,
                        EstablishmentRepository establishmentRepository,
                        DishRepository dishRepository,
                        Clock clock) {
        this(orderRepository, establishmentRepository, dishRepository, clock, null);
    }

    @Transactional
    public OrderCreatedResponse createOrder(UUID userId, CreateOrderRequest request) {
        if (request.getFulfillmentMode() == null) {
            throw new InvalidFulfillmentModeException("La modalidad de retiro es obligatoria ('IMMEDIATE' o 'SCHEDULED')");
        }

        if (request.getFulfillmentMode() == FulfillmentMode.SCHEDULED) {
            if (request.getPickupDate() == null || request.getTimeSlotStart() == null) {
                throw new InvalidFulfillmentModeException("Para la modalidad programada ('SCHEDULED'), la fecha y franja horaria son obligatorias");
            }
        }

        Instant nowInstant = clock.instant();

        // Expire any stale pending orders before checking active reservation (Anti-acaparamiento: Max 1 pending order per diner - FR-027, US3)
        expirePendingOrdersForUser(userId, nowInstant);

        List<Order> activeOrders = orderRepository.findActivePendingOrders(userId, nowInstant);
        if (activeOrders != null && !activeOrders.isEmpty()) {
            throw new ActiveReservationExistsException(
                    "Ya poseés una orden pendiente de confirmación. Por favor, completá o cancelá tu reserva actual para continuar.",
                    activeOrders.get(0).getId()
            );
        }
        if (orderRepository.hasActivePendingOrder(userId, nowInstant)) {
            throw new ActiveReservationExistsException("Ya poseés una orden pendiente de confirmación. Por favor, completá o cancelá tu reserva actual para continuar.");
        }

        // Lock establishment for atomic serialization (FCFS)
        Establishment establishment = establishmentRepository.findByIdForUpdate(request.getEstablishmentId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(establishment.getTimezone() != null ? establishment.getTimezone() : "America/Argentina/Buenos_Aires");
        } catch (Exception e) {
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        }

        LocalDate today = LocalDate.ofInstant(nowInstant, zoneId);
        LocalTime nowTime = LocalTime.ofInstant(nowInstant, zoneId);
        int maxCapacity = establishment.getMaxCapacityPerSlot() != null ? establishment.getMaxCapacityPerSlot() : 10;
        int leadTime = establishment.getLeadTimeMinutes() != null ? establishment.getLeadTimeMinutes() : 30;

        // Calculate slot details and clear/omit slot fields when IMMEDIATE
        LocalDate pickupDate;
        LocalTime timeSlotStart;
        LocalTime timeSlotEnd;

        if (request.getFulfillmentMode() == FulfillmentMode.IMMEDIATE) {
            pickupDate = today;
            timeSlotStart = nowTime.truncatedTo(ChronoUnit.MINUTES);
            timeSlotEnd = timeSlotStart.plusMinutes(leadTime);

            boolean isOpenNow;
            if (operatingScheduleRepository != null) {
                Optional<EstablishmentOperatingSchedule> todaySchedOpt =
                        operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), today.getDayOfWeek());
                if (todaySchedOpt.isPresent()) {
                    EstablishmentOperatingSchedule s = todaySchedOpt.get();
                    isOpenNow = !s.isClosed() && !nowTime.isBefore(s.getOpeningTime()) && nowTime.isBefore(s.getClosingTime());
                } else {
                    isOpenNow = establishment.isOpen(nowTime);
                }
            } else {
                isOpenNow = establishment.isOpen(nowTime);
            }

            if (!isOpenNow) {
                throw new InvalidOrderStateException("El establecimiento se encuentra cerrado para pedidos inmediatos en este momento.");
            }

            LocalTime ongoingSlot = establishment.calculateCurrentOngoingSlot(nowTime);
            if (ongoingSlot != null) {
                long ongoingBooked = orderRepository.countActiveOrdersBySlot(establishment.getId(), today, ongoingSlot, nowInstant);
                if (ongoingBooked >= maxCapacity) {
                    throw new SlotCapacityExceededException("Cocina al límite de capacidad actual — Seleccioná una reserva programada.");
                }
            }
        } else {
            pickupDate = request.getPickupDate();
            timeSlotStart = request.getTimeSlotStart();
            timeSlotEnd = request.getTimeSlotEnd() != null
                    ? request.getTimeSlotEnd()
                    : timeSlotStart.plusMinutes(15);

            // Validation of operating day and hours with establishment_operating_schedules (FR-007, FR-008, FR-036, FR-038)
            boolean isOperatingDay = establishment.isOpenOn(pickupDate);
            LocalTime openTime = establishment.getOpeningTime();
            LocalTime closeTime = establishment.getClosingTime();

            if (operatingScheduleRepository != null) {
                Optional<EstablishmentOperatingSchedule> schedOpt =
                        operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), pickupDate.getDayOfWeek());
                if (schedOpt.isPresent()) {
                    EstablishmentOperatingSchedule sched = schedOpt.get();
                    if (sched.isClosed()) {
                        isOperatingDay = false;
                    } else {
                        isOperatingDay = true;
                        openTime = sched.getOpeningTime();
                        closeTime = sched.getClosingTime();
                    }
                }
            }

            if (!isOperatingDay) {
                throw new InvalidOrderStateException("El establecimiento no se encuentra operativo en la fecha seleccionada.");
            }
            if (openTime != null && timeSlotStart.isBefore(openTime)) {
                throw new InvalidOrderStateException("La franja horaria seleccionada está fuera del horario de atención del establecimiento.");
            }
            if (closeTime != null && timeSlotEnd.isAfter(closeTime)) {
                throw new InvalidOrderStateException("La franja horaria seleccionada excede el horario de cierre del establecimiento.");
            }

            // Lead-time validation for today's reservations (FR-009, FR-036)
            if (pickupDate.isEqual(today)) {
                LocalTime minAllowed = nowTime.plusMinutes(leadTime);
                if (timeSlotStart.isBefore(minAllowed)) {
                    throw new LeadTimeViolationException("La franja seleccionada debe contar con al menos " + leadTime + " minutos de anticipación respecto de la hora actual.");
                }
            }

            // Zero overbooking capacity check (FR-016, FR-017, FR-036)
            long currentBooked = orderRepository.countActiveOrdersBySlot(establishment.getId(), pickupDate, timeSlotStart, nowInstant);
            if (currentBooked >= maxCapacity) {
                throw new SlotCapacityExceededException("La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.");
            }
        }

        // Validate dishes and chef pause status (FR-019 / Spec 013)
        List<UUID> dishIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getDishId)
                .toList();

        List<Dish> dishes = dishRepository.findByIdIn(dishIds);
        Map<UUID, Dish> dishMap = dishes.stream()
                .collect(Collectors.toMap(Dish::getId, d -> d));

        for (CreateOrderItemRequest itemReq : request.getItems()) {
            Dish dish = dishMap.get(itemReq.getDishId());
            if (dish == null) {
                throw new IllegalArgumentException("Plato no encontrado con ID: " + itemReq.getDishId());
            }

            boolean isPaused = (dish.getDescription() != null && dish.getDescription().contains("[PAUSED_BY_CHEF]"));
            if (isPaused) {
                throw new DishPausedByChefException("El plato '" + dish.getName() + "' ha sido pausado temporalmente por la cocina. Por favor, retiralo del carrito para continuar.");
            }
        }

        // Calculate total and order items
        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(userId);
        order.setFulfillmentMode(request.getFulfillmentMode());
        order.setPickupDate(pickupDate);
        order.setTimeSlotStart(timeSlotStart);
        order.setTimeSlotEnd(timeSlotEnd);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        order.setCreatedAt(nowInstant);
        order.setUpdatedAt(nowInstant);
        order.setExpiresAt(nowInstant.plus(15, ChronoUnit.MINUTES));

        for (CreateOrderItemRequest itemReq : request.getItems()) {
            Dish dish = dishMap.get(itemReq.getDishId());
            BigDecimal unitPrice = dish.getRetailPrice() != null ? dish.getRetailPrice() : dish.getPrice();
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem(
                    order,
                    dish.getId(),
                    dish.getName(),
                    unitPrice,
                    itemReq.getQuantity(),
                    subtotal
            );
            order.getItems().add(orderItem);
        }
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);

        long remainingSeconds = 900L;
        return new OrderCreatedResponse(
                saved.getId(),
                saved.getEstablishmentId(),
                saved.getFulfillmentMode(),
                saved.getPickupDate(),
                saved.getTimeSlotStart(),
                saved.getTimeSlotEnd(),
                saved.getStatus(),
                saved.getTotalAmount(),
                saved.getExpiresAt(),
                remainingSeconds,
                saved.getCreatedAt()
        );
    }

    @Transactional
    public List<DinerOrderSummaryResponse> getDinerOrders(UUID userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Instant nowInstant = clock.instant();
        Map<UUID, String> establishmentNames = new HashMap<>();

        return orders.stream()
                .map(order -> toDinerOrderSummary(order, nowInstant, establishmentNames))
                .toList();
    }

    private DinerOrderSummaryResponse toDinerOrderSummary(Order order,
                                                            Instant nowInstant,
                                                            Map<UUID, String> establishmentNames) {
        Order current = expireOrderIfNeeded(order, nowInstant);

        String establishmentName = establishmentNames.computeIfAbsent(
                current.getEstablishmentId(),
                id -> establishmentRepository.findById(id)
                        .map(Establishment::getName)
                        .orElse("Establecimiento")
        );

        Integer ticketNumber = kdsTicketRepository != null
                ? kdsTicketRepository.findByOrderId(current.getId())
                        .map(KdsTicket::getTicketNumber)
                        .orElse(null)
                : null;

        String redirectUrl = resolvePendingRedirectUrl(current);

        List<DinerOrderItemResponse> items = current.getItems().stream()
                .map(item -> new DinerOrderItemResponse(
                        item.getDishId(),
                        item.getDishName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        return new DinerOrderSummaryResponse(
                current.getId(),
                ticketNumber,
                current.getEstablishmentId(),
                establishmentName,
                current.getStatus(),
                current.getFulfillmentMode(),
                current.getPickupDate(),
                current.getTimeSlotStart(),
                current.getTimeSlotEnd(),
                current.getTotalAmount(),
                current.getExpiresAt(),
                current.getCreatedAt(),
                redirectUrl,
                "/pedidos/" + current.getId() + "/seguimiento",
                items
        );
    }

    private Order expireOrderIfNeeded(Order order, Instant nowInstant) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.isExpired(nowInstant)) {
            order.setStatus(OrderStatus.EXPIRED);
            order.setUpdatedAt(nowInstant);
            Order saved = orderRepository.save(order);
            if (temporaryReservationRepository != null) {
                temporaryReservationRepository.findByOrderId(saved.getId()).ifPresent(reservation -> {
                    if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                        reservation.expire();
                        temporaryReservationRepository.save(reservation);
                    }
                });
            }
            if (paymentRepository != null) {
                paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(saved.getId()).ifPresent(payment -> {
                    if (payment.getStatus() == PaymentStatus.IN_PROCESS) {
                        payment.expire();
                        paymentRepository.save(payment);
                    }
                });
            }
            return saved;
        }
        return order;
    }

    private String resolvePendingRedirectUrl(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || paymentRepository == null) {
            return null;
        }
        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(payment -> {
                    String initPoint = normalizeBlankToNull(payment.getInitPoint());
                    if (initPoint != null) {
                        return initPoint;
                    }
                    return normalizeBlankToNull(payment.getSandboxInitPoint());
                })
                .orElse(null);
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private void expirePendingOrdersForUser(UUID userId, Instant nowInstant) {
        List<Order> userOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (userOrders == null) {
            return;
        }
        for (Order candidate : userOrders) {
            expireOrderIfNeeded(candidate, nowInstant);
        }
    }

    private Order findOwnedOrderOrThrow(UUID orderId, UUID userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseGet(() -> orderRepository.findById(orderId)
                        .<Order>map(existing -> {
                            throw new AccessDeniedException("No tenés permiso para acceder a esta orden");
                        })
                        .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    @Transactional
    public OrderDetailResponse getOrderById(UUID orderId, UUID userId) {
        Order order = findOwnedOrderOrThrow(orderId, userId);

        Instant nowInstant = clock.instant();
        Long remainingSeconds = null;

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getExpiresAt() != null) {
            if (!order.getExpiresAt().isAfter(nowInstant)) {
                order = expireOrderIfNeeded(order, nowInstant);
                remainingSeconds = 0L;
            } else {
                remainingSeconds = Math.max(0L, Duration.between(nowInstant, order.getExpiresAt()).getSeconds());
            }
        }

        List<OrderItemDetail> itemDetails = order.getItems().stream()
                .map(item -> new OrderItemDetail(
                        item.getId(),
                        item.getDishId(),
                        item.getDishName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderDetailResponse(
                order.getId(),
                order.getEstablishmentId(),
                order.getFulfillmentMode(),
                order.getPickupDate(),
                order.getTimeSlotStart(),
                order.getTimeSlotEnd(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                remainingSeconds,
                nowInstant,
                itemDetails
        );
    }

    @Transactional
    public OrderSummaryResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = findOwnedOrderOrThrow(orderId, userId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException("La orden no se encuentra en estado cancelable (estado actual: " + order.getStatus() + ")");
        }

        Instant nowInstant = clock.instant();
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(nowInstant)) {
            expireOrderIfNeeded(order, nowInstant);
            throw new InvalidOrderStateException("La orden ya ha expirado");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(nowInstant);
        Order saved = orderRepository.save(order);

        if (temporaryReservationRepository != null) {
            temporaryReservationRepository.findByOrderId(saved.getId()).ifPresent(reservation -> {
                if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                    reservation.release(ReleaseReason.CANCELLED_BY_USER);
                    temporaryReservationRepository.save(reservation);
                }
            });
        }
        if (paymentRepository != null) {
            paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(saved.getId()).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatus.IN_PROCESS) {
                    payment.cancel();
                    paymentRepository.save(payment);
                }
            });
        }

        return new OrderSummaryResponse(saved.getId(), saved.getStatus(), saved.getUpdatedAt());
    }
}
