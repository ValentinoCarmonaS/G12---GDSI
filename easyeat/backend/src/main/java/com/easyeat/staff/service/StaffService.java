package com.easyeat.staff.service;

import com.easyeat.staff.dto.StaffMemberResponse;
import com.easyeat.staff.dto.StaffRegistrationRequest;
import com.easyeat.staff.dto.StaffRegistrationResponse;
import com.easyeat.staff.dto.StaffUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StaffService {

    public StaffRegistrationResponse registerStaff(StaffRegistrationRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<StaffMemberResponse> listStaff() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public StaffMemberResponse getStaffMember(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public StaffMemberResponse updateStaff(UUID id, StaffUpdateRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
