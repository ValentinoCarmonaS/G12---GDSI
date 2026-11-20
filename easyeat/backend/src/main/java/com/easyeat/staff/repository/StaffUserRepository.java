package com.easyeat.staff.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffUserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByTenantId(UUID tenantId);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndRoleAndStatus(UUID tenantId, UserRole role, UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.role = :role AND u.status = :status")
    List<User> findActiveAdminsForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("role") UserRole role,
            @Param("status") UserStatus status);
}
