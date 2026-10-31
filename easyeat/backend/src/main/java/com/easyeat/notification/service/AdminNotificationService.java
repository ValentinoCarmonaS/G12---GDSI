package com.easyeat.notification.service;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.dto.NotificationPageResponse;
import com.easyeat.notification.dto.NotificationResponse;
import com.easyeat.notification.repository.AdminNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AdminNotificationService {

    private final AdminNotificationRepository adminNotificationRepository;

    public AdminNotificationService(AdminNotificationRepository adminNotificationRepository) {
        this.adminNotificationRepository = adminNotificationRepository;
    }

    @Transactional
    public AdminNotification createNotification(UUID tenantId, NotificationType type, String title, String message, UUID dishId, UUID chefId) {
        AdminNotification notification = new AdminNotification(tenantId, type, title, message, dishId, chefId);
        return adminNotificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(boolean unreadOnly, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<AdminNotification> page;
        if (unreadOnly) {
            page = adminNotificationRepository.findByTenantIdAndIsRead(tenantId, false, pageable);
        } else {
            page = adminNotificationRepository.findByTenantId(tenantId, pageable);
        }

        long unreadCount = adminNotificationRepository.countByTenantIdAndIsReadFalse(tenantId);
        List<NotificationResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return new NotificationPageResponse(
                content,
                unreadCount,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        AdminNotification notification = adminNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notificación no encontrada: " + notificationId));

        if (!notification.getTenantId().equals(tenantId)) {
            throw new NoSuchElementException("Notificación no encontrada para este establecimiento: " + notificationId);
        }

        notification.markAsRead();
        AdminNotification saved = adminNotificationRepository.save(notification);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return adminNotificationRepository.countByTenantIdAndIsReadFalse(tenantId);
    }

    private NotificationResponse mapToResponse(AdminNotification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getType() != null ? entity.getType().name() : null,
                entity.getTitle(),
                entity.getMessage(),
                entity.getDishId(),
                entity.getChefId(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}
