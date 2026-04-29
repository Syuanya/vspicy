package com.vspicy.notification.dto;

public record BusinessNotificationEventCommand(
        Long receiverUserId,
        Long bizId,
        String title,
        String content,
        String actorName,
        String result,
        String reason,
        String priority
) {
}
