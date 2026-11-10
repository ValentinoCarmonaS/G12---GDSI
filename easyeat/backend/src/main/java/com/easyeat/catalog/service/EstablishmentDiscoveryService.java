package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class EstablishmentDiscoveryService {

    private final EstablishmentRepository establishmentRepository;
    private final OperatingScheduleRepository operatingScheduleRepository;

    public EstablishmentDiscoveryService(EstablishmentRepository establishmentRepository) {
        this(establishmentRepository, null);
    }

    @Autowired
    public EstablishmentDiscoveryService(
            EstablishmentRepository establishmentRepository,
            @Autowired(required = false) OperatingScheduleRepository operatingScheduleRepository) {
        this.establishmentRepository = establishmentRepository;
        this.operatingScheduleRepository = operatingScheduleRepository;
    }

    @Transactional(readOnly = true)
    public List<EstablishmentSummaryResponse> searchEstablishments(String query) {
        if (query == null || query.trim().length() < 3) {
            throw new InvalidSearchQueryException("Search query must contain at least 3 non-whitespace characters.");
        }

        String sanitizedQuery = query.trim();
        List<Establishment> matches = establishmentRepository.searchActiveByName(sanitizedQuery);

        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }

        return matches.stream()
                .filter(est -> est.getStatus() == EstablishmentStatus.ACTIVE)
                .map(this::mapToSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EstablishmentSummaryResponse> getAllActiveEstablishments() {
        List<Establishment> activeEstablishments = establishmentRepository.findAllByStatus(EstablishmentStatus.ACTIVE);

        if (activeEstablishments == null || activeEstablishments.isEmpty()) {
            return Collections.emptyList();
        }

        return activeEstablishments.stream()
                .map(this::mapToSummary)
                .toList();
    }

    private EstablishmentSummaryResponse mapToSummary(Establishment est) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(est.getTimezone() != null ? est.getTimezone() : "America/Argentina/Buenos_Aires");
        } catch (Exception e) {
            zoneId = ZoneId.of("America/Argentina/Buenos_Aires");
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalTime currentTime = now.toLocalTime();

        LocalTime opening = est.getOpeningTime() != null ? est.getOpeningTime() : LocalTime.of(11, 0);
        LocalTime closing = est.getClosingTime() != null ? est.getClosingTime() : LocalTime.of(23, 0);
        boolean isClosedToday = false;

        if (operatingScheduleRepository != null) {
            try {
                var scheduleOpt = operatingScheduleRepository.findByEstablishmentIdAndDayOfWeek(est.getId(), now.getDayOfWeek());
                if (scheduleOpt.isPresent()) {
                    var sched = scheduleOpt.get();
                    if (sched.isClosed()) {
                        isClosedToday = true;
                    } else {
                        opening = sched.getOpeningTime();
                        closing = sched.getClosingTime();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        boolean isOpen;
        if (est.getStatus() != EstablishmentStatus.ACTIVE || isClosedToday) {
            isOpen = false;
        } else if (opening.equals(closing)) {
            isOpen = true;
        } else if (opening.isBefore(closing)) {
            isOpen = !currentTime.isBefore(opening) && !currentTime.isAfter(closing);
        } else {
            isOpen = !currentTime.isBefore(opening) || !currentTime.isAfter(closing);
        }

        return new EstablishmentSummaryResponse(
                est.getId(),
                est.getName(),
                est.getStatus(),
                opening,
                closing,
                est.getTimezone(),
                isOpen
        );
    }
}

