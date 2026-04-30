package com.vspicy.notification.dto;

import java.util.List;

public record NotificationPreferenceSaveCommand(
        List<NotificationPreferenceItem> preferences
) {
}
