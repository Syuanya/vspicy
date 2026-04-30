package com.vspicy.notification.dto;

public record NotificationDailyStatItem(
        String day,
        Long messageCount,
        Long deliveryCount,
        Long readCount,
        Long unreadCount
) {
}
