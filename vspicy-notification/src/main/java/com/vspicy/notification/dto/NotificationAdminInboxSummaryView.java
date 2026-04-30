package com.vspicy.notification.dto;

public record NotificationAdminInboxSummaryView(
        Long userId,
        String username,
        String nickname,
        Long totalDeliveries,
        Long unreadDeliveries,
        Long readDeliveries,
        Long deletedDeliveries,
        Long highPriorityDeliveries,
        String latestDeliveredAt
) {
}
