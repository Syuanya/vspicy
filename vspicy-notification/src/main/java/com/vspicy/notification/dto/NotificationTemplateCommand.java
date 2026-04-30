package com.vspicy.notification.dto;

public record NotificationTemplateCommand(
        String templateCode,
        String templateName,
        String titleTemplate,
        String contentTemplate,
        String notificationType,
        String bizType,
        String priority,
        Boolean enabled,
        String remark
) {
}
