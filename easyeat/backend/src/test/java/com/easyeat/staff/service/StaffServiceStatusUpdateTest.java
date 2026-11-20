package com.easyeat.staff.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.staff.dto.StaffAccountStatus;
import com.easyeat.staff.dto.StaffMemberResponse;
import com.easyeat.staff.dto.StaffStatusUpdateRequest;
import com.easyeat.staff.exception.OrphanedAdminException;
import com.easyeat.staff.repository.StaffUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceStatusUpdateTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private StaffService staffService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffUserRepository, passwordEncoder);
        tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void updateStaffStatus_suspendsChefSuccessfully() {
        UUID chefId = UUID.randomUUID();
        User chef = new User();
        chef.setId(chefId);
        chef.setTenantId(tenantId);
        chef.setFullName("Carlos Chef");
        chef.setEmail("carlos@easyeat.com");
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findByIdAndTenantId(chefId, tenantId)).thenReturn(Optional.of(chef));
        when(staffUserRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffMemberResponse response = staffService.updateStaffStatus(
                chefId, new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED));

        assertEquals(StaffAccountStatus.SUSPENDED, response.status());
        assertEquals(UserStatus.SUSPENDED, chef.getStatus());
        verify(staffUserRepository).save(chef);
    }

    @Test
    void updateStaffStatus_reactivatesSuspendedStaffSuccessfully() {
        UUID chefId = UUID.randomUUID();
        User chef = new User();
        chef.setId(chefId);
        chef.setTenantId(tenantId);
        chef.setFullName("Carlos Chef");
        chef.setEmail("carlos@easyeat.com");
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.SUSPENDED);

        when(staffUserRepository.findByIdAndTenantId(chefId, tenantId)).thenReturn(Optional.of(chef));
        when(staffUserRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffMemberResponse response = staffService.updateStaffStatus(
                chefId, new StaffStatusUpdateRequest(StaffAccountStatus.ACTIVE));

        assertEquals(StaffAccountStatus.ACTIVE, response.status());
        assertEquals(UserStatus.ACTIVE, chef.getStatus());
        verify(staffUserRepository).save(chef);
    }

    @Test
    void updateStaffStatus_throwsOrphanedAdminException_whenSuspendingLastActiveAdmin() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setTenantId(tenantId);
        admin.setFullName("Unico Admin");
        admin.setEmail("admin@easyeat.com");
        admin.setRole(UserRole.ADMINISTRADOR);
        admin.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(admin));
        when(staffUserRepository.findActiveAdminsForUpdate(tenantId, UserRole.ADMINISTRADOR, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        assertThrows(
                OrphanedAdminException.class,
                () -> staffService.updateStaffStatus(adminId, new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))
        );
    }

    @Test
    void updateStaffStatus_suspendsAdminSuccessfully_whenAtLeastTwoActiveAdminsExist() {
        UUID admin1Id = UUID.randomUUID();
        User admin1 = new User();
        admin1.setId(admin1Id);
        admin1.setTenantId(tenantId);
        admin1.setFullName("Admin Uno");
        admin1.setEmail("admin1@easyeat.com");
        admin1.setRole(UserRole.ADMINISTRADOR);
        admin1.setStatus(UserStatus.ACTIVE);

        User admin2 = new User();
        admin2.setId(UUID.randomUUID());
        admin2.setTenantId(tenantId);
        admin2.setFullName("Admin Dos");
        admin2.setEmail("admin2@easyeat.com");
        admin2.setRole(UserRole.ADMINISTRADOR);
        admin2.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findByIdAndTenantId(admin1Id, tenantId)).thenReturn(Optional.of(admin1));
        when(staffUserRepository.findActiveAdminsForUpdate(tenantId, UserRole.ADMINISTRADOR, UserStatus.ACTIVE))
                .thenReturn(List.of(admin1, admin2));
        when(staffUserRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        StaffMemberResponse response = staffService.updateStaffStatus(
                admin1Id, new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED));

        assertEquals(StaffAccountStatus.SUSPENDED, response.status());
        assertEquals(UserStatus.SUSPENDED, admin1.getStatus());
        verify(staffUserRepository).save(admin1);
    }
}
