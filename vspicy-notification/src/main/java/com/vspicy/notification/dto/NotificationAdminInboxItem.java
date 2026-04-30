package com.vspicy.notification.dto;

public record NotificationAdminInboxItem(
        Long inboxId,
        Long messageId,
        Long userId,
        String username,
        String nickname,
        String title,
        String content,
        String notificationType,
        String bizType,
        Long bizId,
        String priority,
        String publishScope,
        String messageStatus,
        Integer readStatus,
        String readAt,
        Integer deleted,
        String deliveredAt,
        Long senderId,
        String messageCreatedAt
) {
}
