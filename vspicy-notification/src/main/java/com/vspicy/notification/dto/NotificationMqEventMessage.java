package com.vspicy.notification.dto;

public record NotificationMqEventMessage(
        String eventId,
        String eventType,
        Long receiverUserId,
        Long bizId,
        String title,
        String content,
        String actorName,
        String result,
        String reason,
        String priority,
        Long senderId,
        Long createdAt
) {
}
