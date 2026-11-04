package com.easyeat.operations.controller;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.operations.domain.ScheduleChange;
import com.easyeat.operations.dto.OperatingScheduleResponse;
import com.easyeat.operations.dto.UpdateScheduleRequest;
import com.easyeat.operations.service.OperatingScheduleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/operating-schedules")
@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")
public class AdminOperatingScheduleController {

    private final OperatingScheduleService scheduleService;

    public AdminOperatingScheduleController(OperatingScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<OperatingScheduleResponse> getSchedules() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return scheduleService.getWeek(tenantId).stream().map(OperatingScheduleResponse::from).toList();
    }

    @PutMapping
    public List<OperatingScheduleResponse> updateSchedules(@RequestBody List<@Valid UpdateScheduleRequest> request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<ScheduleChange> changes = request.stream().map(UpdateScheduleRequest::toChange).toList();
        return scheduleService.saveWeek(tenantId, changes).stream().map(OperatingScheduleResponse::from).toList();
    }
}
