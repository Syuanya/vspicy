package com.vspicy.video.dto;

public record VideoStorageAlertNotificationView(
        Long id,
        Long alertId,
        String dedupKey,
        Long targetUserId,
        String title,
        String content,
        String alertLevel,
        String alertCode,
        String objectKey,
        String status,
        String notificationTable,
        String notificationId,
        String errorMessage,
        Integer retryCount,
        String sentAt,
        String createdAt,
        String updatedAt
) {
}
