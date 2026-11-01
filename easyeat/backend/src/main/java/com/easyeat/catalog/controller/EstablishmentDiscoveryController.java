package com.easyeat.catalog.controller;

import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.service.EstablishmentDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/establishments")
public class EstablishmentDiscoveryController {

    private final EstablishmentDiscoveryService discoveryService;

    public EstablishmentDiscoveryController(EstablishmentDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<EstablishmentSummaryResponse>> searchEstablishments(@RequestParam("q") String query) {
        List<EstablishmentSummaryResponse> results = discoveryService.searchEstablishments(query);
        return ResponseEntity.ok(results);
    }

    @GetMapping
    public ResponseEntity<List<EstablishmentSummaryResponse>> getAllActiveEstablishments() {
        List<EstablishmentSummaryResponse> results = discoveryService.getAllActiveEstablishments();
        return ResponseEntity.ok(results);
    }
}

