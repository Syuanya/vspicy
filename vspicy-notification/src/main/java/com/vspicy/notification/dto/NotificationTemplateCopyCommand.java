package com.vspicy.notification.dto;

public record NotificationTemplateCopyCommand(
        String templateCode,
        String templateName,
        Boolean enabled
) {
}
