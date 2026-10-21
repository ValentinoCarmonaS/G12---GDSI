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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

@Service
public class StaffService {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_+=";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public StaffRegistrationResponse registerStaff(StaffRegistrationRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Active tenant context is required for staff registration");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (staffUserRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        String temporaryCredential = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(temporaryCredential);

        UserRole userRole = mapStaffRoleToUserRole(request.role());

        User user = new User(
                normalizedEmail,
                passwordHash,
                userRole,
                tenantId,
                request.fullName().trim(),
                true
        );
        user.setStatus(UserStatus.PENDING_FIRST_LOGIN);

        User savedUser = staffUserRepository.save(user);

        return new StaffRegistrationResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                mapUserRoleToStaffRole(savedUser.getRole()),
                mapUserStatusToStaffAccountStatus(savedUser.getStatus()),
                temporaryCredential,
                savedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> listStaff() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Active tenant context is required to query staff members");
        }

        return staffUserRepository.findAllByTenantId(tenantId).stream()
                .map(this::toStaffMemberResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffMemberResponse getStaffMember(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Active tenant context is required to query staff member");
        }

        User user = staffUserRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Staff member not found with ID: " + id));

        return toStaffMemberResponse(user);
    }

    @Transactional
    public StaffMemberResponse updateStaff(UUID id, StaffUpdateRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new TenantViolationException("Active tenant context is required to update staff member");
        }

        User user = staffUserRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Staff member not found with ID: " + id));

        UserRole targetRole = mapStaffRoleToUserRole(request.role());

        if (user.getRole() == UserRole.ADMINISTRADOR && targetRole != UserRole.ADMINISTRADOR) {
            long activeAdminCount = staffUserRepository.countByTenantIdAndRoleAndStatus(
                    tenantId, UserRole.ADMINISTRADOR, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new OrphanedAdminException(tenantId,
                        "Cannot reassign role: cannot demote the last active administrator for tenant " + tenantId);
            }
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && staffUserRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setRole(targetRole);

        User savedUser = staffUserRepository.save(user);
        return toStaffMemberResponse(savedUser);
    }

    private StaffMemberResponse toStaffMemberResponse(User user) {
        return new StaffMemberResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                mapUserRoleToStaffRole(user.getRole()),
                mapUserStatusToStaffAccountStatus(user.getStatus()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private UserRole mapStaffRoleToUserRole(StaffRole role) {
        if (role == null) {
            return UserRole.ENCARGADO;
        }
        return switch (role) {
            case ADMINISTRADOR -> UserRole.ADMINISTRADOR;
            case CHEF -> UserRole.CHEF;
            case ENCARGADO -> UserRole.ENCARGADO;
        };
    }

    private StaffRole mapUserRoleToStaffRole(UserRole role) {
        if (role == null) {
            return StaffRole.ENCARGADO;
        }
        return switch (role) {
            case ADMINISTRADOR -> StaffRole.ADMINISTRADOR;
            case CHEF -> StaffRole.CHEF;
            case ENCARGADO, COMENSAL -> StaffRole.ENCARGADO;
        };
    }

    private StaffAccountStatus mapUserStatusToStaffAccountStatus(UserStatus status) {
        if (status == null) {
            return StaffAccountStatus.ACTIVE;
        }
        return switch (status) {
            case PENDING_FIRST_LOGIN -> StaffAccountStatus.PENDING_FIRST_LOGIN;
            case ACTIVE, UNVERIFIED -> StaffAccountStatus.ACTIVE;
            case SUSPENDED -> StaffAccountStatus.SUSPENDED;
        };
    }

    public static String generateTemporaryPassword() {
        char[] password = new char[12];
        password[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        password[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        password[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length()));
        for (int i = 4; i < 12; i++) {
            password[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        }

        for (int i = 0; i < password.length; i++) {
            int j = RANDOM.nextInt(password.length);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }
        return new String(password);
    }
}
