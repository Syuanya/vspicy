package com.vspicy.notification.dto;

public record NotificationTemplateView(
        Long id,
        String templateCode,
        String templateName,
        String titleTemplate,
        String contentTemplate,
        String notificationType,
        String bizType,
        String priority,
        Boolean enabled,
        String remark,
        Long createdBy,
        Long updatedBy,
        String createdAt,
        String updatedAt
) {
}
