package com.easyeat.staff.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
