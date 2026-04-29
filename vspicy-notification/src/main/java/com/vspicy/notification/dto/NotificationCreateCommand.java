package com.vspicy.notification.dto;

import java.util.List;

public record NotificationCreateCommand(
        String title,
        String content,
        String notificationType,
        String bizType,
        Long bizId,
        String priority,
        List<Long> receiverUserIds
) {
}
