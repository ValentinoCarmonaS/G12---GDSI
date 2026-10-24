package com.easyeat.staff.controller;

import com.easyeat.staff.dto.StaffMemberResponse;
import com.easyeat.staff.dto.StaffRegistrationRequest;
import com.easyeat.staff.dto.StaffRegistrationResponse;
import com.easyeat.staff.dto.StaffUpdateRequest;
import com.easyeat.staff.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping
    public ResponseEntity<StaffRegistrationResponse> registerStaff(
            @Valid @RequestBody StaffRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.registerStaff(request));
    }

    @GetMapping
    public ResponseEntity<List<StaffMemberResponse>> listStaff() {
        return ResponseEntity.ok(staffService.listStaff());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffMemberResponse> getStaffMember(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.getStaffMember(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffMemberResponse> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody StaffUpdateRequest request) {
        return ResponseEntity.ok(staffService.updateStaff(id, request));
    }
}
