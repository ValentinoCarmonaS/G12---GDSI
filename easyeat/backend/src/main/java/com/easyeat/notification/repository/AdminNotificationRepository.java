package com.easyeat.notification.repository;

import com.easyeat.notification.domain.AdminNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, UUID> {

    Page<AdminNotification> findByTenantId(UUID tenantId, Pageable pageable);

    Page<AdminNotification> findByTenantIdAndIsRead(UUID tenantId, Boolean isRead, Pageable pageable);

    List<AdminNotification> findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndIsReadFalse(UUID tenantId);
}
