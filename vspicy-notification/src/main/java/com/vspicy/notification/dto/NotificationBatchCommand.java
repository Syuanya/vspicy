package com.vspicy.notification.dto;

import java.util.List;

public record NotificationBatchCommand(
        List<Long> inboxIds
) {
}
