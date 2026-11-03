package com.easyeat.operations.controller;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.operations.domain.SlotCapacityChange;
import com.easyeat.operations.dto.SlotCapacityAdminResponse;
import com.easyeat.operations.dto.UpdateSlotCapacityRequest;
import com.easyeat.operations.service.SlotCapacityService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/slot-capacities")
@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")
public class AdminSlotCapacityController {

    private final SlotCapacityService capacityService;

    public AdminSlotCapacityController(SlotCapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @GetMapping
    public List<SlotCapacityAdminResponse> getSlotCapacities(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return capacityService.getDayMatrix(tenantId, date).stream().map(SlotCapacityAdminResponse::from).toList();
    }

    /** The list is the bulk-apply path: one request carries every slot to change (SC-005). */
    @PutMapping
    public List<SlotCapacityAdminResponse> updateSlotCapacities(@RequestBody List<@Valid UpdateSlotCapacityRequest> request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<SlotCapacityChange> changes = request.stream().map(UpdateSlotCapacityRequest::toChange).toList();
        return capacityService.saveCapacities(tenantId, changes).stream().map(SlotCapacityAdminResponse::from).toList();
    }
}
