package com.vspicy.notification.dto;

public record NotificationTemplatePublishCheckView(
        Long templateId,
        String templateCode,
        String templateName,
        String receiverMode,
        Long receiverCount,
        String title,
        String content,
        String notificationType,
        String bizType,
        String priority,
        NotificationTemplateValidationView validation
) {
}
