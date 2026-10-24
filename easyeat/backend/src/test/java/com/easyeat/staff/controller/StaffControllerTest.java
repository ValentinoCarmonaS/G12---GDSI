package com.easyeat.staff.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.staff.dto.*;
import com.easyeat.staff.exception.OrphanedAdminException;
import com.easyeat.staff.service.StaffService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.easyeat.auth.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StaffService staffService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("POST /api/v1/admin/staff - 201 Created on valid staff registration")
    void registerStaff_success() throws Exception {
        UUID staffId = UUID.randomUUID();
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "Carlos Chef",
                "carlos@easyeat.com",
                StaffRole.CHEF
        );

        StaffRegistrationResponse response = new StaffRegistrationResponse(
                staffId,
                "Carlos Chef",
                "carlos@easyeat.com",
                StaffRole.CHEF,
                StaffAccountStatus.PENDING_FIRST_LOGIN,
                "TempPass123!#",
                Instant.now()
        );

        when(staffService.registerStaff(any(StaffRegistrationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(staffId.toString()))
                .andExpect(jsonPath("$.fullName").value("Carlos Chef"))
                .andExpect(jsonPath("$.email").value("carlos@easyeat.com"))
                .andExpect(jsonPath("$.role").value("CHEF"))
                .andExpect(jsonPath("$.status").value("PENDING_FIRST_LOGIN"))
                .andExpect(jsonPath("$.temporaryCredential").value("TempPass123!#"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("POST /api/v1/admin/staff - 422 Unprocessable Entity when validation fails")
    void registerStaff_validationError() throws Exception {
        // Missing name and invalid email
        StaffRegistrationRequest invalidRequest = new StaffRegistrationRequest("", "not-an-email", null);

        mockMvc.perform(post("/api/v1/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("POST /api/v1/admin/staff - 409 Conflict when email is already registered")
    void registerStaff_conflictDuplicateEmail() throws Exception {
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "Existing User",
                "duplicate@easyeat.com",
                StaffRole.ENCARGADO
        );

        when(staffService.registerStaff(any(StaffRegistrationRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("duplicate@easyeat.com"));

        mockMvc.perform(post("/api/v1/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email Already Registered"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /api/v1/admin/staff - 200 OK returns staff roster")
    void listStaff_success() throws Exception {
        StaffMemberResponse member = new StaffMemberResponse(
                UUID.randomUUID(),
                "Ana Staff",
                "ana@easyeat.com",
                StaffRole.ENCARGADO,
                StaffAccountStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        when(staffService.listStaff()).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("ana@easyeat.com"))
                .andExpect(jsonPath("$[0].role").value("ENCARGADO"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("GET /api/v1/admin/staff/{id} - 404 Not Found when staff member does not exist")
    void getStaffMember_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(staffService.getStaffMember(id)).thenThrow(new NoSuchElementException("Staff member not found"));

        mockMvc.perform(get("/api/v1/admin/staff/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("PUT /api/v1/admin/staff/{id} - 409 Conflict on OrphanedAdminException")
    void updateStaff_orphanedAdminConflict() throws Exception {
        UUID id = UUID.randomUUID();
        StaffUpdateRequest updateRequest = new StaffUpdateRequest("Demoted Admin", "admin@easyeat.com", StaffRole.CHEF);

        when(staffService.updateStaff(eq(id), any(StaffUpdateRequest.class)))
                .thenThrow(new OrphanedAdminException(UUID.randomUUID(), "Cannot reassign role: establishment must retain at least one active administrator."));

        mockMvc.perform(put("/api/v1/admin/staff/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Orphaned Administrator Constraint Violated"));
    }
}
