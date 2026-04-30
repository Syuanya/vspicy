package com.vspicy.notification.dto;

import java.util.List;

public record NotificationTemplateValidationView(
        Boolean valid,
        List<String> requiredVariables,
        List<String> missingVariables,
        List<String> extraVariables
) {
}
