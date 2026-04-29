package com.vspicy.video.dto;

public record VideoStorageAlertNotificationSyncResult(
        Long selectedAlertCount,
        Long outboxCreatedCount,
        Long sentCount,
        Long skippedCount,
        Long failedCount,
        String notificationTable,
        String message
) {
}
