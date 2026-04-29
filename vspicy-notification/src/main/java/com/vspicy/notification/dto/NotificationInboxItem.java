package com.vspicy.notification.dto;

public record NotificationInboxItem(
        Long inboxId,
        Long messageId,
        Long userId,
        String title,
        String content,
        String notificationType,
        String bizType,
        Long bizId,
        String priority,
        Integer readStatus,
        String readAt,
        String createdAt
) {
}
