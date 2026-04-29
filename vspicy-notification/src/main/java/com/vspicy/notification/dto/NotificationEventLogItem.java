package com.vspicy.notification.dto;

public record NotificationEventLogItem(
        Long id,
        String eventId,
        String eventType,
        Long receiverUserId,
        Long bizId,
        Long messageId,
        String status,
        Integer retryCount,
        String errorMessage,
        String createdAt,
        String updatedAt
) {
}
