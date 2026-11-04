package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.dto.PickupSlotCatalogResponse;
import com.easyeat.order.dto.TimeSlotAvailability;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.repository.OrderRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class PickupSlotService {

    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public PickupSlotService(EstablishmentRepository establishmentRepository,
                             OrderRepository orderRepository,
                             Clock clock) {
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
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
        boolean isEstablishmentOpenNow = establishment.isOpen(nowTime);
        int leadTimeMinutes = establishment.getLeadTimeMinutes() != null ? establishment.getLeadTimeMinutes() : 30;
        int maxCapacity = establishment.getMaxCapacityPerSlot() != null ? establishment.getMaxCapacityPerSlot() : 10;

        List<TimeSlotAvailability> slots = new ArrayList<>();

        if (isOperatingDay && establishment.getOpeningTime() != null && establishment.getClosingTime() != null) {
            LocalTime currentSlotStart = establishment.getOpeningTime();
            LocalTime closingTime = establishment.getClosingTime();

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

