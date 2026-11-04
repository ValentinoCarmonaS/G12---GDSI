package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.repository.SlotCapacityRepository;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.dto.TimeSlotAvailability;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PickupSlotService {

    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;
    private final SlotCapacityRepository slotCapacityRepository;
    private final OperatingScheduleRepository operatingScheduleRepository;
    private final Clock clock;

    @Autowired
    public PickupSlotService(EstablishmentRepository establishmentRepository,
                             OrderRepository orderRepository,
                             @Autowired(required = false) SlotCapacityRepository slotCapacityRepository,
                             @Autowired(required = false) OperatingScheduleRepository operatingScheduleRepository,
                             @Autowired(required = false) Clock clock) {
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
        this.slotCapacityRepository = slotCapacityRepository;
        this.operatingScheduleRepository = operatingScheduleRepository;
        this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
    }

    public PickupSlotService(EstablishmentRepository establishmentRepository,
                             OrderRepository orderRepository,
                             Clock clock) {
        this(establishmentRepository, orderRepository, null, null, clock);
    }

    @Transactional(readOnly = true)
    public PickupSlotCatalogResponse getPickupSlots(UUID establishmentId, LocalDate requestedDate) {
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(establishment.getTimezone() != null ? establishment.getTimezone() : "America/Argentina/Buenos_Aires");
        } catch (Exception e) {
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        }

        Instant nowInstant = clock.instant();
        LocalDate today = LocalDate.ofInstant(nowInstant, zoneId);
        LocalTime nowTime = LocalTime.ofInstant(nowInstant, zoneId);

        LocalDate date = (requestedDate != null) ? requestedDate : today;

        if (date.isBefore(today)) {
            throw new InvalidOrderStateException("La fecha consultada no puede ser pasada");
        }

        if (date.isAfter(today.plusDays(7))) {
            throw new InvalidOrderStateException("La fecha consultada excede la ventana operativa de 7 días");
        }

        boolean isOperatingDay = establishment.isOpenOn(date);
        LocalTime openingTime = establishment.getOpeningTime();
        LocalTime closingTime = establishment.getClosingTime();

        // Optional (FR-032): Consult OperatingScheduleRepository for schedule if configured
        if (operatingScheduleRepository != null) {
            Optional<EstablishmentOperatingSchedule> scheduleOpt = operatingScheduleRepository
                    .findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek());
            if (scheduleOpt.isPresent()) {
                EstablishmentOperatingSchedule schedule = scheduleOpt.get();
                isOperatingDay = !schedule.isClosed();
                openingTime = schedule.getOpeningTime();
                closingTime = schedule.getClosingTime();
            }
        }

        boolean isEstablishmentOpenNow;
        if (operatingScheduleRepository != null) {
            Optional<EstablishmentOperatingSchedule> todayScheduleOpt = operatingScheduleRepository
                    .findByEstablishmentIdAndDayOfWeek(establishmentId, today.getDayOfWeek());
            if (todayScheduleOpt.isPresent()) {
                EstablishmentOperatingSchedule todaySchedule = todayScheduleOpt.get();
                if (todaySchedule.isClosed() || todaySchedule.getOpeningTime() == null || todaySchedule.getClosingTime() == null) {
                    isEstablishmentOpenNow = false;
                } else {
                    isEstablishmentOpenNow = !nowTime.isBefore(todaySchedule.getOpeningTime())
                            && !nowTime.isAfter(todaySchedule.getClosingTime());
                }
            } else {
                isEstablishmentOpenNow = establishment.isOpen(nowTime);
            }
        } else {
            isEstablishmentOpenNow = establishment.isOpen(nowTime);
        }

        int leadTimeMinutes = establishment.getLeadTimeMinutes() != null ? establishment.getLeadTimeMinutes() : 30;
        int defaultMaxCapacity = establishment.getMaxCapacityPerSlot() != null ? establishment.getMaxCapacityPerSlot() : 10;

        // Fetch day's configured slot capacities to avoid N+1 queries
        Map<LocalTime, EstablishmentSlotCapacity> capacityByStartTime = Collections.emptyMap();
        if (slotCapacityRepository != null) {
            List<EstablishmentSlotCapacity> capacities = slotCapacityRepository
                    .findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(establishmentId, date, date);
            if (capacities != null && !capacities.isEmpty()) {
                capacityByStartTime = capacities.stream()
                        .collect(Collectors.toMap(EstablishmentSlotCapacity::getStartTime, c -> c));
            }
        }

        List<TimeSlotAvailability> slots = new ArrayList<>();

        if (isOperatingDay && openingTime != null && closingTime != null) {
            LocalTime currentSlotStart = openingTime;

            // Lead-time cutoff threshold for today
            LocalTime leadTimeCutoff = nowTime.plusMinutes(leadTimeMinutes);

            while (!currentSlotStart.plusMinutes(15).isAfter(closingTime)) {
                LocalTime currentSlotEnd = currentSlotStart.plusMinutes(15);

                long bookedCount = 0;
                if (orderRepository != null) {
                    bookedCount = orderRepository.countActiveOrdersBySlot(establishmentId, date, currentSlotStart, nowInstant);
                }

                int slotMaxCapacity = defaultMaxCapacity;
                boolean hasConfiguredCapacity = false;
                EstablishmentSlotCapacity configuredCapacity = capacityByStartTime.get(currentSlotStart);
                if (configuredCapacity != null) {
                    slotMaxCapacity = configuredCapacity.getMaxCapacity();
                    hasConfiguredCapacity = true;
                }

                double occupancyPercentage;
                boolean isSelectable;
                String status;

                if (hasConfiguredCapacity && slotMaxCapacity == 0) {
                    // FR-032 & Spec 010: max_capacity = 0 forces status = "SOLD_OUT", occupancy = 100, isSelectable = false
                    slotMaxCapacity = 0;
                    occupancyPercentage = 100.0;
                    isSelectable = false;
                    status = "SOLD_OUT";
                } else {
                    occupancyPercentage = (slotMaxCapacity > 0) ? ((double) bookedCount / slotMaxCapacity) * 100.0 : 0.0;
                    isSelectable = true;
                    status = "AVAILABLE";

                    // Lead-time buffer or past verification
                    if (date.isEqual(today)) {
                        if (currentSlotStart.isBefore(leadTimeCutoff)) {
                            isSelectable = false;
                            status = "EXPIRED_OR_LEAD_TIME";
                        }
                    }

                    if (isSelectable) {
                        // Saturation tiers calculation (<80% AVAILABLE, 80-99% HIGH_DEMAND, >=100% SOLD_OUT)
                        if (bookedCount >= slotMaxCapacity || occupancyPercentage >= 100.0) {
                            isSelectable = false;
                            status = "SOLD_OUT";
                        } else if (occupancyPercentage >= 80.0) {
                            status = "HIGH_DEMAND";
                        }
                    }
                }

                slots.add(new TimeSlotAvailability(
                        currentSlotStart,
                        currentSlotEnd,
                        date,
                        slotMaxCapacity,
                        bookedCount,
                        occupancyPercentage,
                        status,
                        isSelectable
                ));

                currentSlotStart = currentSlotEnd;
            }
        }

        return new PickupSlotCatalogResponse(
                establishmentId,
                date,
                nowInstant,
                zoneId.getId(),
                isEstablishmentOpenNow,
                isOperatingDay,
                leadTimeMinutes,
                slots
        );
    }
}
