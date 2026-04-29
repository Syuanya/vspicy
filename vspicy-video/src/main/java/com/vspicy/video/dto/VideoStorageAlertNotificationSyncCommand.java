package com.vspicy.video.dto;

public record VideoStorageAlertNotificationSyncCommand(
        Integer limit,
        String level,
        Long targetUserId
) {
}
