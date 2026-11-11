package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.dto.TimeSlotAvailability;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PickupSlotService {

    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;
    private final OperatingScheduleRepository operatingScheduleRepository;

    @Autowired
    public PickupSlotService(EstablishmentRepository establishmentRepository,
                             OrderRepository orderRepository,
                             Clock clock,
                             @Autowired(required = false) OperatingScheduleRepository operatingScheduleRepository) {
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
        this.operatingScheduleRepository = operatingScheduleRepository;
    }

    public PickupSlotService(EstablishmentRepository establishmentRepository,
                             OrderRepository orderRepository,
                             Clock clock) {
        this(establishmentRepository, orderRepository, clock, null);
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

        // Cross-check operating schedule with establishment_operating_schedules (FR-008, FR-034, FR-038)
        boolean isOperatingDay = establishment.isOpenOn(date);
        LocalTime openingTime = establishment.getOpeningTime();
        LocalTime closingTime = establishment.getClosingTime();

        if (operatingScheduleRepository != null) {
            Optional<EstablishmentOperatingSchedule> scheduleOpt =
                    operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek());
            if (scheduleOpt.isPresent()) {
                EstablishmentOperatingSchedule schedule = scheduleOpt.get();
                if (schedule.isClosed()) {
                    isOperatingDay = false;
                } else {
                    isOperatingDay = true;
                    openingTime = schedule.getOpeningTime();
                    closingTime = schedule.getClosingTime();
                }
            }
        }

        boolean isEstablishmentOpenNow;
        if (operatingScheduleRepository != null) {
            Optional<EstablishmentOperatingSchedule> todayScheduleOpt =
                    operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(establishmentId, today.getDayOfWeek());
            if (todayScheduleOpt.isPresent()) {
                EstablishmentOperatingSchedule todaySchedule = todayScheduleOpt.get();
                isEstablishmentOpenNow = !todaySchedule.isClosed()
                        && !nowTime.isBefore(todaySchedule.getOpeningTime())
                        && nowTime.isBefore(todaySchedule.getClosingTime());
            } else {
                isEstablishmentOpenNow = establishment.isOpen(nowTime);
            }
        } else {
            isEstablishmentOpenNow = establishment.isOpen(nowTime);
        }

        int leadTimeMinutes = establishment.getLeadTimeMinutes() != null ? establishment.getLeadTimeMinutes() : 30;
        int maxCapacity = establishment.getMaxCapacityPerSlot() != null ? establishment.getMaxCapacityPerSlot() : 10;

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

                double occupancyPercentage = (maxCapacity > 0) ? ((double) bookedCount / maxCapacity) * 100.0 : 0.0;

                boolean isSelectable = true;
                String status = "AVAILABLE";

                // Lead-time buffer or past verification
                if (date.isEqual(today)) {
                    if (currentSlotStart.isBefore(leadTimeCutoff)) {
                        isSelectable = false;
                        status = "EXPIRED_OR_LEAD_TIME";
                    }
                }

                if (isSelectable) {
                    // Saturation tiers calculation (<80% AVAILABLE, 80-99% HIGH_DEMAND, >=100% SOLD_OUT)
                    if (bookedCount >= maxCapacity || occupancyPercentage >= 100.0) {
                        isSelectable = false;
                        status = "SOLD_OUT";
                    } else if (occupancyPercentage >= 80.0) {
                        status = "HIGH_DEMAND";
                    }
                }

                // C-12: Only return active, selectable slots
                if (isSelectable) {
                    slots.add(new TimeSlotAvailability(
                            currentSlotStart,
                            currentSlotEnd,
                            date,
                            maxCapacity,
                            bookedCount,
                            occupancyPercentage,
                            status,
                            isSelectable
                    ));
                }

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

