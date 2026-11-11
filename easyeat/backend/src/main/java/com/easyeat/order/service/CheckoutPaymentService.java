package com.easyeat.order.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.catalog.service.DishAvailabilityEngine;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.*;
import com.easyeat.order.dto.*;
import com.easyeat.order.exception.*;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.repository.PaymentRepository;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CheckoutPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutPaymentService.class);
    private static final int RESERVATION_TTL_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;
    private final PaymentRepository paymentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishAvailabilityEngine dishAvailabilityEngine;
    private final PaymentGatewayResolver paymentGatewayResolver;
    private final Clock clock;

    @Autowired
    public CheckoutPaymentService(
            OrderRepository orderRepository,
            TemporaryReservationRepository temporaryReservationRepository,
            PaymentRepository paymentRepository,
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishAvailabilityEngine dishAvailabilityEngine,
            PaymentGatewayResolver paymentGatewayResolver,
            @Autowired(required = false) Clock clock) {
        this.orderRepository = orderRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
        this.paymentRepository = paymentRepository;
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishAvailabilityEngine = dishAvailabilityEngine;
        this.paymentGatewayResolver = paymentGatewayResolver;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    /**
     * Orquestación transaccional atómica para inicio de pago y reserva de franja y stock (INV-01, INV-04, INV-05).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentInitiationResponse initiatePayment(UUID userId, PaymentInitiationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El request de inicio de pago es obligatorio");
        }

        Instant nowInstant = clock.instant();

        // 1. Verificar si ya existe una orden pendiente con reserva activa para este comensal (Anti-acaparamiento INV-05)
        if (orderRepository.hasActivePendingOrder(userId, nowInstant)) {
            throw new ActiveReservationExistsException("Ya tenés un pedido o pago en curso pendiente.");
        }

        // 2. Bloqueo pesimista del establecimiento para serializar el conteo de franja y stock de forma atómica (INV-01, INV-07)
        Establishment establishment = establishmentRepository.findByIdForUpdate(request.establishmentId())
                .orElseThrow(() -> new EstablishmentNotFoundException(request.establishmentId()));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(request.establishmentId(), "El establecimiento no se encuentra activo");
        }

        ZoneId tz = ZoneId.of(establishment.getTimezone() != null ? establishment.getTimezone() : "America/Argentina/Buenos_Aires");
        ZonedDateTime nowZoned = nowInstant.atZone(tz);
        LocalDate today = nowZoned.toLocalDate();
        LocalTime nowTime = nowZoned.toLocalTime();

        int leadTime = establishment.getLeadTimeMinutes();
        int maxCapacity = establishment.getMaxCapacityPerSlot();

        LocalDate pickupDate;
        LocalTime timeSlotStart;
        LocalTime timeSlotEnd;

        if (request.fulfillmentMode() == FulfillmentMode.IMMEDIATE) {
            pickupDate = today;
            timeSlotStart = nowTime.truncatedTo(ChronoUnit.MINUTES);
            timeSlotEnd = timeSlotStart.plusMinutes(leadTime);

            LocalTime ongoingSlot = establishment.calculateCurrentOngoingSlot(nowTime);
            if (ongoingSlot != null) {
                long ongoingBooked = orderRepository.countActiveOrdersBySlot(establishment.getId(), today, ongoingSlot, nowInstant);
                if (ongoingBooked >= maxCapacity) {
                    throw new SlotCapacityExceededException("Cocina al límite de capacidad actual — Seleccioná una reserva programada.");
                }
            }
        } else {
            pickupDate = request.pickupDate();
            timeSlotStart = request.timeSlotStart();
            timeSlotEnd = request.timeSlotEnd() != null ? request.timeSlotEnd() : timeSlotStart.plusMinutes(15);

            if (pickupDate.isEqual(today)) {
                LocalTime minAllowed = nowTime.plusMinutes(leadTime);
                if (timeSlotStart.isBefore(minAllowed)) {
                    throw new LeadTimeViolationException("La franja seleccionada debe contar con al menos " + leadTime + " minutos de anticipación.");
                }
            }

            long currentBooked = orderRepository.countActiveOrdersBySlot(establishment.getId(), pickupDate, timeSlotStart, nowInstant);
            if (currentBooked >= maxCapacity) {
                throw new SlotCapacityExceededException("La franja horaria seleccionada acaba de agotarse. Por favor, seleccioná una nueva franja para continuar.");
            }
        }

        // 3. Validar disponibilidad de stock de los platos tras deducir reservas temporales activas (T026, INV-01)
        List<UUID> dishIds = request.items().stream()
                .map(PaymentInitiationRequest.OrderItemRequest::dishId)
                .toList();

        List<Dish> dishes = dishRepository.findByIdIn(dishIds);
        Map<UUID, Dish> dishMap = new HashMap<>();
        for (Dish d : dishes) {
            dishMap.put(d.getId(), d);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<PaymentGatewayPort.GatewayItem> gatewayItems = new ArrayList<>();

        for (PaymentInitiationRequest.OrderItemRequest itemReq : request.items()) {
            Dish dish = dishMap.get(itemReq.dishId());
            if (dish == null) {
                throw new DishNotFoundException(itemReq.dishId());
            }

            if (Boolean.TRUE.equals(dish.getPausedByChef())) {
                throw new DishPausedByChefException("El plato '" + dish.getName() + "' fue pausado temporalmente por la cocina.");
            }

            int availableStock = dishAvailabilityEngine.calculateAvailableStock(dish.getId(), establishment.getId());
            if (availableStock < itemReq.quantity()) {
                throw new com.easyeat.inventory.exception.InsufficientIngredientStockException(
                        null,
                        dish.getName(),
                        BigDecimal.valueOf(itemReq.quantity()),
                        BigDecimal.valueOf(availableStock),
                        "porciones"
                );
            }

            BigDecimal unitPrice = dish.getRetailPrice() != null ? dish.getRetailPrice() : dish.getPrice();
            if (unitPrice == null) {
                unitPrice = BigDecimal.ZERO;
            }
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setDishId(dish.getId());
            orderItem.setDishName(dish.getName());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(itemReq.quantity());
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);

            gatewayItems.add(new PaymentGatewayPort.GatewayItem(
                    dish.getName(),
                    itemReq.quantity(),
                    unitPrice,
                    "ARS"
            ));
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe total de la orden debe ser mayor a cero.");
        }

        // 4. Crear la orden en estado PENDING_PAYMENT con expiración de 15 min
        Instant expiresAt = nowInstant.plus(Duration.ofMinutes(RESERVATION_TTL_MINUTES));

        Order order = new Order();
        order.setEstablishmentId(establishment.getId());
        order.setUserId(userId);
        order.setFulfillmentMode(request.fulfillmentMode());
        order.setPickupDate(pickupDate);
        order.setTimeSlotStart(timeSlotStart);
        order.setTimeSlotEnd(timeSlotEnd);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(totalAmount);
        order.setExpiresAt(expiresAt);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
        }
        order.setItems(orderItems);
        orderRepository.save(order);

        // 5. Crear la reserva temporal conjunta (INV-04)
        String reservationIdempotency = UUID.randomUUID().toString();
        TemporaryReservation reservation = new TemporaryReservation(order, reservationIdempotency, expiresAt);
        temporaryReservationRepository.save(reservation);

        // 6. Invocar al puerto de pasarela universal (PaymentGatewayPort)
        PaymentMethod method = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.MERCADO_PAGO;
        PaymentGatewayPort gatewayPort;
        try {
            gatewayPort = paymentGatewayResolver.resolve(method);
        } catch (Exception e) {
            gatewayPort = paymentGatewayResolver.resolveDefault();
        }

        String paymentIdempotency = UUID.randomUUID().toString();
        PaymentGatewayPort.CreatePreferenceCommand command = new PaymentGatewayPort.CreatePreferenceCommand(
                order.getId().toString(),
                totalAmount,
                "ARS",
                null,
                gatewayItems,
                paymentIdempotency
        );

        PaymentGatewayPort.PreferenceResult prefResult = gatewayPort.createPreference(command);

        // 7. Persistir registro de Payment en estado IN_PROCESS
        Payment payment = new Payment(
                order,
                reservation,
                method,
                order.getId().toString(),
                totalAmount,
                paymentIdempotency
        );
        payment.assignGatewayPreference(prefResult.preferenceId(), prefResult.initPoint(), prefResult.sandboxInitPoint());
        paymentRepository.save(payment);

        String chosenRedirectUrl = prefResult.sandbox() ? prefResult.sandboxInitPoint() : prefResult.initPoint();
        if (chosenRedirectUrl == null || chosenRedirectUrl.isBlank()) {
            chosenRedirectUrl = prefResult.initPoint();
        }

        return new PaymentInitiationResponse(
                order.getId(),
                reservation.getId(),
                payment.getId(),
                prefResult.preferenceId(),
                chosenRedirectUrl,
                prefResult.sandbox(),
                expiresAt,
                totalAmount,
                "ARS"
        );
    }

    /**
     * Cancelar voluntariamente un intento de pago en curso (AS-4, FR-007, Q3).
     */
    @Transactional
    public CancelPaymentResponse cancelPaymentAttempt(UUID userId, CancelPaymentRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada: " + request.orderId()));

        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("No tenés autorización para cancelar esta orden");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("La orden no se encuentra en estado cancelable: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(clock.instant());
        orderRepository.save(order);

        Optional<TemporaryReservation> resOpt = temporaryReservationRepository.findActiveByOrderId(order.getId());
        ReservationStatus finalResStatus = ReservationStatus.RELEASED;
        if (resOpt.isPresent()) {
            TemporaryReservation res = resOpt.get();
            res.release(ReleaseReason.CANCELLED_BY_USER);
            temporaryReservationRepository.save(res);
            finalResStatus = res.getStatus();
        }

        Optional<Payment> paymentOpt = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId());
        PaymentStatus finalPayStatus = PaymentStatus.CANCELLED;
        if (paymentOpt.isPresent()) {
            Payment p = paymentOpt.get();
            p.cancel();
            paymentRepository.save(p);
            finalPayStatus = p.getStatus();
        }

        return new CancelPaymentResponse(
                order.getId(),
                finalResStatus,
                finalPayStatus,
                "Intento de pago cancelado exitosamente y recursos restituidos."
        );
    }
}
