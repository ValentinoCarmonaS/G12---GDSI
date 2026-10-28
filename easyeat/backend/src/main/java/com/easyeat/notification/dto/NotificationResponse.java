package com.easyeat.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID tenantId,
        String type,
        String title,
        String message,
        UUID dishId,
        UUID chefId,
        boolean isRead,
        Instant createdAt
) {
}
