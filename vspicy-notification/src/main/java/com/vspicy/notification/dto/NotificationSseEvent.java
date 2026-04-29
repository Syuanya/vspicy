package com.vspicy.notification.dto;

public record NotificationSseEvent(
        String eventType,
        Long messageId,
        Long userId,
        String title,
        String content,
        String notificationType,
        String priority,
        long timestamp
) {
}
