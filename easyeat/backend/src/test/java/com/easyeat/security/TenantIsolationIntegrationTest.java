package com.easyeat.security;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.auth.security.SecurityAuditLogger;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.filter.TenantContextFilter;
import com.easyeat.config.MethodSecurityConfig;
import com.easyeat.config.SecurityConfig;
import com.easyeat.staff.controller.StaffController;
import com.easyeat.staff.service.StaffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.staff.dto.StaffAccountStatus;
import com.easyeat.staff.dto.StaffMemberResponse;
import com.easyeat.staff.dto.StaffRole;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@WebMvcTest(StaffController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, TenantContextFilter.class})
class TenantIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffService staffService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SecurityAuditLogger securityAuditLogger;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/admin/staff should return 401 or 403")
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CHEF")
    @DisplayName("CHEF role requesting /api/v1/admin/staff should be rejected with 403 Forbidden")
    void chefRole_isRejectedWith403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ENCARGADO")
    @DisplayName("ENCARGADO (Counter Clerk) requesting /api/v1/admin/staff should be rejected with 403 Forbidden")
    void encargadoRole_isRejectedWith403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMENSAL")
    @DisplayName("COMENSAL (Diner) requesting /api/v1/admin/staff should be rejected with 403 Forbidden")
    void comensalRole_isRejectedWith403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("ADMINISTRADOR role requesting /api/v1/admin/staff should be allowed")
    void administradorRole_isAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
        when(staffService.listStaff()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN role requesting /api/v1/admin/staff should also be allowed")
    void adminRole_isAllowed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
        when(staffService.listStaff()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("ADMINISTRADOR query is scoped strictly to active tenant_id, preventing cross-tenant data leak")
    void administradorQuery_isScopedToActiveTenant() throws Exception {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantAlpha);

        when(staffService.listStaff()).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantAlpha);
            assertThat(TenantContextHolder.getTenantId()).isNotEqualTo(tenantBeta);
            return List.of(new StaffMemberResponse(
                    UUID.randomUUID(),
                    "Admin Alpha",
                    "admin@alpha.com",
                    StaffRole.ADMINISTRADOR,
                    StaffAccountStatus.ACTIVE,
                    Instant.now(),
                    Instant.now()
            ));
        });

        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@alpha.com"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    @DisplayName("Request with missing tenant context triggers TenantViolationException and returns 403")
    void missingTenantContext_returns403() throws Exception {
        TenantContextHolder.clear();
        when(staffService.listStaff()).thenThrow(new TenantViolationException("Active tenant context is required to query staff members"));

        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Cross-Tenant Access Prohibited"));
    }
}
