package com.easyeat.order.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.CreateOrderItemRequest;
import com.easyeat.order.dto.CreateOrderRequest;
import com.easyeat.order.dto.OrderCreatedResponse;
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
import com.easyeat.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;

    public OrderService(OrderRepository orderRepository,
                        EstablishmentRepository establishmentRepository,
                        DishRepository dishRepository) {
        this.orderRepository = orderRepository;
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
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

        // Check active pending reservation (Anti-acaparamiento: Max 1 pending order per diner)
        if (orderRepository.hasActivePendingOrder(userId, Instant.now())) {
            throw new IllegalStateException("El usuario ya posee una orden pendiente de confirmación");
        }

        // Lock establishment for atomic serialization (FCFS)
        Establishment establishment = establishmentRepository.findByIdForUpdate(request.getEstablishmentId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

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

            // FR-019: Check if paused by chef (interoperable description check or future-proof flag)
            boolean isPaused = (dish.getDescription() != null && dish.getDescription().contains("[PAUSED_BY_CHEF]"));
            if (isPaused) {
                throw new DishPausedByChefException("El plato '" + dish.getName() + "' ha sido pausado temporalmente por la cocina. Por favor, retiralo del carrito para continuar.");
            }
        }

        // Calculate slot details and clear/omit slot fields when IMMEDIATE
        LocalDate pickupDate;
        LocalTime timeSlotStart;
        LocalTime timeSlotEnd;

        if (request.getFulfillmentMode() == FulfillmentMode.IMMEDIATE) {
            pickupDate = LocalDate.now();
            LocalTime nowTime = LocalTime.now();
            int leadTime = establishment.getLeadTimeMinutes() != null ? establishment.getLeadTimeMinutes() : 30;
            timeSlotStart = nowTime.truncatedTo(ChronoUnit.MINUTES);
            timeSlotEnd = timeSlotStart.plusMinutes(leadTime);
        } else {
            pickupDate = request.getPickupDate();
            timeSlotStart = request.getTimeSlotStart();
            timeSlotEnd = request.getTimeSlotEnd() != null
                    ? request.getTimeSlotEnd()
                    : timeSlotStart.plusMinutes(15);
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

        Instant now = Instant.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpiresAt(now.plus(15, ChronoUnit.MINUTES));

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
}

