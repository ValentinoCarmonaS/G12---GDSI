package com.easyeat.operations.service;

import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * The only time authority of the operations module (D-001): the server {@link Clock}
 * read in the timezone stored in {@code Establishment.timezone}. It never accepts a
 * time from the client (EC-018).
 */
@Component
public class EstablishmentTimeProvider {

    private final Clock clock;
    private final EstablishmentRepository establishmentRepository;

    public EstablishmentTimeProvider(Clock clock, EstablishmentRepository establishmentRepository) {
        this.clock = clock;
        this.establishmentRepository = establishmentRepository;
    }

    public ZoneId zoneOf(UUID establishmentId) {
        return establishmentRepository.findById(establishmentId)
                .map(establishment -> ZoneId.of(establishment.getTimezone()))
                .orElseThrow(() -> new EstablishmentNotFoundException(establishmentId));
    }

    public ZonedDateTime nowAt(UUID establishmentId) {
        return ZonedDateTime.now(clock.withZone(zoneOf(establishmentId)));
    }
}
