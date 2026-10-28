package com.easyeat.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        long unreadCount,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
