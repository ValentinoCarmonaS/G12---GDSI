package com.easyeat.catalog.service;

import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.repository.EstablishmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EstablishmentDiscoveryService {

    private final EstablishmentRepository establishmentRepository;

    public EstablishmentDiscoveryService(EstablishmentRepository establishmentRepository) {
        this.establishmentRepository = establishmentRepository;
    }

    public List<EstablishmentSummaryResponse> searchEstablishments(String query) {
        throw new UnsupportedOperationException("TDD RED: not implemented yet");
    }
}
