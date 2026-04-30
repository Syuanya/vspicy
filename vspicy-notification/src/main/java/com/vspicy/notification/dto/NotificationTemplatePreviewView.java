package com.vspicy.notification.dto;

public record NotificationTemplatePreviewView(
        String title,
        String content,
        String notificationType,
        String bizType,
        String priority
) {
}
