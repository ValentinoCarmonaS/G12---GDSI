package com.easyeat.notification.service;

import com.easyeat.notification.domain.AdminNotification;
import com.easyeat.notification.domain.NotificationType;
import com.easyeat.notification.dto.NotificationPageResponse;
import com.easyeat.notification.dto.NotificationResponse;
import com.easyeat.notification.repository.AdminNotificationRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminNotificationService {

    private final AdminNotificationRepository adminNotificationRepository;

    public AdminNotificationService(AdminNotificationRepository adminNotificationRepository) {
        this.adminNotificationRepository = adminNotificationRepository;
    }

    @Transactional
    public AdminNotification createNotification(UUID tenantId, NotificationType type, String title, String message, UUID dishId, UUID chefId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(boolean unreadOnly, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
