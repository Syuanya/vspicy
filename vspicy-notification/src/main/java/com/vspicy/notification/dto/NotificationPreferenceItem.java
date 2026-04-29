package com.vspicy.notification.dto;

public record NotificationPreferenceItem(
        String notificationType,
        String notificationName,
        Boolean enabled,
        Boolean forced
) {
}
