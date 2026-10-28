package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class EstablishmentDiscoveryService {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentDiscoveryService(EstablishmentRepository establishmentRepository) {
        this.establishmentRepository = establishmentRepository;
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
                .map(est -> new EstablishmentSummaryResponse(
                        est.getId(),
                        est.getName(),
                        est.getStatus(),
                        est.getOpeningTime(),
                        est.getClosingTime(),
                        est.getTimezone(),
                        est.isOpen()
                ))
                .toList();
    }
}
