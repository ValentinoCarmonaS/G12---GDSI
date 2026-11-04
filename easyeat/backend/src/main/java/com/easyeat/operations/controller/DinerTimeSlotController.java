package com.easyeat.operations.controller;

import com.easyeat.operations.dto.DinerTimeSlotsResponse;
import com.easyeat.operations.service.DinerTimeSlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Any authenticated user may read the slots of an establishment; the contract demands a bearer
 * token but no particular role, and the global rule of {@code SecurityConfig} already enforces it.
 */
@RestController
@RequestMapping("/api/v1/establishments/{tenantId}/time-slots")
public class DinerTimeSlotController {

    private final DinerTimeSlotService timeSlotService;

    public DinerTimeSlotController(DinerTimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @GetMapping
    public DinerTimeSlotsResponse getTimeSlots(
            @PathVariable UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return DinerTimeSlotsResponse.from(timeSlotService.availabilityFor(tenantId, date));
    }
}
