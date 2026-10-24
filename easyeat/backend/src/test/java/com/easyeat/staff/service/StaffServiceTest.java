package com.easyeat.staff.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.staff.dto.*;
import com.easyeat.staff.exception.OrphanedAdminException;
import com.easyeat.staff.repository.StaffUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffService staffService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Should successfully register staff member with 12-char temp password and PENDING_FIRST_LOGIN")
    void registerStaff_success() {
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "Elena Gomez",
                "Elena.Gomez@EasyEat.com",
                StaffRole.CHEF
        );

        when(staffUserRepository.existsByEmail("elena.gomez@easyeat.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_temp_password");
        when(staffUserRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        StaffRegistrationResponse response = staffService.registerStaff(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.fullName()).isEqualTo("Elena Gomez");
        assertThat(response.email()).isEqualTo("elena.gomez@easyeat.com");
        assertThat(response.role()).isEqualTo(StaffRole.CHEF);
        assertThat(response.status()).isEqualTo(StaffAccountStatus.PENDING_FIRST_LOGIN);
        assertThat(response.temporaryCredential()).isNotNull().hasSize(12);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(staffUserRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("elena.gomez@easyeat.com");
        assertThat(savedUser.getFullName()).isEqualTo("Elena Gomez");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CHEF);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_FIRST_LOGIN);
        assertThat(savedUser.getMustChangePassword()).isTrue();
        assertThat(savedUser.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should reject staff registration with EmailAlreadyRegisteredException when email already exists")
    void registerStaff_duplicateEmail_throwsEmailAlreadyRegisteredException() {
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "Duplicate User",
                "existing@easyeat.com",
                StaffRole.ENCARGADO
        );

        when(staffUserRepository.existsByEmail("existing@easyeat.com")).thenReturn(true);

        assertThatThrownBy(() -> staffService.registerStaff(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("existing@easyeat.com");

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw TenantViolationException when registering staff without tenant context")
    void registerStaff_withoutTenantContext_throwsTenantViolationException() {
        TenantContextHolder.clear();
        StaffRegistrationRequest request = new StaffRegistrationRequest(
                "No Tenant User",
                "notenant@easyeat.com",
                StaffRole.CHEF
        );

        assertThatThrownBy(() -> staffService.registerStaff(request))
                .isInstanceOf(TenantViolationException.class);

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should list all staff members belonging to the current tenant")
    void listStaff_returnsTenantStaffRoster() {
        User chef = new User("chef@easyeat.com", "hash", UserRole.CHEF, tenantId, "Chef Mario", false);
        chef.setId(UUID.randomUUID());
        chef.setStatus(UserStatus.ACTIVE);

        User clerk = new User("clerk@easyeat.com", "hash", UserRole.ENCARGADO, tenantId, "Clerk Ana", false);
        clerk.setId(UUID.randomUUID());
        clerk.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findAllByTenantId(tenantId)).thenReturn(List.of(chef, clerk));

        List<StaffMemberResponse> roster = staffService.listStaff();

        assertThat(roster).hasSize(2);
        assertThat(roster.get(0).email()).isEqualTo("chef@easyeat.com");
        assertThat(roster.get(0).role()).isEqualTo(StaffRole.CHEF);
        assertThat(roster.get(1).email()).isEqualTo("clerk@easyeat.com");
        assertThat(roster.get(1).role()).isEqualTo(StaffRole.ENCARGADO);
    }

    @Test
    @DisplayName("Should throw OrphanedAdminException when demoting the last active administrator of the establishment")
    void updateStaff_demotingSoleAdmin_throwsOrphanedAdminException() {
        UUID adminId = UUID.randomUUID();
        User soleAdmin = new User("admin@easyeat.com", "hash", UserRole.ADMINISTRADOR, tenantId, "Sole Admin", false);
        soleAdmin.setId(adminId);
        soleAdmin.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(soleAdmin));
        // Only 1 active administrator exists in this establishment
        when(staffUserRepository.countByTenantIdAndRoleAndStatus(tenantId, UserRole.ADMINISTRADOR, UserStatus.ACTIVE)).thenReturn(1L);

        // Attempting to demote sole admin to ENCARGADO
        StaffUpdateRequest updateRequest = new StaffUpdateRequest("Sole Admin", "admin@easyeat.com", StaffRole.ENCARGADO);

        assertThatThrownBy(() -> staffService.updateStaff(adminId, updateRequest))
                .isInstanceOf(OrphanedAdminException.class)
                .hasMessageContaining("last active administrator");

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow demoting an administrator when at least one other active admin exists")
    void updateStaff_demotingAdmin_succeedsWhenMultipleAdminsExist() {
        UUID adminId = UUID.randomUUID();
        User adminToDemote = new User("admin1@easyeat.com", "hash", UserRole.ADMINISTRADOR, tenantId, "Admin One", false);
        adminToDemote.setId(adminId);
        adminToDemote.setStatus(UserStatus.ACTIVE);

        when(staffUserRepository.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminToDemote));
        when(staffUserRepository.countByTenantIdAndRoleAndStatus(tenantId, UserRole.ADMINISTRADOR, UserStatus.ACTIVE)).thenReturn(2L);
        when(staffUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffUpdateRequest updateRequest = new StaffUpdateRequest("Admin One Renamed", "admin1@easyeat.com", StaffRole.CHEF);

        StaffMemberResponse updated = staffService.updateStaff(adminId, updateRequest);

        assertThat(updated.role()).isEqualTo(StaffRole.CHEF);
        assertThat(adminToDemote.getRole()).isEqualTo(UserRole.CHEF);
        assertThat(adminToDemote.getFullName()).isEqualTo("Admin One Renamed");
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when staff member not found in tenant")
    void getStaffMember_notFound_throwsNoSuchElementException() {
        UUID randomId = UUID.randomUUID();
        when(staffUserRepository.findByIdAndTenantId(randomId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaffMember(randomId))
                .isInstanceOf(NoSuchElementException.class);
    }
}
