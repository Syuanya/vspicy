package com.vspicy.notification.dto;

import java.util.List;
import java.util.Map;

public record NotificationTemplatePublishCommand(
        List<Long> receiverUserIds,
        Map<String, String> variables,
        Long bizId,
        String bizType,
        String priority
) {
}
