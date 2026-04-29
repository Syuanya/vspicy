package com.vspicy.notification.dto;

import java.util.List;

public record NotificationPreferenceSaveCommand(
        Long userId,
        List<NotificationPreferenceItem> preferences
) {
}
